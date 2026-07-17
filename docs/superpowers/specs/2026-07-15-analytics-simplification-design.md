# 设计文档：数据管理简化 —— 删除模板概念，一步上传

**日期**：2026-07-15
**分支**：brand-rename-surveymind
**状态**：待实施

---

## 背景

数据管理功能的本质需求是一句话：**用户上传 csv/excel，存储后由数字员工分析**。

当前实现让用户走三步：建模板 → 建数据集 → 上传文件。前端 2651 行、后端 3090 行。

这不是有人一次性设计过度，而是功能逐层叠加、老路径从未拿掉。2026-05-20 的
`excel-inspect-template-design.md` 已经写明"让业务用户对照 Excel 手填 20+ 字段违反直觉"，
并加了"从 Excel 导入"自动推断字段 —— 方向正确，但它被做成**额外入口**而非**替代流程**
（原文："两个入口并存，手动创建保持原样不动"），且只建模板就停手，不建数据集、不入库。

本设计把那条路走完。

## 现状的三个硬问题

### 1. 同一个文件要上传两次

`InspectTemplateDialog` 上传文件 → 后端推断字段 → `handleCreate()` **只调 `createTemplate`，
把文件丢弃**。用户接着建数据集、再传一次同一个文件才真正入库。

后端 `ExcelInspectService` 已能从表头推断字段名与类型。一步上传所需的零件全部存在，
只是 UI 没接起来。

### 2. 共用模板的数据集会被静默混算（数据正确性 bug）

`physical_table` 挂在**模板**上，共用模板的数据集写进**同一张物理表**，靠 `dataset_id` 列区分。

但 `AnalyticsSchemaTool` 给 AI 的描述是：

```
数据集: 华东销售 (id=77, 物理表=dataset_sales, 行数=1200)
字段清单:
  - amount (DECIMAL) — 金额
```

**字段清单里没有 `dataset_id`，也从不提示按它过滤。** 六个 Agent 工具中只有
`AnalyticsProfileTool` 过滤 `dataset_id`；`ComputeTool` / `ChartTool` / `ExportTool` /
`QueryTool` / `SqlGuard` 对它零感知。

后果：AI 执行 `SELECT SUM(amount) FROM dataset_sales`，把两个数据集加在一起，
**给出错误数字且不报错**。

补充证据：**没有任何 spec 论证过"多数据集共用一个模板"这个能力**。它是数据模型的副产品，
不是设计意图。拆掉它不牺牲任何经过论证的需求。

### 3. 模板暴露了用户不该碰的概念

`TemplateEditor.vue`（483 行）让用户手工填 `role`（维度/度量/时间键）、`timeGranularity`、
`aggregation`、`computeHint`、`semantic`。这是 BI 建模工具的概念。一个"想让 AI 看看销售数据"
的用户不知道也不该知道什么叫聚合方式。

## 不动的部分（明确排除）

- **"schema → 物理表 → AI 写 SQL"这套架构保留。** 数字员工靠 `analytics_schema` → `analytics_query`
  对强类型列跑只读 SELECT（`SqlGuard` 强制 LIMIT 10000、拒绝非 SELECT）。要让 AI 能分析，
  就必须有强类型列定义。砍掉它 AI 只能面对无类型 JSON，分析能力断崖下跌。
- **外部数据源（`DatasourceList.vue`，571 行）本轮不动。** 它是"连数据库"，与"传文件"是两个
  产品。拆分它是独立的产品决策，混进来会让本次重构失焦。

## 设计

### 数据模型：一数据集 = 一 schema = 一物理表

| 现在 | 改为 |
|---|---|
| `mate_dataset_template`（含 `physical_table`、`applied_ddl_hash`、`code`） | **删除** |
| `mate_dataset_template_field`（挂 `template_id`） | `mate_dataset_field`（改挂 `dataset_id`） |
| `mate_dataset`（含 `template_id`） | 收编 `physical_table`、`applied_ddl_hash`；去掉 `template_id` |

**物理表命名**：`dataset_<datasetId>`。天然唯一，不再需要从模板 code slug 化的那套派生逻辑。

**物理表去掉 `dataset_id` 列** —— 一表一数据集后它恒为常量。这不只是省一列：它让
"AI 忘记加 `WHERE dataset_id`"这个 bug **在结构上不可能再发生**，而不是靠指望 AI 每次都记得。
`upload_log_id` 保留，用于按批次溯源。

**迁移**：已确认无存量生产数据。新增一条 migration，`DROP` 旧的模板两表并重建
`mate_dataset` / `mate_dataset_field`，无需数据搬迁。（若将来发现存量，需要按 `dataset_id`
拆表的 Java migration —— 本设计不含。）

