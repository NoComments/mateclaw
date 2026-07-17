package vip.mate.analytics.tool;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI @Tool: pure in-memory statistical computations on data rows.
 *
 * <p>Operates on query result rows from {@link AnalyticsQueryTool} — no database
 * access, no SQL. The Agent calls analytics_query first, then passes the rows
 * here for statistical analysis.
 *
 * <p>Supported operations: mom, yoy, moving_avg, zscore, correlation, linear_trend.
 */
@Component
public class AnalyticsComputeTool {

    private static final double ZSCORE_THRESHOLD = 2.0;
    private static final java.util.Set<String> SUPPORTED_OPS = java.util.Set.of(
            "mom", "yoy", "moving_avg", "zscore", "correlation", "linear_trend");

    /**
     * Performs a statistical computation on the given data rows.
     *
     * @param operation   one of: mom, yoy, moving_avg, zscore, correlation, linear_trend
     * @param data        list of row maps (from analytics_query output)
     * @param valueField  primary numeric field name
     * @param orderField  time/category field for ordering (required for mom/yoy/moving_avg/linear_trend)
     * @param secondField second numeric field (required for correlation)
     * @param windowSize  window size for moving_avg (default 3)
     * @return computation result map
     */
    @Tool(description = "Perform statistical computations on data rows. "
            + "Supported operations: mom (month-over-month %), yoy (year-over-year %), "
            + "moving_avg (moving average), zscore (anomaly detection), "
            + "correlation (Pearson r between two fields), linear_trend (slope + intercept). "
            + "Pass data as list of row maps from analytics_query output.")
    public Map<String, Object> analyticsCompute(
            @ToolParam(description = "Operation: mom | yoy | moving_avg | zscore | correlation | linear_trend")
            String operation,
            @ToolParam(description = "Data rows from analytics_query output")
            List<Map<String, Object>> data,
            @ToolParam(description = "Primary value field name")
            String valueField,
            @ToolParam(description = "Optional: time/category field for ordering", required = false)
            String orderField,
            @ToolParam(description = "Optional: second value field for correlation", required = false)
            String secondField,
            @ToolParam(description = "Optional: window size for moving_avg (default 3)", required = false)
            Integer windowSize
    ) {
        if (!SUPPORTED_OPS.contains(operation)) {
            return Map.of("error", "Unknown operation: " + operation
                    + ". Supported: mom, yoy, moving_avg, zscore, correlation, linear_trend");
        }

        if (data == null || data.isEmpty()) {
            return Map.of("rows", List.of(), "operation", operation);
        }

        return switch (operation) {
            case "mom" -> computeMom(data, valueField);
            case "yoy" -> computeYoy(data, valueField);
            case "moving_avg" -> computeMovingAvg(data, valueField, windowSize != null ? windowSize : 3);
            case "zscore" -> computeZscore(data, valueField);
            case "correlation" -> computeCorrelation(data, valueField, secondField);
            case "linear_trend" -> computeLinearTrend(data, valueField);
            default -> throw new IllegalStateException("Unexpected operation: " + operation);
        };
    }

