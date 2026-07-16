package vip.mate.analytics.tool.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlGuardTest {

    // rejectInsert — INSERT SQL → SqlGuardException
    @Test
    void rejectInsert() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("INSERT INTO dataset_x (col) VALUES (1)"));
    }

    // rejectUpdate
    @Test
    void rejectUpdate() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("UPDATE dataset_x SET col = 1 WHERE id = 1"));
    }

    // rejectDrop
    @Test
    void rejectDrop() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("DROP TABLE dataset_x"));
    }

    // rejectInfoSchema — INFORMATION_SCHEMA table → SqlGuardException
    @Test
    void rejectInfoSchema() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("SELECT * FROM information_schema.tables"));
    }

    // rejectSystemTable — mysql.user → SqlGuardException
    @Test
    void rejectSystemTable() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("SELECT * FROM mysql.user"));
    }

    // rejectNonDatasetTable — SELECT * FROM mate_agent → SqlGuardException
    @Test
    void rejectNonDatasetTable() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("SELECT * FROM mate_agent"));
    }

    // rejectMultipleStatements
    @Test
    void rejectMultipleStatements() {
        assertThrows(SqlGuardException.class, () ->
            SqlGuard.safen("SELECT 1 FROM dataset_x; DROP TABLE dataset_x"));
    }

    // acceptDatasetTable — SELECT * FROM dataset_x → valid
    @Test
    void acceptDatasetTable() {
        assertDoesNotThrow(() -> SqlGuard.safen("SELECT * FROM dataset_x LIMIT 100"));
    }

    // acceptWhitelistedMetaTable — SELECT id FROM mate_dataset → valid
    @Test
    void acceptWhitelistedMetaTable() {
        assertDoesNotThrow(() -> SqlGuard.safen("SELECT id FROM mate_dataset LIMIT 100"));
    }

    // acceptDatasetFieldTable — SELECT field_code, semantic FROM mate_dataset_field → valid
    @Test
    void acceptDatasetFieldTable() {
        assertDoesNotThrow(() ->
            SqlGuard.safen("SELECT field_code, semantic FROM mate_dataset_field WHERE dataset_id = 8002"));
    }

    // appendsLimitWhenAbsent — "SELECT * FROM dataset_x" → returned SQL contains "LIMIT 10000"
    @Test
    void appendsLimitWhenAbsent() {
        String result = SqlGuard.safen("SELECT * FROM dataset_x");
        assertTrue(result.toUpperCase().contains("LIMIT 10000"),
            "Expected LIMIT 10000 in: " + result);
    }

    // capsLimitTo10000 — "SELECT * FROM dataset_x LIMIT 50000" → returned SQL has LIMIT 10000
    @Test
    void capsLimitTo10000() {
        String result = SqlGuard.safen("SELECT * FROM dataset_x LIMIT 50000");
        assertTrue(result.toUpperCase().contains("LIMIT 10000"),
            "Expected LIMIT 10000 in: " + result);
        assertFalse(result.contains("50000"), "Should not contain original limit 50000");
    }

    // acceptJoinBetweenDatasetTables
    @Test
    void acceptJoinBetweenDatasetTables() {
        assertDoesNotThrow(() ->
            SqlGuard.safen("SELECT a.col FROM dataset_a a JOIN dataset_b b ON a.id = b.id LIMIT 100"));
    }

    // rejectBlankSql
    @Test
    void rejectBlankSql() {
        assertThrows(SqlGuardException.class, () -> SqlGuard.safen(""));
        assertThrows(SqlGuardException.class, () -> SqlGuard.safen("   "));
        assertThrows(SqlGuardException.class, () -> SqlGuard.safen(null));
    }
}
