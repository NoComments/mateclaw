# Implementation Plan: Data Analyst Deep Analysis Enhancement

**Spec**: `docs/superpowers/specs/2026-05-20-data-analyst-deep-analysis-design.md`
**Branch**: `feat/data-analyst-expert`
**Date**: 2026-05-20

---

## Phase 1: Backend - AnalyticsComputeTool + Migration

### Step 1.1: Add Commons Math3 dependency to pom.xml

**File**: `mateclaw-server/pom.xml`

Add Apache Commons Math3 dependency (not currently in the dependency tree):

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
</dependency>
```

Add inside the `<dependencies>` block, near other Apache Commons entries if present.

**Verify**: `mvn dependency:resolve` succeeds.

---

### Step 1.2: Create AnalyticsComputeToolTest (TDD - Red)

**File**: `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsComputeToolTest.java`

Write unit tests BEFORE the implementation. The tool is pure computation (no DB, no Spring context), so plain JUnit 5:

```java
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
        // 13 months: 2023-01 to 2024-01
        for (int i = 1; i <= 13; i++) {
            String month = String.format("20%02d-%02d", i <= 12 ? 23 : 24, i <= 12 ? i : 1);
            data.add(row("month", month, "sales", 100 + i * 10));
        }
        Map<String, Object> result = tool.analyticsCompute("yoy", data, "sales", "month", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        // First 12 rows should have null yoy_pct, 13th should have a value
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
                row("id", "4", "val", 100), // outlier
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
        // Drop orderField - not needed for correlation
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
```

**Verify**: `mvn test -Dtest=AnalyticsComputeToolTest` — should compile-fail (class not created yet). That's expected for TDD red phase.

---

### Step 1.3: Create AnalyticsComputeTool (TDD - Green)

**File**: `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsComputeTool.java`

```java
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
            default -> Map.of("error", "Unknown operation: " + operation
                    + ". Supported: mom, yoy, moving_avg, zscore, correlation, linear_trend");
        };
    }

    // -------------------------------------------------------------------------
    // Operations
    // -------------------------------------------------------------------------

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
        int period = 12; // default YoY period
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
            out.put("zscore", round2(z));
            rows.add(out);
            if (Math.abs(z) > ZSCORE_THRESHOLD) {
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
```

**Verify**: `mvn test -Dtest=AnalyticsComputeToolTest` — all 8 tests green.

---

### Step 1.4: Create V117 Flyway migrations (H2 + MySQL)

**File**: `mateclaw-server/src/main/resources/db/migration/h2/V117__analytics_compute_tool.sql`

```sql
-- V117: Add analyticsCompute tool binding + semantic columns on template fields
--
-- Part 1: Bind the new tool to the data-analyst agent (id=1000000020)
MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id)
VALUES (1000000026, 1000000020, 'analyticsCompute', TRUE, NOW(), NOW(), 0);

-- Part 2: Semantic layer columns on mate_dataset_template_field
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS time_granularity VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS aggregation VARCHAR(10) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS compute_hint VARCHAR(500) DEFAULT NULL;
```

**File**: `mateclaw-server/src/main/resources/db/migration/mysql/V117__analytics_compute_tool.sql`

```sql
-- V117: Add analyticsCompute tool binding + semantic columns on template fields
--
-- Part 1: Bind the new tool to the data-analyst agent (id=1000000020)
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000026, 1000000020, 'analyticsCompute', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE enabled = TRUE, deleted = 0, update_time = NOW();

-- Part 2: Semantic layer columns on mate_dataset_template_field
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS time_granularity VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS aggregation VARCHAR(10) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS compute_hint VARCHAR(500) DEFAULT NULL;
```

**Verify**: `mvn spring-boot:run` starts without Flyway errors, then Ctrl-C.

---

### Step 1.5: Extend DatasetTemplateField entity

**File**: `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateField.java`

Add 4 fields after `excelHeader` (line 57), before `createTime`:

```java
    /** Semantic role: DIMENSION, MEASURE, or TIME_KEY. Nullable. */
    private String role;

    /** Time granularity (DAY/WEEK/MONTH/QUARTER/YEAR). Only meaningful when role = TIME_KEY. */
    private String timeGranularity;

    /** Default aggregation (SUM/AVG/COUNT/MAX/MIN). Only meaningful when role = MEASURE. */
    private String aggregation;

    /** Free-text hint for derived-metric computation logic. */
    private String computeHint;
