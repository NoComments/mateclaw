package vip.mate.datasource.service;

import org.springframework.stereotype.Service;
import vip.mate.common.sql.SafeSqlRewriter;
import vip.mate.common.sql.SqlRewriteException;
import vip.mate.exception.MateClawException;

/**
 * SQL safety validation for queries against external datasources.
 *
 * <p>Delegates to {@link SafeSqlRewriter} with datasource-specific rules:
 * no table allowlist (external DBs are fully user-owned), LIMIT 500.
 *
 * @author MateClaw Team
 */
@Service
public class SqlValidationService {

    private static final SafeSqlRewriter REWRITER = SafeSqlRewriter.builder()
            .maxLimit(500)
            .build();

    /**
     * Validates and normalizes SQL for execution against an external datasource.
     *
     * @param sql raw SQL
     * @return the safe SQL (may have LIMIT appended or capped)
     */
    public String validateAndNormalize(String sql) {
        try {
            return REWRITER.rewrite(sql);
        } catch (SqlRewriteException e) {
            throw new MateClawException("err.datasource.sql_invalid", e.getMessage());
        }
    }
}
