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
 * <p>Every field must match a header because one-step upload fields come from inspection
 * of this same workbook. Any unmatched expected header causes an
 * {@link IllegalArgumentException} that lists the missing headers.
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
     * @param fields   field definitions from the dataset
     * @return {@code Map<columnIndex, fieldCode>} for every matched field
     * @throws IllegalArgumentException if any field has no matching column
     */
    public static Map<Integer, String> match(List<String> headers, List<DatasetField> fields) {
        Map<Integer, String> result = new HashMap<>();
        List<String> missing = new ArrayList<>();

        for (DatasetField field : fields) {
            int idx = findHeaderIndex(headers, field.getExcelHeader());

            if (idx >= 0) {
                result.put(idx, field.getFieldCode());
            } else {
                missing.add(field.getExcelHeader());
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel 表头未找到以下字段: " + String.join(", ", missing));
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
