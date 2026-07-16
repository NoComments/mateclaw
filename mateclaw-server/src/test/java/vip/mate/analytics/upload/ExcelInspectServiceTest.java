package vip.mate.analytics.upload;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class ExcelInspectServiceTest {

    private final ExcelInspectService service = new ExcelInspectService();

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Build an in-memory .xlsx and return its bytes. */
    private byte[] xlsx(String sheetName, String[] headers, Object[] sampleValues) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            if (sampleValues != null) {
                Row s = sheet.createRow(1);
                for (int i = 0; i < sampleValues.length; i++) {
                    if (sampleValues[i] instanceof Number n) s.createCell(i).setCellValue(n.doubleValue());
                    else if (sampleValues[i] instanceof String str) s.createCell(i).setCellValue(str);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void inspect_infersMixedTypes() throws IOException {
        byte[] data = xlsx("Sheet1",
                new String[]{"日期", "头数", "日增重", "备注"},
                new Object[]{"2024-01-01", 120, 0.45, "正常"});

        var result = service.inspect(new ByteArrayInputStream(data), null);

        assertThat(result.sampleRowAvailable()).isTrue();
        assertThat(result.suggestedFields()).hasSize(4);

        var byName = result.suggestedFields().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExcelInspectService.InspectedField::fieldName,
                        ExcelInspectService.InspectedField::fieldType));

        assertThat(byName).containsEntry("日期", "DATE")
                          .containsEntry("头数", "INT")
                          .containsEntry("日增重", "DECIMAL")
                          .containsEntry("备注", "STRING");
    }

    @Test
    void inspect_defaultsToStringWhenNoSampleRow() throws IOException {
        byte[] data = xlsx("Sheet1", new String[]{"字段A", "字段B"}, null);

        var result = service.inspect(new ByteArrayInputStream(data), null);

        assertThat(result.sampleRowAvailable()).isFalse();
        assertThat(result.suggestedFields())
                .extracting(ExcelInspectService.InspectedField::fieldType)
                .containsOnly("STRING");
    }

    @Test
    void inspect_skipsBlankHeaderColumns() throws IOException {
        byte[] data = xlsx("Sheet1",
                new String[]{"有效列", "", "另一列"},
                new Object[]{"x", null, "y"});

        var result = service.inspect(new ByteArrayInputStream(data), null);

        assertThat(result.suggestedFields())
                .extracting(ExcelInspectService.InspectedField::fieldName)
                .containsExactly("有效列", "另一列");
    }

    @Test
    void inspect_throwsWhenSheetNotFound() throws IOException {
        byte[] data = xlsx("Sheet1", new String[]{"A"}, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.inspect(new ByteArrayInputStream(data), "不存在的Sheet"))
                .withMessageContaining("Sheet not found");
    }

    @Test
    void inspect_throwsWhenNoHeadersCanBeInferred() throws IOException {
        byte[] data = xlsx("Sheet1", new String[]{}, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.inspect(new ByteArrayInputStream(data), null))
                .withMessageContaining("表头");
    }
}
