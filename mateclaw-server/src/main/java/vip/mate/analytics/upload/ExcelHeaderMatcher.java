package vip.mate.analytics.upload;

import vip.mate.analytics.dataset.DatasetField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<Integer, String> owners = new HashMap<>();
        Set<Integer> exactMatches = new HashSet<>();
        List<DatasetField> fallbackFields = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // Resolve all exact matches first so fallback matching cannot steal a
        // column that belongs to another field, regardless of field order.
        for (DatasetField field : fields) {
            int idx = findExactHeaderIndex(headers, field.getExcelHeader());

            if (idx >= 0) {
                putMatch(result, owners, idx, field);
                exactMatches.add(idx);
            } else {
                fallbackFields.add(field);
            }
        }

        for (DatasetField field : fallbackFields) {
            List<Integer> candidates = findParenFallbackIndices(
                    headers, field.getExcelHeader(), exactMatches);
            if (candidates.size() > 1) {
                throw new IllegalArgumentException(
                        "Excel 表头匹配有歧义: " + field.getExcelHeader()
                                + " 可匹配 " + candidates.stream()
                                .map(headers::get)
                                .map(String::trim)
                                .toList());
            }
            if (candidates.isEmpty()) {
                missing.add(field.getExcelHeader());
                continue;
            }
            putMatch(result, owners, candidates.get(0), field);
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel 表头未找到以下字段: " + String.join(", ", missing));
        }

        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static int findExactHeaderIndex(List<String> headers, String excelHeader) {
        if (excelHeader == null || excelHeader.isBlank()) {
            return -1;
        }
        String fieldTrimmed = excelHeader.trim();

        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h == null) {
                continue;
            }
            if (h.trim().equals(fieldTrimmed)) {
                return i;
            }
        }
        return -1;
    }

    private static List<Integer> findParenFallbackIndices(
            List<String> headers, String excelHeader, Set<Integer> exactMatches) {
        List<Integer> candidates = new ArrayList<>();
        if (excelHeader == null || excelHeader.isBlank()) {
            return candidates;
        }
        String fieldTrimmed = excelHeader.trim();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h == null || exactMatches.contains(i)) {
                continue;
            }
            String hTrimmed = h.trim();
            if (stripParens(hTrimmed).equals(stripParens(fieldTrimmed))) {
                candidates.add(i);
            }
        }
        return candidates;
    }

    private static void putMatch(
            Map<Integer, String> result,
            Map<Integer, String> owners,
            int columnIndex,
            DatasetField field) {
        String previousHeader = owners.putIfAbsent(columnIndex, field.getExcelHeader());
        if (previousHeader != null) {
            throw new IllegalArgumentException(
                    "Excel 表头匹配有歧义: " + previousHeader + " 与 "
                            + field.getExcelHeader() + " 都匹配第 " + (columnIndex + 1) + " 列");
        }
        result.put(columnIndex, field.getFieldCode());
    }

    private static String stripParens(String s) {
        return PAREN_SUFFIX.matcher(s).replaceAll("").trim();
    }
}
