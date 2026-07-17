package vip.mate.analytics.upload;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vip.mate.analytics.dataset.DatasetField;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExcelParseService}.
 *
 * <p>The test xlsx is built programmatically in {@code @BeforeAll} so there
 * are no binary fixtures to maintain.
 */
class ExcelParseServiceTest {

    private static Path tempXlsx;

    private static final String SHEET_NAME = "家禽";

    private static final List<DatasetField> FIELDS = List.of(
            field("farm_code",   "养殖场编码",   "STRING",  false, 0),
            field("end_stock",   "期末存栏（只）", "DECIMAL", false, 1),
            field("is_contract", "是否代养",     "INT",     true,  2)
    );

    // ── fixture setup ─────────────────────────────────────────────────────────

    @BeforeAll
    static void createTempWorkbook() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(SHEET_NAME);

            // row 0: headers
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("养殖场编码");
            header.createCell(1).setCellValue("期末存栏（只）");
            header.createCell(2).setCellValue("是否代养");

            // row 1: ["4101001", 325600, 0]
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("4101001");
            row1.createCell(1).setCellValue(325600.0);
            row1.createCell(2).setCellValue(0.0);

            // row 2: ["4101002", 0.0, 1]
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("4101002");
            row2.createCell(1).setCellValue(0.0);
            row2.createCell(2).setCellValue(1.0);

            // row 3: intentionally null row (not created → POI returns null for it)

            tempXlsx = Files.createTempFile("excel-parse-test-", ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(tempXlsx.toFile())) {
                wb.write(fos);
            }
        }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void parse_returnsAllDataRows() throws IOException {
        ExcelParseService svc = new ExcelParseService();
        try (InputStream in = Files.newInputStream(tempXlsx)) {
            List<ParsedRow> rows = svc.parse(in, FIELDS, SHEET_NAME).rows();
            // null row (row index 3) should be skipped → exactly 2 data rows
            assertEquals(2, rows.size());
        }
    }

    @Test
    void parse_mapsFieldCodesCorrectly() throws IOException {
        ExcelParseService svc = new ExcelParseService();
        try (InputStream in = Files.newInputStream(tempXlsx)) {
            List<ParsedRow> rows = svc.parse(in, FIELDS, SHEET_NAME).rows();

            ParsedRow first = rows.get(0);
            assertEquals("4101001", first.values().get("farm_code"));
            assertEquals(BigDecimal.valueOf(325600.0), first.values().get("end_stock"));
            assertEquals(0L, first.values().get("is_contract"));
        }
    }

    @Test
    void parse_usesSheetNameWhenProvided() throws IOException {
        ExcelParseService svc = new ExcelParseService();
        try (InputStream in = Files.newInputStream(tempXlsx)) {
            // should not throw — sheet "家禽" exists
            List<ParsedRow> rows = svc.parse(in, FIELDS, SHEET_NAME).rows();
            assertFalse(rows.isEmpty());
        }
    }

    @Test
    void parse_isolatesRowWithUnparseableCellInsteadOfAborting() throws IOException {
        // A DECIMAL column ("期末存栏（只）") whose second data row holds the text "无"
        // must skip only that row and report it — not fail the whole sheet.
        Path badXlsx = Files.createTempFile("excel-parse-bad-", ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("养殖场编码");
            header.createCell(1).setCellValue("期末存栏（只）");
            header.createCell(2).setCellValue("是否代养");

            Row good = sheet.createRow(1);
            good.createCell(0).setCellValue("4101001");
            good.createCell(1).setCellValue(325600.0);
            good.createCell(2).setCellValue(0.0);

            Row bad = sheet.createRow(2);
            bad.createCell(0).setCellValue("4101002");
            bad.createCell(1).setCellValue("无");   // not a number
            bad.createCell(2).setCellValue(1.0);

            try (FileOutputStream fos = new FileOutputStream(badXlsx.toFile())) {
                wb.write(fos);
            }
        }

        ExcelParseService svc = new ExcelParseService();
        try (InputStream in = Files.newInputStream(badXlsx)) {
            ParseResult result = svc.parse(in, FIELDS, SHEET_NAME);

            assertEquals(1, result.rows().size(), "the clean row survives");
            assertEquals(1, result.rejected(), "the bad row is counted as rejected");
            assertEquals(1, result.errors().size());
            String msg = result.errors().get(0);
            assertTrue(msg.contains("期末存栏（只）") && msg.contains("无"),
                    "error should name the column and the offending value: " + msg);
        }
    }

    @Test
    void parse_rejectsFractionalValueInIntColumnInsteadOfTruncating() throws IOException {
        // is_contract is INT; a row with 1.5 must be rejected, not silently truncated to 1.
        Path badXlsx = Files.createTempFile("excel-parse-frac-", ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("养殖场编码");
            header.createCell(1).setCellValue("期末存栏（只）");
            header.createCell(2).setCellValue("是否代养");

            Row good = sheet.createRow(1);
            good.createCell(0).setCellValue("4101001");
            good.createCell(1).setCellValue(325600.0);
            good.createCell(2).setCellValue(1.0);   // whole → 1L, fine

            Row bad = sheet.createRow(2);
            bad.createCell(0).setCellValue("4101002");
            bad.createCell(1).setCellValue(0.0);
            bad.createCell(2).setCellValue(1.5);    // fractional in INT column

            try (FileOutputStream fos = new FileOutputStream(badXlsx.toFile())) {
                wb.write(fos);
            }
        }

        ExcelParseService svc = new ExcelParseService();
        try (InputStream in = Files.newInputStream(badXlsx)) {
            ParseResult result = svc.parse(in, FIELDS, SHEET_NAME);

            assertEquals(1, result.rows().size());
            assertEquals(1L, result.rows().get(0).values().get("is_contract"));
            assertEquals(1, result.rejected());
            assertTrue(result.errors().get(0).contains("是否代养"),
                    "error should name the INT column: " + result.errors().get(0));
        }
    }

    @Test
    void parse_throwsWhenSheetNotFound() throws IOException {
        ExcelParseService svc = new ExcelParseService();
        try (InputStream in = Files.newInputStream(tempXlsx)) {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> svc.parse(in, FIELDS, "NonExistent")
            );
            assertTrue(ex.getMessage().contains("NonExistent"),
                    "Message should name the missing sheet: " + ex.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static DatasetField field(String code, String excelHeader,
                                      String type, boolean nullable, int ordinal) {
        DatasetField f = new DatasetField();
        f.setFieldCode(code);
        f.setFieldName(excelHeader);
        f.setFieldType(type);
        f.setExcelHeader(excelHeader);
        f.setIsNullable(nullable);
        f.setOrdinal(ordinal);
        return f;
    }
}
