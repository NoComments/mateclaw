package vip.mate.analytics.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AnalyticsChartTool} — no Spring context needed.
 */
class AnalyticsChartToolTest {

    static final List<Map<String, Object>> DATA = List.of(
            Map.of("city", "郑州", "stock", 1000, "species", "肉鸡"),
            Map.of("city", "洛阳", "stock", 800, "species", "蛋鸡"),
            Map.of("city", "郑州", "stock", 600, "species", "蛋鸡")
    );

    private final AnalyticsChartTool tool = new AnalyticsChartTool();

    @Test
    void bar_containsSeriesWithBarType() {
        Map<String, Object> option = tool.analyticsChart("bar", DATA, "city", "stock", null);

        assertThat(option).containsKey("series");
        List<?> series = (List<?>) option.get("series");
        assertThat(series).hasSize(1);
        Map<?, ?> s = (Map<?, ?>) series.get(0);
        assertThat(s.get("type")).isEqualTo("bar");
    }

    @Test
    void line_containsSeriesWithLineType() {
        Map<String, Object> option = tool.analyticsChart("line", DATA, "city", "stock", null);

        assertThat(option).containsKey("series");
        List<?> series = (List<?>) option.get("series");
        assertThat(series).hasSize(1);
        Map<?, ?> s = (Map<?, ?>) series.get(0);
        assertThat(s.get("type")).isEqualTo("line");
    }

    @Test
    void pie_containsPieData() {
        Map<String, Object> option = tool.analyticsChart("pie", DATA, "city", "stock", null);

        assertThat(option).containsKey("series");
        List<?> series = (List<?>) option.get("series");
        assertThat(series).hasSize(1);
        Map<?, ?> s = (Map<?, ?>) series.get(0);
        assertThat(s.get("type")).isEqualTo("pie");
        List<?> pieData = (List<?>) s.get("data");
        assertThat(pieData).hasSize(3);
        Map<?, ?> first = (Map<?, ?>) pieData.get(0);
        assertThat(first.containsKey("name")).isTrue();
        assertThat(first.containsKey("value")).isTrue();
    }

    @Test
    void scatter_containsScatterPoints() {
        Map<String, Object> option = tool.analyticsChart("scatter", DATA, "city", "stock", null);

        assertThat(option).containsKey("series");
        List<?> series = (List<?>) option.get("series");
        assertThat(series).hasSize(1);
        Map<?, ?> s = (Map<?, ?>) series.get(0);
        assertThat(s.get("type")).isEqualTo("scatter");
        List<?> points = (List<?>) s.get("data");
        assertThat(points).hasSize(3);
        List<?> firstPoint = (List<?>) points.get(0);
        assertThat(firstPoint).hasSize(2);
    }

    @Test
    void heatmap_containsHeatmapSeries() {
        Map<String, Object> option = tool.analyticsChart("heatmap", DATA, "city", "stock", null);

        assertThat(option).containsKey("series");
        List<?> series = (List<?>) option.get("series");
        assertThat(series).hasSize(1);
        Map<?, ?> s = (Map<?, ?>) series.get(0);
        assertThat(s.get("type")).isEqualTo("heatmap");
        List<?> heatData = (List<?>) s.get("data");
        assertThat(heatData).hasSize(3);
    }

    @Test
    void unknownType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> tool.analyticsChart("radar", DATA, "city", "stock", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported chartType: radar");
    }

    @Test
    void bar_withSeriesField_groupsIntoMultipleSeries() {
        Map<String, Object> option = tool.analyticsChart("bar", DATA, "city", "stock", "species");

        assertThat(option).containsKey("legend").containsKey("series");
        List<?> series = (List<?>) option.get("series");
        // DATA has two distinct species values: 肉鸡 and 蛋鸡
        assertThat(series).hasSize(2);
        series.forEach(s -> assertThat(((Map<?, ?>) s).get("type")).isEqualTo("bar"));
    }
}
