package vip.mate.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.upload.ExcelInspectService;
import vip.mate.analytics.upload.ExcelInspectService.InspectResult;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.DatasetTemplateService;
import vip.mate.analytics.template.DatasetTemplateService.TemplateWithFields;
import vip.mate.analytics.template.DatasetTemplateService.UpdateFieldMetaRequest;
import vip.mate.common.result.R;

import java.util.List;

/**
 * REST surface for dataset template management.
 *
 * <p>Workspace isolation is enforced via the {@code X-Workspace-Id} request header
 * (set by the workspace interceptor in production; passed directly in tests).
 * Creator identity is carried on the {@code X-User-Id} header and stamped onto
 * new template rows before persistence.
 */
@Tag(name = "数据集模板管理")
@RestController
@RequestMapping("/api/v1/analytics/templates")
@RequiredArgsConstructor
public class DatasetTemplateController {

    private final DatasetTemplateService templateService;
    private final ExcelInspectService inspectService;

    // ------------------------------------------------------------------ list

    @Operation(summary = "List templates in the workspace")
    @GetMapping
    public R<List<DatasetTemplate>> list(
            @RequestHeader("X-Workspace-Id") long workspaceId) {
        return R.ok(templateService.listByWorkspace(workspaceId));
    }

    // ------------------------------------------------------------------ create

    @Operation(summary = "Create a template with its initial fields")
    @PostMapping
    public R<DatasetTemplate> create(
            @RequestBody CreateTemplateRequest body,
            @RequestHeader("X-Workspace-Id") long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        DatasetTemplate t = body.template();
        // Enforce workspace ownership from the trusted header; the request body
        // must not be able to plant a row into another tenant's workspace.
        t.setWorkspaceId(workspaceId);
        if (userId != null) {
            t.setCreator(userId);
        }
        List<DatasetTemplateField> fields = body.fields() != null ? body.fields() : List.of();
        return R.ok(templateService.create(t, fields));
    }

    // ------------------------------------------------------------------ get with fields

    @Operation(summary = "Get a template by id, including its fields ordered by ordinal")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        TemplateWithFields result = templateService.getWithFields(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(R.ok(result));
    }

    // ------------------------------------------------------------------ list fields

    @Operation(summary = "List fields of a template, ordered by ordinal")
    @GetMapping("/{id}/fields")
    public R<List<DatasetTemplateField>> listFields(@PathVariable Long id) {
        return R.ok(templateService.listFields(id));
    }

    // ------------------------------------------------------------------ delete template

    @Operation(summary = "Delete a template — blocked if datasets reference it")
    @DeleteMapping("/{id}")
    public ResponseEntity<R<Void>> delete(@PathVariable Long id) {
        try {
            templateService.delete(id);
            return ResponseEntity.ok(R.ok());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(R.fail(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ append field

    @Operation(summary = "Append a field to an existing template")
    @PostMapping("/{id}/fields")
    public R<Void> appendField(
            @PathVariable Long id,
            @RequestBody DatasetTemplateField field) {
        templateService.appendField(id, field);
        return R.ok();
    }

    // ------------------------------------------------------------------ update field metadata

    @Operation(summary = "Update display/metadata columns of a field (fieldName, fieldUnit, semantic, excelHeader, ordinal, isNullable)")
    @PatchMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<R<Void>> updateFieldMeta(
            @PathVariable Long id,
            @PathVariable Long fieldId,
            @RequestBody UpdateFieldMetaRequest body) {
        try {
            templateService.updateFieldMeta(id, fieldId, body);
            return ResponseEntity.ok(R.ok());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ remove field

    @Operation(summary = "Remove a field — blocked if the template already has successful upload data")
    @DeleteMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<R<Void>> removeField(
            @PathVariable Long id,
            @PathVariable Long fieldId) {
        try {
            templateService.removeField(id, fieldId);
            return ResponseEntity.ok(R.ok());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(R.fail(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ toggle enabled

    @Operation(summary = "Enable or disable a template")
    @PutMapping("/{id}/enabled")
    public R<Void> setEnabled(
            @PathVariable Long id,
            @RequestBody EnabledRequest body) {
        templateService.setEnabled(id, body.enabled());
        return R.ok();
    }

    // ------------------------------------------------------------------ inspect excel

    @Operation(summary = "Inspect an .xlsx file and return inferred field definitions (no data persisted)")
    @PostMapping("/inspect-excel")
    public ResponseEntity<R<InspectResult>> inspectExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String sheetName) {
        try {
            InspectResult result = inspectService.inspect(file.getInputStream(), sheetName);
            return ResponseEntity.ok(R.ok(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(R.fail(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(R.fail("文件解析失败: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ request records

    /** Request body for template creation. */
    public record CreateTemplateRequest(DatasetTemplate template, List<DatasetTemplateField> fields) {}

    /** Request body for the enabled toggle. */
    public record EnabledRequest(boolean enabled) {}
}
