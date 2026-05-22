package vip.mate.analytics.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ingests parsed Excel rows into the dynamic dataset physical table in batches.
 *
 * <p>Batch-level error isolation: if a single batch fails (e.g. constraint
 * violation), that batch is counted as rejected and processing continues with
 * the next batch rather than aborting the entire upload.
 *
 * <p>After all batches complete, the owning {@link Dataset}'s {@code rowCount}
 * and {@code lastUploadAt} are updated to reflect the new state.
 */
@Service
@RequiredArgsConstructor
public class ExcelIngestService {

    private static final int BATCH_SIZE = 500;

    /** Guards against SQL injection via misconfigured template physical table names. */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^dataset_[a-z0-9_]+$");

    private final JdbcTemplate jdbc;
    private final DatasetRepository datasetRepo;

    /**
     * Inserts all parsed rows into the physical table defined by the template,
     * partitioned into batches of {@value #BATCH_SIZE}.
     *
     * @param ds          the owning dataset (must already be persisted)
     * @param tpl         the template providing {@code physicalTable}
     * @param fields      ordered list of fields that map to INSERT columns
     * @param rows        rows produced by {@link ExcelParseService}
     * @param uploadLogId FK to the upload-log entry for traceability
     * @return summary of inserted, rejected, and any per-batch error messages
     */
    @Transactional
    public IngestResult ingest(
            Dataset ds,
            DatasetTemplate tpl,
            List<DatasetTemplateField> fields,
            List<ParsedRow> rows,
            Long uploadLogId
    ) {
        if (rows.isEmpty()) {
            return new IngestResult(0, 0, List.of());
        }

        String physicalTable = tpl.getPhysicalTable();
        if (!SAFE_TABLE_NAME.matcher(physicalTable).matches()) {
            throw new IllegalArgumentException(
                    "Unsafe physical table name: " + physicalTable);
        }

        String sql = buildInsertSql(physicalTable, fields);

        int inserted = 0;
        int rejected = 0;
        List<String> errors = new ArrayList<>();

        List<List<ParsedRow>> batches = partition(rows, BATCH_SIZE);
        for (List<ParsedRow> batch : batches) {
            try {
                jdbc.batchUpdate(sql, batch, batch.size(), (ps, row) -> {
                    ps.setLong(1, ds.getId());
                    ps.setLong(2, uploadLogId);
                    for (int i = 0; i < fields.size(); i++) {
                        ps.setObject(i + 3, row.values().get(fields.get(i).getFieldCode()));
                    }
                });
                inserted += batch.size();
            } catch (DataAccessException e) {
                rejected += batch.size();
                errors.add("行 " + batch.get(0).rowNumber() + "+: "
                        + e.getMostSpecificCause().getMessage());
            }
        }

        ds.setRowCount((ds.getRowCount() == null ? 0 : ds.getRowCount()) + inserted);
        ds.setLastUploadAt(LocalDateTime.now());
        datasetRepo.updateById(ds);

        return new IngestResult(inserted, rejected, errors);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String buildInsertSql(String physicalTable, List<DatasetTemplateField> fields) {
        String fieldColumns = fields.stream()
                .map(DatasetTemplateField::getFieldCode)
                .collect(Collectors.joining(", "));
        String placeholders = fields.stream()
                .map(f -> "?")
                .collect(Collectors.joining(", "));
        return String.format(
                "INSERT INTO %s (dataset_id, upload_log_id, %s) VALUES (?, ?, %s)",
                physicalTable, fieldColumns, placeholders);
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
