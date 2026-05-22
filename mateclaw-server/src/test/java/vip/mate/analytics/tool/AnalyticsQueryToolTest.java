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
import vip.mate.analytics.tool.guard.SqlGuardException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AnalyticsQueryTool}.
 * Uses an isolated in-memory H2 database so no external infrastructure is needed.
 */
@SpringBootTest(
        classes = MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics_query_tool_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.ai.dashscope.api-key=test-key",
        "spring.main.web-application-type=none"
})
class AnalyticsQueryToolTest {

    @Autowired
    private AnalyticsQueryTool tool;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS dataset_query_test (id BIGINT PRIMARY KEY, dataset_id BIGINT, name VARCHAR(64))");
        jdbc.execute("DELETE FROM dataset_query_test");
        jdbc.execute("INSERT INTO dataset_query_test VALUES (1, 100, 'row1')");
        jdbc.execute("INSERT INTO dataset_query_test VALUES (2, 100, 'row2')");
    }

    @AfterEach
    void teardown() {
        jdbc.execute("DROP TABLE IF EXISTS dataset_query_test");
    }

    @Test
    @DisplayName("应该返回匹配的行数和数据当查询合法 dataset_* 表时")
    void query_returnsRows() {
        Map<String, Object> result = tool.analyticsQuery("SELECT * FROM dataset_query_test WHERE dataset_id = 100");

        assertThat((int) result.get("rowCount")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(2);
        assertThat(result.get("executedSql")).isNotNull();
    }

    @Test
    @DisplayName("应该包含 executedSql 键当查询成功时")
    void query_includesExecutedSql() {
        Map<String, Object> result = tool.analyticsQuery("SELECT id FROM dataset_query_test");

        assertThat(result).containsKey("executedSql");
        assertThat(result).containsKey("rowCount");
        assertThat(result).containsKey("rows");
    }

    @Test
    @DisplayName("应该抛出 SqlGuardException 当 SQL 包含 INSERT 时")
    void query_rejectsSqlWithInsert() {
        assertThatThrownBy(() -> tool.analyticsQuery("INSERT INTO dataset_query_test VALUES (3, 100, 'x')"))
                .isInstanceOf(SqlGuardException.class);
    }

    @Test
    @DisplayName("应该抛出 SqlGuardException 当查询非 dataset_* 表时")
    void query_rejectsNonDatasetTable() {
        assertThatThrownBy(() -> tool.analyticsQuery("SELECT * FROM mate_agent"))
                .isInstanceOf(SqlGuardException.class);
    }

    @Test
    @DisplayName("应该自动追加 LIMIT 当 SQL 未包含 LIMIT 时")
    void query_appendsLimitWhenAbsent() {
        Map<String, Object> result = tool.analyticsQuery("SELECT * FROM dataset_query_test");

        String executedSql = (String) result.get("executedSql");
        assertThat(executedSql.toUpperCase()).contains("LIMIT");
    }

    @Test
    @DisplayName("应该抛出 SqlGuardException 当 SQL 为空时")
    void query_rejectsBlankSql() {
        assertThatThrownBy(() -> tool.analyticsQuery("  "))
                .isInstanceOf(SqlGuardException.class);
    }
}
