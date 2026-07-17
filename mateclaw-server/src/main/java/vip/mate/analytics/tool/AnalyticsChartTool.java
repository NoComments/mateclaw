package vip.mate.analytics.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Spring AI @Tool: builds ECharts option JSON for frontend chart rendering.
 *
 * <p>Accepts data rows from {@code analytics_query} output and converts them
 * into a ready-to-use ECharts {@code option} map. No database access — pure
 * data-shaping logic so this class has no Spring dependencies beyond
 * {@code @Component}.
 *
 * @author MateClaw Team
 */
@Component
public class AnalyticsChartTool {

    /**
     * Build an ECharts option map for the requested chart type.
     *
     * @param chartType   chart type: bar | line | pie | scatter | heatmap
     * @param data        data rows — list of maps from {@code analytics_query} rows field
     * @param xField      field name for x-axis or category
     * @param yField      field name for y-axis or value
     * @param seriesField optional field name for series grouping (bar/line only)
     * @return ECharts option map, ready to be serialised to JSON
     */
    @Tool(description = "Build an ECharts option JSON for frontend visualization. "
            + "Supported chartType: bar, line, pie, scatter, heatmap. "
            + "Pass data as list of row maps from analytics_query output.")
    public Map<String, Object> analyticsChart(
            @ToolParam(description = "Chart type: bar | line | pie | scatter | heatmap") String chartType,
            @ToolParam(description = "Data rows, list of maps (from analytics_query rows field)") List<Map<String, Object>> data,
            @ToolParam(description = "Field name for x-axis or category") String xField,
            @ToolParam(description = "Field name for y-axis or value") String yField,
            @ToolParam(description = "Optional: field name for series grouping", required = false) String seriesField
    ) {
        return switch (chartType.toLowerCase().trim()) {
            case "bar"      -> buildBarOrLine("bar", data, xField, yField, seriesField);
            case "line"     -> buildBarOrLine("line", data, xField, yField, seriesField);
            case "pie"      -> buildPie(data, xField, yField);
            case "scatter"  -> buildScatter(data, xField, yField);
            case "heatmap"  -> buildHeatmap(data, xField, yField);
            default         -> throw new IllegalArgumentException("Unsupported chartType: " + chartType);
        };
    }

    // -------------------------------------------------------------------------
    // Private chart builders
    // -------------------------------------------------------------------------

    private Map<String, Object> buildBarOrLine(
            String type,
            List<Map<String, Object>> data,
            String xField,
            String yField,
            String seriesField
    ) {
        List<Object> xData = data.stream().map(r -> r.get(xField)).collect(toList());
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", xData));
        option.put("yAxis", Map.of("type", "value"));

        if (seriesField == null) {
            List<Object> yData = data.stream().map(r -> r.get(yField)).collect(toList());
            option.put("series", List.of(Map.of("type", type, "data", yData)));
        } else {
            Map<Object, List<Map<String, Object>>> grouped = data.stream()
                    .collect(groupingBy(r -> r.getOrDefault(seriesField, "unknown")));
            List<Map<String, Object>> series = grouped.entrySet().stream()
                    .map(e -> Map.of(
                            "type", type,
                            "name", e.getKey(),
                            "data", e.getValue().stream().map(r -> r.get(yField)).collect(toList())
                    ))
                    .collect(toList());
            option.put("legend", Map.of("data", new ArrayList<>(grouped.keySet())));
            option.put("series", series);
        }
        return option;
    }

    private Map<String, Object> buildPie(List<Map<String, Object>> data, String xField, String yField) {
        List<Map<String, Object>> pieData = data.stream()
                .map(r -> Map.of("name", String.valueOf(r.get(xField)), "value", r.get(yField)))
                .collect(toList());
        return Map.of(
                "tooltip", Map.of("trigger", "item"),
                "series", List.of(Map.of("type", "pie", "radius", "60%", "data", pieData))
        );
    }

    private Map<String, Object> buildScatter(List<Map<String, Object>> data, String xField, String yField) {
        List<List<Object>> scatterData = data.stream()
                .map(r -> List.of(r.get(xField), r.get(yField)))
                .collect(toList());
        return Map.of(
                "xAxis", Map.of("type", "value"),
                "yAxis", Map.of("type", "value"),
                "series", List.of(Map.of("type", "scatter", "data", scatterData))
        );
    }

    private Map<String, Object> buildHeatmap(List<Map<String, Object>> data, String xField, String yField) {
        List<List<Object>> heatData = data.stream()
                .map(r -> List.of(r.get(xField), r.get(yField), r.get(yField)))
                .collect(toList());
        return Map.of(
                "visualMap", Map.of("type", "continuous"),
                "series", List.of(Map.of("type", "heatmap", "data", heatData))
        );
    }
}
