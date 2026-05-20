# 设计文档：数据分析 Agent 深度分析能力增强

**日期**：2026-05-20
**分支**：feat/data-analyst-expert
**状态**：已确认，待实施

---

## 1. 背景与目标

### 业务场景

客户需要对来源于 Excel 的业务数据（几十万~几百万行）做深度分析：时序趋势研判、异常检测、历史数据因果推断。当前系统已完成 Phase-1（Excel 导入落库 + Agent Text2SQL 查询），但缺少统计计算能力和完整的产品形态。

### 设计目标

1. 新增通用统计计算 `@Tool`，让 Agent 能精确执行环比/同比/异常检测/相关性分析等操作
2. 增强语义层，让 Agent 更准确地理解字段含义和分析方法
3. 将分析入口从独立页面统一到数字员工 Agent Chat 体系
4. 侧边栏收敛为「数据管理」一级目录 + 两个二级入口

### 设计原则

- **不引入新概念**：复用现有 Skill/Tool/Agent 绑定机制
- **不触碰核心代码**：变更完全收敛在 analytics 模块内部
- **遵循既有 pattern**：内置 Agent 的三层架构（Flyway 迁移 + SeedService + SKILL.md）保持一致

---

## 2. 现有架构基线

### 2.1 内置 Agent 创建模式（三层架构）

所有内置 Agent 遵循完全对称的模式：

| 层 | 负责 | 实现 |
|---|---|---|
| Flyway 迁移 | 种 `mate_agent` 行 + `mate_agent_tool` 工具绑定 | V113/V114/V116 |
| SeedService | Flyway 后回填 `mate_agent_skill` Skill 绑定 | `*AgentSeedService` (Order 120~130) |
| Skill 目录 | classpath 下的 SKILL.md + LESSONS.md | `skills/<name>/` |

原因：Skill 的 `mate_skill.id` 由 `BuiltinSkillSeedService`（Order 110）在 Flyway 之后才分配，迁移脚本拿不到稳定的 skill_id。

### 2.2 Skill → Tool 自动绑定机制

系统已支持在 SKILL.md front-matter 中声明 `dependencies.tools`，完整链路：

```
SKILL.md dependencies.tools
  -> BuiltinSkillSeedService 解析后写入 config_json.requiredTools
  -> SkillPackageResolver -> SkillManifest -> manifest.allowedTools
  -> ResolvedSkill.getEffectiveAllowedTools() 返回工具名集合
  -> AgentBindingService.getEffectiveToolNames(agentId) 合并:
       mate_agent_tool 直接绑定 UNION Skill 声明的 tools UNION SYSTEM_LEVEL_TOOLS
  -> AgentGraphBuilder: toolSet.withAllowedToolsOnly(boundTools) 过滤
```

已使用此机制的 Skill：`sql_query`、`browser_cdp`、`cron`、`xlsx` 等。

### 2.3 当前数据分析 Agent 状态

- **Agent**：`mate_agent` id=1000000020「数据分析专家」（V116 迁移种入）
- **工具绑定**：`mate_agent_tool` 5 条记录（analyticsSchema/Query/Profile/Chart/Export）
- **Skill 绑定**：`DataAnalystAgentSeedService` (Order 130) 绑定 `data-analyst` Skill
- **Skill 声明**：`data-analyst/SKILL.md` **缺少** `dependencies.tools`（不影响当前 Agent，因为 V116 已直接绑定工具；但影响用户自建分析 Agent）

### 2.4 现有 Tool 集

| Tool | 功能 | 安全机制 |
|---|---|---|
| `AnalyticsSchemaTool` | 列出数据集 / 描述字段 schema | workspace_id 隔离 |
| `AnalyticsQueryTool` | 执行只读 SELECT | SqlGuard 白名单 + LIMIT 10000 |
| `AnalyticsProfileTool` | 列统计画像（count/min/max/mean/top5） | admin-controlled fieldCode |
| `AnalyticsChartTool` | 构建 ECharts option JSON | 纯数据转换，无 DB 访问 |
| `AnalyticsExportTool` | 导出查询结果为 xlsx | SqlGuard |

---

## 3. 后端变更

### 3.1 新增 `AnalyticsComputeTool`

**位置**：`vip.mate.analytics.tool.AnalyticsComputeTool`

通用统计计算工具。Agent 传入操作指令 + 数据行，工具精确计算后返回结果。不执行 SQL，不访问数据库，纯内存计算。

**接口**：

```java
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
)
```

**操作清单**：

