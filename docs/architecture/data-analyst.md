# 数据分析专家（Data Analyst）架构与路线图

> **本文件是数据分析专家功能的 SSOT（单一事实来源）**。任何新会话开始这条线的工作前，先 Read 本文件了解上下文、当前进度、设计契约。每个阶段完成后必须更新「进度跟踪」段。
>
> 关联分支：`feat/data-analyst-expert`（从 `baseline/v1.3.0` 切出）
> Phase 1 实施计划：`docs/superpowers/plans/2026-05-18-data-analyst-phase-1.md`

---

## 1. 目标

为青文 Claws 客户提供"内置数据分析"能力：用户上传 Excel（畜牧业季报、住户调查问卷等），系统自动落本地表，通过数字员工"数据分析专家"对话式查询、画像、出图、校验、导出。后续支持对接国家统计云自动取数。

**核心需求来源**：
- `/Users/justbin/project/2BPro/QingClaws/reDocs/raw-req/统计调查agent 功能需求.docx`
- `/Users/justbin/project/2BPro/QingClaws/reDocs/raw-req/居民收支调查本地ai核心功能需求.docx`
- 畜牧业 xls 样表 + 居民收支调查问卷 A/B/C/E/G/M

**数据规模**（客户层面）：
| 数据 | 量级 | 处理策略 |
|---|---|---|
| 畜牧企业季报 | ~1 万条/季度 × 20-40 列指标 | MySQL 直查 |
| 住户问卷各类 | 月 1 万条 × 100 列指标，年几十万 | MySQL + 索引 + 物化汇总 |
| 账页（家庭日记账） | 3000 万/年（**MVP 范围不含**） | 暂不内置 |

---

## 2. 关键架构决策

### D1 — 模板登记制（确认采纳）
Excel 不是任意结构。管理员先在系统注册"数据集模板"（声明列名、code、类型、单位、口径），用户上传该模板对应的 xlsx 时按 schema 校验落表。

**取舍**：相比"任意 Excel + 字段映射 UI"工作量降一半；相比"硬编码 schema"灵活很多。
**约束**：模板登记后已上传过数据则禁止破坏性变更（只允许追加字段，不允许改类型/删字段）。

### D2 — 同模板上传追加到同一物理表（确认采纳）
每个 Template 对应一张动态生成的物理表 `dataset_<template_code>`，每行带分区列 `province / city / county / period`。多次上传 = 多次写入这张表，按分区键区分。"Dataset"是逻辑视图（template + 分区过滤）。

**好处**：跨时间对比、增量更新、单一数据源；用户问"2024Q1 vs 2025Q1"不需要跨表 JOIN。

### D3 — 独立模块入口（确认采纳）
左侧导航新建一级菜单"数据分析"（路径 `/analytics`）。当前产品没有数据分析模块，无需嵌入既有业务页。后续若有其他业务页需要"分析此数据"快捷入口，再在 Phase 4+ 加。

### D4 — 数据库底座
MySQL 8（生产）+ H2（dev/test）。**不引入 DuckDB / ClickHouse**——客户层面无 3000 万级数据，关系库够用。

### D5 — Agent + Skill + Tool 三层结构
- **Agent**（`mate_agent` 行，固定 ID `1000000020`）—— 数字员工"数据分析专家"，Plan-Execute 模式
- **Skill**（`skills/data-analyst/SKILL.md`）—— 提供工作方法论（如何拆解分析任务、报告模板、口径术语表）
- **Tool**（`vip.mate.tool.analytics.*`，@Tool 注解）—— 5 个原子工具

### D6 — NL→SQL 安全模型
`analytics.query` 工具接受 LLM 生成的 SQL，但执行前强制：
- 只允许 SELECT（解析 AST 检查）
- 表白名单（只能查 `dataset_*` 和 `mate_dataset*` 元数据表）
- 列白名单（按当前 Agent 用户的 Dataset 权限）
- 自动 `LIMIT 10000`、超时 30s
- 写操作（INSERT/UPDATE/DELETE/DDL）一律拒绝

### D7 — Web MVC，非 WebFlux
项目已禁用 WebFlux（见 root CLAUDE.md），本模块全部走 Servlet + 虚拟线程。文件上传走 `MultipartFile`。

