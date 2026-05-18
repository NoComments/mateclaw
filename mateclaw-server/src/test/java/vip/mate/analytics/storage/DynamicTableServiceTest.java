package vip.mate.analytics.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vip.mate.MateClawApplication;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.DatasetTemplateRepository;

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
    DatasetTemplateRepository templateRepo;

    // ---------------------------------------------------------------------- helpers

    private static DatasetTemplateField field(String code, String type, int ordinal) {
        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode(code);
        f.setFieldType(type);
        f.setOrdinal(ordinal);
        f.setFieldName(code);
        f.setExcelHeader(code);
        return f;
    }

    private DatasetTemplate insertTemplate(String code, String physicalTable) {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode(code);
        t.setName("Test " + code);
        t.setWorkspaceId(1L);
        t.setPartitionKeys("[]");
        t.setPhysicalTable(physicalTable);
        t.setCategory("CUSTOM");
        t.setEnabled(true);
        templateRepo.insert(t);
        return t;
    }

    // ---------------------------------------------------------------------- tests

    /** ensureTable should CREATE the physical table when it does not yet exist. */
    @Test
    void ensureTable_createsPhysicalTable() {
        DatasetTemplate t = insertTemplate("test_dyn1", "dataset_test_dyn1");

        List<DatasetTemplateField> fields = List.of(
            field("farm_code", "STRING", 0),
            field("end_stock", "DECIMAL", 1)
        );

        try {
            service.ensureTable(t, fields);

            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'DATASET_TEST_DYN1'",
                Integer.class);
            assertThat(count).isEqualTo(1);

            // appliedDdlHash should be persisted
            DatasetTemplate reloaded = templateRepo.selectById(t.getId());
            assertThat(reloaded.getAppliedDdlHash()).isNotBlank();
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS dataset_test_dyn1");
            templateRepo.deleteById(t.getId());
        }
    }

    /** Second call with same fields must be a no-op — no errors, hash still set. */
    @Test
    void ensureTable_skipsDdlWhenHashUnchanged() {
        DatasetTemplate t = insertTemplate("test_dyn2", "dataset_test_dyn2");

        List<DatasetTemplateField> fields = List.of(
            field("region_code", "STRING", 0),
            field("total_area", "DECIMAL", 1)
        );

        try {
            service.ensureTable(t, fields);
            String hashAfterFirst = t.getAppliedDdlHash();
            assertThat(hashAfterFirst).isNotBlank();

            // Second call — must not throw, table must still exist
            service.ensureTable(t, fields);

            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'DATASET_TEST_DYN2'",
                Integer.class);
            assertThat(count).isEqualTo(1);
            assertThat(t.getAppliedDdlHash()).isEqualTo(hashAfterFirst);
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS dataset_test_dyn2");
            templateRepo.deleteById(t.getId());
        }
    }

    /** When a new field is appended the missing column must be added via ALTER TABLE. */
    @Test
    void ensureTable_addsColumnWhenFieldAppended() {
        DatasetTemplate t = insertTemplate("test_dyn3", "dataset_test_dyn3");

        List<DatasetTemplateField> initialFields = List.of(
            field("crop_code", "STRING", 0)
        );

        try {
            service.ensureTable(t, initialFields);

            // Append a new field
            List<DatasetTemplateField> expandedFields = List.of(
                field("crop_code", "STRING", 0),
                field("yield_kg", "DECIMAL", 1)
            );
            service.ensureTable(t, expandedFields);

            // New column must now exist in INFORMATION_SCHEMA
            Integer colCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE UPPER(TABLE_NAME) = 'DATASET_TEST_DYN3' AND UPPER(COLUMN_NAME) = 'YIELD_KG'",
                Integer.class);
            assertThat(colCount).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS dataset_test_dyn3");
            templateRepo.deleteById(t.getId());
        }
    }

    /** An unsafe physical table name must be rejected before any DB access. */
    @Test
    void ensureTable_rejectsUnsafeTableName() {
        DatasetTemplate t = new DatasetTemplate();
        t.setPhysicalTable("'; DROP TABLE users; --");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.ensureTable(t, List.of()));
    }
}
