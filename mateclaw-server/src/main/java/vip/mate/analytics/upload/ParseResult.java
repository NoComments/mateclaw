package vip.mate.analytics.upload;

import java.util.List;

/**
 * Summary returned by {@link ExcelParseService#parse} after reading a sheet.
 *
 * <p>Rows whose cells cannot be converted to their declared {@code FieldType}
 * are skipped rather than aborting the whole upload — mirroring the batch-level
 * isolation {@link ExcelIngestService} applies at the database layer. The caller
 * merges these with the ingest result before writing the upload log.
 *
 * @param rows     rows that parsed cleanly (header row excluded)
 * @param rejected number of data rows skipped because a cell failed to convert
 * @param errors   human-readable error messages, one entry per rejected row
 */
public record ParseResult(List<ParsedRow> rows, int rejected, List<String> errors) {}
