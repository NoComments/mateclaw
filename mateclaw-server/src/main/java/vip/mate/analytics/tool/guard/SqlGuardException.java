package vip.mate.analytics.tool.guard;

/**
 * Thrown by {@link SqlGuard#safen(String)} when the supplied SQL fails any safety rule.
 */
public class SqlGuardException extends RuntimeException {

    public SqlGuardException(String message) {
        super(message);
    }
}
