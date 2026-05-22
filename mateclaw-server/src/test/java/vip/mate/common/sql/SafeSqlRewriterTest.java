package vip.mate.common.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SafeSqlRewriter} covering both
 * datasource (no allowlist) and analytics (with allowlist) scenarios.
 */
class SafeSqlRewriterTest {

    /** Datasource-style: no table restrictions, LIMIT 500. */
    private static final SafeSqlRewriter DS_REWRITER = SafeSqlRewriter.builder()
            .maxLimit(500)
            .build();

    /** Analytics-style: table allowlist + pattern, LIMIT 10 000. */
    private static final SafeSqlRewriter ANALYTICS_REWRITER = SafeSqlRewriter.builder()
            .maxLimit(10_000)
            .tableAllowlist(Set.of("mate_dataset", "mate_dataset_template"))
            .tableAllowPattern("dataset_")
            .forbiddenPrefixes(List.of("information_schema", "mysql.", "sys."))
            .build();

    // ── Datasource scenario (no allowlist) ────────────────────────────

    @Nested
    @DisplayName("Datasource rewriter (no allowlist)")
    class DatasourceTests {

        @Test
        void selectPassesThrough() {
            String sql = "SELECT id, name FROM orders WHERE status = 'active'";
            String result = DS_REWRITER.rewrite(sql);
            assertTrue(result.toLowerCase().contains("select"));
        }

        @Test
        void limitAppendedWhenMissing() {
            String result = DS_REWRITER.rewrite("SELECT * FROM users");
            assertTrue(result.toUpperCase().contains("LIMIT"), "should append LIMIT");
        }

        @Test
        void limitCappedWhenExceeded() {
            String result = DS_REWRITER.rewrite("SELECT * FROM users LIMIT 9999");
            // The rewriter should cap to 500
            assertFalse(result.contains("9999"), "should not keep original excessive LIMIT");
        }

        @Test
        void limitPreservedWhenWithinCap() {
            String result = DS_REWRITER.rewrite("SELECT * FROM users LIMIT 100");
            assertTrue(result.contains("100"));
        }

        @Test
        void anyTableAllowed() {
            // Without allowlist, any table name is fine
            assertDoesNotThrow(() -> DS_REWRITER.rewrite("SELECT 1 FROM some_random_table"));
        }

        @Test
        void writeStatementsRejected() {
            assertThrows(SqlRewriteException.class, () -> DS_REWRITER.rewrite("DELETE FROM users"));
            assertThrows(SqlRewriteException.class, () -> DS_REWRITER.rewrite("UPDATE users SET name='x'"));
            assertThrows(SqlRewriteException.class, () -> DS_REWRITER.rewrite("INSERT INTO users VALUES(1)"));
            assertThrows(SqlRewriteException.class, () -> DS_REWRITER.rewrite("DROP TABLE users"));
        }

        @Test
        void multipleStatementsRejected() {
            assertThrows(SqlRewriteException.class,
                    () -> DS_REWRITER.rewrite("SELECT 1; SELECT 2"));
        }

        @Test
        void unionQueryGetsLimit() {
            String result = DS_REWRITER.rewrite(
                    "SELECT id FROM a UNION ALL SELECT id FROM b");
            assertTrue(result.toUpperCase().contains("LIMIT"));
        }
    }

    // ── Analytics scenario (with allowlist) ───────────────────────────

    @Nested
    @DisplayName("Analytics rewriter (with allowlist)")
    class AnalyticsTests {

        @Test
        void allowlistedTablePasses() {
            assertDoesNotThrow(() ->
                    ANALYTICS_REWRITER.rewrite("SELECT * FROM mate_dataset WHERE id = 1"));
        }

        @Test
        void patternMatchedTablePasses() {
            assertDoesNotThrow(() ->
                    ANALYTICS_REWRITER.rewrite("SELECT col FROM dataset_123 WHERE dataset_id = 1"));
        }

        @Test
        void unknownTableRejected() {
            assertThrows(SqlRewriteException.class, () ->
                    ANALYTICS_REWRITER.rewrite("SELECT * FROM mate_user"));
        }

        @Test
        void forbiddenPrefixRejected() {
            assertThrows(SqlRewriteException.class, () ->
                    ANALYTICS_REWRITER.rewrite("SELECT * FROM information_schema.tables"));
        }

        @Test
        void systemSchemaRejected() {
            assertThrows(SqlRewriteException.class, () ->
                    ANALYTICS_REWRITER.rewrite("SELECT * FROM mysql.user"));
        }

        @Test
        void limitCappedAt10000() {
            String result = ANALYTICS_REWRITER.rewrite(
                    "SELECT * FROM mate_dataset LIMIT 99999");
            assertFalse(result.contains("99999"));
        }

        @Test
        void joinWithAllowedTables() {
            assertDoesNotThrow(() -> ANALYTICS_REWRITER.rewrite(
                    "SELECT a.id FROM mate_dataset a JOIN dataset_456 b ON a.id = b.dataset_id"));
        }

        @Test
        void joinWithDisallowedTableRejected() {
            assertThrows(SqlRewriteException.class, () -> ANALYTICS_REWRITER.rewrite(
                    "SELECT a.id FROM mate_dataset a JOIN mate_user b ON a.owner = b.id"));
        }
    }
}
