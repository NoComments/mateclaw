package vip.mate.analytics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.mate.analytics.controller.DatasetController.CreateDatasetRequest;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetService;
import vip.mate.analytics.dataset.DatasetUploadLog;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateRepository;
import vip.mate.common.result.R;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Plain unit tests for {@link DatasetController}.
 *
 * <p>No Spring context is loaded; all collaborators are Mockito mocks.
 */
class DatasetControllerTest {

    private DatasetService datasetService;
    private DatasetRepository datasetRepo;
    private DatasetTemplateRepository templateRepo;
    private DatasetUploadLogRepository uploadLogRepo;
    private JdbcTemplate jdbc;
    private DatasetController controller;

    @BeforeEach
    void setUp() {
        datasetService = mock(DatasetService.class);
        datasetRepo = mock(DatasetRepository.class);
        templateRepo = mock(DatasetTemplateRepository.class);
        uploadLogRepo = mock(DatasetUploadLogRepository.class);
        jdbc = mock(JdbcTemplate.class);
        controller = new DatasetController(datasetService, datasetRepo, templateRepo, uploadLogRepo, jdbc);
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("GET /api/analytics/datasets returns 200 with dataset list for workspace")
    void list_returnsDatasetListForWorkspace() {
        Dataset d1 = new Dataset();
        d1.setId(1L);
        d1.setName("Dataset A");
        d1.setWorkspaceId(42L);

        Dataset d2 = new Dataset();
        d2.setId(2L);
        d2.setName("Dataset B");
        d2.setWorkspaceId(42L);

        when(datasetService.listByWorkspace(42L)).thenReturn(List.of(d1, d2));

        R<List<Dataset>> response = controller.list(42L);

        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getName()).isEqualTo("Dataset A");
        assertThat(response.getData().get(1).getName()).isEqualTo("Dataset B");
        verify(datasetService).listByWorkspace(42L);
    }

    @Test
    @DisplayName("GET /api/analytics/datasets returns empty list when workspace has none")
    void list_returnsEmptyListWhenNoneExist() {
        when(datasetService.listByWorkspace(99L)).thenReturn(List.of());

        R<List<Dataset>> response = controller.list(99L);

        assertThat(response.getData()).isEmpty();
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("POST /api/analytics/datasets stamps workspaceId and creator from headers")
    void create_stampsWorkspaceAndCreator() {
        Dataset saved = new Dataset();
        saved.setId(10L);
        saved.setWorkspaceId(5L);
        saved.setTemplateId(3L);
        saved.setName("My Dataset");
        saved.setCreator(7L);

        when(datasetService.create(any(Dataset.class))).thenReturn(saved);

        CreateDatasetRequest body = new CreateDatasetRequest(3L, "My Dataset", "desc");
        R<Dataset> response = controller.create(body, 5L, 7L);

        verify(datasetService).create(argThat(ds ->
                ds.getWorkspaceId().equals(5L)
                && ds.getTemplateId().equals(3L)
                && Long.valueOf(7L).equals(ds.getCreator())));
        assertThat(response.getData().getId()).isEqualTo(10L);
    }

    // ------------------------------------------------------------------ get

    @Test
    @DisplayName("GET /api/analytics/datasets/{id} returns 404 when not found")
    void get_returns404WhenNotFound() {
        when(datasetService.getById(99L)).thenReturn(null);

        ResponseEntity<?> response = controller.get(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/analytics/datasets/{id} returns 200 with dataset when found")
    void get_returnsDatasetWhenFound() {
        Dataset ds = new Dataset();
        ds.setId(3L);
        ds.setName("Found Dataset");

        when(datasetService.getById(3L)).thenReturn(ds);

        ResponseEntity<?> response = controller.get(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ preview

    @Test
    @DisplayName("GET /api/analytics/datasets/{id}/preview returns 200 with rows")
    void preview_returnsRowsFromPhysicalTable() {
        Dataset ds = new Dataset();
        ds.setId(1L);
        ds.setTemplateId(10L);

        DatasetTemplate template = new DatasetTemplate();
        template.setId(10L);
        template.setPhysicalTable("ds_livestock_health");

        when(datasetService.getById(1L)).thenReturn(ds);
        when(templateRepo.selectById(10L)).thenReturn(template);

        List<Map<String, Object>> fakeRows = List.of(
                Map.of("id", 1, "dataset_id", 1L, "animal_id", "A001"),
                Map.of("id", 2, "dataset_id", 1L, "animal_id", "A002")
        );
        when(jdbc.queryForList(
                eq("SELECT * FROM ds_livestock_health WHERE dataset_id = ? LIMIT ?"),
                eq(1L), eq(100)
        )).thenReturn(fakeRows);

        ResponseEntity<?> response = controller.preview(1L, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        R<List<Map<String, Object>>> body = (R<List<Map<String, Object>>>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getData()).hasSize(2);
        verify(jdbc).queryForList(
                "SELECT * FROM ds_livestock_health WHERE dataset_id = ? LIMIT ?",
                1L, 100);
    }

    @Test
    @DisplayName("GET /api/analytics/datasets/{id}/preview returns 404 when dataset not found")
    void preview_returns404WhenDatasetNotFound() {
        when(datasetService.getById(99L)).thenReturn(null);

        ResponseEntity<?> response = controller.preview(99L, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/analytics/datasets/{id}/preview returns empty list when no physical table")
    void preview_returnsEmptyListWhenNoPhysicalTable() {
        Dataset ds = new Dataset();
        ds.setId(5L);
        ds.setTemplateId(20L);

        DatasetTemplate template = new DatasetTemplate();
        template.setId(20L);
        template.setPhysicalTable(null);

        when(datasetService.getById(5L)).thenReturn(ds);
        when(templateRepo.selectById(20L)).thenReturn(template);

        ResponseEntity<?> response = controller.preview(5L, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        R<List<?>> body = (R<List<?>>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getData()).isEmpty();
        verifyNoInteractions(jdbc);
    }

    // ------------------------------------------------------------------ uploads

    @Test
    @DisplayName("GET /api/analytics/datasets/{id}/uploads returns upload log list")
    void uploads_returnsUploadLogs() {
        Dataset ds = new Dataset();
        ds.setId(1L);

        DatasetUploadLog log1 = new DatasetUploadLog();
        log1.setId(100L);
        log1.setDatasetId(1L);
        log1.setStatus("SUCCESS");

        when(datasetService.getById(1L)).thenReturn(ds);
        when(uploadLogRepo.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log1));

        ResponseEntity<?> response = controller.uploads(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        R<List<DatasetUploadLog>> body = (R<List<DatasetUploadLog>>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getData()).hasSize(1);
        assertThat(body.getData().get(0).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("GET /api/analytics/datasets/{id}/uploads returns 404 when dataset not found")
    void uploads_returns404WhenDatasetNotFound() {
        when(datasetService.getById(88L)).thenReturn(null);

        ResponseEntity<?> response = controller.uploads(88L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ delete

    @Test
    @DisplayName("DELETE /api/analytics/datasets/{id} returns 200 when deletion succeeds")
    void delete_returns200WhenSucceeds() {
        doNothing().when(datasetService).delete(1L);

        ResponseEntity<R<Void>> response = controller.delete(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(datasetService).delete(1L);
    }

    @Test
    @DisplayName("DELETE /api/analytics/datasets/{id} returns 404 when dataset not found")
    void delete_returns404WhenNotFound() {
        doThrow(new IllegalArgumentException("Dataset not found: 99"))
                .when(datasetService).delete(99L);

        ResponseEntity<R<Void>> response = controller.delete(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMsg()).contains("Dataset not found");
    }
}
