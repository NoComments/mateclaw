package vip.mate.analytics.upload;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads an .xlsx file and infers a {@link InspectResult} from its header row
 * and optional first data row. No data is persisted — purely a read operation.
 *
 * <p>Type inference priority:
 * <ol>
 *   <li>NUMERIC cell formatted as date → DATE</li>
 *   <li>NUMERIC cell whose value equals Math.floor(value) → INT</li>
 *   <li>NUMERIC cell with fractional part → DECIMAL</li>
 *   <li>STRING cell matching {@code \d{4}[-/]\d{2}[-/]\d{2}.*} → DATE</li>
 *   <li>Anything else, or no sample row → STRING</li>
 * </ol>
 */
@Service
public class ExcelInspectService {

    private static final Pattern DATE_STRING_PATTERN = Pattern.compile("\\d{4}[-/]\\d{2}[-/]\\d{2}.*");

    /**
     * Inspect the first sheet (or the named sheet) of the workbook.
     *
     * @param in        .xlsx input stream; caller is responsible for closing it
     * @param sheetName sheet to read; {@code null} → first sheet
     * @return inspection result with inferred field definitions
     * @throws IOException              if the stream cannot be read
     * @throws IllegalArgumentException if {@code sheetName} is non-null but not found
     */
    public InspectResult inspect(InputStream in, @Nullable String sheetName) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            XSSFSheet sheet = resolveSheet(wb, sheetName);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("无法推断 Excel 表头，请确认工作表第一行包含列名");
            }

            List<String> headers = extractHeaders(headerRow);
            Row sampleRow = sheet.getRow(1);
            boolean hasSample = sampleRow != null;

            List<InspectedField> fields = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header == null || header.isBlank()) continue;
                String type = hasSample ? inferType(sampleRow.getCell(i)) : "STRING";
                fields.add(new InspectedField(header, type, i));
            }

            if (fields.isEmpty()) {
                throw new IllegalArgumentException("无法推断 Excel 表头，请确认工作表第一行包含列名");
            }

            return new InspectResult(headers, fields, hasSample);
        }
    }

    // ── result types ──────────────────────────────────────────────────────────

    /** A single inferred field definition. */
    public record InspectedField(String fieldName, String fieldType, int ordinal) {}

    /** Full result returned by {@link #inspect}. */
    public record InspectResult(
            List<String> headers,
            List<InspectedField> suggestedFields,
            boolean sampleRowAvailable
    ) {}

    // ── private helpers ───────────────────────────────────────────────────────

    private XSSFSheet resolveSheet(XSSFWorkbook wb, @Nullable String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return wb.getSheetAt(0);
        }
        XSSFSheet sheet = wb.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + sheetName);
        }
        return sheet;
    }

    private List<String> extractHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        int last = headerRow.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = headerRow.getCell(c);
            headers.add(cell == null ? null : cell.getStringCellValue());
        }
        return headers;
    }

    private String inferType(@Nullable Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "STRING";

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return "DATE";
            double v = cell.getNumericCellValue();
            return v == Math.floor(v) ? "INT" : "DECIMAL";
        }

        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (DATE_STRING_PATTERN.matcher(s).matches()) return "DATE";
        }

        return "STRING";
    }
}
