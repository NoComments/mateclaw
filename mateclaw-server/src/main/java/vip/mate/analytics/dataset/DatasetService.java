package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vip.mate.analytics.storage.DynamicTableService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CRUD service for {@link Dataset}.
 *
 * <p>The {@code delete} operation drops the dataset's dynamic physical table.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DatasetService {

    /**
     * Unquoted column identifiers empirically rejected by both H2 (MySQL mode)
     * and MySQL: order, group, key, index, row, value, left, check, primary.
     */
    private static final Set<String> RESERVED_FIELD_CODES = Set.of(
            "order", "group", "key", "index", "row", "value", "left", "check", "primary");

    private final DatasetRepository datasetRepo;
    private final JdbcTemplate jdbc;
    private final DatasetFieldRepository fieldRepo;

    /**
     * Derives and validates field metadata without reading or writing the database.
     *
     * <p>This method is explicitly non-transactional so one-step upload callers can
     * understand and validate the workbook schema before any persistent work begins.
     * Existing field codes are preserved for callers that already prepared their fields.
     *
     * @param fields ordered field definitions to prepare in place
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prepareFields(List<DatasetField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("数据集至少需要一个字段");
        }

        Set<String> usedFieldNames = new HashSet<>();
        for (DatasetField f : fields) {
            if (!StringUtils.hasText(f.getFieldName())) {
                throw new IllegalArgumentException("字段名不能为空");
            }
            if (!usedFieldNames.add(f.getFieldName().trim())) {
                throw new IllegalArgumentException("字段名重复: " + f.getFieldName());
            }
            FieldType.fromString(f.getFieldType());
        }

        Set<String> usedFieldCodes = new HashSet<>();
        for (DatasetField f : fields) {
            if (!StringUtils.hasText(f.getFieldCode())) {
                f.setFieldCode(generateUniqueFieldCodeInBatch(f.getFieldName(), usedFieldCodes));
            }
            DynamicTableService.validateName(f.getFieldCode(), "fieldCode");
            usedFieldCodes.add(f.getFieldCode());
            if (!StringUtils.hasText(f.getExcelHeader())) {
                f.setExcelHeader(f.getFieldName());
            }
            if (f.getIsNullable() == null) {
                f.setIsNullable(true);
            }
        }
    }

    /**
     * Create a dataset together with its schema, then name its physical table after
     * the generated id.
     *
     * <p>fieldCode and excelHeader are derived from fieldName when absent — callers
     * (and the LLM) never supply physical column names.
     *
     * @param ds     dataset to persist; workspaceId and name must be set
     * @param fields ordered field definitions; fieldName and fieldType required
     * @return the persisted dataset with id and physicalTable populated
     */
    public Dataset createWithFields(Dataset ds, List<DatasetField> fields) {
        if (!StringUtils.hasText(ds.getName())) {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        prepareFields(fields);

        if (ds.getRowCount() == null) {
            ds.setRowCount(0);
        }
        ds.setPhysicalTable("dataset_placeholder");
        datasetRepo.insert(ds);

        ds.setPhysicalTable("dataset_" + ds.getId());
        datasetRepo.updateById(ds);

        for (DatasetField f : fields) {
            f.setDatasetId(ds.getId());
            fieldRepo.insert(f);
        }
        return ds;
    }

    /**
     * Return all non-deleted datasets belonging to a workspace.
     *
     * @param workspaceId target workspace
     */
    @Transactional(readOnly = true)
    public List<Dataset> listByWorkspace(Long workspaceId) {
        return datasetRepo.selectList(
                new LambdaQueryWrapper<Dataset>()
                        .eq(Dataset::getWorkspaceId, workspaceId));
    }

    /**
     * Fetch a single dataset by its primary key.
     *
     * @param id dataset id
     * @return dataset, or {@code null} if not found / soft-deleted
     */
    @Transactional(readOnly = true)
    public Dataset getById(Long id) {
        return datasetRepo.selectById(id);
    }

    /**
     * Soft-delete the dataset record and drop its dynamic physical table.
     *
     * <p>The physical table name is read directly from the dataset and validated
     * before it is used in DDL.
     *
     * @param id dataset id to delete
     * @throws IllegalArgumentException if the dataset cannot be found
     */
    public void delete(Long id) {
        Dataset ds = datasetRepo.selectById(id);
        if (ds == null) {
            throw new IllegalArgumentException("Dataset not found: " + id);
        }

        String physicalTable = ds.getPhysicalTable();
        if (physicalTable != null && !physicalTable.isBlank()) {
            if (!physicalTable.matches("^dataset_[a-z0-9_]+$")) {
                throw new IllegalArgumentException("Unsafe physical table name: " + physicalTable);
            }
            // NOTE: DROP TABLE is DDL and causes an implicit commit on MySQL. If the
            // datasetRepo.deleteById(id) call below throws, this DROP has already been
            // committed and cannot be rolled back — the dataset metadata row would then
            // survive pointing at a physical table that no longer exists.
            jdbc.execute("DROP TABLE IF EXISTS " + physicalTable);
        }

        fieldRepo.delete(new LambdaQueryWrapper<DatasetField>()
                .eq(DatasetField::getDatasetId, id));
        // Soft-delete the dataset record (MyBatis-Plus fills the deleted flag)
        datasetRepo.deleteById(id);
    }

    private static String generateUniqueFieldCodeInBatch(String fieldName, Set<String> usedCodes) {
        String base = slugify(fieldName);
        if (base.isEmpty()) {
            base = "field";
        }
        if (RESERVED_FIELD_CODES.contains(base)) {
            base += "_col";
        }
        String candidate = base;
        int suffix = 2;
        while (usedCodes.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