```

**Verify**: `mvn compile` succeeds. Lombok `@Data` auto-generates getters/setters.

---

### Step 1.6: Update data-analyst SKILL.md

**File**: `mateclaw-server/src/main/resources/skills/data-analyst/SKILL.md`

**Change 1** — Replace front-matter (lines 1-6) with:

```yaml
---
name: data-analyst
description: 数据分析方法论 -- 当你被绑定为数据分析专家时遵循此 SKILL 完成数据集的分析、画像、校验、出图与导出。
version: "1.1.0"
author: data-analyst-agent
dependencies:
  tools:
    - analyticsSchema
    - analyticsQuery
    - analyticsProfile
    - analyticsChart
    - analyticsExport
    - analyticsCompute
---
```

**Change 2** — Append after the "异常处理" section (after line 54), before "## 自演进":

```markdown
## 统计分析方法论

当需要做趋势、异常、相关性分析时，使用 analytics_compute 工具：

### 趋势分析
1. 先用 analytics_query 按时间聚合（月度/季度）
2. 调用 analytics_compute(operation="mom") 计算环比
3. 调用 analytics_compute(operation="linear_trend") 判断整体趋势方向
4. 如有同比需求，调用 analytics_compute(operation="yoy")

### 异常检测
1. 先用 analytics_query 按时间聚合
2. 调用 analytics_compute(operation="zscore") 标记异常点（|z|>2）
3. 对异常点用 analytics_query 钻取明细，按维度拆分找根因

### 相关性验证
1. 用 analytics_query 查出两个指标的时间序列
2. 调用 analytics_compute(operation="correlation") 计算相关系数
3. r > 0.7 强正相关，r < -0.7 强负相关，|r| < 0.3 无显著关联
4. 相关不等于因果 -- 必须结合业务逻辑解释

### 语义字段说明
analytics_schema 返回的字段可能包含以下语义标注，利用它们来做更精确的分析：
- role=TIME_KEY + timeGranularity -> 该字段是时间轴，用它做 GROUP BY 时间聚合
- role=MEASURE + aggregation -> 该字段是度量，用指定的聚合方式汇总
- role=DIMENSION -> 该字段是维度，可用于分组/下钻
- computeHint -> 衍生指标的计算逻辑说明
```

**Verify**: `mvn spring-boot:run` — skill loads without error in logs, then Ctrl-C.

---

### Step 1.7: Backend integration test

**Verify**:

```bash
mvn test -Dtest=AnalyticsComputeToolTest
mvn test  # full suite
```

All green before proceeding to Phase 2.

---

## Phase 2: Backend - UpdateTemplateField API compatibility

### Step 2.1: Check and extend UpdateTemplateFieldRequest (if needed)

The `DatasetTemplateController` uses the existing update endpoint. Since MyBatis Plus `updateById` only updates non-null fields, and the 4 new fields are nullable on the entity, the controller should already pass them through if the client sends them.

**Action**: Read `DatasetTemplateController.java` update method to confirm. If the request DTO is a separate class (not the entity), add the 4 new fields to it. If it uses the entity directly, no change needed.

Also update `DatasetTemplateService.java` if it has a DTO conversion layer that filters fields.

**Verify**: Manual curl test — update a field with `role=MEASURE`, confirm it persists.

---

## Phase 3: Frontend - Types, i18n, Route, Sidebar

### Step 3.1: Extend TypeScript types

**File**: `mateclaw-ui/src/types/analytics.ts`

Add 4 optional fields to `DatasetTemplateField` interface (after `semantic?: string`, line 32):

```typescript
  role?: 'DIMENSION' | 'MEASURE' | 'TIME_KEY'
  timeGranularity?: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
  computeHint?: string
