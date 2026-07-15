package vip.mate.analytics.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vip.mate.MateClawApplication;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetField;
import vip.mate.analytics.dataset.DatasetRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Integration tests for {@link DynamicTableService} — runs against an H2 in-memory DB.
 */
@SpringBootTest(
        classes = MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dynamic_table_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.ai.dashscope.api-key=test-key",
        "spring.main.web-application-type=none"
})
class DynamicTableServiceTest {

    @Autowired
    DynamicTableService service;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DatasetRepository datasetRepo;

    // ---------------------------------------------------------------------- helpers

    private static DatasetField field(String code, String type, int ordinal) {
        DatasetField f = new DatasetField();
        f.setFieldCode(code);
        f.setFieldType(type);
        f.setOrdinal(ordinal);
        f.setFieldName(code);
        f.setExcelHeader(code);
        return f;
    }

    private Dataset insertDataset(String name) {
        Dataset ds = new Dataset();
        ds.setName(name);
        ds.setWorkspaceId(1L);
        ds.setPhysicalTable("dataset_placeholder");
        ds.setRowCount(0);
        datasetRepo.insert(ds);
        ds.setPhysicalTable("dataset_" + ds.getId());
        datasetRepo.updateById(ds);
        return ds;
    }

    // ---------------------------------------------------------------------- tests

    /** ensureTable should CREATE the physical table when it does not yet exist. */
    @Test
    void ensureTable_createsPhysicalTable() {
        Dataset ds = insertDataset("test_dyn1");

        List<DatasetField> fields = List.of(
            field("farm_code", "STRING", 0),
            field("end_stock", "DECIMAL", 1)
        );

        try {
            service.ensureTable(ds, fields);

            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)",
                Integer.class, ds.getPhysicalTable());
            assertThat(count).isEqualTo(1);

            Integer datasetIdColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = 'DATASET_ID'",
                Integer.class, ds.getPhysicalTable());
            assertThat(datasetIdColumns).isZero();

            Integer uploadLogColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = 'UPLOAD_LOG_ID'",
                Integer.class, ds.getPhysicalTable());
            assertThat(uploadLogColumns).isEqualTo(1);

            // appliedDdlHash should be persisted
            Dataset reloaded = datasetRepo.selectById(ds.getId());
            assertThat(reloaded.getAppliedDdlHash()).isNotBlank();
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS " + ds.getPhysicalTable());
            datasetRepo.deleteById(ds.getId());
        }
    }

    /** Second call with same fields must be a no-op — no errors, hash still set. */
    @Test
    void ensureTable_skipsDdlWhenHashUnchanged() {
        Dataset ds = insertDataset("test_dyn2");

        List<DatasetField> fields = List.of(
            field("region_code", "STRING", 0),
            field("total_area", "DECIMAL", 1)
        );

        try {
            service.ensureTable(ds, fields);
            String hashAfterFirst = ds.getAppliedDdlHash();
            assertThat(hashAfterFirst).isNotBlank();

            // Second call — must not throw, table must still exist
            service.ensureTable(ds, fields);

            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)",
                Integer.class, ds.getPhysicalTable());
            assertThat(count).isEqualTo(1);
            assertThat(ds.getAppliedDdlHash()).isEqualTo(hashAfterFirst);
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS " + ds.getPhysicalTable());
            datasetRepo.deleteById(ds.getId());
        }
    }

    /** When a new field is appended the missing column must be added via ALTER TABLE. */
    @Test
    void ensureTable_addsColumnWhenFieldAppended() {
        Dataset ds = insertDataset("test_dyn3");

        List<DatasetField> initialFields = List.of(
            field("crop_code", "STRING", 0)
        );

        try {
            service.ensureTable(ds, initialFields);

            // Append a new field
            List<DatasetField> expandedFields = List.of(
                field("crop_code", "STRING", 0),
                field("yield_kg", "DECIMAL", 1)
            );
            service.ensureTable(ds, expandedFields);

            // New column must now exist in INFORMATION_SCHEMA
            Integer colCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = 'YIELD_KG'",
                Integer.class, ds.getPhysicalTable());
            assertThat(colCount).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS " + ds.getPhysicalTable());
            datasetRepo.deleteById(ds.getId());
        }
    }

    /** An unsafe physical table name must be rejected before any DB access. */
    @Test
    void ensureTable_rejectsUnsafeTableName() {
        Dataset ds = new Dataset();
        ds.setPhysicalTable("'; DROP TABLE users; --");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.ensureTable(ds, List.of()));
    }
}
