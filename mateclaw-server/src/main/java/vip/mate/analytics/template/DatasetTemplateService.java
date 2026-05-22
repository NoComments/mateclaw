package vip.mate.analytics.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CRUD + field-lifecycle service for {@link DatasetTemplate}.
 *
 * <p>Protection invariant: once a template has real uploaded data
 * (SUCCESS/PARTIAL upload logs), fields can only be <em>appended</em> or have
 * their <em>metadata</em> updated — deletion is blocked.  Physical-column
 * attributes ({@code fieldCode}, {@code fieldType}, {@code isPartitionKey})
 * are never updatable via this service.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DatasetTemplateService {

    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;
    private final DatasetRepository datasetRepo;
    private final DatasetUploadLogRepository uploadLogRepo;

    /**
     * List all non-deleted templates belonging to the given workspace.
     *
     * @param workspaceId owning workspace
     * @return templates ordered by create_time ascending
     */
    @Transactional(readOnly = true)
    public List<DatasetTemplate> listByWorkspace(Long workspaceId) {
        return templateRepo.selectList(new LambdaQueryWrapper<DatasetTemplate>()
                .eq(DatasetTemplate::getWorkspaceId, workspaceId)
                .orderByAsc(DatasetTemplate::getCreateTime));
    }

    /**
     * Fetch a single template with its fields ordered by {@code ordinal}.
     *
     * @param templateId template to look up
     * @return the template + ordered field list, or {@code null} if not found
     */
    @Transactional(readOnly = true)
    public TemplateWithFields getWithFields(Long templateId) {
        DatasetTemplate t = templateRepo.selectById(templateId);
        if (t == null) return null;
        List<DatasetTemplateField> fields = fieldRepo.selectList(
                new LambdaQueryWrapper<DatasetTemplateField>()
                        .eq(DatasetTemplateField::getTemplateId, templateId)
                        .orderByAsc(DatasetTemplateField::getOrdinal));
        return new TemplateWithFields(t, fields);
    }

    /** Projection returned by {@link #getWithFields(Long)}. */
    public record TemplateWithFields(DatasetTemplate template, List<DatasetTemplateField> fields) {}

    /**
     * Create a template and atomically insert all its fields.
     *
     * <p>Side-effects:
     * <ul>
     *   <li>{@code code} is auto-generated from {@code name} when blank
     *       (see {@link #generateUniqueTemplateCode(Long, String)}).</li>
     *   <li>{@code physicalTable} is derived from the resolved {@code code} via {@link #toPhysicalTable(String)}.</li>
     *   <li>Per field: {@code fieldCode} is auto-generated from {@code fieldName} when blank,
     *       and {@code excelHeader} defaults to {@code fieldName} when blank.</li>
     *   <li>{@code enabled} defaults to {@code true} when not set.</li>
     *   <li>{@code templateId} is stamped on every field after the template row is inserted.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if {@code name} is blank, any field's {@code fieldName} is blank,
     *                                  or any field carries an unknown {@link FieldType}
     */
    public DatasetTemplate create(DatasetTemplate t, List<DatasetTemplateField> fields) {
        if (!StringUtils.hasText(t.getName())) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        // Validate eagerly — fail before any writes
        for (DatasetTemplateField f : fields) {
            if (!StringUtils.hasText(f.getFieldName())) {
                throw new IllegalArgumentException("字段名不能为空");
            }
            FieldType.fromString(f.getFieldType()); // throws IllegalArgumentException on bad value
        }

        if (!StringUtils.hasText(t.getCode())) {
            t.setCode(generateUniqueTemplateCode(t.getWorkspaceId(), t.getName()));
        }
        t.setPhysicalTable(toPhysicalTable(t.getCode()));
        if (t.getEnabled() == null) {
            t.setEnabled(true);
        }

        templateRepo.insert(t);

        // Within a single create() batch the template is freshly inserted, so any
        // fieldCode collision can only originate from this batch — track in a Set.
        Set<String> usedFieldCodes = new HashSet<>();
        for (DatasetTemplateField f : fields) {
            if (!StringUtils.hasText(f.getFieldCode())) {
                f.setFieldCode(generateUniqueFieldCodeInBatch(f.getFieldName(), usedFieldCodes));
            }
            usedFieldCodes.add(f.getFieldCode());
            if (!StringUtils.hasText(f.getExcelHeader())) {
                f.setExcelHeader(f.getFieldName());
            }
            f.setTemplateId(t.getId());
            fieldRepo.insert(f);
        }

        return t;
    }

    /**
     * List fields of a template ordered by {@code ordinal}.
     */
    @Transactional(readOnly = true)
    public List<DatasetTemplateField> listFields(Long templateId) {
        return fieldRepo.selectList(new LambdaQueryWrapper<DatasetTemplateField>()
                .eq(DatasetTemplateField::getTemplateId, templateId)
                .orderByAsc(DatasetTemplateField::getOrdinal));
    }

    /**
     * Soft-delete a template — only allowed when no datasets reference it.
     *
     * <p>Fields are also soft-deleted (logical-delete cascade).
     *
     * @throws IllegalStateException if any dataset still binds to this template
     */
    public void delete(Long templateId) {
        long count = datasetRepo.countByTemplateId(templateId);
        if (count > 0) {
            throw new IllegalStateException("模板下已有数据集，禁止删除");
        }
        fieldRepo.delete(new LambdaQueryWrapper<DatasetTemplateField>()
                .eq(DatasetTemplateField::getTemplateId, templateId));
        templateRepo.deleteById(templateId);
    }

    /**
     * Append a new field to an existing template.
     *
     * <p>Appending is always permitted regardless of whether datasets already exist.
     *
     * <p>Auto-derivation: when {@code fieldCode} is blank it is generated from {@code fieldName}
     * (uniqueness guaranteed within the template); when {@code excelHeader} is blank it defaults
     * to {@code fieldName}.
     *
     * @param templateId target template
     * @param field      new field to add; its {@code templateId} will be set here
     * @throws IllegalArgumentException if {@code fieldName} is blank or {@code fieldType} is unknown
     */
    public void appendField(Long templateId, DatasetTemplateField field) {
        if (!StringUtils.hasText(field.getFieldName())) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        FieldType.fromString(field.getFieldType());

        if (!StringUtils.hasText(field.getFieldCode())) {
            field.setFieldCode(generateUniqueFieldCode(templateId, field.getFieldName()));
        }
        if (!StringUtils.hasText(field.getExcelHeader())) {
            field.setExcelHeader(field.getFieldName());
        }
        field.setTemplateId(templateId);
        fieldRepo.insert(field);
    }

    /**
     * Update the display/metadata columns of a field.
     *
     * <p>Only safe, non-physical columns may be changed:
     * {@code fieldName}, {@code fieldUnit}, {@code semantic},
     * {@code excelHeader}, {@code ordinal}, {@code isNullable},
     * {@code role}, {@code timeGranularity}, {@code aggregation}, {@code computeHint}.
     * Physical-column attributes ({@code fieldCode}, {@code fieldType},
     * {@code isPartitionKey}) are intentionally excluded.
     *
     * @param templateId owning template (used to verify ownership)
     * @param fieldId    field to update
     * @param patch      new values; {@code null} means "leave unchanged"
     * @throws IllegalArgumentException if the field is not found under this template
     */
    public void updateFieldMeta(Long templateId, Long fieldId, UpdateFieldMetaRequest patch) {
        DatasetTemplateField f = fieldRepo.selectById(fieldId);
        if (f == null || !templateId.equals(f.getTemplateId())) {
            throw new IllegalArgumentException("Field not found: " + fieldId);
        }
        if (patch.fieldName() != null)       f.setFieldName(patch.fieldName());
        if (patch.fieldUnit() != null)       f.setFieldUnit(patch.fieldUnit());
        if (patch.semantic() != null)        f.setSemantic(patch.semantic());
        if (patch.excelHeader() != null)     f.setExcelHeader(patch.excelHeader());
        if (patch.ordinal() != null)         f.setOrdinal(patch.ordinal());
        if (patch.isNullable() != null)      f.setIsNullable(patch.isNullable());
        if (patch.role() != null)            f.setRole(patch.role());
        if (patch.timeGranularity() != null) f.setTimeGranularity(patch.timeGranularity());
        if (patch.aggregation() != null)     f.setAggregation(patch.aggregation());
        if (patch.computeHint() != null)     f.setComputeHint(patch.computeHint());
        fieldRepo.updateById(f);
    }

    /** Patch body for {@link #updateFieldMeta}. Null fields are left unchanged. */
    public record UpdateFieldMetaRequest(
            String fieldName,
            String fieldUnit,
            String semantic,
            String excelHeader,
            Integer ordinal,
            Boolean isNullable,
            String role,
            String timeGranularity,
            String aggregation,
            String computeHint
    ) {}

    /**
     * Remove a field — only allowed when the template has no successful uploads yet
     * (i.e. the physical table is still empty or not created).
     *
     * <p>Once real data has been written the column cannot be safely dropped.
     *
     * @throws IllegalStateException if the template already has successful upload data
     */
    public void removeField(Long templateId, Long fieldId) {
        long uploads = uploadLogRepo.countSuccessfulByTemplateId(templateId);
        if (uploads > 0) {
            throw new IllegalStateException("模板下已有真实上传数据，禁止删除字段（仅允许追加）");
        }
        fieldRepo.deleteById(fieldId);
    }

    /**
     * Enable or disable a template (soft toggle via the {@code enabled} flag).
     */
    public void setEnabled(Long templateId, boolean enabled) {
        DatasetTemplate t = new DatasetTemplate();
        t.setId(templateId);
        t.setEnabled(enabled);
        templateRepo.updateById(t);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Derive the physical table name from a template code.
     *
     * <p>Rule: {@code "dataset_" + code.toLowerCase().replaceAll("[^a-z0-9]+", "_")}
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "Livestock-Poultry"} → {@code "dataset_livestock_poultry"}</li>
     *   <li>{@code "household_survey_2025"} → {@code "dataset_household_survey_2025"}</li>
     * </ul>
     */
    public static String toPhysicalTable(String code) {
        return "dataset_" + code.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    /**
     * Auto-generate a workspace-unique {@code template.code} from a display name.
     *
     * <p>Strategy: slugify {@code name} (lowercase ASCII alnum + underscore); when the result
     * is empty (e.g. pure CJK input) fall back to {@code "tpl"}; on conflict append {@code _2},
     * {@code _3}, … until a free slot is found.
     */
    String generateUniqueTemplateCode(Long workspaceId, String name) {
        String base = slugify(name);
        if (base.isEmpty()) {
            base = "tpl";
        }
        String candidate = base;
        int suffix = 2;
        while (templateRepo.selectCount(new LambdaQueryWrapper<DatasetTemplate>()
                .eq(DatasetTemplate::getWorkspaceId, workspaceId)
                .eq(DatasetTemplate::getCode, candidate)) > 0) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /**
     * Auto-generate a template-unique {@code fieldCode} from a display field name,
     * checking against rows already persisted under {@code templateId}.
     */
    String generateUniqueFieldCode(Long templateId, String fieldName) {
        String base = slugify(fieldName);
        if (base.isEmpty()) {
            base = "field";
        }
        String candidate = base;
        int suffix = 2;
        while (fieldRepo.selectCount(new LambdaQueryWrapper<DatasetTemplateField>()
                .eq(DatasetTemplateField::getTemplateId, templateId)
                .eq(DatasetTemplateField::getFieldCode, candidate)) > 0) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /**
     * In-memory variant of {@link #generateUniqueFieldCode}: collision detection runs against
     * a caller-owned {@link Set} of codes already chosen earlier in the same batch (used by
     * {@link #create} where the template row has not yet been inserted).
     */
    static String generateUniqueFieldCodeInBatch(String fieldName, Set<String> usedCodes) {
        String base = slugify(fieldName);
        if (base.isEmpty()) {
            base = "field";
        }
        String candidate = base;
        int suffix = 2;
        while (usedCodes.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /**
     * Reduce a free-text string to {@code [a-z0-9_]} — lowercase, collapse any run of
     * non-alnum chars to a single underscore, strip leading/trailing underscores.
     *
     * <p>CJK characters are stripped entirely (we do not transliterate). Pure-CJK input
     * therefore returns an empty string; callers must provide a fallback.
     */
    static String slugify(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
