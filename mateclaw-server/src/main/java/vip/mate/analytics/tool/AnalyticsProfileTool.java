package vip.mate.analytics.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetField;
import vip.mate.analytics.dataset.DatasetFieldRepository;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.FieldType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI @Tool: statistical profiling of dataset columns.
 *
 * <p>For each requested field code the tool executes targeted aggregate SQL
 * against the dataset's physical table and
 * returns per-column statistics suitable for LLM consumption.
 *
 * <p><b>Security note:</b> the physical table name comes from the
 * dataset-owned {@code Dataset.physicalTable} value. The column name is taken
 * from {@code DatasetField.fieldCode} (stored value)
 * only <em>after</em> a successful lookup — the raw LLM-supplied field code
 * string is never concatenated into SQL.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsProfileTool {

    private final JdbcTemplate jdbc;
    private final DatasetRepository datasetRepo;
    private final DatasetFieldRepository fieldRepo;

    /**
     * Profiles column statistics for the given dataset fields.
     *
     * @param datasetId  id of the dataset to profile
     * @param fieldCodes list of field codes to include, e.g. {@code ["end_stock", "province"]}
     * @return map of fieldCode → statistics map (or error map on unknown field)
     */
    @Tool(description = "Profile column statistics for a dataset: count, null%, distinct count, min/max/mean for numeric columns, top 5 values for string columns.")
    public Map<String, Object> analyticsProfile(
            @ToolParam(description = "Dataset id to profile") Long datasetId,
            @ToolParam(description = "List of field codes to profile, e.g. [\"end_stock\", \"province\"]") List<String> fieldCodes
    ) {
        Dataset dataset = datasetRepo.selectById(datasetId);
        if (dataset == null) {
            return Map.of("error", "dataset not found: " + datasetId);
        }

        String physicalTable = dataset.getPhysicalTable();

        // Pre-load all fields for this dataset once (avoids N+1)
        List<DatasetField> allFields = fieldRepo.selectList(
                new QueryWrapper<DatasetField>()
                        .eq("dataset_id", dataset.getId())
                        .eq("deleted", 0));

        Map<String, Object> result = new LinkedHashMap<>();
        for (String requestedCode : fieldCodes) {
            DatasetField field = allFields.stream()
                    .filter(f -> f.getFieldCode().equals(requestedCode))
                    .findFirst()
                    .orElse(null);

            if (field == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "unknown field: " + requestedCode);
                result.put(requestedCode, err);
                continue;
            }

            // Use the stored fieldCode (admin-controlled) — never the raw requestedCode — in SQL
            String col = field.getFieldCode();
            FieldType type = FieldType.fromString(field.getFieldType());

            result.put(requestedCode, buildFieldStats(physicalTable, col, type));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildFieldStats(
            String physicalTable, String col, FieldType type) {

        Map<String, Object> stats = new LinkedHashMap<>();

        if (type == FieldType.INT || type == FieldType.DECIMAL) {
            String sql = String.format(
                    "SELECT COUNT(*) AS total_count, COUNT(%s) AS non_null_count," +
                    " COUNT(DISTINCT %s) AS distinct_count," +
                    " MIN(%s) AS min_val, MAX(%s) AS max_val, AVG(%s) AS mean_val" +
                    " FROM %s",
                    col, col, col, col, col, physicalTable);
            jdbc.query(sql, rs -> {
                long total = rs.getLong("total_count");
                long nonNull = rs.getLong("non_null_count");
                stats.put("totalCount", total);
                stats.put("nonNullCount", nonNull);
                stats.put("nullPercent", total == 0 ? 0.0 : (total - nonNull) * 100.0 / total);
                stats.put("distinctCount", rs.getLong("distinct_count"));
                stats.put("min", rs.getObject("min_val"));
                stats.put("max", rs.getObject("max_val"));
                stats.put("mean", rs.getObject("mean_val"));
            });

        } else if (type == FieldType.DATE || type == FieldType.BOOLEAN) {
            String sql = String.format(
                    "SELECT COUNT(*) AS total_count, COUNT(%s) AS non_null_count," +
                    " COUNT(DISTINCT %s) AS distinct_count," +
                    " MIN(%s) AS min_val, MAX(%s) AS max_val" +
                    " FROM %s",
                    col, col, col, col, physicalTable);
            jdbc.query(sql, rs -> {
                long total = rs.getLong("total_count");
                long nonNull = rs.getLong("non_null_count");
                stats.put("totalCount", total);
                stats.put("nonNullCount", nonNull);
                stats.put("nullPercent", total == 0 ? 0.0 : (total - nonNull) * 100.0 / total);
                stats.put("distinctCount", rs.getLong("distinct_count"));
                stats.put("min", rs.getObject("min_val"));
                stats.put("max", rs.getObject("max_val"));
            });

        } else {
            // STRING
            String aggSql = String.format(
                    "SELECT COUNT(*) AS total_count, COUNT(%s) AS non_null_count," +
                    " COUNT(DISTINCT %s) AS distinct_count" +
                    " FROM %s",
                    col, col, physicalTable);
            jdbc.query(aggSql, rs -> {
                long total = rs.getLong("total_count");
                long nonNull = rs.getLong("non_null_count");
                stats.put("totalCount", total);
                stats.put("nonNullCount", nonNull);
                stats.put("nullPercent", total == 0 ? 0.0 : (total - nonNull) * 100.0 / total);
                stats.put("distinctCount", rs.getLong("distinct_count"));
            });

            String top5Sql = String.format(
                    "SELECT %s AS top_value, COUNT(*) AS freq FROM %s" +
                    " WHERE %s IS NOT NULL" +
                    " GROUP BY %s ORDER BY freq DESC LIMIT 5",
                    col, physicalTable, col, col);
            List<Map<String, Object>> top5 = new ArrayList<>();
            jdbc.query(top5Sql, rs -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("value", rs.getObject("top_value"));
                entry.put("freq", rs.getLong("freq"));
                top5.add(entry);
            });
            stats.put("top5", top5);
        }

        return stats;
    }
}