---

## 3. 4 阶段路线图

```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   Phase 1 — MVP     │───▶│  Phase 2 — Validate │───▶│   Phase 3 — Sync    │───▶│   Phase 4+ — Adv    │
│  Excel→Dataset→Agent│    │   规则引擎 + 校验    │    │     统计云对接       │    │  OCR / 台账 / 价格   │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘    └─────────────────────┘
```

### Phase 1 — MVP 闭环（2-3 周）
**目标**：用户能登录后台 → 注册模板 → 上传一个畜牧 xls → 在"数据分析"页面对话框问"按市汇总期末存栏数"→ 得到表格 + 柱状图 + 可导出 xlsx。

**包含**：
- 数据集模板管理（注册/列出/字段配置）
- Excel 上传 + 解析 + 校验 + 落表
- 动态物理表 DDL 自动生成与维护
- 数据集管理（列出/预览/删除）
- 5 个分析工具：`analytics.schema` / `query` / `profile` / `chart` / `export`
- 数据分析专家 Agent + Skill 包
- 前端三页：模板管理 / 数据集管理 / 数据分析对话
- 左侧导航新增「数据分析」菜单

**不包含**：校验规则引擎、统计云、OCR、台账学习、价格库、跨部门交叉验证、记账质量评估、自动异常发现。

**完成判据**：用 `reDocs/raw-req/畜牧业调查数据格式.xls` 中"家禽"sheet 做端到端 smoke，全流程能跑通且生成可用报告。

### Phase 2 — 数据校验（2 周）
**目标**：用户可针对 Dataset 定义校验规则、跑批校验、查看异常清单。

**包含**：
- 规则 DSL（YAML 格式声明字段约束、跨字段逻辑、趋势异常）
- 规则引擎（执行器）
- `analytics.validate` 工具
- 异常清单 UI 页（按 Dataset 列出、按规则筛选、导出）
- 内置规则模板（畜牧/居民收支两套）

**完成判据**：能针对一份畜牧数据自动检测出"期末存栏 < 自宰数量"等明显矛盾。

### Phase 3 — 统计云对接（1-2 周）
**目标**：定时从国家统计云拉取问卷/账页数据落本地表，Dataset 概念扩展为"远端来源"。

**包含**：
- 统计云 API 适配器（OAuth + 拉取 + 分页）
- Dataset 增加 `source_type = UPLOAD | STATSCLOUD` 字段
- 同步 Workflow（用 1.3.0 已有的 workflow runtime）
- Cron Trigger（每日/每季度）
- 同步任务历史 + 失败重试

**完成判据**：能通过 cron 自动从统计云拉一类问卷并 upsert 到本地表。

### Phase 4+ — 高级能力（按客户优先级单独评估）
| 子项 | 估算 | 依赖 |
|---|---|---|
| 凭证图片识别（养殖场水电饲料） | 2 周 | OCR 服务（如已集成则减半） |
| 个人收入台账学习（异常发放识别） | 2-3 周 | Phase 2 校验规则引擎 |
| 外网价格区间库（爬虫） | 2 周 | Trigger + 外网抓取工具 |
| 社保/医保/惠农部门交叉验证 | 1-2 周 | 各部门 API 对接 |
| 记账质量评估（多维度评分） | 1 周 | Phase 2 完成 |
| 自动学习异常发现（ML） | 单独评估 | Python 服务或 ONNX 内嵌 |

每个子项独立评估，不阻塞前 3 阶段。

---

## 4. 数据模型（Phase 1）

### 4.1 元数据表

