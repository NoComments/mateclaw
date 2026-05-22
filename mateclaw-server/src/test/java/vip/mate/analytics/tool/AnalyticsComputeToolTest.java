package vip.mate.analytics.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsComputeToolTest {

    private AnalyticsComputeTool tool;

    @BeforeEach
    void setUp() {
        tool = new AnalyticsComputeTool();
    }

    // --- mom ---

    @Test
    void mom_normalData_returnsMomPct() {
        List<Map<String, Object>> data = listOf(
                row("month", "2024-01", "sales", 100),
                row("month", "2024-02", "sales", 120),
                row("month", "2024-03", "sales", 108)
        );
        Map<String, Object> result = tool.analyticsCompute("mom", data, "sales", "month", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(3, rows.size());
        assertNull(rows.get(0).get("mom_pct"));
        assertEquals(20.0, (Double) rows.get(1).get("mom_pct"), 0.01);
        assertEquals(-10.0, (Double) rows.get(2).get("mom_pct"), 0.01);
    }

    @Test
    void mom_emptyData_returnsEmptyRows() {
        Map<String, Object> result = tool.analyticsCompute("mom", List.of(), "sales", "month", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertTrue(rows.isEmpty());
    }

    // --- yoy ---

    @Test
    void yoy_withEnoughPeriods_returnsYoyPct() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            String month = String.format("20%02d-%02d", i <= 12 ? 23 : 24, i <= 12 ? i : 1);
            data.add(row("month", month, "sales", 100 + i * 10));
        }
        Map<String, Object> result = tool.analyticsCompute("yoy", data, "sales", "month", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertNull(rows.get(0).get("yoy_pct"));
        assertNotNull(rows.get(12).get("yoy_pct"));
    }

    // --- moving_avg ---

    @Test
    void movingAvg_window3_returnsMovingAverages() {
        List<Map<String, Object>> data = listOf(
                row("month", "M1", "val", 10),
                row("month", "M2", "val", 20),
                row("month", "M3", "val", 30),
                row("month", "M4", "val", 40)
        );
        Map<String, Object> result = tool.analyticsCompute("moving_avg", data, "val", "month", null, 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertNull(rows.get(0).get("ma_3"));
        assertNull(rows.get(1).get("ma_3"));
        assertEquals(20.0, (Double) rows.get(2).get("ma_3"), 0.01);
        assertEquals(30.0, (Double) rows.get(3).get("ma_3"), 0.01);
    }

    // --- zscore ---

    @Test
    void zscore_withOutlier_detectsAnomaly() {
        List<Map<String, Object>> data = listOf(
                row("id", "1", "val", 10),
                row("id", "2", "val", 12),
                row("id", "3", "val", 11),
                row("id", "4", "val", 100),
                row("id", "5", "val", 10)
        );
        Map<String, Object> result = tool.analyticsCompute("zscore", data, "val", null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> anomalies = (List<Map<String, Object>>) result.get("anomalies");
        assertFalse(anomalies.isEmpty());
    }

    // --- correlation ---

    @Test
    void correlation_twoFields_returnsCoefficient() {
        List<Map<String, Object>> data = listOf(
                row("x", 1, "y", 2),
                row("x", 2, "y", 4),
                row("x", 3, "y", 6),
                row("x", 4, "y", 8)
        );
        Map<String, Object> result = tool.analyticsCompute("correlation", data, "x", null, "y", null);
        double r = ((Number) result.get("r")).doubleValue();
        assertEquals(1.0, r, 0.01);
        assertEquals("strong positive", result.get("interpretation"));
    }

    // --- linear_trend ---

    @Test
    void linearTrend_increasingData_returnsPositiveSlope() {
        List<Map<String, Object>> data = listOf(
                row("month", "M1", "val", 10),
                row("month", "M2", "val", 20),
                row("month", "M3", "val", 30)
        );
        Map<String, Object> result = tool.analyticsCompute("linear_trend", data, "val", "month", null, null);
        double slope = ((Number) result.get("slope")).doubleValue();
        assertTrue(slope > 0);
        assertEquals("increasing", result.get("direction"));
    }

    // --- error: unknown operation ---

    @Test
    void unknownOperation_returnsError() {
        Map<String, Object> result = tool.analyticsCompute("bogus", List.of(), "x", null, null, null);
        assertNotNull(result.get("error"));
    }

    // --- error: missing value field ---

    @Test
    void missingValueField_returnsError() {
        List<Map<String, Object>> data = listOf(row("a", 1, "b", 2));
        Map<String, Object> result = tool.analyticsCompute("mom", data, "nonexistent", "a", null, null);
        assertNotNull(result.get("error"));
    }

    // ---- helpers ----

    private static Map<String, Object> row(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    @SafeVarargs
    private static List<Map<String, Object>> listOf(Map<String, Object>... rows) {
        return new ArrayList<>(java.util.Arrays.asList(rows));
    }
}
