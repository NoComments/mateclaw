package vip.mate.analytics.dataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetReservedWordsTest {

    @Mock
    DataSource dataSource;

    @Mock
    Connection connection;

    @Mock
    DatabaseMetaData metadata;

    @Test
    void combinesStandardAndDriverKeywordsAndCachesTheResult() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSQLKeywords()).thenReturn("RANK, Vendor_Word, DESC");
        SqlReservedWords reservedWords = new SqlReservedWords(dataSource);

        Set<String> first = reservedWords.get();
        Set<String> second = reservedWords.get();

        assertThat(first).contains("order", "rank", "vendor_word", "desc");
        assertThat(second).isSameAs(first);
        verify(dataSource, times(1)).getConnection();
        verify(metadata, times(1)).getSQLKeywords();
    }

    @Test
    void emptyDriverKeywordsFallBackToStandardWords() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSQLKeywords()).thenReturn("  ");
        SqlReservedWords reservedWords = new SqlReservedWords(dataSource);

        assertThat(reservedWords.get()).contains(
                "order", "group", "key", "index", "primary", "check",
                "left", "value", "row", "desc");
    }

    @Test
    void metadataFailureFallsBackToStandardWordsWithoutEscaping() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSQLKeywords()).thenThrow(new SQLException("metadata unavailable"));
        SqlReservedWords reservedWords = new SqlReservedWords(dataSource);

        assertThatCode(reservedWords::get).doesNotThrowAnyException();
        assertThat(reservedWords.get()).contains("order", "group", "desc");
    }
}
