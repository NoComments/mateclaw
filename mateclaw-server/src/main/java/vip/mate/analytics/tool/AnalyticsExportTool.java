package vip.mate.analytics.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vip.mate.analytics.tool.guard.SqlGuard;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring AI @Tool: exports the result of a validated SELECT query to an xlsx file
 * and returns a download URL path.
 *
 * <p>All SQL passes through {@link SqlGuard#safen(String)} before execution.
 * The generated file is written to the system temp directory and served by
 * {@link vip.mate.analytics.controller.ExportDownloadController}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsExportTool {

    private final JdbcTemplate jdbc;

    /**
     * Exports query results to an xlsx file and returns the download URL.
     *
     * @param sql SQL SELECT targeting dataset_* tables
     * @return download path like {@code /api/analytics/exports/{filename}}
     */
    @Tool(description = "Export SQL query results to an xlsx file and return a download URL. Always call analytics_schema first to know column names.")
    public String analyticsExport(
            @ToolParam(description = "SQL SELECT to export; passed through SqlGuard") String sql
    ) {
        String safeSql = SqlGuard.safen(sql);

        List<Map<String, Object>> rows = jdbc.queryForList(safeSql);

        Path tempFile;
        try {
            tempFile = Files.createTempFile("analytics-export-", ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("无法创建临时文件: " + e.getMessage(), e);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(tempFile)) {

            XSSFSheet sheet = workbook.createSheet("Export");

            // Determine column order from first row, or empty if no rows
            List<String> columns = rows.isEmpty()
                    ? List.of()
                    : List.copyOf(rows.get(0).keySet());

            // Row 0: headers
            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                headerRow.createCell(i).setCellValue(columns.get(i));
            }

            // Rows 1+: data values
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                XSSFRow dataRow = sheet.createRow(rowIdx + 1);
                Map<String, Object> rowData = rows.get(rowIdx);
                for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                    Object value = rowData.get(columns.get(colIdx));
                    dataRow.createCell(colIdx).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }

            workbook.write(out);
        } catch (IOException e) {
            throw new RuntimeException("xlsx 写入失败: " + e.getMessage(), e);
        }

        String filename = tempFile.getFileName().toString();
        log.info("[AnalyticsExport] exported {} rows to {}", rows.size(), filename);
        return "/api/analytics/exports/" + filename;
    }
}
