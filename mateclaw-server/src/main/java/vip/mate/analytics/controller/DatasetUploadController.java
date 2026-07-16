package vip.mate.analytics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetField;
import vip.mate.analytics.dataset.DatasetFieldRepository;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetUploadLog;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;
import vip.mate.analytics.storage.DynamicTableService;
import vip.mate.analytics.upload.ExcelIngestService;
import vip.mate.analytics.upload.ExcelParseService;
import vip.mate.analytics.upload.IngestResult;
import vip.mate.analytics.upload.ParsedRow;
import vip.mate.common.result.R;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST surface for uploading Excel data into a dataset's physical table.
 *
 * <p>Accepts a multipart {@code .xlsx} file and an optional sheet name.
 * The upload flow:
 * <ol>
 *   <li>Validate file constraints (size ≤ 50 MB, extension {@code .xlsx}).</li>
 *   <li>Load the dataset and its fields.</li>
 *   <li>Ensure the physical table DDL is up-to-date.</li>
 *   <li>Create a {@code PROCESSING} upload-log entry.</li>
 *   <li>Parse rows from the workbook.</li>
 *   <li>Ingest rows into the physical table.</li>
 *   <li>Update the log with final counts and status ({@code SUCCESS / PARTIAL / FAILED}).</li>
 * </ol>
 *
 * <p>The log update in step 7 runs in a {@code finally} block so partial progress
 * is always recorded even if an unexpected exception occurs.
 */
@Tag(name = "数据集上传")
@RestController
@RequestMapping("/api/v1/analytics/datasets")
@RequiredArgsConstructor
@Slf4j
public class DatasetUploadController {

    private final DatasetRepository datasetRepo;
    private final DatasetFieldRepository fieldRepo;
    private final DatasetUploadLogRepository uploadLogRepo;
    private final DynamicTableService dynamicTable;
    private final ExcelParseService parse;
    private final ExcelIngestService ingest;

    /**
     * Upload an Excel file into the dataset identified by {@code id}.
     *
     * @param id     dataset primary key
     * @param file   multipart {@code .xlsx} file (required)
     * @param sheet  optional sheet name; if omitted the first sheet is used
     * @param userId optional uploader identity from the {@code X-User-Id} header
     * @return the completed upload-log entry
     */
    @Operation(summary = "Upload an Excel file into a dataset")
    @PostMapping("/{id}/upload")
    public ResponseEntity<R<DatasetUploadLog>> upload(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheet", required = false) String sheet,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        // ── 1. File validation ────────────────────────────────────────────────
        try {
            DatasetFileValidator.validate(file);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(e.getMessage()));
        }

        // ── 2. Load dataset → fields ─────────────────────────────────────────
        Dataset ds = datasetRepo.selectById(id);
        if (ds == null || Objects.equals(ds.getDeleted(), 1)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("Dataset not found: " + id));
        }

        List<DatasetField> fields = fieldRepo.selectList(
                new LambdaQueryWrapper<DatasetField>()
                        .eq(DatasetField::getDatasetId, ds.getId())
                        .eq(DatasetField::getDeleted, 0)
                        .orderByAsc(DatasetField::getOrdinal));

        // ── 3. Ensure physical table DDL ──────────────────────────────────────
        dynamicTable.ensureTable(ds, fields);

        // ── 4. Create PROCESSING log ──────────────────────────────────────────
        DatasetUploadLog uploadLog = new DatasetUploadLog();
        uploadLog.setDatasetId(id);
        uploadLog.setFileName(file.getOriginalFilename());
        uploadLog.setFileSize(file.getSize());
        uploadLog.setStatus("PROCESSING");
        uploadLog.setUploader(userId);
        uploadLog.setUploadTime(LocalDateTime.now());
        uploadLogRepo.insert(uploadLog);

        // ── 5 + 6. Parse then ingest (log update always runs in finally) ──────
        try {
            List<ParsedRow> rows = parse.parse(file.getInputStream(), fields, sheet);

            IngestResult result = ingest.ingest(ds, fields, rows, uploadLog.getId());

            uploadLog.setRowsReceived(rows.size());
            uploadLog.setRowsInserted(result.inserted());
            uploadLog.setRowsRejected(result.rejected());

            if (result.inserted() == 0) {
                uploadLog.setStatus("FAILED");
                uploadLog.setErrorSummary(result.errors().isEmpty()
                        ? "No ingestable rows found in sheet"
                        : String.join("; ", result.errors()));
            } else if (result.rejected() == 0) {
                uploadLog.setStatus("SUCCESS");
            } else if (result.inserted() > 0) {
                uploadLog.setStatus("PARTIAL");
                uploadLog.setErrorSummary(String.join("; ", result.errors()));
            } else {
                uploadLog.setStatus("FAILED");
                uploadLog.setErrorSummary(String.join("; ", result.errors()));
            }

        } catch (IOException e) {
            log.error("Failed to read uploaded file for dataset {}: {}", id, e.getMessage(), e);
            uploadLog.setStatus("FAILED");
            uploadLog.setErrorSummary("File read error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Upload rejected for dataset {}: {}", id, e.getMessage());
            uploadLog.setStatus("FAILED");
            uploadLog.setErrorSummary(e.getMessage());
        } finally {
            // ── 7. Always persist final log state ─────────────────────────────
            uploadLogRepo.updateById(uploadLog);
        }

        return ResponseEntity.ok(R.ok(uploadLog));
    }

}
