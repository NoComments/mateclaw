package vip.mate.analytics.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.DatasetTemplateService;
import vip.mate.analytics.template.DatasetTemplateService.TemplateWithFields;
import vip.mate.analytics.controller.DatasetTemplateController.CreateTemplateRequest;
import vip.mate.analytics.controller.DatasetTemplateController.EnabledRequest;
import vip.mate.analytics.template.DatasetTemplateService.UpdateFieldMetaRequest;
import vip.mate.analytics.upload.ExcelInspectService;
import vip.mate.analytics.upload.ExcelInspectService.InspectResult;
import vip.mate.analytics.upload.ExcelInspectService.InspectedField;
import vip.mate.common.result.R;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Plain unit tests for {@link DatasetTemplateController}.
 *
 * <p>No Spring context is loaded; all collaborators are Mockito mocks.
 * Spring wiring is covered by the broader integration test suite.
 */
class DatasetTemplateControllerTest {

    private DatasetTemplateService service;
    private ExcelInspectService inspectService;
    private DatasetTemplateController controller;

    @BeforeEach
    void setUp() {
        service = mock(DatasetTemplateService.class);
        inspectService = mock(ExcelInspectService.class);
        controller = new DatasetTemplateController(service, inspectService);
    }

    // ------------------------------------------------------------------ happy path: list

    @Test
    @DisplayName("GET /api/analytics/templates returns 200 with template list for workspace")
    void list_returnsTemplatesForWorkspace() {
        DatasetTemplate t1 = new DatasetTemplate();
        t1.setId(1L);
        t1.setName("Template A");
        t1.setWorkspaceId(42L);

        DatasetTemplate t2 = new DatasetTemplate();
        t2.setId(2L);
        t2.setName("Template B");
        t2.setWorkspaceId(42L);

        when(service.listByWorkspace(42L)).thenReturn(List.of(t1, t2));

        R<List<DatasetTemplate>> response = controller.list(42L);

        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getName()).isEqualTo("Template A");
        assertThat(response.getData().get(1).getName()).isEqualTo("Template B");
        verify(service).listByWorkspace(42L);
    }

    @Test
    @DisplayName("GET /api/analytics/templates returns empty list when workspace has no templates")
    void list_returnsEmptyListWhenNoneExist() {
        when(service.listByWorkspace(99L)).thenReturn(List.of());

        R<List<DatasetTemplate>> response = controller.list(99L);

        assertThat(response.getData()).isEmpty();
    }

    // ------------------------------------------------------------------ protection: removeField 409

    @Test
    @DisplayName("DELETE /api/analytics/templates/1/fields/1 returns 409 when service throws IllegalStateException")
    void removeField_returns409WhenServiceThrowsIllegalState() {
        doThrow(new IllegalStateException("模板下已有真实上传数据，禁止删除字段（仅允许追加）"))
                .when(service).removeField(1L, 1L);

        ResponseEntity<R<Void>> response = controller.removeField(1L, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMsg()).contains("禁止删除字段");
        verify(service).removeField(1L, 1L);
    }

    @Test
    @DisplayName("DELETE /api/analytics/templates/1/fields/1 returns 200 when removal succeeds")
    void removeField_returns200WhenSucceeds() {
        doNothing().when(service).removeField(1L, 1L);

        ResponseEntity<R<Void>> response = controller.removeField(1L, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ create stamps workspaceId

    @Test
    @DisplayName("POST /api/analytics/templates stamps workspaceId and creator from headers")
    void create_stampsWorkspaceAndCreator() {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode("survey");
        t.setName("Survey");

        DatasetTemplate saved = new DatasetTemplate();
        saved.setId(10L);
        saved.setCode("survey");
        saved.setWorkspaceId(5L);
        saved.setCreator(7L);

        when(service.create(any(DatasetTemplate.class), anyList())).thenReturn(saved);

        CreateTemplateRequest body = new CreateTemplateRequest(t, List.of());
        R<DatasetTemplate> response = controller.create(body, 5L, 7L);

        // workspaceId and creator must have been stamped before the service call
        verify(service).create(argThat(tmpl -> tmpl.getWorkspaceId() == 5L && Long.valueOf(7L).equals(tmpl.getCreator())), anyList());
        assertThat(response.getData().getId()).isEqualTo(10L);
    }

    // ------------------------------------------------------------------ get with fields

    @Test
    @DisplayName("GET /api/analytics/templates/{id} returns 404 when template not found")
    void get_returns404WhenNotFound() {
        when(service.getWithFields(99L)).thenReturn(null);

        ResponseEntity<?> response = controller.get(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/analytics/templates/{id} returns template with fields when found")
    void get_returnsTemplateWithFields() {
        DatasetTemplate t = new DatasetTemplate();
        t.setId(3L);
        DatasetTemplateField f = new DatasetTemplateField();
        f.setId(100L);
        TemplateWithFields twf = new TemplateWithFields(t, List.of(f));

        when(service.getWithFields(3L)).thenReturn(twf);

        ResponseEntity<?> response = controller.get(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ updateFieldMeta

    @Test
    @DisplayName("PATCH /api/analytics/templates/1/fields/1 returns 200 when update succeeds")
    void updateFieldMeta_returns200WhenSucceeds() {
        var patch = new UpdateFieldMetaRequest("新名称", null, null, null, null, null, null, null, null, null);
        doNothing().when(service).updateFieldMeta(1L, 1L, patch);

        ResponseEntity<R<Void>> response = controller.updateFieldMeta(1L, 1L, patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).updateFieldMeta(1L, 1L, patch);
    }

    @Test
    @DisplayName("PATCH /api/analytics/templates/1/fields/99 returns 404 when field not found")
    void updateFieldMeta_returns404WhenFieldNotFound() {
        var patch = new UpdateFieldMetaRequest("名称", null, null, null, null, null, null, null, null, null);
        doThrow(new IllegalArgumentException("Field not found: 99"))
                .when(service).updateFieldMeta(1L, 99L, patch);

        ResponseEntity<R<Void>> response = controller.updateFieldMeta(1L, 99L, patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ setEnabled

    @Test
    @DisplayName("PUT /api/analytics/templates/{id}/enabled delegates to service")
    void setEnabled_delegatesToService() {
        controller.setEnabled(5L, new EnabledRequest(false));

        verify(service).setEnabled(5L, false);
    }

    // ------------------------------------------------------------------ inspect-excel

    @Test
    @DisplayName("POST /inspect-excel returns 200 with inferred fields on success")
    void inspectExcel_returns200WhenSucceeds() throws Exception {
        var fields = List.of(
                new InspectedField("日期", "DATE", 0),
                new InspectedField("头数", "INT", 1));
        var result = new InspectResult(List.of("日期", "头数"), fields, true);
        when(inspectService.inspect(any(), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{});

        ResponseEntity<R<InspectResult>> response = controller.inspectExcel(file, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().suggestedFields()).hasSize(2);
    }

    @Test
    @DisplayName("POST /inspect-excel returns 400 when sheet not found")
    void inspectExcel_returns400WhenSheetNotFound() throws Exception {
        when(inspectService.inspect(any(), eq("missing")))
                .thenThrow(new IllegalArgumentException("Sheet not found: missing"));

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{});

        ResponseEntity<R<InspectResult>> response = controller.inspectExcel(file, "missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