```
mate_dataset_template       数据集模板（如"畜牧家禽季报"）
  id                BIGINT PK
  workspace_id      BIGINT NOT NULL
  code              VARCHAR(64)  UK  -- 唯一标识，落物理表名后缀 dataset_<code>
  name              VARCHAR(128)
  description       VARCHAR(1024)
  category          VARCHAR(32)      -- 'LIVESTOCK' | 'HOUSEHOLD' | 'CUSTOM'
  partition_keys    VARCHAR(256)     -- JSON: ["province","city","county","period"]
  enabled           TINYINT(1)
  create_time/update_time TIMESTAMP
  creator/updater   BIGINT

mate_dataset_template_field 模板字段（每行 = 一列指标）
  id                BIGINT PK
  template_id       BIGINT FK -> mate_dataset_template.id
  field_code        VARCHAR(64)     -- 物理列名（snake_case）
  field_name        VARCHAR(256)    -- 中文显示名（如"期末存栏（只）-肉鸡（只）"）
  field_type        VARCHAR(16)     -- 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'
  field_unit        VARCHAR(32)     -- 单位（如"只"、"元"），可空
  semantic          VARCHAR(512)    -- 业务口径说明，注入 LLM 提示词
  is_partition_key  TINYINT(1)      -- 是否分区列
  is_nullable       TINYINT(1)
  ordinal           INT             -- 列顺序（决定 Excel 列对齐）
  excel_header      VARCHAR(256)    -- Excel 表头原文（用于匹配上传文件）
  UNIQUE (template_id, field_code)

mate_dataset              数据集（逻辑视图，绑定 template + 一次或多次上传）
  id                BIGINT PK
  workspace_id      BIGINT
  template_id       BIGINT FK
  name              VARCHAR(128)    -- 如"郑州市畜牧家禽 2025Q1"
  description       VARCHAR(1024)
  row_count         INT             -- 缓存的行数
  last_upload_at    TIMESTAMP
  create_time/update_time/creator/updater

mate_dataset_upload_log   每次上传一条
  id                BIGINT PK
  dataset_id        BIGINT FK -> mate_dataset.id
  file_name         VARCHAR(256)
  file_size         BIGINT
  rows_received     INT
  rows_inserted     INT
  rows_rejected     INT
  status            VARCHAR(16)     -- 'SUCCESS' | 'PARTIAL' | 'FAILED'
  error_summary     VARCHAR(2048)
  uploader          BIGINT
  upload_time       TIMESTAMP
```

### 4.2 动态业务表

每个 Template 落一张物理表，命名 `dataset_<template.code>`。例如 `dataset_livestock_poultry_quarterly`。

DDL 由 `DynamicTableService` 根据 `mate_dataset_template_field` 生成：

```sql
CREATE TABLE dataset_livestock_poultry_quarterly (
    id              BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
    dataset_id      BIGINT       NOT NULL,
    upload_log_id   BIGINT       NOT NULL,
    -- 分区列（来自 partition_keys）
    province        VARCHAR(64),
    city            VARCHAR(64),
    county          VARCHAR(64),
    period          VARCHAR(16),    -- 'YYYY-Q1' / 'YYYY-MM' / 'YYYY'
    -- 业务列（来自 template_field 的 field_code）
    farm_code       VARCHAR(64),
    is_contract     TINYINT(1),
    end_stock_total DECIMAL(18,2),
    end_stock_meat_total DECIMAL(18,2),
    ...
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dataset (dataset_id),
    INDEX idx_partition (province, city, county, period)
);
```

> **重要约束**：动态表不通过 Flyway 管理（Flyway 不知道字段是什么）。由 `DynamicTableService` 在模板首次启用 / 字段追加时执行 DDL，并写一条记录到 `mate_dataset_template`（带 `applied_ddl_hash`，防止重复执行）。回滚由人工 SQL。

---

## 5. 模块/包划分（Phase 1）

### 后端 — `vip.mate.analytics`

