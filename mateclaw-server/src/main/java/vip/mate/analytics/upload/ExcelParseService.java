package vip.mate.analytics.upload;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.FieldType;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses an uploaded .xlsx file into typed {@link ParsedRow} records using
 * a dataset template's field mapping.
 *
 * <p>Processing steps:
 * <ol>
 *   <li>Open the workbook and locate the target sheet.</li>
 *   <li>Read header row (row 0) and delegate to {@link ExcelHeaderMatcher} to
 *       build a {@code colIndex → fieldCode} mapping.</li>
 *   <li>For every subsequent row, convert each mapped cell to the Java type
 *       dictated by the field's {@link FieldType}.</li>
 *   <li>Skip null rows and completely blank rows (all mapped cells null).</li>
 * </ol>
 */
@Service
public class ExcelParseService {

    /**
     * Parses the Excel input stream and returns one {@link ParsedRow} per
     * non-blank data row.
     *
     * @param in        input stream of an .xlsx file; caller is responsible for closing it
     * @param fields    field definitions from the dataset template
     * @param sheetName name of the sheet to read; if {@code null} the first sheet is used
     * @return ordered list of parsed rows (header row excluded)
     * @throws IOException              if the stream cannot be read
     * @throws IllegalArgumentException if {@code sheetName} is non-null but not found,
     *                                  or if a required header column is missing
     */
    public List<ParsedRow> parse(InputStream in,
                                 List<DatasetTemplateField> fields,
                                 @Nullable String sheetName) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            XSSFSheet sheet = resolveSheet(wb, sheetName);

            // ── header row ───────────────────────────────────────────────────
            Row headerRow = sheet.getRow(0);
            List<String> headers = extractHeaders(headerRow);

            Map<Integer, String> colToField = ExcelHeaderMatcher.match(headers, fields);

            // field code → FieldType for O(1) lookup during row iteration
            Map<String, FieldType> typeByCode = buildTypeIndex(fields);

            // ── data rows ────────────────────────────────────────────────────
            List<ParsedRow> result = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();

            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue; // sparse row — skip
                }

                Map<String, Object> values = buildRowValues(row, colToField, typeByCode);

                if (isBlankRow(values)) {
                    continue;
                }

                result.add(new ParsedRow(r, values));
            }

            return result;
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private XSSFSheet resolveSheet(XSSFWorkbook wb, @Nullable String sheetName) {
        if (sheetName == null) {
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
        if (headerRow == null) {
            return headers;
        }
        int last = headerRow.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = headerRow.getCell(c);
            headers.add(cell == null ? null : cell.getStringCellValue());
        }
        return headers;
    }

    private Map<String, FieldType> buildTypeIndex(List<DatasetTemplateField> fields) {
        Map<String, FieldType> index = new HashMap<>();
        for (DatasetTemplateField f : fields) {
            if (f.getFieldType() != null) {
                index.put(f.getFieldCode(), FieldType.fromString(f.getFieldType()));
            }
        }
        return index;
    }

    private Map<String, Object> buildRowValues(Row row,
                                               Map<Integer, String> colToField,
                                               Map<String, FieldType> typeByCode) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<Integer, String> entry : colToField.entrySet()) {
            int colIdx = entry.getKey();
            String fieldCode = entry.getValue();
            Cell cell = row.getCell(colIdx);
            FieldType type = typeByCode.get(fieldCode);
            values.put(fieldCode, convertCell(cell, type));
        }
        return values;
    }

    /**
     * Converts a single cell to its Java representation according to the field type.
     *
     * <p>Returns {@code null} for null or blank cells regardless of type.
     */
    @Nullable
    private Object convertCell(@Nullable Cell cell, @Nullable FieldType type) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (type == null) {
            return cell.toString();
        }
        return switch (type) {
            case STRING -> convertToString(cell);
            case INT    -> convertToInt(cell);
            case DECIMAL -> convertToDecimal(cell);
            case BOOLEAN -> convertToBoolean(cell);
            case DATE   -> convertToDate(cell);
        };
    }

    private String convertToString(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case STRING  -> cell.getStringCellValue();
            default      -> cell.toString();
        };
    }

    private long convertToInt(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> (long) cell.getNumericCellValue();
            case STRING  -> Long.parseLong(cell.getStringCellValue().trim());
            default      -> Long.parseLong(cell.toString().trim());
        };
    }

    private BigDecimal convertToDecimal(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> new BigDecimal(cell.getStringCellValue().trim());
            default      -> new BigDecimal(cell.toString().trim());
        };
    }

    private int convertToBoolean(Cell cell) {
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue() ? 1 : 0;
            case NUMERIC -> (int) cell.getNumericCellValue();
            default      -> Integer.parseInt(cell.toString().trim());
        };
    }

    private Object convertToDate(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return cell.toString();
    }

    /** Returns {@code true} if every value in the map is {@code null}. */
    private boolean isBlankRow(Map<String, Object> values) {
        return values.values().stream().allMatch(v -> v == null);
    }
}
