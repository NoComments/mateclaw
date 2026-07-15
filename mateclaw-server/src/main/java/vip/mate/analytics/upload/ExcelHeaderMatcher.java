package vip.mate.analytics.upload;

import vip.mate.analytics.dataset.DatasetField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps Excel column headers (row 0) to {@link DatasetField#getFieldCode()} values.
 *
 * <p>Matching is attempted in this order:
 * <ol>
 *   <li>Exact match after trim.</li>
 *   <li>Strip-parens match: both sides strip {@code （...）} and {@code (...)} then compare.</li>
 * </ol>
 *
 * <p>Required fields (isNullable=false, any isPartitionKey value) that have no matching
 * header column cause an {@link IllegalArgumentException} listing all missing field codes.
 * Nullable fields with no match are silently skipped.
 */
public final class ExcelHeaderMatcher {

    /** Matches full-width {@code （...）} and half-width {@code (...)} parenthetical suffixes. */
    private static final Pattern PAREN_SUFFIX = Pattern.compile("[（(][^）)]*[）)]");

    private ExcelHeaderMatcher() {
        // utility class — no instances
    }

    /**
     * Maps Excel column indices to field codes.
     *
     * @param headers  ordered list of header strings from Excel row 0 (may include unknown columns)
     * @param fields   field definitions from the dataset template
     * @return {@code Map<columnIndex, fieldCode>} for every matched field
     * @throws IllegalArgumentException if any required (non-nullable) field has no matching column
     */
    public static Map<Integer, String> match(List<String> headers, List<DatasetField> fields) {
        Map<Integer, String> result = new HashMap<>();
        List<String> missing = new ArrayList<>();

        for (DatasetField field : fields) {
            int idx = findHeaderIndex(headers, field.getExcelHeader());

            if (idx >= 0) {
                result.put(idx, field.getFieldCode());
            } else {
                boolean required = !Boolean.TRUE.equals(field.getIsNullable());
                if (required) {
                    missing.add(field.getFieldCode());
                }
                // nullable with no match → silently skip
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel 表头缺少以下必填字段: " + String.join(", ", missing));
        }

        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the 0-based index of the first header that matches {@code excelHeader},
     * or {@code -1} if no match is found.
     */
    private static int findHeaderIndex(List<String> headers, String excelHeader) {
        if (excelHeader == null || excelHeader.isBlank()) {
            return -1;
        }
        String fieldTrimmed = excelHeader.trim();

        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h == null) {
                continue;
            }
            String hTrimmed = h.trim();

            // Strategy 1: exact match after trim
            if (hTrimmed.equals(fieldTrimmed)) {
                return i;
            }

            // Strategy 2: strip parenthetical suffixes from both sides, then compare
            if (stripParens(hTrimmed).equals(stripParens(fieldTrimmed))) {
                return i;
            }
        }
        return -1;
    }

    private static String stripParens(String s) {
        return PAREN_SUFFIX.matcher(s).replaceAll("").trim();
    }
}
