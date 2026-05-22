package vip.mate.analytics.upload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ExcelIngestService} using an in-memory H2.
 *
 * <p>The datasource URL is overridden via {@code @TestPropertySource} so the test
 * can run concurrently with a running server that holds the file-based H2 lock.
 *
 * <p>Each test creates a throwaway physical table, runs the ingest, then tears
 * it down. The seed {@link Dataset} row (id = 9001) is inserted and cleaned up
 * around each test so the row-count assertions start from a known baseline.
 */
@SpringBootTest(
        classes = vip.mate.MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ingest_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "spring.ai.dashscope.api-key=test-key"
})
class ExcelIngestServiceTest {

    @Autowired
    ExcelIngestService service;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DatasetRepository datasetRepo;

    private static final String TABLE = "dataset_ingest_test";

    @BeforeEach
    void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + TABLE
                + " (id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "  dataset_id BIGINT,"
                + "  upload_log_id BIGINT,"
                + "  farm_code VARCHAR(64),"
                + "  end_stock DECIMAL(20,4))");
        jdbc.execute("DELETE FROM " + TABLE);

        // Idempotent seed — delete first so re-runs after a test failure don't collide.
        jdbc.execute("DELETE FROM mate_dataset WHERE id = 9001");
        jdbc.execute(
                "INSERT INTO mate_dataset (id, workspace_id, template_id, name, row_count, deleted, create_time, update_time)"
                        + " VALUES (9001, 1, 1, 'test', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void teardown() {
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE);
        jdbc.execute("DELETE FROM mate_dataset WHERE id = 9001");
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void ingest_insertsAllRowsAndUpdatesRowCount() {
        DatasetTemplate tpl = new DatasetTemplate();
        tpl.setPhysicalTable(TABLE);

        Dataset ds = new Dataset();
        ds.setId(9001L);
        ds.setRowCount(0);

        List<DatasetTemplateField> fields = List.of(
                field("farm_code", "STRING"),
                field("end_stock", "DECIMAL"));

        List<ParsedRow> rows = List.of(
                new ParsedRow(1, Map.of("farm_code", "4101001", "end_stock", new BigDecimal("325600"))),
                new ParsedRow(2, Map.of("farm_code", "4101002", "end_stock", new BigDecimal("0"))));

        IngestResult result = service.ingest(ds, tpl, fields, rows, 500L);

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.rejected()).isEqualTo(0);
        assertThat(result.errors()).isEmpty();

        int count = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        assertThat(count).isEqualTo(2);

        Integer rowCount = jdbc.queryForObject(
                "SELECT row_count FROM mate_dataset WHERE id = 9001", Integer.class);
        assertThat(rowCount).isEqualTo(2);
    }

    @Test
    void ingest_emptyRows_returnsZero() {
        DatasetTemplate tpl = new DatasetTemplate();
        tpl.setPhysicalTable(TABLE);

        Dataset ds = new Dataset();
        ds.setId(9001L);
        ds.setRowCount(0);

        IngestResult result = service.ingest(ds, tpl, List.of(), List.of(), 500L);

        assertThat(result.inserted()).isEqualTo(0);
        assertThat(result.rejected()).isEqualTo(0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Minimal {@link DatasetTemplateField} with just the properties exercised by
     * {@link ExcelIngestService} (fieldCode + fieldType).
     */
    private DatasetTemplateField field(String code, String type) {
        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode(code);
        f.setFieldType(type);
        return f;
    }
}