| operation | 必填参数 | 输出 |
|---|---|---|
| `mom` | data, valueField, orderField | 每行追加 `mom_pct`（环比 %） |
| `yoy` | data, valueField, orderField | 每行追加 `yoy_pct`（同比 %，需 >= 13 周期） |
| `moving_avg` | data, valueField, orderField, windowSize | 每行追加 `ma_{N}` |
| `zscore` | data, valueField | 每行追加 `zscore`，返回 `anomalies` 列表（\|z\| > 2） |
| `correlation` | data, valueField, secondField | 返回 `r`、`p_value`、`interpretation` |
| `linear_trend` | data, valueField, orderField | 返回 `slope`、`intercept`、`r_squared`、`direction` |

**实现依赖**：纯 Java 算术 + Apache Commons Math3（`SimpleRegression`、`PearsonsCorrelation`）。需确认 pom.xml 依赖树中是否已间接引入；若无则显式添加。

### 3.2 Flyway 迁移 V117

新增 `V117__analytics_compute_tool.sql`（H2 + MySQL 双份）：

**Part 1**：为现有数据分析 Agent 绑定新工具

```sql
MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id)
VALUES (1000000026, 1000000020, 'analyticsCompute', TRUE, NOW(), NOW(), 0);
```

**Part 2**：`mate_dataset_template_field` 新增语义列

```sql
ALTER TABLE mate_dataset_template_field ADD COLUMN role VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN time_granularity VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN aggregation VARCHAR(10) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN compute_hint VARCHAR(500) DEFAULT NULL;
```

### 3.3 `DatasetTemplateField` 实体扩展

新增 4 个字段：

| 字段 | Java 类型 | 说明 |
|---|---|---|
| `role` | `String` | `DIMENSION` / `MEASURE` / `TIME_KEY`，可选 |
| `timeGranularity` | `String` | `DAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR`，仅 TIME_KEY |
| `aggregation` | `String` | `SUM` / `AVG` / `COUNT` / `MAX` / `MIN`，仅 MEASURE |
| `computeHint` | `String` | 衍生指标计算说明文本，可选 |

`AnalyticsSchemaTool` 无需修改 —— 现有代码遍历 field 属性输出，新字段非空时自动包含在返回结果中。

### 3.4 `data-analyst/SKILL.md` 更新

**Front-matter 补充 dependencies.tools**：

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

**内容追加统计分析方法论**（在现有"调用顺序约定"和"SQL 编写规范"之后）：

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
```

---

## 4. 前端变更

### 4.1 删除 `AnalystChat.vue`

分析对话复用数字员工通用 Agent Chat 页面，不再维护独立的分析聊天组件。

### 4.2 路由调整

`src/router/index.ts` analytics children 删除 `chat` 路由，`redirect` 改为 `/analytics/datasets`。

### 4.3 侧边栏

```
数据管理（一级）
  |- 数据集模板    -> /analytics/templates
  |- 数据集        -> /analytics/datasets
```

### 4.4 `DatasetList.vue` 增加"分析"按钮

操作栏新增"分析"按钮，跳转到预置数据分析 Agent（id=1000000020）的对话页：

```typescript
function goToAnalysis(row: Dataset) {
  router.push({ name: 'AgentChat', params: { agentId: '1000000020' } })
}
```

### 4.5 `TemplateEditor.vue` 补充语义字段编辑

字段编辑表格新增 4 列：

| 列 | 控件 | 条件显示 |
|---|---|---|
| 角色 | `el-select`（DIMENSION / MEASURE / TIME_KEY） | 始终可选 |
| 时间粒度 | `el-select`（DAY / WEEK / MONTH / QUARTER / YEAR） | 仅 role=TIME_KEY |
| 聚合方式 | `el-select`（SUM / AVG / COUNT / MAX / MIN） | 仅 role=MEASURE |
| 计算说明 | `el-input` | 始终可选 |

### 4.6 类型与 i18n

- `src/types/analytics.ts`：`DatasetTemplateField` 接口补 `role?`、`timeGranularity?`、`aggregation?`、`computeHint?`
- `zh-CN.ts` / `en-US.ts`：删除 chat 相关 key，新增语义字段 label

---

## 5. 数据流时序

### 5.1 典型深度分析场景

```
用户: "过去一年出栏量有没有异常波动？找出原因"

