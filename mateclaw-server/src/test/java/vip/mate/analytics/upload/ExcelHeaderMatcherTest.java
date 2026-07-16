package vip.mate.analytics.upload;

import org.junit.jupiter.api.Test;
import vip.mate.analytics.dataset.DatasetField;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExcelHeaderMatcher}.
 */
class ExcelHeaderMatcherTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DatasetField field(String code, String excelHeader, boolean nullable) {
        DatasetField f = new DatasetField();
        f.setFieldCode(code);
        f.setExcelHeader(excelHeader);
        f.setIsNullable(nullable);
        return f;
    }

    // ── Test 1: exact header match ────────────────────────────────────────────

    @Test
    void exactMatch_returnsMappedColumnIndices() {
        List<String> headers = List.of("养殖场编码", "期末存栏（只）");
        List<DatasetField> fields = List.of(
                field("farm_code", "养殖场编码", false),
                field("end_stock", "期末存栏（只）", false)
        );

        Map<Integer, String> result = ExcelHeaderMatcher.match(headers, fields);

        assertEquals(2, result.size());
        assertEquals("farm_code", result.get(0));
        assertEquals("end_stock", result.get(1));
    }

    // ── Test 2: paren-stripped match ──────────────────────────────────────────

    @Test
    void parenStrippedMatch_matchesWhenHeaderLacksSuffix() {
        List<String> headers = List.of("期末存栏");
        List<DatasetField> fields = List.of(
                field("end_stock", "期末存栏（只）", false)
        );

        Map<Integer, String> result = ExcelHeaderMatcher.match(headers, fields);

        assertEquals(1, result.size());
        assertEquals("end_stock", result.get(0));
    }

    // ── Test 3: throws when required field missing ────────────────────────────

    @Test
    void missingRequiredField_throwsIllegalArgumentException() {
        List<String> headers = List.of("养殖场编码"); // "期末存栏" missing
        List<DatasetField> fields = List.of(
                field("farm_code", "养殖场编码", false),
                field("end_stock", "期末存栏（只）", false)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ExcelHeaderMatcher.match(headers, fields)
        );
        assertTrue(ex.getMessage().contains("期末存栏（只）"),
                "Exception message should list the unmatched Excel header: " + ex.getMessage());
    }

    // ── Test 4: every requested field must match this inspected workbook ──────

    @Test
    void nullableFieldWithNoMatch_throwsAndNamesExpectedHeader() {
        List<String> headers = List.of("养殖场编码");
        List<DatasetField> fields = List.of(
                field("farm_code", "养殖场编码", false),
                field("remark", "备注", true)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ExcelHeaderMatcher.match(headers, fields));

        assertTrue(ex.getMessage().contains("备注"),
                "Exception message should name the unmatched Excel header: " + ex.getMessage());
    }

    // ── Test 5: extra/unknown columns in Excel should not throw ───────────────

    @Test
    void extraColumnsInExcel_ignoredGracefully() {
        List<String> headers = List.of("养殖场编码", "未知列", "期末存栏（只）");
        List<DatasetField> fields = List.of(
                field("farm_code", "养殖场编码", false),
                field("end_stock", "期末存栏（只）", false)
        );

        Map<Integer, String> result = ExcelHeaderMatcher.match(headers, fields);

        assertEquals(2, result.size());
        assertEquals("farm_code", result.get(0));
        assertEquals("end_stock", result.get(2));
        assertFalse(result.containsKey(1)); // unknown column not in result
    }
}