### 后端 API

| 方法 | 用途 |
|---|---|
| `POST /analytics/datasets/inspect` | 传文件 → 返回推断字段，不落库（现有 `templates/inspect-excel` 改名迁移） |
| `POST /analytics/datasets` | 传文件 + 名称 + 确认后的字段 → **建表 + 入库，一次完成**，返回 `{ dataset, uploadLog }` |
| `POST /analytics/datasets/{id}/upload` | 向已有数据集追加数据（保留，按现有 schema 匹配表头） |
| `GET /analytics/datasets`、`/{id}/preview`、`/{id}/uploads`、`DELETE /{id}` | 保留 |
| `/analytics/templates/**`（6 个） | **全部删除** |

`POST /analytics/datasets` 一次调用吃掉文件并完成入库 —— 文件只传一次。

### 删除的代码

- 前端：`TemplateList.vue`(268) + `TemplateEditor.vue`(483) + `InspectTemplateDialog.vue`(282) ≈ **1033 行**
- 后端：`DatasetTemplateController`、`DatasetTemplateService`、`DatasetTemplate`、
  `DatasetTemplateField`、两个 repo
- 类型：`DatasetTemplate`、`CreateTemplateRequest`、`UpdateTemplateFieldRequest` 等

### UI 流程

数据管理页从"两张引导卡 + 三个平铺 tab"改为：

- **主体是数据集列表。** 空状态即一个大拖拽区（"拖入 Excel/CSV，或点击选择"），
  常驻「上传数据」主按钮。引导卡片删除 —— 它们在给三条路径打广告，而用户只需要一条。
- **上传弹窗两步**：
  1. 拖入文件 → 自动调 `inspect`
  2. 确认页：数据集名称（默认取文件名去扩展名）+ 字段表（可改名/改类型/删列）→「创建并分析」
  3. 建完直接跳 chat 并带上 `datasetId`
- **行内操作精简**为：分析 / 预览 / 追加数据 / 删除。「上传记录」收进预览页的一个 tab，
  不再占独立路由。
- 保留「外部数据源」tab，不改动。

### 同批修复的 bug

#### CSV 支持（新功能，需求明确要求）

后端 grep `csv` **零命中**。`ExcelParseService` 用 `XSSFWorkbook`，只吃 `.xlsx`（`.xls` 也不支持）。
但引导卡片写着"手里有 Excel/CSV？"，上传框 `accept=".xlsx"`。**用户拿 CSV 来会被静默挡在
文件选择器外。**

需要新增 CSV 解析，`inspect` 与 `ingest` 两条路径都要走通。类型推断复用
`ExcelInspectService` 现有优先级规则（DATE → INT → DECIMAL → STRING）。

#### workspace 硬编码

`AnalyticsSchemaTool` 写死 `workspace_id = 1L`（源码自标 `PHASE-1 limitation`）。
前端 `DatasetList.workspaceId()` 拿不到时也兜底 `'1'`。**在 2 号工作区上传的数据集，
数字员工列不出来。**

修复不需要新基建：`ChatOrigin` 已带 `workspaceId`，且已通过 Spring AI `ToolContext` 送达工具层。
照抄 `WorkflowAuthoringTool` 的既有模式：

```java
@Tool(description = "...")
public String analyticsSchema(
        @ToolParam(...) Long datasetId,
        // ChatOrigin-scoped workspace lookup; never trust the LLM to pass workspaceId.
        @Nullable ToolContext ctx) {
    Long workspaceId = ctx == null ? null : ChatOrigin.from(ctx).workspaceId();
    if (workspaceId == null || workspaceId <= 0) {
        return "无法确定当前 workspace，工具放弃执行。";
    }
    ...
}
```

前端 `workspaceId()` 的 `'1'` 兜底一并去掉 —— 无工作区时应报错，而不是静默写进 1 号。

#### 跨工作区隔离

`SqlGuard` 只限制表名前缀 `dataset_`，不校验归属。A 工作区可让 AI 查 B 工作区的表。

工具里的 SQL 有两种来源，校验落位不同：

| 工具 | SQL 来源 | 现状 | 校验落位 |
|---|---|---|---|
| `AnalyticsQueryTool`、`AnalyticsExportTool` | **LLM 编写** | 走 `SqlGuard` | 在 `SqlGuard` 内校验 |
| `AnalyticsProfileTool`、`AnalyticsSchemaTool` | 工具按 `datasetId` 参数化拼接 | 不走 `SqlGuard`（SQL 非 LLM 编写，安全） | 各自校验 `datasetId` 归属 |
| `AnalyticsChartTool`、`AnalyticsComputeTool` | 不访问数据库 | — | 无需改动 |

