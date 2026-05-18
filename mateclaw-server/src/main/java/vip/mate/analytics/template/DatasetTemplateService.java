package vip.mate.analytics.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.analytics.dataset.DatasetRepository;

import java.util.List;

/**
 * CRUD + field-lifecycle service for {@link DatasetTemplate}.
 *
 * <p>Protection invariant: once a template has been used by any
 * {@link vip.mate.analytics.dataset.Dataset}, fields can only be <em>appended</em>
 * — deletion and type changes are blocked.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DatasetTemplateService {

    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;
    private final DatasetRepository datasetRepo;

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
     *   <li>{@code physicalTable} is derived from {@code code} via {@link #toPhysicalTable(String)}.</li>
     *   <li>{@code enabled} defaults to {@code true} when not set.</li>
     *   <li>{@code templateId} is stamped on every field after the template row is inserted.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if any field carries an unknown {@link FieldType}
     */
    public DatasetTemplate create(DatasetTemplate t, List<DatasetTemplateField> fields) {
        // Validate all field types eagerly — fail before any writes
        for (DatasetTemplateField f : fields) {
            FieldType.fromString(f.getFieldType()); // throws IllegalArgumentException on bad value
        }

        t.setPhysicalTable(toPhysicalTable(t.getCode()));
        if (t.getEnabled() == null) {
            t.setEnabled(true);
        }

        templateRepo.insert(t);

        for (DatasetTemplateField f : fields) {
            f.setTemplateId(t.getId());
            fieldRepo.insert(f);
        }

        return t;
    }

    /**
     * Append a new field to an existing template.
     *
     * <p>Appending is always permitted regardless of whether datasets already exist.
     *
     * @param templateId target template
     * @param field      new field to add; its {@code templateId} will be set here
     */
    public void appendField(Long templateId, DatasetTemplateField field) {
        field.setTemplateId(templateId);
        fieldRepo.insert(field);
    }

    /**
     * Remove a field — only allowed when no {@link vip.mate.analytics.dataset.Dataset}
     * rows reference this template.
     *
     * @throws IllegalStateException if any dataset already uses this template
     */
    public void removeField(Long templateId, Long fieldId) {
        long count = datasetRepo.countByTemplateId(templateId);
        if (count > 0) {
            throw new IllegalStateException("模板下已有数据集，禁止删除字段（仅允许追加）");
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
}
