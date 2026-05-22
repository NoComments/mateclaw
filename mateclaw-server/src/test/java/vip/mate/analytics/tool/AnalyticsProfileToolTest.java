package vip.mate.analytics.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vip.mate.MateClawApplication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AnalyticsProfileTool}.
 *
 * <p>Uses an isolated in-memory H2 database. Template, field, and dataset rows
 * are inserted with explicit snowflake-style IDs (8001/8002/8003) to avoid
 * collisions with seed data loaded by {@code DatabaseBootstrapRunner}.
 */
@SpringBootTest(
        classes = MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics_profile_tool_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.ai.dashscope.api-key=test-key",
        "spring.main.web-application-type=none"
})
class AnalyticsProfileToolTest {

    @Autowired
    private AnalyticsProfileTool tool;

    @Autowired
    private JdbcTemplate jdbc;

    // IDs chosen to be far from seed-data ranges
    private static final long TEMPLATE_ID = 8001L;
    private static final long DATASET_ID  = 8002L;

    @BeforeEach
    void setup() {
        // Physical table used by the test template
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS dataset_profile_test " +
            "(id BIGINT PRIMARY KEY AUTO_INCREMENT, dataset_id BIGINT, upload_log_id BIGINT, " +
            "farm_code VARCHAR(64), end_stock DECIMAL(20,4))");
        jdbc.execute("DELETE FROM dataset_profile_test");
        jdbc.update("INSERT INTO dataset_profile_test VALUES (1, ?, 1, 'A', 1000)", DATASET_ID);
        jdbc.update("INSERT INTO dataset_profile_test VALUES (2, ?, 1, 'B', 2000)", DATASET_ID);
        jdbc.update("INSERT INTO dataset_profile_test VALUES (3, ?, 1, 'A', NULL)", DATASET_ID);

        // Template row
        jdbc.update(
            "INSERT INTO mate_dataset_template " +
            "(id, workspace_id, code, name, description, category, partition_keys, physical_table, " +
            " applied_ddl_hash, enabled, creator, updater, create_time, update_time, deleted) " +
            "VALUES (?, 1, 'test_tpl', 'Test Template', NULL, 'CUSTOM', '[]', " +
            "'dataset_profile_test', NULL, 1, 0, 0, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE physical_table = 'dataset_profile_test'",
            TEMPLATE_ID);

        // Field: end_stock (DECIMAL / numeric)
        jdbc.update(
            "INSERT INTO mate_dataset_template_field " +
            "(id, template_id, field_code, field_name, field_type, field_unit, semantic, " +
            " is_partition_key, is_nullable, ordinal, excel_header, create_time, update_time, deleted) " +
            "VALUES (?, ?, 'end_stock', '期末存栏', 'DECIMAL', '只', NULL, 0, 1, 0, '期末存栏', NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE field_type = 'DECIMAL'",
            80011L, TEMPLATE_ID);

        // Field: farm_code (STRING)
        jdbc.update(
            "INSERT INTO mate_dataset_template_field " +
            "(id, template_id, field_code, field_name, field_type, field_unit, semantic, " +
            " is_partition_key, is_nullable, ordinal, excel_header, create_time, update_time, deleted) " +
            "VALUES (?, ?, 'farm_code', '农场编码', 'STRING', NULL, NULL, 0, 0, 1, '农场编码', NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE field_type = 'STRING'",
            80012L, TEMPLATE_ID);

        // Dataset row
        jdbc.update(
            "INSERT INTO mate_dataset " +
            "(id, workspace_id, template_id, name, description, row_count, last_upload_at, " +
            " creator, updater, create_time, update_time, deleted) " +
            "VALUES (?, 1, ?, 'Profile Test Dataset', NULL, 3, NOW(), 0, 0, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE template_id = ?",
            DATASET_ID, TEMPLATE_ID, TEMPLATE_ID);
    }

    @AfterEach
    void teardown() {
        jdbc.execute("DROP TABLE IF EXISTS dataset_profile_test");
        jdbc.update("DELETE FROM mate_dataset WHERE id = ?", DATASET_ID);
        jdbc.update("DELETE FROM mate_dataset_template_field WHERE template_id = ?", TEMPLATE_ID);
        jdbc.update("DELETE FROM mate_dataset_template WHERE id = ?", TEMPLATE_ID);
    }

    @Test
    @DisplayName("应该返回数值字段统计信息当字段类型为 DECIMAL 时")
    void profile_numericField_returnsStats() {
        Map<String, Object> result = tool.analyticsProfile(DATASET_ID, List.of("end_stock"));

        assertThat(result).containsKey("end_stock");

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.get("end_stock");
        assertThat(stats).doesNotContainKey("error");
        assertThat(((Number) stats.get("totalCount")).longValue()).isEqualTo(3L);
        assertThat(((Number) stats.get("nonNullCount")).longValue()).isEqualTo(2L);
        assertThat(((Number) stats.get("nullPercent")).doubleValue())
                .isEqualTo(100.0 / 3, org.assertj.core.api.Assertions.within(0.01));
        assertThat(((Number) stats.get("distinctCount")).longValue()).isEqualTo(2L);
        assertThat(stats).containsKey("min");
        assertThat(stats).containsKey("max");
        assertThat(stats).containsKey("mean");
    }

    @Test
    @DisplayName("应该返回 top5 值频次当字段类型为 STRING 时")
    void profile_stringField_returnsTop5() {
        Map<String, Object> result = tool.analyticsProfile(DATASET_ID, List.of("farm_code"));

        assertThat(result).containsKey("farm_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.get("farm_code");
        assertThat(stats).doesNotContainKey("error");
        assertThat(((Number) stats.get("totalCount")).longValue()).isEqualTo(3L);
        assertThat(((Number) stats.get("nonNullCount")).longValue()).isEqualTo(3L);
        assertThat(((Number) stats.get("distinctCount")).longValue()).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top5 = (List<Map<String, Object>>) stats.get("top5");
        assertThat(top5).isNotEmpty();
        // 'A' appears twice — should be first
        assertThat(top5.get(0).get("value")).isEqualTo("A");
        assertThat(((Number) top5.get(0).get("freq")).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("应该返回 error 键当 fieldCode 不存在时")
    void profile_unknownField_returnsError() {
        Map<String, Object> result = tool.analyticsProfile(DATASET_ID, List.of("nonexistent_field"));

        assertThat(result).containsKey("nonexistent_field");

        @SuppressWarnings("unchecked")
        Map<String, Object> fieldResult = (Map<String, Object>) result.get("nonexistent_field");
        assertThat(fieldResult).containsKey("error");
        assertThat(fieldResult.get("error").toString()).contains("unknown field");
    }

    @Test
    @DisplayName("应该返回 error 当 datasetId 不存在时")
    void profile_unknownDataset_returnsTopLevelError() {
        Map<String, Object> result = tool.analyticsProfile(99999L, List.of("end_stock"));

        assertThat(result).containsKey("error");
        assertThat(result.get("error").toString()).contains("99999");
    }
}
