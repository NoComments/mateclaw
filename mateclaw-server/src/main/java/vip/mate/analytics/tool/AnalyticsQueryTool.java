package vip.mate.analytics.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vip.mate.analytics.tool.guard.SqlGuard;

import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI @Tool: executes read-only SELECT queries against dataset_* tables.
 *
 * <p>All SQL passes through {@link SqlGuard#safen(String)} before execution, which:
 * <ul>
 *   <li>Rejects any non-SELECT statement (INSERT/UPDATE/DELETE/DDL).</li>
 *   <li>Restricts access to {@code dataset_*} tables and a small metadata whitelist.</li>
 *   <li>Enforces LIMIT ≤ 10 000 (appends LIMIT 10000 when absent).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AnalyticsQueryTool {

    private final JdbcTemplate jdbc;

    /**
     * Executes a validated, read-only SELECT and returns structured results.
     *
     * @param sql SQL SELECT statement targeting dataset_* tables
     * @return map with keys: {@code executedSql}, {@code rowCount}, {@code rows}
     */
    @Tool(description = "Execute a read-only SELECT on dataset_* tables. Always call analytics_schema first to know column names. Automatically enforces LIMIT 10000 and 30-second timeout.")
    public Map<String, Object> analyticsQuery(
            @ToolParam(description = "SQL SELECT statement targeting dataset_* tables") String sql
    ) {
        String safeSql = SqlGuard.safen(sql);

        List<Map<String, Object>> rows = jdbc.query(safeSql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            return row;
        });

        return Map.of(
                "executedSql", safeSql,
                "rowCount", rows.size(),
                "rows", rows
        );
    }
}
