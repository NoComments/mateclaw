package vip.mate.analytics.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetField;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetService;
import vip.mate.analytics.dataset.DatasetUploadLog;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;
import vip.mate.analytics.storage.DynamicTableService;
import vip.mate.analytics.upload.ExcelIngestService;
import vip.mate.analytics.upload.ExcelInspectService;
import vip.mate.analytics.upload.ExcelInspectService.InspectResult;
import vip.mate.analytics.upload.ExcelParseService;
import vip.mate.analytics.upload.IngestResult;
import vip.mate.analytics.upload.ParsedRow;
import vip.mate.common.result.R;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST surface for dataset lifecycle management.
 *
 * <p>Workspace isolation is enforced via the {@code X-Workspace-Id} request header.
 * The preview endpoint queries the physical table using code-generated SQL (not LLM-generated),
 * so SqlGuard is not required — the table name is validated by the service before use.
 */
@Tag(name = "数据集管理")
@RestController
@RequestMapping("/api/v1/analytics/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final DatasetRepository datasetRepo;
    private final DatasetUploadLogRepository uploadLogRepo;
    private final DynamicTableService dynamicTable;
    private final ExcelParseService parse;
    private final ExcelIngestService ingest;
    private final ExcelInspectService inspectService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ list

    @Operation(summary = "List datasets in the workspace")
    @GetMapping
    public R<List<Dataset>> list(
            @RequestHeader("X-Workspace-Id") long workspaceId) {
        return R.ok(datasetService.listByWorkspace(workspaceId));
    }

    // ------------------------------------------------------------------ create

    /** Response of the one-step upload: the dataset plus the log of its first ingest. */
    public record CreateDatasetResponse(Dataset dataset, DatasetUploadLog uploadLog) {}

    /**
     * Inspect a file's headers and infer field definitions. Nothing is persisted —
     * the client shows these for confirmation, then posts them back to {@link #createFromFile}.
     */
    @Operation(summary = "Infer field definitions from a file without persisting")
    @PostMapping("/inspect")
    public ResponseEntity<R<InspectResult>> inspect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheet", required = false) String sheet) throws IOException {
        validateFile(file);
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(R.ok(inspectService.inspect(in, sheet)));
        }
    }

    /**
     * Create a dataset from a file in one call: persist schema, create the physical
     * table, and ingest every row. The file is uploaded exactly once.
     *
     * @param file       multipart .xlsx (required)
     * @param name       dataset name
     * @param fieldsJson JSON array of {fieldName, fieldType, fieldUnit?, ordinal}
     * @param sheet      optional sheet name; first sheet when omitted
     */
    @Operation(summary = "Create a dataset from a file and ingest it in one call")
    @PostMapping
    public ResponseEntity<R<CreateDatasetResponse>> createFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("fields") String fieldsJson,
            @RequestParam(value = "sheet", required = false) String sheet,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) throws IOException {

        validateFile(file);

        List<DatasetField> fields = parseFields(fieldsJson);

        Dataset ds = new Dataset();
        ds.setWorkspaceId(workspaceId);
        ds.setName(name);
        ds.setRowCount(0);
        datasetService.createWithFields(ds, fields);

        dynamicTable.ensureTable(ds, fields);

        DatasetUploadLog uploadLog = new DatasetUploadLog();
        uploadLog.setDatasetId(ds.getId());
        uploadLog.setFileName(file.getOriginalFilename());
        uploadLog.setFileSize(file.getSize());
        uploadLog.setStatus("PROCESSING");
        uploadLog.setUploader(userId);
        uploadLog.setUploadTime(LocalDateTime.now());
        uploadLogRepo.insert(uploadLog);

        try {
            List<ParsedRow> rows = parse.parse(file.getInputStream(), fields, sheet);
            IngestResult result = ingest.ingest(ds, fields, rows, uploadLog.getId());

            uploadLog.setRowsReceived(rows.size());
            uploadLog.setRowsInserted(result.inserted());
            uploadLog.setRowsRejected(result.rejected());
            if (result.rejected() == 0) {
                uploadLog.setStatus("SUCCESS");
            } else if (result.inserted() > 0) {
                uploadLog.setStatus("PARTIAL");
                uploadLog.setErrorSummary(String.join("; ", result.errors()));
            } else {
                uploadLog.setStatus("FAILED");
                uploadLog.setErrorSummary(String.join("; ", result.errors()));
            }

            ds.setRowCount(result.inserted());
            ds.setLastUploadAt(LocalDateTime.now());
            datasetRepo.updateById(ds);
        } catch (IOException e) {
            uploadLog.setStatus("FAILED");
            uploadLog.setErrorSummary("File read error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            uploadLog.setStatus("FAILED");
            uploadLog.setErrorSummary(e.getMessage());
        } finally {
            uploadLogRepo.updateById(uploadLog);
        }

        return ResponseEntity.ok(R.ok(new CreateDatasetResponse(ds, uploadLog)));
    }

    // ------------------------------------------------------------------ get one

    @Operation(summary = "Get a dataset by id")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Dataset ds = datasetService.getById(id);
        if (ds == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(R.ok(ds));
    }

    // ------------------------------------------------------------------ preview

    @Operation(summary = "Preview the first N rows from the dataset's physical table")
    @GetMapping("/{id}/preview")
    public ResponseEntity<?> preview(
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit) {
        Dataset ds = datasetService.getById(id);
        if (ds == null) {
            return ResponseEntity.notFound().build();
        }

        if (ds.getPhysicalTable() == null || ds.getPhysicalTable().isBlank()) {
            return ResponseEntity.ok(R.ok(List.of()));
        }

        String physicalTable = ds.getPhysicalTable();
        // Same guard as DatasetService.delete() and ExcelIngestService.SAFE_TABLE_NAME:
        // only a dataset's own table is readable here, so a physical_table value that
        // somehow named an application table cannot be selected from.
        if (!physicalTable.matches("^dataset_[a-z0-9_]+$")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("Unsafe physical table name"));
        }

        // Code-generated SQL — table name is validated above
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + physicalTable + " LIMIT ?", limit);
        return ResponseEntity.ok(R.ok(rows));
    }

    // ------------------------------------------------------------------ upload log history

    @Operation(summary = "List upload log history for a dataset")
    @GetMapping("/{id}/uploads")
    public ResponseEntity<?> uploads(@PathVariable Long id) {
        Dataset ds = datasetService.getById(id);
        if (ds == null) {
            return ResponseEntity.notFound().build();
        }
        List<DatasetUploadLog> logs = uploadLogRepo.selectList(
                new LambdaQueryWrapper<DatasetUploadLog>()
                        .eq(DatasetUploadLog::getDatasetId, id)
                        .orderByDesc(DatasetUploadLog::getUploadTime));
        return ResponseEntity.ok(R.ok(logs));
    }

    // ------------------------------------------------------------------ delete

    @Operation(summary = "Delete a dataset and its physical rows")
    @DeleteMapping("/{id}")
    public ResponseEntity<R<Void>> delete(@PathVariable Long id) {
        try {
            datasetService.delete(id);
            return ResponseEntity.ok(R.ok());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(e.getMessage()));
        }
    }

    /** Maps the client's field JSON onto entities. Physical column names are derived server-side. */
    private List<DatasetField> parseFields(String fieldsJson) {
        try {
            JsonNode arr = objectMapper.readTree(fieldsJson);
            List<DatasetField> out = new ArrayList<>();
            int i = 0;
            for (JsonNode n : arr) {
                DatasetField f = new DatasetField();
                f.setFieldName(n.path("fieldName").asText());
                f.setFieldType(n.path("fieldType").asText("STRING"));
                if (n.hasNonNull("fieldUnit")) {
                    f.setFieldUnit(n.get("fieldUnit").asText());
                }
                f.setOrdinal(n.path("ordinal").asInt(i));
                f.setIsNullable(true);
                out.add(f);
                i++;
            }
            return out;
        } catch (IOException e) {
            throw new IllegalArgumentException("字段定义格式错误: " + e.getMessage());
        }
    }

    /** Validates file size and extension. Mirrors DatasetUploadController.validateFile. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file must not be empty");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File too large: " + file.getSize() + " bytes (max 50 MB)");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "Only .xlsx files are accepted; received: " + originalName);
        }
    }
}
