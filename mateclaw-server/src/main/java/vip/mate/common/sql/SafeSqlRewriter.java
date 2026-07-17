package vip.mate.common.sql;

import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unified SQL safety rewriter for LLM-generated queries.
 *
 * <p>Validates that the SQL is a single, read-only SELECT statement,
 * optionally checks table names against an allowlist, and enforces
 * a configurable LIMIT ceiling.
 *
 * <p>Use the {@link #builder()} to configure behavior per scenario:
 * <ul>
 *   <li>Analytics (local datasets): {@code tableAllowlist} + {@code tableAllowPattern} set</li>
 *   <li>Datasource (external DBs): no table restrictions</li>
 * </ul>
 */
public final class SafeSqlRewriter {

    private final long maxLimit;
    private final Set<String> tableAllowlist;
    private final String tableAllowPattern;
    private final List<String> forbiddenPrefixes;

    /** Matches DML/DDL write keywords as a whole word (case-insensitive). */
    private static final Pattern WRITE_KEYWORD = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|grant|truncate|replace|merge)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private SafeSqlRewriter(Builder builder) {
        this.maxLimit = builder.maxLimit;
        this.tableAllowlist = builder.tableAllowlist;
        this.tableAllowPattern = builder.tableAllowPattern;
        this.forbiddenPrefixes = builder.forbiddenPrefixes;
    }

    /**
     * Validates and rewrites the SQL so it is safe to execute.
     *
     * @param sql raw SQL from the LLM
     * @return the safe SQL (may have LIMIT appended or capped)
     * @throws SqlRewriteException if the SQL violates any safety rule
     */
    public String rewrite(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlRewriteException("SQL 不能为空");
        }

        // Strip trailing semicolon
        String cleaned = sql.strip();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }

        // Write keyword regex — catches even if JSQLParser would miss malformed DDL
        if (WRITE_KEYWORD.matcher(cleaned).find()) {
            throw new SqlRewriteException("不允许写操作关键字");
        }

        // Parse and reject multi-statement
        Statement statement;
        try {
            Statements stmts = CCJSqlParserUtil.parseStatements(cleaned);
            if (stmts.getStatements().size() != 1) {
                throw new SqlRewriteException("仅允许单条 SQL 语句，检测到 " + stmts.getStatements().size() + " 条");
            }
            statement = stmts.getStatements().get(0);
        } catch (SqlRewriteException e) {
            throw e;
        } catch (Exception e) {
            throw new SqlRewriteException("SQL 解析失败: " + e.getMessage());
        }

        // Must be SELECT
        if (!(statement instanceof Select select)) {
            throw new SqlRewriteException("只允许 SELECT 语句，检测到: " + statement.getClass().getSimpleName());
        }

        // Table allowlist (only when configured)
        if (tableAllowlist != null || tableAllowPattern != null || forbiddenPrefixes != null) {
            List<String> tables = new TablesNamesFinder().getTableList(statement);
            for (String rawName : tables) {
                validateTable(rawName);
            }
        }

        // LIMIT enforcement — PlainSelect and SetOperationList
        if (select.getSelectBody() instanceof PlainSelect ps) {
            enforceLimitOnPlain(ps);
        } else if (select.getSelectBody() instanceof SetOperationList sol) {
            enforceLimitOnSetOp(sol);
        }

        return select.toString();
    }

    private void validateTable(String rawName) {
        String normalized = rawName.toLowerCase().replaceAll("[`\"']", "");

        // Forbidden prefixes (system schemas)
        if (forbiddenPrefixes != null) {
            for (String prefix : forbiddenPrefixes) {
                if (normalized.startsWith(prefix)) {
                    throw new SqlRewriteException("禁止访问系统表: " + rawName);
                }
            }
        }

        // If no allowlist/pattern configured, all tables are allowed
        if (tableAllowlist == null && tableAllowPattern == null) {
            return;
        }

        String bare = normalized.contains(".")
                ? normalized.substring(normalized.lastIndexOf('.') + 1)
                : normalized;

        if (tableAllowlist != null && tableAllowlist.contains(bare)) {
            return;
        }
        if (tableAllowPattern != null && bare.startsWith(tableAllowPattern)) {
            return;
        }

        throw new SqlRewriteException("禁止访问未授权的表: " + rawName);
    }

    private void enforceLimitOnPlain(PlainSelect ps) {
        Limit limit = ps.getLimit();
        if (limit == null) {
            Limit newLimit = new Limit();
            newLimit.setRowCount(new LongValue(maxLimit));
            ps.setLimit(newLimit);
        } else if (limit.getRowCount() instanceof LongValue lv && lv.getValue() > maxLimit) {
            lv.setValue(maxLimit);
        }
    }

    private void enforceLimitOnSetOp(SetOperationList sol) {
        Limit limit = sol.getLimit();
        if (limit == null) {
            Limit newLimit = new Limit();
            newLimit.setRowCount(new LongValue(maxLimit));
            sol.setLimit(newLimit);
        } else if (limit.getRowCount() instanceof LongValue lv && lv.getValue() > maxLimit) {
            lv.setValue(maxLimit);
        }
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private long maxLimit = 500;
        private Set<String> tableAllowlist;
        private String tableAllowPattern;
        private List<String> forbiddenPrefixes;

        private Builder() {}

        /** Maximum row limit. SQL with LIMIT exceeding this value gets capped. Default: 500. */
        public Builder maxLimit(long maxLimit) {
            this.maxLimit = maxLimit;
            return this;
        }

        /** Exact table names that are always allowed (lowercase, no schema prefix). */
        public Builder tableAllowlist(Set<String> allowlist) {
            this.tableAllowlist = allowlist;
            return this;
        }

        /** Table name prefix pattern that is allowed (e.g. "dataset_"). */
        public Builder tableAllowPattern(String pattern) {
            this.tableAllowPattern = pattern;
            return this;
        }

        /** Forbidden schema/table prefixes (e.g. "information_schema", "mysql."). */
        public Builder forbiddenPrefixes(List<String> prefixes) {
            this.forbiddenPrefixes = prefixes;
            return this;
        }

        public SafeSqlRewriter build() {
            return new SafeSqlRewriter(this);
        }
    }
}
