package vip.mate.analytics.storage;

import vip.mate.analytics.dataset.FieldType;

/**
 * Maps logical {@link FieldType} values to physical SQL column type declarations.
 *
 * <p>Used by {@link DynamicTableService} when generating {@code CREATE TABLE} and
 * {@code ALTER TABLE … ADD COLUMN} DDL statements for {@code dataset_*} tables.
 */
public final class PhysicalColumnType {

    private PhysicalColumnType() {
    }

    /**
     * Returns the SQL type string for the given {@link FieldType}.
     *
     * @param t the logical field type; must not be {@code null}
     * @return a SQL type declaration suitable for H2 and MySQL
     */
    public static String sqlType(FieldType t) {
        return switch (t) {
            case STRING  -> "VARCHAR(512)";
            case INT     -> "BIGINT";
            case DECIMAL -> "DECIMAL(20,4)";
            case BOOLEAN -> "TINYINT";
            case DATE    -> "DATE";
        };
    }
}
