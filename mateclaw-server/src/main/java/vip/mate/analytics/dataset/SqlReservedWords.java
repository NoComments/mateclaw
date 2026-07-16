package vip.mate.analytics.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves words that cannot safely be used as unquoted SQL identifiers. */
@Component
@RequiredArgsConstructor
@Slf4j
class SqlReservedWords {

    /**
     * JDBC requires {@code DatabaseMetaData.getSQLKeywords()} to exclude SQL:2003
     * keywords, so this standard baseline must always accompany the driver's list.
     */
    private static final Set<String> SQL_2003_RESERVED_WORDS = words("""
            ABS ALL ALLOCATE ALTER AND ANY ARE ARRAY AS ASC ASENSITIVE ASYMMETRIC AT ATOMIC
            AUTHORIZATION AVG BEGIN BETWEEN BIGINT BINARY BLOB BOOLEAN BOTH BY CALL CALLED
            CARDINALITY CASCADED CASE CAST CEIL CEILING CHAR CHARACTER CHARACTER_LENGTH
            CHAR_LENGTH CHECK CLOB CLOSE COALESCE COLLATE COLLECT COLUMN COMMIT CONDITION
            CONNECT CONSTRAINT CONVERT CORR CORRESPONDING COUNT COVAR_POP COVAR_SAMP CREATE
            CROSS CUBE CUME_DIST CURRENT CURRENT_DATE CURRENT_DEFAULT_TRANSFORM_GROUP
            CURRENT_PATH CURRENT_ROLE CURRENT_TIME CURRENT_TIMESTAMP
            CURRENT_TRANSFORM_GROUP_FOR_TYPE CURRENT_USER CURSOR CYCLE DATE DAY DEALLOCATE DEC
            DECIMAL DECLARE DEFAULT DELETE DENSE_RANK DEREF DESC DESCRIBE DETERMINISTIC DISCONNECT
            DISTINCT DOUBLE DROP DYNAMIC EACH ELEMENT ELSE END END-EXEC ESCAPE EVERY EXCEPT EXEC
            EXECUTE EXISTS EXP EXTERNAL EXTRACT FALSE FETCH FILTER FLOAT FLOOR FOR FOREIGN FREE
            FROM FULL FUNCTION FUSION GET GLOBAL GRANT GROUP GROUPING HAVING HOLD HOUR IDENTITY
            IN INDEX INDICATOR INNER INOUT INSENSITIVE INSERT INT INTEGER INTERSECT INTERSECTION
            INTERVAL INTO IS JOIN KEY LANGUAGE LARGE LATERAL LEADING LEFT LIKE LN LOCAL LOCALTIME
            LOCALTIMESTAMP LOWER MATCH MAX MEMBER MERGE METHOD MIN MINUTE MOD MODIFIES MODULE
            MONTH MULTISET NATIONAL NATURAL NCHAR NCLOB NEW NO NONE NORMALIZE NOT NULL NULLIF
            NUMERIC OCTET_LENGTH OF OLD ON ONLY OPEN OR ORDER OUT OUTER OVER OVERLAPS OVERLAY
            PARAMETER PARTITION PERCENTILE_CONT PERCENTILE_DISC PERCENT_RANK POSITION POWER
            PRECISION PREPARE PRIMARY PROCEDURE RANGE RANK READS REAL RECURSIVE REF REFERENCES
            REFERENCING REGR_AVGX REGR_AVGY REGR_COUNT REGR_INTERCEPT REGR_R2 REGR_SLOPE
            REGR_SXX REGR_SXY REGR_SYY RELEASE RESULT RETURN RETURNS REVOKE RIGHT ROLLBACK ROLLUP
            ROW ROWS ROW_NUMBER SAVEPOINT SCOPE SCROLL SEARCH SECOND SELECT SENSITIVE SESSION_USER
            SET SIMILAR SMALLINT SOME SPECIFIC SPECIFICTYPE SQL SQLEXCEPTION SQLSTATE SQLWARNING
            SQRT START STATIC STDDEV_POP STDDEV_SAMP SUBMULTISET SUBSTRING SUM SYMMETRIC SYSTEM
            SYSTEM_USER TABLE TABLESAMPLE THEN TIME TIMESTAMP TIMEZONE_HOUR TIMEZONE_MINUTE TO
            TRAILING TRANSLATE TRANSLATION TREAT TRIGGER TRIM TRUE UESCAPE UNION UNIQUE UNKNOWN
            UNNEST UPDATE UPPER USER USING VALUE VALUES VARCHAR VARYING VAR_POP VAR_SAMP WHEN
            WHENEVER WHERE WIDTH_BUCKET WINDOW WITH WITHIN WITHOUT YEAR
            """);

    private final DataSource dataSource;

    private volatile Set<String> cached;

    Set<String> get() {
        Set<String> result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = resolve();
                    cached = result;
                }
            }
        }
        return result;
    }

    boolean isReserved(String word) {
        return get().contains(word.toLowerCase(Locale.ROOT));
    }

    private Set<String> resolve() {
        Set<String> resolved = new HashSet<>(SQL_2003_RESERVED_WORDS);
        try (Connection connection = dataSource.getConnection()) {
            String driverKeywords = connection.getMetaData().getSQLKeywords();
            if (driverKeywords != null && !driverKeywords.isBlank()) {
                Arrays.stream(driverKeywords.split(","))
                        .map(String::trim)
                        .filter(keyword -> !keyword.isEmpty())
                        .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                        .forEach(resolved::add);
            }
        } catch (Exception e) {
            // Keyword discovery must never make an upload fail; the standard list is safe fallback.
            log.warn("Could not read database-specific SQL keywords; using the standard reserved-word baseline: {}",
                    e.toString());
        }
        return Set.copyOf(resolved);
    }

    private static Set<String> words(String value) {
        return Arrays.stream(value.split("\\s+"))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
