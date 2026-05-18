package vip.mate.analytics.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.mate.analytics.tool.guard.SqlGuardException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalyticsExportTool}.
 * Uses pure Mockito — no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsExportToolTest {

    @Mock
    JdbcTemplate jdbc;

    @InjectMocks
    AnalyticsExportTool tool;

    @Test
    void export_createsXlsxFileAndReturnsUrl() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("city", "郑州", "stock", 1000)
        ));

        String url = tool.analyticsExport("SELECT city, stock FROM dataset_livestock");

        assertThat(url).startsWith("/api/analytics/exports/");

        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Path.of(System.getProperty("java.io.tmpdir"), filename)).exists();
    }

    @Test
    void export_returnsEmptyFileWhenNoRows() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        String url = tool.analyticsExport("SELECT city FROM dataset_x");

        assertThat(url).startsWith("/api/analytics/exports/");
    }

    @Test
    void export_rejectsNonSelectSql() {
        assertThatThrownBy(() -> tool.analyticsExport("INSERT INTO dataset_x VALUES (1)"))
                .isInstanceOf(SqlGuardException.class);
    }
}
