package vip.mate.analytics.dataset;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vip.mate.analytics.controller.DatasetController;
import vip.mate.analytics.storage.DynamicTableService;
import vip.mate.common.result.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

/**
 * Integration test for the one-step upload: a single multipart call must create
 * the dataset, its schema, its physical table, and ingest the rows.
 */
@SpringBootTest(
        classes = vip.mate.MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dataset_create_from_file_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "spring.ai.dashscope.api-key=test-key"
})
class DatasetCreateFromFileTest {

    @Autowired
    DatasetController controller;

    @Autowired
    DatasetRepository datasetRepo;

    @Autowired
    DatasetFieldRepository fieldRepo;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SqlReservedWords sqlReservedWords;

    @MockitoSpyBean
    DynamicTableService dynamicTable;

    @Test
    @DisplayName("resolved reserved words include the SQL:2003 baseline")
    void resolvedReservedWordsIncludeSql2003Baseline() {
        assertThat(sqlReservedWords.get()).contains(
                "order", "group", "key", "index", "primary", "check",
                "left", "value", "row", "desc");
    }

    /** Builds a 2-column, 2-row .xlsx in memory. */
    private MockMultipartFile xlsx() throws IOException {
        return xlsx(
                new String[]{"省份", "金额"},
                new Object[][]{{"广东", 100.5}, {"江苏", 200.25}});
    }

    /** Builds a real .xlsx workbook with the supplied headers and rows. */
    private MockMultipartFile xlsx(String[] headers, Object[][] rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value instanceof Boolean bool) {
                        row.createCell(c).setCellValue(bool);
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile(
                    "file", "销售.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    @DisplayName("one multipart call creates the dataset, its table, and ingests the rows")
    void createFromFileIngestsRows() throws IOException {
        String fieldsJson = """
                [{"fieldName":"省份","fieldType":"STRING","ordinal":0},
                 {"fieldName":"金额","fieldType":"DECIMAL","ordinal":1}]
                """;

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> resp =
                controller.createFromFile(xlsx(), "季度销售", fieldsJson, null, 9L, 42L);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        DatasetController.CreateDatasetResponse body = resp.getBody().getData();

        // Dataset row created, physical table named after its id
        Long dsId = body.dataset().getId();
        Dataset saved = datasetRepo.selectById(dsId);
        assertThat(saved.getPhysicalTable()).isEqualTo("dataset_" + dsId);
        assertThat(saved.getWorkspaceId()).isEqualTo(9L);

        // Schema persisted, owned by the dataset
        List<DatasetField> fields = fieldRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetField>()
                        .eq("dataset_id", dsId).orderByAsc("ordinal"));
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).getExcelHeader()).isEqualTo("省份");

        // Rows actually landed in the physical table
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM " + saved.getPhysicalTable());
        assertThat(rows).hasSize(2);

        // Upload log reflects the ingest
        assertThat(body.uploadLog().getRowsInserted()).isEqualTo(2);
        assertThat(body.uploadLog().getStatus()).isEqualTo("SUCCESS");

