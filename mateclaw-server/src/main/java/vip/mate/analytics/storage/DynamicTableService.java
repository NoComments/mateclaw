package vip.mate.analytics.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetField;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.FieldType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of physical {@code dataset_*} tables in the database.
 *
 * <p>DDL is only re-applied when the SHA-256 hash of the field list has changed or the
 * table does not yet exist, making repeated calls safe for concurrent boot scenarios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicTableService {

    /** Allowed physical table and column name pattern: lowercase, starts with a letter. */
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final JdbcTemplate jdbc;
    private final DatasetRepository datasetRepo;

    /**
     * Ensures the physical table for {@code ds} matches the given {@code fields}.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Validate {@code physicalTable} name against {@link #SAFE_NAME}.</li>
     *   <li>Compute SHA-256 hash of the sorted field list.</li>
     *   <li>If the hash is unchanged AND the table already exists → no-op.</li>
     *   <li>If the table does not exist → {@code CREATE TABLE}.</li>
     *   <li>If the table exists but the hash differs → {@code ALTER TABLE ADD COLUMN} for
     *       each missing column.</li>
     *   <li>Persist the updated hash on the dataset row.</li>
     * </ol>
     *
     * @param ds       the dataset whose physical table to synchronise
     * @param fields   the current field definitions (ordinal-ordered)
     * @throws IllegalArgumentException if the physical table name is unsafe
     */
    public void ensureTable(Dataset ds, List<DatasetField> fields) {
        String physicalTable = ds.getPhysicalTable();
        validateName(physicalTable, "physicalTable");

        String newHash = hash(fields);

        boolean exists = tableExists(physicalTable);

        if (newHash.equals(ds.getAppliedDdlHash()) && exists) {
            log.debug("DynamicTableService: table {} is up-to-date (hash match), skipping DDL", physicalTable);
            return;
        }

        if (!exists) {
            log.info("DynamicTableService: creating table {}", physicalTable);
            jdbc.execute(buildCreate(physicalTable, fields));
        } else {
            // Table exists but fields have changed — add missing columns only
            Set<String> existing = existingColumns(physicalTable);
            for (DatasetField f : fields) {
                String col = f.getFieldCode();
                validateName(col, "fieldCode");
                if (!existing.contains(col.toLowerCase())) {
                    FieldType ft = FieldType.fromString(f.getFieldType());
                    // Intentionally no NOT NULL here (unlike buildCreate): the table may
                    // already hold rows, and adding a NOT NULL column to a non-empty table
                    // without a DEFAULT fails on both H2 and MySQL.
                    String sql = "ALTER TABLE " + physicalTable + " ADD COLUMN "
                            + col + " " + PhysicalColumnType.sqlType(ft);
                    log.info("DynamicTableService: ALTER TABLE {} ADD COLUMN {} {}", physicalTable, col, PhysicalColumnType.sqlType(ft));
                    jdbc.execute(sql);
                }
            }
        }

        ds.setAppliedDdlHash(newHash);
        datasetRepo.updateById(ds);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the full {@code CREATE TABLE} DDL for the given physical table and fields.
     */
    private String buildCreate(String physicalTable, List<DatasetField> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(physicalTable).append(" (\n");
        sb.append("  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n");
        sb.append("  upload_log_id BIGINT,\n");
        for (DatasetField f : fields) {
            validateName(f.getFieldCode(), "fieldCode");
            FieldType ft = FieldType.fromString(f.getFieldType());
            String nullable = Boolean.FALSE.equals(f.getIsNullable()) ? " NOT NULL" : "";
            sb.append("  ").append(f.getFieldCode()).append(" ")
              .append(PhysicalColumnType.sqlType(ft)).append(nullable).append(",\n");
        }
        sb.setLength(sb.length() - 2);
        sb.append("\n)");
        return sb.toString();
    }

    /**
     * Returns the lower-cased column names currently present in the physical table.
     */
    private Set<String> existingColumns(String table) {
        return new HashSet<>(jdbc.queryForList(
            "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = UPPER(?)",
            String.class, table));
    }

    /**
     * Returns {@code true} if the given table exists in the current schema.
     */
    private boolean tableExists(String table) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)",
            Integer.class, table);
        return c != null && c > 0;
    }

    /**
     * Computes a SHA-256 digest of the sorted field list so schema drift can be detected.
     *
     * <p>Input is {@code fieldCode|fieldType\n} per field, sorted by ordinal ascending.
     */
    private String hash(List<DatasetField> fields) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            fields.stream()
                .sorted(Comparator.comparingInt(f -> f.getOrdinal() == null ? 0 : f.getOrdinal()))
                .forEach(f -> md.update(
                    (f.getFieldCode() + "|" + f.getFieldType() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Validates that {@code name} is safe to use in DDL (lower-case alphanumeric + underscore).
     *
     * @throws IllegalArgumentException if the name does not match {@link #SAFE_NAME}
     */
    public static void validateName(String name, String label) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Unsafe " + label + " '" + name + "': must match ^[a-z][a-z0-9_]{0,62}$");
        }
    }
}
