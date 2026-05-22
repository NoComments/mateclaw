package vip.mate.common.sql;

/**
 * Thrown by {@link SafeSqlRewriter#rewrite(String)} when the supplied SQL
 * fails any safety rule.
 */
public class SqlRewriteException extends RuntimeException {

    public SqlRewriteException(String message) {
        super(message);
    }
}