新命名 `dataset_<datasetId>` 使表名自带身份，`SqlGuard` 的校验才成为可能
（今天的 `dataset_<模板code>` 不携带该信息）：提取 SQL 引用的 `dataset_<id>` 表名，
校验每个 id 所属 workspace 等于 `ChatOrigin` 的 workspaceId，否则拒绝。

`SqlGuard.safen(String)` 是静态方法，需扩展签名以接收 workspaceId 与一个
dataset→workspace 解析器（`SqlGuard.safen(sql, workspaceId, resolver)`）。

`ProfileTool` / `SchemaTool` 则在入口处校验传入的 `datasetId` 属于当前 workspace ——
否则 AI 传任意 datasetId 即可读到别的工作区。

#### agentId 硬编码

`DatasetList.vue:203` 写死 `agentId: '1000000020'`，与后端
`DataAnalystAgentSeedService.DATA_ANALYST_AGENT_ID` 各存一份。改为后端下发。

## 错误处理

- **表头无法推断**（空文件 / 无表头行）→ `inspect` 返回明确错误，前端在弹窗内提示，不进入确认页。
- **CSV 编码**（GBK 等非 UTF-8）→ 需探测；探测失败时提示用户另存为 UTF-8，而非静默乱码入库。
- **追加数据表头不匹配** → 沿用 `ExcelHeaderMatcher` 现有行为：必填字段缺列时抛错并列出缺失
  字段码；可空字段无匹配则跳过。
- **部分行入库失败** → 沿用现有 `IngestResult` 的 `rowsInserted` / `rowsRejected` 语义。
- **无 workspace 上下文** → 工具与接口均拒绝执行并明确报错，不兜底到 1 号工作区。

## 测试

现状：`mateclaw-ui` **没有组件测试基建**（仅一个 `node:test` 跑纯函数的用例，
无 vitest、package.json 无 `test` 脚本）。后端有 JUnit（`DatasetServiceTest`、
`DatasetControllerTest` 等）。

本设计的测试策略：

- **后端**：沿用 JUnit。重点覆盖 `POST /analytics/datasets` 的建表+入库事务性、
  CSV 解析与类型推断、`SqlGuard` 跨 workspace 拒绝、无 workspace 上下文时工具拒绝执行。
  删除模板相关的既有测试。
- **前端**：不在本轮引入组件测试栈（那是独立的一件事）。UI 靠
  `mateclaw-ui/.claude/skills/verify` 的 Playwright 流程实跑验证：
  拖入文件 → 确认字段 → 创建 → 跳转 chat。

## 分期

本设计超出单个实现计划的容量，拆成三期。每期结束时系统可用。

**第一期：模型合并 + 一步上传**（原子，不可拆半）
模型合并、API 重构、UI 一步上传、删除模板代码。模型/API/UI 三者互相咬合 ——
删了模板表，模板页就编译不过；改了 API，UI 必须同步。只能一次做完。

**第二期：CSV 支持**
纯增量，不动已有结构。独立于一期，但排在后面是因为一期会重写 `inspect` 与 ingest
的入口，先做 CSV 会白改两遍。

**第三期：workspace 隔离**
`SchemaTool` 的 `ToolContext` 改造、`SqlGuard` 归属校验、`ProfileTool` / `SchemaTool`
的 datasetId 校验、前端 `'1'` 兜底移除、agentId 下发。

排在最后的理由：`SqlGuard` 的归属校验**依赖一期的 `dataset_<datasetId>` 重命名**
（旧命名不携带身份信息，无法校验）；且一期会重写六个工具的取 schema 路径，
提前改会返工。当前无存量数据，该 bug 尚未伤及真实用户，可以承受这个顺序。

若判断 workspace 隔离需优先（例如即将有真实多工作区用户），`SchemaTool` 的
`ToolContext` 改造可独立提前，但 `SqlGuard` 校验仍须等一期。

## 影响面

- 破坏性变更：`/analytics/templates/**` 全部下线。已确认无存量数据与外部消费方。
- `DataAnalystAgentSeedService` 种子 agent 的工具描述可能提及模板，需同步核对。
- V115 / V117 两个 migration 涉及模板表，新 migration 需在其后 DROP。
- **去掉物理表 `dataset_id` 列会波及 `AnalyticsProfileTool`** —— 它现在按
  `WHERE dataset_id = ?` 过滤（5 处引用），一表一数据集后该子句须移除。
- 六个 Agent 工具均需从模板取 schema 改为从 dataset 取，`SchemaTool` 不再 join 模板表。
