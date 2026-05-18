package vip.mate.analytics.upload;

import java.util.List;

/**
 * Summary returned by {@link ExcelIngestService#ingest} after a batch upload attempt.
 *
 * @param inserted number of rows successfully written to the physical table
 * @param rejected number of rows that could not be written (entire batch counted on failure)
 * @param errors   human-readable error messages, one entry per failed batch
 */
public record IngestResult(int inserted, int rejected, List<String> errors) {}
