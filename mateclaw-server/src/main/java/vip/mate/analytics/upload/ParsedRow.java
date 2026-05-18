package vip.mate.analytics.upload;

import java.util.Map;

/**
 * A single data row parsed from an Excel upload.
 *
 * @param rowNumber 1-based row number from the original sheet (header = row 0)
 * @param values    field code → typed Java value (String, Long, BigDecimal, Integer, LocalDate, or null)
 */
public record ParsedRow(int rowNumber, Map<String, Object> values) {}
