package vip.mate.analytics.tool.guard;

import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL safety gate for LLM-generated queries.
 *
 * <p>Call {@link #safen(String)} before executing any LLM-supplied SQL. The method
 * either returns the (possibly rewritten) SQL that is safe to run, or throws
 * {@link SqlGuardException} describing why the SQL was rejected.
 *
 * <p>This is a stateless utility class — no Spring bean, no instantiation.
 */
public final class SqlGuard {

    private static final long MAX_LIMIT = 10_000L;

    /** Tables in the meta-catalog that analysts are explicitly permitted to read. */
    private static final Set<String> WHITELIST = Set.of(
        "mate_dataset",
        "mate_dataset_template",
        "mate_dataset_template_field",
        "mate_dataset_upload_log"
    );

    /** Forbidden schema/database prefixes — checked against the full (lowercased) name. */
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
        "information_schema",
        "mysql.",
        "sys.",
        "performance_schema"
    );

    /** Matches any DML/DDL write keyword as a whole word (case-insensitive). */
    private static final Pattern WRITE_KEYWORD = Pattern.compile(
        "\\b(insert|update|delete|drop|alter|create|grant|truncate|replace|merge)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private SqlGuard() {
        // utility class
    }

    /**
     * Validates and, if necessary, rewrites {@code sql} so it is safe to execute.
     *
     * @param sql raw SQL string from the LLM
     * @return the safe SQL (may have LIMIT appended or capped)
     * @throws SqlGuardException if the SQL violates any safety rule
     */
    public static String safen(String sql) {
        // Rule 1: null / blank
        if (sql == null || sql.isBlank()) {
            throw new SqlGuardException("空 SQL");
        }

        // Rule 2: multiple statements — semicolon followed by non-whitespace
        if (sql.contains(";") && sql.replaceFirst(";\\s*$", "").contains(";")) {
            throw new SqlGuardException("不允许多条语句");
        }
        // Also catch "SELECT ... ; DROP ..." where text follows the semicolon
        String trimmedAfterSemi = sql.substring(sql.indexOf(';') + 1).trim();
        if (sql.contains(";") && !trimmedAfterSemi.isEmpty()) {
            throw new SqlGuardException("不允许多条语句");
        }

        // Rule 3: write keyword regex
        if (WRITE_KEYWORD.matcher(sql).find()) {
            throw new SqlGuardException("不允许写操作关键字");
        }

        // Rule 4: JSQLParser parse
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            throw new SqlGuardException("SQL 解析失败: " + e.getMessage());
        }

        // Rule 5: must be SELECT
        if (!(statement instanceof Select select)) {
            throw new SqlGuardException("只允许 SELECT 语句");
        }

        // Rule 6: table allowlist
        List<String> tables = new TablesNamesFinder().getTableList((net.sf.jsqlparser.statement.Statement) select);
        for (String rawName : tables) {
            validateTable(rawName);
        }

        // Rule 7: LIMIT enforcement — operate on PlainSelect only
        if (select.getSelectBody() instanceof PlainSelect plainSelect) {
            Limit limit = plainSelect.getLimit();
            if (limit == null) {
                // No LIMIT — append LIMIT 10000
                Limit newLimit = new Limit();
                newLimit.setRowCount(new LongValue(MAX_LIMIT));
                plainSelect.setLimit(newLimit);
            } else if (limit.getRowCount() instanceof LongValue lv && lv.getValue() > MAX_LIMIT) {
                // LIMIT too large — cap to 10000
                lv.setValue(MAX_LIMIT);
            }
        }

        return select.toString();
    }

    /**
     * Validates a single table name returned by {@link TablesNamesFinder}.
     *
     * @param rawName table name as returned by JSQLParser (may include schema prefix / quotes)
     * @throws SqlGuardException if the table is not permitted
     */
    private static void validateTable(String rawName) {
        // Normalize: lowercase, strip backticks/double-quotes/single-quotes
        String normalized = rawName.toLowerCase().replaceAll("[`\"']", "");

        // Check forbidden prefixes against the full normalized name (handles "information_schema.tables")
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                throw new SqlGuardException("禁止访问系统表: " + rawName);
            }
        }

        // Strip schema prefix to get bare table name for allowlist/pattern check
        String bare = normalized.contains(".")
            ? normalized.substring(normalized.lastIndexOf('.') + 1)
            : normalized;

        if (bare.startsWith("dataset_") || WHITELIST.contains(bare)) {
            return; // allowed
        }

        throw new SqlGuardException("禁止访问未授权的表: " + rawName);
    }
}