Agent Turn 1: analytics_schema(null)            -> 数据集列表
Agent Turn 2: analytics_schema(datasetId=5)     -> 字段 + 语义 + role
Agent Turn 3: analytics_query(月度聚合 SQL)      -> 12 行汇总
Agent Turn 4: analytics_compute(zscore)          -> 异常月份: [3月, 7月]
Agent Turn 5: analytics_compute(mom)             -> 环比变化
Agent Turn 6: analytics_query(钻取 3 月明细)     -> 按维度拆分
Agent Turn 7: analytics_compute(correlation)     -> disease_rate vs output: r=-0.82
Agent Turn 8: analytics_chart(line)              -> ECharts 趋势图
Agent:         输出结论 + 表格 + 图表 + 建议
```

### 5.2 安全链路

```
Agent 生成 SQL
  -> AnalyticsQueryTool -> SqlGuard.safen(sql) -> 只读 + 白名单 + LIMIT
  -> 返回 rows

Agent 传 rows 给 AnalyticsComputeTool
  -> 纯内存计算，不访问 DB，无注入风险
  -> 返回统计结果
```

---

## 6. 文件清单

### 6.1 后端

| 操作 | 路径 |
|---|---|
| 新建 | `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsComputeTool.java` |
| 新建 | `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsComputeToolTest.java` |
| 新建 | `mateclaw-server/src/main/resources/db/migration/h2/V117__analytics_compute_tool.sql` |
| 新建 | `mateclaw-server/src/main/resources/db/migration/mysql/V117__analytics_compute_tool.sql` |
| 修改 | `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateField.java` |
| 修改 | `mateclaw-server/src/main/resources/skills/data-analyst/SKILL.md` |

### 6.2 前端

| 操作 | 路径 |
|---|---|
| 删除 | `mateclaw-ui/src/views/analytics/AnalystChat.vue` |
| 修改 | `mateclaw-ui/src/router/index.ts` |
| 修改 | `mateclaw-ui/src/views/MainLayout.vue`（侧边栏配置） |
| 修改 | `mateclaw-ui/src/views/analytics/DatasetList.vue` |
| 修改 | `mateclaw-ui/src/views/analytics/TemplateEditor.vue` |
| 修改 | `mateclaw-ui/src/types/analytics.ts` |
| 修改 | `mateclaw-ui/src/i18n/locales/zh-CN.ts` |
| 修改 | `mateclaw-ui/src/i18n/locales/en-US.ts` |

### 6.3 不涉及的文件

| 文件 | 原因 |
|---|---|
| `AgentBindingService.java` | 绑定机制不变 |
| `AgentGraphBuilder.java` | 工具过滤逻辑不变 |
| `ToolRegistry.java` | 新 @Component 自动扫描 |
| `BuiltinSkillSeedService.java` | 已有 dependencies.tools 解析能力 |
| `DataAnalystAgentSeedService.java` | Skill 绑定逻辑不变 |
| `SqlGuard.java` | 新 Tool 不走 SQL |
| `AnalyticsSchemaTool.java` | 新字段非空时自动出现在输出中 |
| 其余 4 个 analytics Tool | 不动 |
| V116 迁移 | 已执行，不修改 |

---

## 7. 测试要求

| 测试 | 覆盖范围 |
|---|---|
| `AnalyticsComputeToolTest` | 6 个操作各 1 正例 + 空数据/缺失字段边界 |
| `mvn test` | 整体编译和测试通过 |
| `pnpm build` | 前端类型检查 + 构建通过 |
| 手动烟测 | 侧边栏"数据管理" -> 数据集"分析"跳转 -> Agent Chat 可对话 |

---

## 8. 不做的事

- 不改 Agent/Skill/Tool 核心绑定机制
- 不引入 Python 沙箱或 DuckDB
- 不做 Agent-Dataset 显式绑定（保持 workspace 级隔离）
- 不做导入性能优化（POI SAX 流式解析、异步导入属于独立 Epic）
- 不做上传模式切换（APPEND/REPLACE）
- 不做查询超时或慢查询降级

---

## 9. 未来演进方向（本次不实施）

| 方向 | 说明 |
|---|---|
| 导入性能 | POI SAX 流式解析 + 异步导入 + 进度条 |
| Agent-Dataset 绑定 | 多业务线场景下按 Agent 限定可见数据集 |
| 预置分析模板 | 常见分析模式一键生成（月度经营分析、异常预警等） |
| Python 沙箱 | ARIMA 预测、Granger 因果检验等高级统计 |
| 关键字段自动索引 | DynamicTableService 建表时为标记字段建索引 |
| 查询超时防护 | SqlGuard 增加 query timeout |
