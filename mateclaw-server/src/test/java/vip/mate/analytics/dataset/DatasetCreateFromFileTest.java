package vip.mate.analytics.dataset;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vip.mate.analytics.controller.DatasetController;
import vip.mate.common.result.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** Builds a 2-column, 2-row .xlsx in memory. */
    private MockMultipartFile xlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("省份");
            header.createCell(1).setCellValue("金额");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("广东");
            r1.createCell(1).setCellValue(100.5);
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("江苏");
            r2.createCell(1).setCellValue(200.25);
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
    @DisplayName("rejects a non-xlsx file before creating anything")
    void rejectsNonXlsx() {
        MockMultipartFile bad = new MockMultipartFile(
                "file", "data.txt", "text/plain", "省份,金额".getBytes());

        long before = datasetRepo.selectCount(null);

        assertThatThrownBy(() -> controller.createFromFile(
                bad, "坏文件", "[]", null, 9L, 42L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(datasetRepo.selectCount(null)).isEqualTo(before);
    }
}
