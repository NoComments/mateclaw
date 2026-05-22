package vip.mate.analytics.tool.guard;

import vip.mate.common.sql.SafeSqlRewriter;
import vip.mate.common.sql.SqlRewriteException;

import java.util.List;
import java.util.Set;

/**
 * SQL safety gate for LLM-generated queries against local dataset tables.
 *
 * <p>Delegates to {@link SafeSqlRewriter} with analytics-specific rules:
 * table allowlist ({@code dataset_*} + metadata tables), LIMIT 10 000,
 * and system schema blocking.
 *
 * <p>This is a stateless utility class — no Spring bean, no instantiation.
 */
public final class SqlGuard {

    private static final SafeSqlRewriter REWRITER = SafeSqlRewriter.builder()
            .maxLimit(10_000)
            .tableAllowlist(Set.of(
                    "mate_dataset",
                    "mate_dataset_template",
                    "mate_dataset_template_field",
                    "mate_dataset_upload_log"
            ))
            .tableAllowPattern("dataset_")
            .forbiddenPrefixes(List.of(
                    "information_schema",
                    "mysql.",
                    "sys.",
                    "performance_schema"
            ))
            .build();

    private SqlGuard() {
    }

    /**
     * Validates and rewrites {@code sql} so it is safe to execute
     * against the local application database.
     *
     * @param sql raw SQL string from the LLM
     * @return the safe SQL (may have LIMIT appended or capped)
     * @throws SqlGuardException if the SQL violates any safety rule
     */
    public static String safen(String sql) {
        try {
            return REWRITER.rewrite(sql);
        } catch (SqlRewriteException e) {
            throw new SqlGuardException(e.getMessage());
        }
    }
}
