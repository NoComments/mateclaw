package vip.mate.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetService;
import vip.mate.analytics.dataset.DatasetUploadLog;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;
import vip.mate.analytics.storage.DynamicTableService;
import vip.mate.analytics.upload.ExcelIngestService;
import vip.mate.analytics.upload.ExcelInspectService;
import vip.mate.analytics.upload.ExcelParseService;
import vip.mate.analytics.upload.IngestResult;
import vip.mate.common.result.R;

import java.io.IOException;
import java.io.InputStream;
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
    private DatasetUploadLogRepository uploadLogRepo;
    private DynamicTableService dynamicTable;
    private ExcelParseService parse;
    private ExcelIngestService ingest;
    private ExcelInspectService inspectService;
    private ObjectMapper objectMapper;
    private JdbcTemplate jdbc;
    private DatasetController controller;

    @BeforeEach
    void setUp() {
        datasetService = mock(DatasetService.class);
        datasetRepo = mock(DatasetRepository.class);
        uploadLogRepo = mock(DatasetUploadLogRepository.class);
        dynamicTable = mock(DynamicTableService.class);
        parse = mock(ExcelParseService.class);
        ingest = mock(ExcelIngestService.class);
        inspectService = mock(ExcelInspectService.class);
        objectMapper = new ObjectMapper();
        jdbc = mock(JdbcTemplate.class);
        controller = new DatasetController(
                datasetService, datasetRepo, uploadLogRepo, dynamicTable,
                parse, ingest, inspectService, objectMapper, jdbc);
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
    @DisplayName("POST /api/analytics/datasets stamps workspaceId and uploader from headers")
    void createFromFile_stampsWorkspaceAndUploader() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1});
        String fieldsJson = """
                [{"fieldName":"Amount","fieldType":"DECIMAL","ordinal":0}]
                """;

        doAnswer(invocation -> {
            Dataset ds = invocation.getArgument(0);
            ds.setId(10L);
            ds.setPhysicalTable("dataset_10");
            return ds;
        }).when(datasetService).createWithFields(any(Dataset.class), anyList());
        doAnswer(invocation -> {
            DatasetUploadLog log = invocation.getArgument(0);
            log.setId(20L);
            return 1;
        }).when(uploadLogRepo).insert((DatasetUploadLog) any());
        when(parse.parse(any(InputStream.class), anyList(), isNull())).thenReturn(List.of());
        when(ingest.ingest(any(Dataset.class), anyList(), anyList(), eq(20L)))
                .thenReturn(new IngestResult(0, 0, List.of()));

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(file, "My Dataset", fieldsJson, null, 5L, 7L);

        verify(datasetService).createWithFields(argThat(ds ->
                        ds.getWorkspaceId().equals(5L)
                        && ds.getName().equals("My Dataset")),
                argThat(fields -> fields.size() == 1
                        && fields.get(0).getFieldName().equals("Amount")));
        verify(uploadLogRepo).insert((DatasetUploadLog) argThat((DatasetUploadLog log) ->
                log.getDatasetId().equals(10L)
                        && log.getUploader().equals(7L)));
        assertThat(response.getBody().getData().dataset().getId()).isEqualTo(10L);
        assertThat(response.getBody().getData().uploadLog().getStatus()).isEqualTo("SUCCESS");
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
    @DisplayName("preview refuses a physical table name outside the dataset_ namespace")
    void preview_rejectsNonDatasetPhysicalTableName() {
        Dataset ds = new Dataset();
        ds.setId(1L);
        ds.setPhysicalTable("mate_user");

        when(datasetService.getById(1L)).thenReturn(ds);

        ResponseEntity<?> response = controller.preview(1L, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("GET /api/analytics/datasets/{id}/preview returns 200 with rows")
    void preview_returnsRowsFromPhysicalTable() {
        Dataset ds = new Dataset();
        ds.setId(1L);
        ds.setPhysicalTable("dataset_1");

        when(datasetService.getById(1L)).thenReturn(ds);

        List<Map<String, Object>> fakeRows = List.of(
                Map.of("id", 1, "upload_log_id", 10L, "animal_id", "A001"),
                Map.of("id", 2, "upload_log_id", 10L, "animal_id", "A002")
        );
        when(jdbc.queryForList(
                eq("SELECT * FROM dataset_1 LIMIT ?"),
                eq(100)
        )).thenReturn(fakeRows);

        ResponseEntity<?> response = controller.preview(1L, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        R<List<Map<String, Object>>> body = (R<List<Map<String, Object>>>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getData()).hasSize(2);
        verify(jdbc).queryForList(
                "SELECT * FROM dataset_1 LIMIT ?", 100);
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
        ds.setPhysicalTable(null);

        when(datasetService.getById(5L)).thenReturn(ds);

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