```

Add the same 4 fields to `UpdateTemplateFieldRequest` interface (after `isNullable?: boolean`, line 93):

```typescript
  role?: 'DIMENSION' | 'MEASURE' | 'TIME_KEY'
  timeGranularity?: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
  computeHint?: string
```

---

### Step 3.2: Add i18n keys (zh-CN + en-US)

**File**: `mateclaw-ui/src/i18n/locales/zh-CN.ts`

In the `analytics` object (after `createFromInspect: '创建模板'`, line 3607):

```typescript
    role: '角色',
    roleDimension: '维度',
    roleMeasure: '度量',
    roleTimeKey: '时间键',
    timeGranularity: '时间粒度',
    timeGranDay: '日',
    timeGranWeek: '周',
    timeGranMonth: '月',
    timeGranQuarter: '季',
    timeGranYear: '年',
    aggregation: '聚合方式',
    computeHint: '计算说明',
    computeHintPlaceholder: '描述衍生指标的计算逻辑',
    analyze: '分析',
```

In the `nav` object, replace `analyticsChat` (line 406):

```typescript
    // analyticsChat: '数据分析',  <-- DELETE this line
    dataManagement: '数据管理',   // <-- ADD this line
```

**File**: `mateclaw-ui/src/i18n/locales/en-US.ts`

Same pattern in the `analytics` object (after `createFromInspect: 'Create Template'`, line 3515):

```typescript
    role: 'Role',
    roleDimension: 'Dimension',
    roleMeasure: 'Measure',
    roleTimeKey: 'Time Key',
    timeGranularity: 'Time Granularity',
    timeGranDay: 'Day',
    timeGranWeek: 'Week',
    timeGranMonth: 'Month',
    timeGranQuarter: 'Quarter',
    timeGranYear: 'Year',
    aggregation: 'Aggregation',
    computeHint: 'Compute Hint',
    computeHintPlaceholder: 'Describe how this derived metric is computed',
    analyze: 'Analyze',
```

In the `nav` object, replace `analyticsChat` (line 406):

```typescript
    // analyticsChat: 'Data Analysis',  <-- DELETE this line
    dataManagement: 'Data Management', // <-- ADD this line
```

---

### Step 3.3: Remove AnalyticsChat route + delete AnalystChat.vue

**File**: `mateclaw-ui/src/router/index.ts`

Delete the chat route block (lines 296-301):

```typescript
// DELETE these lines:
            {
              path: 'chat',
              name: 'AnalyticsChat',
              component: () => import('@/views/analytics/AnalystChat.vue'),
              meta: { title: 'Analytics - Chat' },
            },
```

Change the analytics redirect (line 264) from `/analytics/templates` to `/analytics/datasets`:

```typescript
          redirect: '/analytics/datasets',