        // The physical table must NOT carry a dataset_id column any more
        assertThat(rows.get(0)).doesNotContainKey("dataset_id");

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("reserved-word headers are mangled and upload end to end")
    void reservedWordHeaderUploadsAndRemainsQueryable() throws IOException {
        String fieldsJson = """
                [{"fieldName":"Order","fieldType":"STRING","ordinal":0},
                 {"fieldName":"Amount","fieldType":"DECIMAL","ordinal":1}]
                """;

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(
                        xlsx(new String[]{"Order", "Amount"}, new Object[][]{{"A-100", 25.5}}),
                        "Orders", fieldsJson, null, 9L, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DatasetController.CreateDatasetResponse body = response.getBody().getData();
        Dataset saved = datasetRepo.selectById(body.dataset().getId());
        List<DatasetField> fields = fieldRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetField>()
                        .eq("dataset_id", saved.getId()).orderByAsc("ordinal"));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_col, amount FROM " + saved.getPhysicalTable());

        assertAll(
                () -> assertThat(body.uploadLog().getStatus()).isEqualTo("SUCCESS"),
                () -> assertThat(body.uploadLog().getRowsInserted()).isEqualTo(1),
                () -> assertThat(fields).extracting(DatasetField::getFieldCode)
                        .containsExactly("order_col", "amount"),
                () -> assertThat(rows).hasSize(1),
                () -> assertThat(rows.get(0).get("order_col")).isEqualTo("A-100"),
                () -> assertThat(rows.get(0).get("amount").toString()).isEqualTo("25.5000"));

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("driver and standard reserved-word headers upload end to end")
    void driverAndStandardReservedWordHeadersUploadAndRemainQueryable() throws IOException {
        String fieldsJson = """
                [{"fieldName":"Rank","fieldType":"DECIMAL","ordinal":0},
                 {"fieldName":"Desc","fieldType":"STRING","ordinal":1}]
                """;

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(
                        xlsx(new String[]{"Rank", "Desc"}, new Object[][]{{1, "Highest"}}),
                        "Rankings", fieldsJson, null, 9L, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DatasetController.CreateDatasetResponse body = response.getBody().getData();
        Dataset saved = datasetRepo.selectById(body.dataset().getId());
        List<DatasetField> fields = fieldRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetField>()
                        .eq("dataset_id", saved.getId()).orderByAsc("ordinal"));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT rank_col, desc_col FROM " + saved.getPhysicalTable());

        assertAll(
                () -> assertThat(body.uploadLog().getStatus()).isEqualTo("SUCCESS"),
                () -> assertThat(body.uploadLog().getRowsInserted()).isEqualTo(1),
                () -> assertThat(fields).extracting(DatasetField::getFieldCode)
                        .containsExactly("rank_col", "desc_col"),
                () -> assertThat(rows).hasSize(1),
                () -> assertThat(rows.get(0).get("rank_col").toString()).isEqualTo("1.0000"),
                () -> assertThat(rows.get(0).get("desc_col")).isEqualTo("Highest"));

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("an ensureTable failure removes the just-created dataset and fields")
    void ensureTableFailureLeavesNoActiveMetadata() throws IOException {
        long datasetsBefore = datasetRepo.selectCount(null);
        long fieldsBefore = fieldRepo.selectCount(null);
        doThrow(new IllegalStateException("forced ensureTable failure"))
                .when(dynamicTable).ensureTable(any(Dataset.class), anyList());

        Throwable failure = catchThrowable(() -> controller.createFromFile(
                xlsx(new String[]{"Amount"}, new Object[][]{{25.5}}),
                "DDL failure", "[{\"fieldName\":\"Amount\",\"fieldType\":\"DECIMAL\",\"ordinal\":0}]",
                null, 9L, 42L));

        assertAll(
                () -> assertThat(failure).isInstanceOf(IllegalStateException.class)
                        .hasMessage("forced ensureTable failure"),
                () -> assertThat(datasetRepo.selectCount(null)).isEqualTo(datasetsBefore),
                () -> assertThat(fieldRepo.selectCount(null)).isEqualTo(fieldsBefore));
    }

    @Test
    @DisplayName("headers differing only inside parentheses keep values in their exact columns")
    void parentheticalHeadersIngestIntoTheirExactColumns() throws IOException {
        String fieldsJson = """
                [{"fieldName":"金额(元)","fieldType":"DECIMAL","ordinal":0},
                 {"fieldName":"金额(万元)","fieldType":"DECIMAL","ordinal":1}]
                """;

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(
                        xlsx(new String[]{"金额(元)", "金额(万元)"}, new Object[][]{{100.0, 0.01}}),
                        "金额单位", fieldsJson, null, 9L, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DatasetController.CreateDatasetResponse body = response.getBody().getData();
        Dataset saved = datasetRepo.selectById(body.dataset().getId());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT field, field_2 FROM " + saved.getPhysicalTable());

        assertAll(
                () -> assertThat(body.uploadLog().getStatus()).isEqualTo("SUCCESS"),
                () -> assertThat(rows).hasSize(1),
                () -> assertThat(rows.get(0).get("field").toString()).isEqualTo("100.0000"),
                () -> assertThat(rows.get(0).get("field_2").toString()).isEqualTo("0.0100"));

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("digit-leading header is prefixed with f_ and upload succeeds end to end")
    void digitLeadingHeaderIsPrefixedAndUploadsSuccessfully() throws IOException {
        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(
                        xlsx(new String[]{"2024"}, new Object[][]{{100}}),
                        "年度数据",
                        "[{\"fieldName\":\"2024\",\"fieldType\":\"DECIMAL\",\"ordinal\":0}]",
                        null, 9L, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DatasetController.CreateDatasetResponse body = response.getBody().getData();
        Dataset saved = datasetRepo.selectById(body.dataset().getId());

        List<DatasetField> fields = fieldRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetField>()
                        .eq("dataset_id", saved.getId()));
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getFieldCode()).isEqualTo("f_2024");
        assertThat(body.uploadLog().getStatus()).isEqualTo("SUCCESS");

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("a non-numeric cell in an INT column skips only that row, upload is PARTIAL")
    void unparseableCellIsolatesRowInsteadOfFailingWholeUpload() throws IOException {
        // Column 数量 is INT; the second data row holds "无" (real-world "no data").
        // Only that row must be rejected — the clean row still lands.
        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(
                        xlsx(new String[]{"数量"}, new Object[][]{{100}, {"无"}}),
                        "存栏数据",
                        "[{\"fieldName\":\"数量\",\"fieldType\":\"INT\",\"ordinal\":0}]",
                        null, 9L, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DatasetController.CreateDatasetResponse body = response.getBody().getData();
        Dataset saved = datasetRepo.selectById(body.dataset().getId());

        assertAll(
                () -> assertThat(body.uploadLog().getStatus()).isEqualTo("PARTIAL"),
                () -> assertThat(body.uploadLog().getRowsInserted()).isEqualTo(1),
                () -> assertThat(body.uploadLog().getRowsRejected()).isEqualTo(1),
                () -> assertThat(body.uploadLog().getErrorSummary()).contains("数量", "无"),
                () -> assertThat(jdbc.queryForList(
                        "SELECT * FROM " + saved.getPhysicalTable())).hasSize(1));

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("unmatched requested headers return 400 before creating a dataset")
    void unmatchedHeadersReturn400WithoutDataset() throws IOException {
        long datasetsBefore = datasetRepo.selectCount(null);
        long tablesBefore = dynamicTableCount();
        String fieldsJson = """
                [{"fieldName":"Province","fieldType":"STRING","ordinal":0},
                 {"fieldName":"Amount","fieldType":"DECIMAL","ordinal":1}]
                """;

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(xlsx(), "错位字段", fieldsJson, null, 9L, 42L);

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().getMsg()).contains("Province", "Amount"),
                () -> assertThat(datasetRepo.selectCount(null)).isEqualTo(datasetsBefore),
                () -> assertThat(dynamicTableCount()).isEqualTo(tablesBefore));
    }

    @Test
    @DisplayName("all rows unparseable: dataset is created with a FAILED log naming the bad cell")
    void allRowsUnparseableCreateFailedLogNotA400() throws IOException {
        // Every data row has a bad cell, so 0 rows ingest. This is the degenerate case
        // of per-row isolation and matches the existing empty-sheet outcome: the dataset
        // and its table are created (schema is valid) and the log is FAILED — not a 400.
        ResponseEntity<R<DatasetController.CreateDatasetResponse>> response =
                controller.createFromFile(
                        xlsx(new String[]{"金额"}, new Object[][]{{"not-a-decimal"}}),
                        "错误数值",
                        "[{\"fieldName\":\"金额\",\"fieldType\":\"DECIMAL\",\"ordinal\":0}]",
                        null, 9L, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DatasetController.CreateDatasetResponse body = response.getBody().getData();
        Dataset saved = datasetRepo.selectById(body.dataset().getId());

        assertAll(
                () -> assertThat(body.uploadLog().getStatus()).isEqualTo("FAILED"),
                () -> assertThat(body.uploadLog().getRowsInserted()).isEqualTo(0),
                () -> assertThat(body.uploadLog().getRowsRejected()).isEqualTo(1),
                () -> assertThat(body.uploadLog().getErrorSummary()).contains("金额", "not-a-decimal"));

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("non-xlsx file returns 400 with the validation message")
    void rejectsNonXlsxWithBadRequest() {
        MockMultipartFile bad = new MockMultipartFile(
                "file", "data.txt", "text/plain", "省份,金额".getBytes());
        long datasetsBefore = datasetRepo.selectCount(null);
        AtomicReference<ResponseEntity<R<DatasetController.CreateDatasetResponse>>> response =
                new AtomicReference<>();

        Throwable failure = catchThrowable(() -> response.set(controller.createFromFile(
                bad, "坏文件", "[]", null, 9L, 42L)));

        assertAll(
                () -> assertThat(failure).isNull(),
                () -> assertThat(response.get()).isNotNull(),
                () -> assertThat(response.get().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.get().getBody().getMsg())
                        .isEqualTo("Only .xlsx files are accepted; received: data.txt"),
                () -> assertThat(datasetRepo.selectCount(null)).isEqualTo(datasetsBefore));
    }

    private long dynamicTableCount() {
        return jdbc.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES", String.class)
                .stream()
                .filter(name -> name.toLowerCase().startsWith("dataset_"))
                .count();
    }
}
