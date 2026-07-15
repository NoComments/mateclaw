package vip.mate.analytics.controller;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import vip.mate.analytics.dataset.DatasetUploadLog;
import vip.mate.common.result.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DatasetUploadController}.
 *
 * <p>Uses an isolated in-memory H2 database so the test can run alongside other
 * tests without file-lock conflicts. The physical dataset table is created via
 * {@link vip.mate.analytics.storage.DynamicTableService} during the upload and
 * dropped in teardown.
 */
@SpringBootTest(
        classes = vip.mate.MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics_upload_ctrl_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "spring.ai.dashscope.api-key=test-key"
})
class DatasetUploadControllerTest {

    @Autowired
    DatasetUploadController controller;

    @Autowired
    JdbcTemplate jdbc;

    /** Stable IDs used across setup / test / teardown. */
    private static final long DATASET_ID  = 88002L;

    /** One physical table per dataset, named from the dataset id. */
    private static final String PHYSICAL_TABLE = "dataset_" + DATASET_ID;

    @BeforeEach
    void setup() {
        // Clean up any leftover state from a failed previous run
        jdbc.execute("DELETE FROM mate_dataset_upload_log WHERE dataset_id = " + DATASET_ID);
        jdbc.execute("DELETE FROM mate_dataset_field WHERE dataset_id = " + DATASET_ID);
        jdbc.execute("DELETE FROM mate_dataset WHERE id = " + DATASET_ID);
        jdbc.execute("DROP TABLE IF EXISTS " + PHYSICAL_TABLE);

        // Seed the dataset that owns both the schema and physical table.
        jdbc.execute(
                "INSERT INTO mate_dataset"
                + " (id, workspace_id, name, physical_table, row_count, deleted, create_time, update_time)"
                + " VALUES (" + DATASET_ID + ", 1, '上传控制器测试数据集', '"
                + PHYSICAL_TABLE + "', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // Seed dataset field: 期末存栏 → end_stock (DECIMAL, ordinal=0)
        jdbc.execute(
                "INSERT INTO mate_dataset_field"
                + " (id, dataset_id, field_code, field_name, field_type, excel_header,"
                + "  ordinal, is_nullable, deleted, create_time, update_time)"
                + " VALUES (88010, " + DATASET_ID + ", 'end_stock', '期末存栏', 'DECIMAL', '期末存栏',"
                + "  0, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void teardown() {
        jdbc.execute("DELETE FROM mate_dataset_upload_log WHERE dataset_id = " + DATASET_ID);
        jdbc.execute("DELETE FROM mate_dataset_field WHERE dataset_id = " + DATASET_ID);
        jdbc.execute("DELETE FROM mate_dataset WHERE id = " + DATASET_ID);
        jdbc.execute("DROP TABLE IF EXISTS " + PHYSICAL_TABLE);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/upload returns SUCCESS log with rowsInserted > 0 for valid xlsx")
    void upload_validFile_returnsSuccessLog() throws IOException {
        MockMultipartFile file = buildXlsx();

        ResponseEntity<R<DatasetUploadLog>> response =
                controller.upload(DATASET_ID, file, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        R<DatasetUploadLog> body = response.getBody();
        assertThat(body).isNotNull();

        DatasetUploadLog log = body.getData();
        assertThat(log).isNotNull();
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getRowsInserted()).isGreaterThan(0);
        assertThat(log.getRowsRejected()).isEqualTo(0);
        assertThat(log.getFileName()).isEqualTo("test.xlsx");

        // Verify the physical table actually received rows
        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + PHYSICAL_TABLE, Integer.class);
        assertThat(rowCount).isGreaterThan(0);

        Integer datasetIdColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = 'DATASET_ID'",
                Integer.class, PHYSICAL_TABLE);
        assertThat(datasetIdColumns).isZero();
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/upload returns 404 for unknown dataset id")
    void upload_unknownDataset_returns404() throws IOException {
        MockMultipartFile file = buildXlsx();

        ResponseEntity<R<DatasetUploadLog>> response =
                controller.upload(999_999L, file, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /{id}/upload throws for non-xlsx file")
    void upload_nonXlsxFile_throwsIllegalArgument() {
        MockMultipartFile csv = new MockMultipartFile(
                "file", "data.csv", "text/csv", "a,b\n1,2".getBytes());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> controller.upload(DATASET_ID, csv, null, null));
    }

    @Test
    @DisplayName("POST /{id}/upload throws for oversized file")
    void upload_oversizedFile_throwsIllegalArgument() {
        // Create a byte array > 50 MB
        byte[] bigData = new byte[51 * 1024 * 1024];
        MockMultipartFile big = new MockMultipartFile(
                "file", "big.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bigData);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> controller.upload(DATASET_ID, big, null, null));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal in-memory {@code .xlsx} with one header row (期末存栏)
     * and two data rows.
     */
    private MockMultipartFile buildXlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("期末存栏");  // matches field's excel_header

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(1000.0);

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(2500.5);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            return new MockMultipartFile(
                    "file",
                    "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