```

**File**: Delete `mateclaw-ui/src/views/analytics/AnalystChat.vue`

```bash
rm mateclaw-ui/src/views/analytics/AnalystChat.vue
```

---

### Step 3.4: Update sidebar in MainLayout.vue

**File**: `mateclaw-ui/src/views/layout/MainLayout.vue`

Replace the 3 analytics sidebar entries (lines 382-396) with 2 entries under a `dataManagement` group label. The sidebar uses a flat item list inside the `business` group. Replace:

```typescript
      {
        path: '/analytics/templates',
        label: t('nav.analyticsTemplates'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>`,
      },
      {
        path: '/analytics/datasets',
        label: t('nav.analyticsDatasets'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>`,
      },
      {
        path: '/analytics/chat',
        label: t('nav.analyticsChat'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/><polyline points="2 20 22 20"/></svg>`,
      },
```

With:

```typescript
      {
        path: '/analytics/templates',
        label: t('nav.analyticsTemplates'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>`,
      },
      {
        path: '/analytics/datasets',
        label: t('nav.analyticsDatasets'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>`,
      },
```

(Simply remove the `/analytics/chat` entry.)

---

### Step 3.5: Add "Analyze" button to DatasetList.vue

**File**: `mateclaw-ui/src/views/analytics/DatasetList.vue`

**Change 1** — In the action-row template (after the "Upload Log" button, line 35), add an "Analyze" button:

```html
                  <button class="action-btn accent" @click="goToAnalysis(row)">
                    {{ t('analytics.analyze') }}
                  </button>
```

**Change 2** — The `goToChat` function (lines 207-209) already exists but is unused in the template. Replace it with `goToAnalysis`:

```typescript
function goToAnalysis(row: Dataset) {
  router.push({ path: '/chat', query: { agentId: '1000000020' } })
}
```

And delete the old `goToChat` function.

**Change 3** — Update the `el-table-column` width for the actions column (line 24) from `300` to `360` to fit the new button.

---

### Step 3.6: Add semantic fields to TemplateEditor.vue

**File**: `mateclaw-ui/src/views/analytics/TemplateEditor.vue`

**Change 1** — Add 4 columns to the el-table (after the `nullable` column, line 48, before the edit column):

```html
            <el-table-column prop="role" :label="t('analytics.role')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.role" size="small">
                  {{ t(`analytics.role${row.role === 'TIME_KEY' ? 'TimeKey' : row.role === 'MEASURE' ? 'Measure' : 'Dimension'}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.timeGranularity')" width="100">
              <template #default="{ row }">
                <span v-if="row.role === 'TIME_KEY' && row.timeGranularity">{{ row.timeGranularity }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.aggregation')" width="100">
              <template #default="{ row }">
                <span v-if="row.role === 'MEASURE' && row.aggregation">{{ row.aggregation }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="computeHint" :label="t('analytics.computeHint')" min-width="120" />
```

**Change 2** — Add 4 form items to the Add Field dialog (after `isNullable` switch, line 104, before `</el-form>`):

```html
        <el-form-item :label="t('analytics.role')">
          <el-select v-model="form.role" clearable style="width: 100%">
            <el-option label="Dimension" value="DIMENSION" />
            <el-option label="Measure" value="MEASURE" />
            <el-option label="Time Key" value="TIME_KEY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'TIME_KEY'" :label="t('analytics.timeGranularity')">
          <el-select v-model="form.timeGranularity" clearable style="width: 100%">
            <el-option v-for="g in ['DAY','WEEK','MONTH','QUARTER','YEAR']" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'MEASURE'" :label="t('analytics.aggregation')">
          <el-select v-model="form.aggregation" clearable style="width: 100%">
            <el-option v-for="a in ['SUM','AVG','COUNT','MAX','MIN']" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.computeHint')">
          <el-input v-model="form.computeHint" type="textarea" :rows="2"
            :placeholder="t('analytics.computeHintPlaceholder')" />
        </el-form-item>
```

**Change 3** — Add same 4 form items to the Edit Field dialog (after `isNullable` switch, line 147, before `</el-form>`):

```html
        <el-form-item :label="t('analytics.role')">
          <el-select v-model="editForm.role" clearable style="width: 100%">
            <el-option label="Dimension" value="DIMENSION" />
            <el-option label="Measure" value="MEASURE" />
            <el-option label="Time Key" value="TIME_KEY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.role === 'TIME_KEY'" :label="t('analytics.timeGranularity')">
          <el-select v-model="editForm.timeGranularity" clearable style="width: 100%">
            <el-option v-for="g in ['DAY','WEEK','MONTH','QUARTER','YEAR']" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.role === 'MEASURE'" :label="t('analytics.aggregation')">
          <el-select v-model="editForm.aggregation" clearable style="width: 100%">
            <el-option v-for="a in ['SUM','AVG','COUNT','MAX','MIN']" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.computeHint')">
          <el-input v-model="editForm.computeHint" type="textarea" :rows="2"
            :placeholder="t('analytics.computeHintPlaceholder')" />
        </el-form-item>
```

**Change 4** — Extend `form` reactive (lines 187-194):

```typescript
const form = reactive({
  fieldName: '',
  fieldType: 'STRING' as FieldType,
  fieldUnit: '',
  semantic: '',
  ordinal: 0,
  isNullable: true,
  role: '' as string,
  timeGranularity: '' as string,
  aggregation: '' as string,
  computeHint: '',
})
```

**Change 5** — Extend `editForm` reactive (lines 196-202):

```typescript
const editForm = reactive({
  fieldName: '',
  fieldUnit: '',
  semantic: '',
  ordinal: 0,
  isNullable: true,
  role: '' as string,
  timeGranularity: '' as string,
  aggregation: '' as string,
  computeHint: '',
})
```

**Change 6** — In `openAddFieldDialog()` (line 244), add resets:

```typescript
  form.role = ''
  form.timeGranularity = ''
  form.aggregation = ''
  form.computeHint = ''
```

**Change 7** — In `openEditDialog()` (line 278), populate new fields:

```typescript
  editForm.role = row.role ?? ''
  editForm.timeGranularity = row.timeGranularity ?? ''
  editForm.aggregation = row.aggregation ?? ''
  editForm.computeHint = row.computeHint ?? ''
```

**Change 8** — In `handleAddField()` (line 260), include new fields in the payload:

```typescript
    await createTemplateField(templateId, {
      fieldName: form.fieldName,
      fieldType: form.fieldType,
      fieldUnit: form.fieldUnit || undefined,
      semantic: form.semantic || undefined,
      ordinal: form.ordinal,
      isNullable: form.isNullable,
      role: form.role || undefined,
      timeGranularity: form.timeGranularity || undefined,
      aggregation: form.aggregation || undefined,
      computeHint: form.computeHint || undefined,
    })
```

**Change 9** — In `handleEditField()` (line 293), include new fields:

```typescript
    await updateTemplateField(templateId, editingField.value.id, {
      fieldName: editForm.fieldName,
      fieldUnit: editForm.fieldUnit || undefined,
      semantic: editForm.semantic || undefined,
      ordinal: editForm.ordinal,
      isNullable: editForm.isNullable,
      role: editForm.role || undefined,
      timeGranularity: editForm.timeGranularity || undefined,
      aggregation: editForm.aggregation || undefined,
      computeHint: editForm.computeHint || undefined,
    })
```

---

## Phase 4: Verification

### Step 4.1: Backend verify

```bash
cd mateclaw-server && mvn test
```

All tests green.

### Step 4.2: Frontend verify

```bash
cd mateclaw-ui && pnpm build && pnpm lint
```

Zero errors.

### Step 4.3: Manual smoke test

1. `mvn spring-boot:run` + `pnpm dev`
2. Login `admin / admin123`
3. Sidebar: verify `/analytics/chat` entry is gone, only Templates + Datasets remain
4. Datasets page: verify "Analyze" button appears, clicking it navigates to `/chat?agentId=1000000020`
5. Template Editor: create/edit a field, verify Role/TimeGranularity/Aggregation/ComputeHint controls work
6. Agent Chat with data-analyst: verify `analytics_compute` tool is available

---

## Summary

| Phase | Steps | Files touched |
|---|---|---|
| 1: Backend core | 1.1-1.7 | pom.xml, AnalyticsComputeTool.java, AnalyticsComputeToolTest.java, V117 H2+MySQL, DatasetTemplateField.java, SKILL.md |
| 2: Backend API | 2.1 | Controller/Service DTO (if needed) |
| 3: Frontend | 3.1-3.6 | analytics.ts, zh-CN.ts, en-US.ts, router/index.ts, MainLayout.vue, DatasetList.vue, TemplateEditor.vue, -AnalystChat.vue |
| 4: Verify | 4.1-4.3 | (no new files) |