```
vip.mate.analytics/
├── template/
│   ├── DatasetTemplate.java               (entity)
│   ├── DatasetTemplateField.java          (entity)
│   ├── DatasetTemplateRepository.java     (MyBatis-Plus mapper)
│   ├── DatasetTemplateFieldRepository.java
│   └── DatasetTemplateService.java        (CRUD + 字段增删检查)
├── dataset/
│   ├── Dataset.java
│   ├── DatasetRepository.java
│   ├── DatasetService.java                (列表/详情/删除)
│   ├── DatasetUploadLog.java
│   └── DatasetUploadLogRepository.java
├── storage/
│   ├── DynamicTableService.java           (DDL 生成/执行/字段追加)
│   ├── DynamicTableNamingService.java     (template.code → 物理表名)
│   └── PhysicalColumnType.java            (FieldType → SQL 类型映射)
├── upload/
│   ├── ExcelParseService.java             (POI 读 xlsx，按 template 对齐)
│   ├── ExcelIngestService.java            (校验 + 批量 INSERT)
│   └── ExcelHeaderMatcher.java            (匹配 excel_header → field_code)
├── tool/
│   ├── AnalyticsSchemaTool.java           (@Tool analytics_schema)
│   ├── AnalyticsQueryTool.java            (@Tool analytics_query, SQL guard)
│   ├── AnalyticsProfileTool.java          (@Tool analytics_profile)
│   ├── AnalyticsChartTool.java            (@Tool analytics_chart)
│   ├── AnalyticsExportTool.java           (@Tool analytics_export)
│   ├── guard/
│   │   ├── SqlGuard.java                  (JSQLParser AST 校验)
│   │   └── SqlGuardException.java
│   └── dto/
│       ├── QueryRequest.java
│       ├── QueryResult.java
│       ├── ProfileResult.java
│       └── ChartSpec.java
├── controller/
│   ├── DatasetTemplateController.java     (REST /api/analytics/templates)
│   ├── DatasetController.java             (REST /api/analytics/datasets)
│   └── DatasetUploadController.java       (REST /api/analytics/datasets/{id}/upload)
└── config/
    └── AnalyticsConfig.java               (限流/超时/最大行数等配置)
```

### 前端 — `mateclaw-ui/src/views/Analytics/`

```
views/Analytics/
├── index.vue                  (路由入口 = TemplateList)
├── TemplateList.vue           (模板列表 + 注册/编辑/启停)
├── TemplateEditor.vue         (字段配置：名称/code/类型/单位/语义)
├── DatasetList.vue            (数据集列表 + 上传 + 删除)
├── DatasetUploadDialog.vue    (上传弹窗：选模板 → 选文件 → 预校验 → 提交)
├── DatasetPreview.vue         (数据集详情：前 100 行预览 + 上传历史)
└── AnalystChat.vue            (右侧抽屉：复用 ChatPanel 组件，绑定 agent_id=1000000020)
```

路由（router/index.ts 新增）：
```
{
  path: 'analytics',
  name: 'Analytics',
  redirect: '/analytics/datasets',
  children: [
    { path: 'datasets', component: () => import('@/views/Analytics/DatasetList.vue') },
    { path: 'datasets/:id', component: () => import('@/views/Analytics/DatasetPreview.vue') },
    { path: 'templates', component: () => import('@/views/Analytics/TemplateList.vue') },
    { path: 'templates/:id', component: () => import('@/views/Analytics/TemplateEditor.vue'), meta: { requireAdmin: true } },
    { path: 'chat', component: () => import('@/views/Analytics/AnalystChat.vue') },
  ],
}
```

### Skill 包 — `mateclaw-server/src/main/resources/skills/data-analyst/`

```
skills/data-analyst/
├── SKILL.md            (能力声明 + 工作方法论 + 报告模板 + 口径术语表)
└── LESSONS.md          (自演进：审核员纠正过的规则沉淀)
```

---

## 6. Agent + Tool 契约（Phase 1）

### Agent: 数据分析专家
- **固定 ID**: `1000000020`（保留段：1000000020 ~ 1000000029 给本模块未来扩展）
- **agent_type**: `react`（Plan-Execute 在系统提示词里引导，不引入新类型）
- **绑定工具**: `analytics_schema` / `analytics_query` / `analytics_profile` / `analytics_chart` / `analytics_export`
- **绑定技能**: `data-analyst`
- **系统提示词要点**：
  - 角色定位：数据分析专家
  - 必读：通过 `analytics_schema` 先了解可用数据集和字段语义
  - SQL 输出规范：必带 LIMIT、按业务列名而非物理列名思考
  - 报告模板：结论先行 + 数据支撑 + 图表 + 建议
  - 不确定字段口径时主动问用户

### Tool 接口

