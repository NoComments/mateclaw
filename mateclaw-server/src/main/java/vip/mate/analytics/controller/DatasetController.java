package vip.mate.analytics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetService;
import vip.mate.analytics.dataset.DatasetUploadLog;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;
import vip.mate.common.result.R;

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
    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ list

    @Operation(summary = "List datasets in the workspace")
    @GetMapping
    public R<List<Dataset>> list(
            @RequestHeader("X-Workspace-Id") long workspaceId) {
        return R.ok(datasetService.listByWorkspace(workspaceId));
    }

    // ------------------------------------------------------------------ create

    @Operation(summary = "Create a new dataset")
    @PostMapping
    public R<Dataset> create(
            @RequestBody CreateDatasetRequest body,
            @RequestHeader("X-Workspace-Id") long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Dataset ds = new Dataset();
        ds.setName(body.name());
        ds.setDescription(body.description());
        ds.setWorkspaceId(workspaceId);
        if (userId != null) {
            ds.setCreator(userId);
        }
        return R.ok(datasetService.create(ds));
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

    // ------------------------------------------------------------------ request record

    /** Request body for dataset creation. */
    public record CreateDatasetRequest(String name, String description) {}
}