    private Map<String, Object> computeMom(List<Map<String, Object>> data, String valueField) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Double prev = null;
        for (Map<String, Object> row : data) {
            Double val = toDouble(row.get(valueField));
            if (val == null) {
                return Map.of("error", "Field '" + valueField + "' not found or not numeric in data rows");
            }
            Map<String, Object> out = new LinkedHashMap<>(row);
            if (prev != null && prev != 0.0) {
                out.put("mom_pct", round2((val - prev) / prev * 100));
            } else {
                out.put("mom_pct", null);
            }
            rows.add(out);
            prev = val;
        }
        return Map.of("operation", "mom", "rows", rows);
    }

    private Map<String, Object> computeYoy(List<Map<String, Object>> data, String valueField) {
        int period = 12;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Double val = toDouble(data.get(i).get(valueField));
            if (val == null) {
                return Map.of("error", "Field '" + valueField + "' not found or not numeric in data rows");
            }
            Map<String, Object> out = new LinkedHashMap<>(data.get(i));
            if (i >= period) {
                Double prevVal = toDouble(data.get(i - period).get(valueField));
                if (prevVal != null && prevVal != 0.0) {
                    out.put("yoy_pct", round2((val - prevVal) / prevVal * 100));
                } else {
                    out.put("yoy_pct", null);
                }
            } else {
                out.put("yoy_pct", null);
            }
            rows.add(out);
        }
        return Map.of("operation", "yoy", "rows", rows);
    }

    private Map<String, Object> computeMovingAvg(List<Map<String, Object>> data, String valueField, int window) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Double val = toDouble(data.get(i).get(valueField));
            if (val == null) {
                return Map.of("error", "Field '" + valueField + "' not found or not numeric in data rows");
            }
            Map<String, Object> out = new LinkedHashMap<>(data.get(i));
            if (i >= window - 1) {
                double sum = 0;
                for (int j = i - window + 1; j <= i; j++) {
                    sum += toDouble(data.get(j).get(valueField));
                }
                out.put("ma_" + window, round2(sum / window));
            } else {
                out.put("ma_" + window, null);
            }
            rows.add(out);
        }
        return Map.of("operation", "moving_avg", "rows", rows);
    }

    private Map<String, Object> computeZscore(List<Map<String, Object>> data, String valueField) {
        double[] values = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            Double val = toDouble(data.get(i).get(valueField));
            if (val == null) {
                return Map.of("error", "Field '" + valueField + "' not found or not numeric in data rows");
            }
            values[i] = val;
        }

        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;

        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        double stdDev = Math.sqrt(variance / values.length);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> out = new LinkedHashMap<>(data.get(i));
            double z = stdDev == 0 ? 0 : (values[i] - mean) / stdDev;
            double zRounded = round2(z);
            out.put("zscore", zRounded);
            rows.add(out);
            if (Math.abs(zRounded) >= ZSCORE_THRESHOLD) {
                anomalies.add(out);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "zscore");
        result.put("mean", round2(mean));
        result.put("stdDev", round2(stdDev));
        result.put("rows", rows);
        result.put("anomalies", anomalies);
        return result;
    }

    private Map<String, Object> computeCorrelation(List<Map<String, Object>> data,
                                                    String valueField, String secondField) {
        if (secondField == null || secondField.isBlank()) {
            return Map.of("error", "correlation requires secondField parameter");
        }

        double[] x = new double[data.size()];
        double[] y = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            Double xVal = toDouble(data.get(i).get(valueField));
            Double yVal = toDouble(data.get(i).get(secondField));
            if (xVal == null) {
                return Map.of("error", "Field '" + valueField + "' not found or not numeric");
            }
            if (yVal == null) {
                return Map.of("error", "Field '" + secondField + "' not found or not numeric");
            }
            x[i] = xVal;
            y[i] = yVal;
        }

        PearsonsCorrelation pc = new PearsonsCorrelation();
        double r = pc.correlation(x, y);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "correlation");
        result.put("r", round2(r));
        result.put("interpretation", interpretCorrelation(r));
        return result;
    }

    private Map<String, Object> computeLinearTrend(List<Map<String, Object>> data, String valueField) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < data.size(); i++) {
            Double val = toDouble(data.get(i).get(valueField));
            if (val == null) {
                return Map.of("error", "Field '" + valueField + "' not found or not numeric");
            }
            regression.addData(i, val);
        }

        double slope = regression.getSlope();
        double intercept = regression.getIntercept();
        double rSquared = regression.getRSquare();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "linear_trend");
        result.put("slope", round2(slope));
        result.put("intercept", round2(intercept));
        result.put("r_squared", round2(rSquared));
        result.put("direction", slope > 0.001 ? "increasing" : slope < -0.001 ? "decreasing" : "flat");
        return result;
    }

    private static Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static String interpretCorrelation(double r) {
        if (r > 0.7) return "strong positive";
        if (r > 0.3) return "moderate positive";
        if (r > -0.3) return "weak / none";
        if (r > -0.7) return "moderate negative";
        return "strong negative";
    }
}
