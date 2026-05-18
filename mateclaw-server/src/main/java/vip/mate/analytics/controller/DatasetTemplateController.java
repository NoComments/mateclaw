package vip.mate.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.DatasetTemplateService;
import vip.mate.analytics.template.DatasetTemplateService.TemplateWithFields;
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
@RequestMapping("/api/analytics/templates")
@RequiredArgsConstructor
public class DatasetTemplateController {

    private final DatasetTemplateService templateService;

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

    // ------------------------------------------------------------------ append field

    @Operation(summary = "Append a field to an existing template")
    @PostMapping("/{id}/fields")
    public R<Void> appendField(
            @PathVariable Long id,
            @RequestBody DatasetTemplateField field) {
        templateService.appendField(id, field);
        return R.ok();
    }

    // ------------------------------------------------------------------ remove field

    @Operation(summary = "Remove a field — blocked if the template already has datasets")
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

    // ------------------------------------------------------------------ request records

    /** Request body for template creation. */
    public record CreateTemplateRequest(DatasetTemplate template, List<DatasetTemplateField> fields) {}

    /** Request body for the enabled toggle. */
    public record EnabledRequest(boolean enabled) {}
}