| Tool | 输入 | 输出 | 副作用 |
|---|---|---|---|
| `analytics_schema` | `dataset_id?: long` | 数据集列表 + 字段名/code/类型/单位/语义 | 无 |
| `analytics_query` | `sql: string, dataset_id: long` | `{columns, rows[]}` JSON | 只读 |
| `analytics_profile` | `dataset_id, field_codes[]` | 每列分布/min/max/missing% | 只读 |
| `analytics_chart` | `chart_type, data, x, y, series?` | ECharts option JSON | 无 |
| `analytics_export` | `dataset_id, sql?, file_name` | 下载链接 | 生成 xlsx 临时文件 |

---

## 7. 安全（Phase 1）

- 所有 `/api/analytics/**` 走现有 Spring Security JWT；动态表的行级权限交给 `workspace_id` 隔离（与 Dataset 同 workspace 才可见）。
- `analytics_query` 的 SQL Guard：
  - 拒绝多语句（`;` 后还有内容）
  - 仅 SELECT（JSQLParser 解析 `Statement` 必须是 `Select`）
  - FROM/JOIN 表名必须以 `dataset_` 开头或在元数据白名单
  - 无 `INFORMATION_SCHEMA` / `mysql.*` 等系统表
  - 自动注入 `LIMIT 10000`（如果原 SQL 没 LIMIT 或 LIMIT 更大）
  - 30s 超时（`Statement.setQueryTimeout`）
- 文件上传：扩展名白名单 `.xlsx`（不收 `.xls` —— Excel 97 二进制太老，POI HSSF 漏洞历史多）；大小上限 50MB；上传后存到 `${app.upload-root}/analytics/` 不直接放 static。
- Tool Guard：`analytics_query` 标记 `requiresApproval=false`（只读），`analytics_export` 标记 `requiresApproval=true`（生成文件，可能含敏感数据外发风险）。

---

## 8. 测试策略

- 单元测试：所有 Service / Tool / SqlGuard / ExcelHeaderMatcher 必须有测试，覆盖率 ≥80%。
- 集成测试：使用 H2，覆盖完整上传→落表→查询路径。
- 端到端 smoke：用 `reDocs/raw-req/畜牧业调查数据格式.xls` 的"家禽" sheet 真实跑通，作为 Phase 1 完成判据。
- 不引入新测试框架，沿用项目现有 JUnit 5 + Mockito。
- 前端测试沿用 `node --test`（见 `mateclaw-ui/CLAUDE.md`）。

---

## 9. 进度跟踪

> **每完成一个阶段，更新此段**。新会话从这里读"做到哪了"。

| 阶段 | 状态 | 开始日期 | 完成日期 | 提交范围 | 备注 |
|---|---|---|---|---|---|
| Phase 1 — MVP 闭环 | ✅ 完成 | 2026-05-18 | 2026-05-18 | feat/data-analyst-expert (T1–T25) | E2E smoke 通过：畜牧家禽 sheet 100 行全量写入，preview/upload-history/API 全部正常。Plan: `docs/superpowers/plans/2026-05-18-data-analyst-phase-1.md` |
| Phase 2 — 数据校验 | ⏸ 待 Phase 1 完成 | - | - | - | - |
| Phase 3 — 统计云对接 | ⏸ 待 Phase 2 完成 | - | - | - | 需要客户提供统计云 API 文档 |
| Phase 4+ — 高级能力 | ⏸ 单独评估 | - | - | - | - |

**状态图例**：⏸ 待启动 / 🚧 进行中 / ✅ 完成 / ❌ 阻塞

---

## 10. 待澄清事项（持续维护）

- [ ] **统计云 API 文档**：Phase 3 启动前必须拿到对接文档（认证方式、接口列表、分页约定、数据格式）
- [ ] **凭证识别 OCR 服务**：Phase 4 若做，需要确认是用本地模型（PaddleOCR）还是云服务（阿里云 OCR/腾讯 OCR）
- [ ] **行级权限**：是否需要按"地市"隔离不同调查员的数据可见性？目前 Phase 1 只做 workspace 级隔离
- [ ] **审计要求**：是否需要记录每次 `analytics_query` 的 SQL + 调用人 + 结果摘要（合规审计）？

---

*最后更新: 2026-05-18 · feat/data-analyst-expert*
