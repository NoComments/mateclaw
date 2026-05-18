package vip.mate.analytics.template;

/**
 * Supported column types for dataset template fields.
 */
public enum FieldType {
    STRING, INT, DECIMAL, BOOLEAN, DATE;

    /**
     * Case-insensitive lookup.
     *
     * @throws IllegalArgumentException if {@code s} does not match any member
     */
    public static FieldType fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown FieldType: " + s);
        }
    }
}
