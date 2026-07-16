# 数据管理简化 · 第一期实现计划（模型合并 + 一步上传）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除「模板」这个用户概念，把建模板 → 建数据集 → 上传文件三步合并为一次上传，且文件只传一次。

**Architecture:** 一数据集 = 一 schema = 一物理表。`mate_dataset_template` / `mate_dataset_template_field` 两表删除，字段定义改挂 `mate_dataset`，物理表命名为 `dataset_<datasetId>`，并去掉物理表的 `dataset_id` 列。新增 `POST /analytics/datasets` 一次完成建表+入库。

**Tech Stack:** Spring Boot 3 + MyBatis-Plus + Flyway（H2/MySQL 双方言）+ Apache POI；前端 Vue 3 + Element Plus + Vite。

## Global Constraints

- **测试命令必须在 `mateclaw-server/` 目录内运行** —— 仓库**没有根 pom.xml**，`mateclaw-server` 是独立 Maven 工程（parent 为 spring-boot-starter-parent）。`mvn -pl mateclaw-server` 会报 "Could not find the selected project in the reactor"。
- 单测命令：`cd mateclaw-server && mvn test -Dtest=<TestClassName>`（已验证可用）。
- 后端测试风格：`@SpringBootTest(classes = vip.mate.MateClawApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)` + `@ActiveProfiles("dev")` + `@TestPropertySource` 指定独立 H2 内存库 + AssertJ。集成测试约 50s/个，属正常。
- **Flyway 迁移必须同时写 `db/migration/h2/` 和 `db/migration/mysql/` 两份**，版本号一致。当前最新为 V122，本期用 **V123**。
- 物理表名必须匹配 `ExcelIngestService.SAFE_TABLE_NAME` = `^dataset_[a-z0-9_]+$`。`dataset_<Snowflake数字id>` 满足。
- ID 为应用生成（MyBatis-Plus `IdType.ASSIGN_ID`），**建表不要写 AUTO_INCREMENT**。
- **前端无组件测试基建**（无 vitest，package.json 无 `test` 脚本）。前端改动用 `mateclaw-ui/.claude/skills/verify` 的 Playwright 流程实跑验证，不写组件单测。
- `mateclaw-ui` 的 `npm run lint` 仓库级损坏（ESLint 9 缺 `eslint.config.js`），**不要试图修**。类型检查用 `cd mateclaw-ui && node --max-old-space-size=6144 ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit`；`src/views/layout/TrialBanner.vue:77` 有**既存**报错，与本期无关，忽略即可。

---

## 死列清理（本计划相对 spec 的细化）

调查发现以下列**全代码库无任何读取方**，新表不再保留：

| 列 | 出处 | 证据 |
|---|---|---|
| `role`、`time_granularity`、`aggregation`、`compute_hint` | V117 加在 template_field | `grep -rn "getRole()\|getAggregation()\|getTimeGranularity()\|getComputeHint()"` → 0 命中。由 `TemplateEditor.vue` 写入，无人读。 |
| `is_partition_key`、`partition_keys` | V115 | 仅出现在 javadoc 注释里，无逻辑读取。 |
| `code`、`category`、`enabled` | V115 template | `code` 仅用于派生 `physical_table`；改用 `dataset_<id>` 后无用。 |

**必须保留**：`excel_header`（`ExcelHeaderMatcher` 靠它匹配上传表头）、`semantic` 与 `field_unit`（`AnalyticsSchemaTool` 注入给 LLM）。

## 已核对，无需任务

spec 的「影响面」列出「`DataAnalystAgentSeedService` 种子 agent 的工具描述可能提及模板，需同步核对」。
已核对：`grep -c -i "模板\|template" installer/DataAnalystAgentSeedService.java` → **0 命中**，无需改动。

## File Structure

**新建**
- `mateclaw-server/src/main/resources/db/migration/{h2,mysql}/V123__collapse_dataset_template.sql` — 删旧表建新表
- `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetField.java` — 字段实体（挂 datasetId）
- `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetFieldRepository.java`
- `mateclaw-server/src/test/java/vip/mate/analytics/dataset/DatasetCreateFromFileTest.java` — 一步上传集成测试

**修改**
- `dataset/Dataset.java` — 加 `physicalTable` / `appliedDdlHash`，去 `templateId`
- `dataset/DatasetService.java` — 收编建库建表逻辑，去模板依赖
- `storage/DynamicTableService.java` — 签名改 `(Dataset, List<DatasetField>)`，建表不再写 `dataset_id` 列
- `upload/ExcelIngestService.java` — 签名去 `DatasetTemplate`，INSERT 去 `dataset_id`
- `upload/ExcelParseService.java`、`upload/ExcelHeaderMatcher.java` — 类型 `DatasetTemplateField` → `DatasetField`
- `controller/DatasetController.java` — 新增一步上传端点
- `controller/DatasetUploadController.java` — 追加上传改用 dataset 自身 schema
- `tool/AnalyticsSchemaTool.java`、`tool/AnalyticsProfileTool.java` — 不再 join 模板；Profile 去掉 `WHERE dataset_id = ?`
- `mateclaw-ui/src/api/analytics.ts`、`src/types/analytics.ts`
- `mateclaw-ui/src/views/analytics/{index.vue,DatasetList.vue,UploadDialog.vue}`
- `mateclaw-ui/src/router/index.ts` — 删模板路由
- `mateclaw-ui/src/i18n/locales/{zh-CN,en-US}.ts`

**删除**
- 后端：`template/` 整个包（`DatasetTemplate`、`DatasetTemplateField`、两个 Repository、`DatasetTemplateService`）、`controller/DatasetTemplateController.java`
- 后端测试：`template/DatasetTemplateServiceTest.java`、`controller/DatasetTemplateControllerTest.java`
- 前端：`views/analytics/{TemplateList.vue,TemplateEditor.vue,InspectTemplateDialog.vue}`

---

## Task 1: 数据模型迁移 + DatasetField 实体

**Files:**
- Create: `mateclaw-server/src/main/resources/db/migration/mysql/V123__collapse_dataset_template.sql`
- Create: `mateclaw-server/src/main/resources/db/migration/h2/V123__collapse_dataset_template.sql`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetField.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetFieldRepository.java`
- Modify: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/Dataset.java`
- Test: `mateclaw-server/src/test/java/vip/mate/analytics/dataset/DatasetFieldRepositoryTest.java`

**Interfaces:**
- Produces: `DatasetField` 实体（getter：`getId/getDatasetId/getFieldCode/getFieldName/getFieldType/getFieldUnit/getSemantic/getIsNullable/getOrdinal/getExcelHeader`）；`DatasetFieldRepository extends BaseMapper<DatasetField>`；`Dataset.getPhysicalTable()` / `Dataset.getAppliedDdlHash()`。
- Consumes: 无（首个任务）。

- [ ] **Step 1: 写迁移（MySQL 方言）**

`mateclaw-server/src/main/resources/db/migration/mysql/V123__collapse_dataset_template.sql`：

```sql
-- V123: Collapse dataset template into dataset — one dataset = one schema = one physical table.
--
-- Rationale: physical_table lived on the template, so datasets sharing a template wrote
-- into ONE physical table separated only by a dataset_id column. The schema tool never
-- told the agent that column existed and 4 of 6 tools ignored it, so cross-dataset
-- queries silently mixed rows. One table per dataset makes that bug unrepresentable.
--
-- No production data exists (confirmed 2026-07-15), so this drops and recreates.

DROP TABLE IF EXISTS mate_dataset_template_field;
DROP TABLE IF EXISTS mate_dataset_template;
DROP TABLE IF EXISTS mate_dataset;

-- Dataset — now owns its schema and physical table directly.
CREATE TABLE mate_dataset (
    id               BIGINT        NOT NULL PRIMARY KEY,
    workspace_id     BIGINT        NOT NULL,
    name             VARCHAR(128)  NOT NULL,
    description      VARCHAR(1024),
    physical_table   VARCHAR(96)   NOT NULL,   -- "dataset_" + id
    applied_ddl_hash VARCHAR(64),              -- SHA-256 of field list; tracks DDL sync state
    row_count        INT           NOT NULL DEFAULT 0,
    last_upload_at   DATETIME(3),
    creator          BIGINT,
    updater          BIGINT,
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          INT           NOT NULL DEFAULT 0,
    KEY idx_dataset_workspace (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Dataset instances; each owns one physical table and its typed field definitions.';

-- Field definitions — one row per typed column, owned by a dataset.
CREATE TABLE mate_dataset_field (
    id           BIGINT        NOT NULL PRIMARY KEY,
    dataset_id   BIGINT        NOT NULL,
    field_code   VARCHAR(64)   NOT NULL,   -- snake_case physical column name
    field_name   VARCHAR(256)  NOT NULL,   -- display name shown in UI / LLM prompts
    field_type   VARCHAR(16)   NOT NULL,   -- 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'
    field_unit   VARCHAR(32),              -- read by AnalyticsSchemaTool
    semantic     VARCHAR(512),             -- read by AnalyticsSchemaTool, injected into prompts
    is_nullable  TINYINT(1)    NOT NULL DEFAULT 1,
    ordinal      INT           NOT NULL,
    excel_header VARCHAR(512)  NOT NULL,   -- read by ExcelHeaderMatcher
    create_time  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dataset_field_code (dataset_id, field_code),
    KEY idx_dataset_field_dataset (dataset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Typed field definitions per dataset; drives physical DDL and Excel header matching.';
```

- [ ] **Step 2: 写迁移（H2 方言）**

`mateclaw-server/src/main/resources/db/migration/h2/V123__collapse_dataset_template.sql`：内容同上，但去掉 MySQL 专有语法。H2 版本不写 `ENGINE=` / `COMMENT=` / `ON UPDATE CURRENT_TIMESTAMP(3)`，且 `KEY` 索引须改为独立 `CREATE INDEX` 语句：

```sql
-- V123: Collapse dataset template into dataset — H2 dialect.
-- See the mysql/ counterpart for full rationale.

DROP TABLE IF EXISTS mate_dataset_template_field;
DROP TABLE IF EXISTS mate_dataset_template;
DROP TABLE IF EXISTS mate_dataset;

CREATE TABLE mate_dataset (
    id               BIGINT        NOT NULL PRIMARY KEY,
    workspace_id     BIGINT        NOT NULL,
    name             VARCHAR(128)  NOT NULL,
    description      VARCHAR(1024),
    physical_table   VARCHAR(96)   NOT NULL,
    applied_ddl_hash VARCHAR(64),
    row_count        INT           NOT NULL DEFAULT 0,
    last_upload_at   TIMESTAMP,
    creator          BIGINT,
    updater          BIGINT,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT           NOT NULL DEFAULT 0
);
CREATE INDEX idx_dataset_workspace ON mate_dataset (workspace_id);

CREATE TABLE mate_dataset_field (
    id           BIGINT        NOT NULL PRIMARY KEY,
    dataset_id   BIGINT        NOT NULL,
    field_code   VARCHAR(64)   NOT NULL,
    field_name   VARCHAR(256)  NOT NULL,
    field_type   VARCHAR(16)   NOT NULL,
    field_unit   VARCHAR(32),
    semantic     VARCHAR(512),
    is_nullable  TINYINT       NOT NULL DEFAULT 1,
    ordinal      INT           NOT NULL,
    excel_header VARCHAR(512)  NOT NULL,
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INT           NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_dataset_field_code ON mate_dataset_field (dataset_id, field_code);
CREATE INDEX idx_dataset_field_dataset ON mate_dataset_field (dataset_id);
```

- [ ] **Step 3: 写 DatasetField 实体**

`mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetField.java`：

```java
package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One typed column of a {@link Dataset}'s physical table.
 *
 * <p>Replaces {@code DatasetTemplateField}: fields are now owned by the dataset
 * itself rather than a shared template, so one dataset maps to exactly one
 * physical table.
 */
@Data
@TableName("mate_dataset_field")
public class DatasetField {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long datasetId;

    /** snake_case physical column name. */
    private String fieldCode;

    /** Display name shown in UI and injected into LLM prompts. */
    private String fieldName;

    /** One of: STRING | INT | DECIMAL | BOOLEAN | DATE. */
    private String fieldType;

    private String fieldUnit;

    /** Business meaning injected into LLM system prompts. */
    private String semantic;

    private Boolean isNullable;

    private Integer ordinal;

    /** Exact source header text; used by ExcelHeaderMatcher on upload. */
    private String excelHeader;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 4: 写 DatasetFieldRepository**

`mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetFieldRepository.java`：

```java
package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus mapper for {@link DatasetField}. */
@Mapper
public interface DatasetFieldRepository extends BaseMapper<DatasetField> {
}
```

- [ ] **Step 5: 改 Dataset 实体**

`mateclaw-server/src/main/java/vip/mate/analytics/dataset/Dataset.java`：删除 `private Long templateId;`，新增两个字段，并更新类 javadoc。

将类 javadoc 整体替换为：

```java
/**
 * A dataset is a collection of uploaded rows plus the typed schema describing them.
 *
 * <p>Each dataset owns exactly one physical table named {@code dataset_<id>}, whose
 * columns are defined by this dataset's {@link DatasetField} rows. One dataset per
 * table means queries cannot accidentally mix rows from another dataset.
 */
```

在 `private String description;` 之后插入：

```java
    /** Physical table holding this dataset's rows; always "dataset_" + id. */
    private String physicalTable;

    /** SHA-256 of the field list; lets DynamicTableService skip no-op DDL. */
    private String appliedDdlHash;
```

- [ ] **Step 6: 写失败测试**

`mateclaw-server/src/test/java/vip/mate/analytics/dataset/DatasetFieldRepositoryTest.java`：

```java
package vip.mate.analytics.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V123 schema: a dataset owns its physical table name and its fields.
 */
@SpringBootTest(
        classes = vip.mate.MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dataset_field_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "spring.ai.dashscope.api-key=test-key"
})
class DatasetFieldRepositoryTest {

    @Autowired
    DatasetRepository datasetRepo;

    @Autowired
    DatasetFieldRepository fieldRepo;

    @Test
    @DisplayName("dataset persists its own physical table name — no template indirection")
    void datasetOwnsPhysicalTable() {
        Dataset ds = new Dataset();
        ds.setWorkspaceId(7L);
        ds.setName("销售数据");
        ds.setRowCount(0);
        datasetRepo.insert(ds);

        ds.setPhysicalTable("dataset_" + ds.getId());
        datasetRepo.updateById(ds);

        Dataset found = datasetRepo.selectById(ds.getId());
        assertThat(found.getPhysicalTable()).isEqualTo("dataset_" + ds.getId());
        assertThat(found.getWorkspaceId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("fields are owned by a dataset and round-trip through the repository")
    void fieldsBelongToDataset() {
        Dataset ds = new Dataset();
        ds.setWorkspaceId(7L);
        ds.setName("库存数据");
        ds.setRowCount(0);
        ds.setPhysicalTable("dataset_placeholder");
        datasetRepo.insert(ds);

        DatasetField f = new DatasetField();
        f.setDatasetId(ds.getId());
        f.setFieldCode("amount");
        f.setFieldName("金额");
        f.setFieldType("DECIMAL");
        f.setFieldUnit("元");
        f.setSemantic("订单成交金额");
        f.setIsNullable(true);
        f.setOrdinal(0);
        f.setExcelHeader("金额（元）");
        fieldRepo.insert(f);

        DatasetField found = fieldRepo.selectById(f.getId());
        assertThat(found.getDatasetId()).isEqualTo(ds.getId());
        assertThat(found.getExcelHeader()).isEqualTo("金额（元）");
        assertThat(found.getSemantic()).isEqualTo("订单成交金额");
    }
}
```

- [ ] **Step 7: 运行测试确认失败**

```bash
cd mateclaw-server && mvn test -Dtest=DatasetFieldRepositoryTest
```

预期：FAIL。此时 `template/` 包仍引用已被 V123 删掉的表，Spring 上下文启动即报错，或 `Dataset` 的 `template_id` 列不存在。这是预期的 —— Task 2 才会清掉这些引用。

> **给实现者：** 本任务的测试在 Task 2 完成前无法转绿。这是有意为之：模型合并无法在保持编译通过的前提下拆成更小步骤（删了模板表，`DatasetTemplateService` 就编译不过）。Task 1 与 Task 2 应当**连续完成后再一起提交**。若你在 Task 1 结束时看到红灯，继续做 Task 2，不要试图回退。

- [ ] **Step 8: 暂不提交**

Task 1 与 Task 2 合并提交（见 Task 2 Step 8）。

---

## Task 2: 删除 template 包，改造存储与上传服务

**Files:**
- Delete: `mateclaw-server/src/main/java/vip/mate/analytics/template/` 整个包
- Delete: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetTemplateController.java`
- Delete: `mateclaw-server/src/test/java/vip/mate/analytics/template/DatasetTemplateServiceTest.java`
- Delete: `mateclaw-server/src/test/java/vip/mate/analytics/controller/DatasetTemplateControllerTest.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/FieldType.java`（从 `template/FieldType.java` 移动）
- Modify: `storage/DynamicTableService.java`、`storage/PhysicalColumnType.java`、`upload/ExcelIngestService.java`、`upload/ExcelParseService.java`、`upload/ExcelHeaderMatcher.java`、`dataset/DatasetService.java`、`tool/AnalyticsSchemaTool.java`、`tool/AnalyticsProfileTool.java`、`controller/DatasetUploadController.java`、`controller/DatasetController.java`

> **引用 template 包的文件就是这 12 个**（`grep -rln "analytics.template\|DatasetTemplate"` 实测结果）：上列 10 个 + `dataset/Dataset.java`（Task 1 已改）+ `controller/DatasetTemplateController.java`（本任务删除）。
>
> **`AnalyticsChartTool` / `AnalyticsComputeTool` / `AnalyticsExportTool` / `AnalyticsQueryTool` 不引用 template，无需改动。** spec 的「影响面」写的是「六个 Agent 工具均需改」，实测只有 `SchemaTool` 与 `ProfileTool` 两个 —— 以本计划为准。

**Interfaces:**
- Consumes: Task 1 的 `DatasetField`、`DatasetFieldRepository`、`Dataset.getPhysicalTable()`、`Dataset.getAppliedDdlHash()`。
- Produces:
  - `DynamicTableService.ensureTable(Dataset ds, List<DatasetField> fields)` → `void`
  - `ExcelIngestService.ingest(Dataset ds, List<DatasetField> fields, List<ParsedRow> rows, Long uploadLogId)` → `IngestResult`
  - `ExcelParseService.parse(InputStream in, List<DatasetField> fields, String sheetName)` → `List<ParsedRow>`
  - `ExcelHeaderMatcher.match(List<String> headers, List<DatasetField> fields)` → `Map<Integer,String>`
  - `DatasetService.createWithFields(Dataset ds, List<DatasetField> fields)` → `Dataset`

- [ ] **Step 1: 移动 FieldType 到 dataset 包**

`template/FieldType.java` → `dataset/FieldType.java`，只改 `package` 行为 `package vip.mate.analytics.dataset;`，枚举内容不动。全局把 `import vip.mate.analytics.template.FieldType;` 替换为 `import vip.mate.analytics.dataset.FieldType;`。

- [ ] **Step 2: 删除 template 包与模板 Controller**

```bash
cd mateclaw-server
rm -rf src/main/java/vip/mate/analytics/template
rm -f src/main/java/vip/mate/analytics/controller/DatasetTemplateController.java
rm -rf src/test/java/vip/mate/analytics/template
rm -f src/test/java/vip/mate/analytics/controller/DatasetTemplateControllerTest.java
```

- [ ] **Step 3: 改 DynamicTableService**

`storage/DynamicTableService.java`：
- 签名 `ensureTable(DatasetTemplate template, List<DatasetTemplateField> fields)` → `ensureTable(Dataset ds, List<DatasetField> fields)`
- `String physicalTable = template.getPhysicalTable();` → `String physicalTable = ds.getPhysicalTable();`
- 结尾 `template.setAppliedDdlHash(newHash); templateRepo.updateById(template);` → `ds.setAppliedDdlHash(newHash); datasetRepo.updateById(ds);`（注入 `DatasetRepository` 取代 `DatasetTemplateRepository`）
- **`buildCreate` 删除 `dataset_id` 列**：

```java
    private String buildCreate(String physicalTable, List<DatasetField> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(physicalTable).append(" (\n");
        sb.append("  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n");
        sb.append("  upload_log_id BIGINT,\n");
        for (DatasetField f : fields) {
            validateName(f.getFieldCode(), "fieldCode");
            FieldType ft = FieldType.fromString(f.getFieldType());
            sb.append("  ").append(f.getFieldCode()).append(" ")
              .append(PhysicalColumnType.sqlType(ft)).append(",\n");
        }
        sb.setLength(sb.length() - 2);
        sb.append("\n)");
        return sb.toString();
    }
```

> `dataset_id` 列被移除是本期的核心目的之一：一表一数据集后它恒为常量，去掉它让「AI 忘记加 WHERE dataset_id」这类 bug 无法再表达。`upload_log_id` 保留用于按批次溯源。
>
> 注意：此处 `id ... AUTO_INCREMENT` 是**物理数据表**的行 id，由数据库生成，与 Global Constraints 中「元数据表 ID 由应用生成」不冲突 —— 保持现状不要改。

- [ ] **Step 4: 改 ExcelIngestService**

`upload/ExcelIngestService.java`：
- 签名去掉 `DatasetTemplate tpl` 参数：`ingest(Dataset ds, List<DatasetField> fields, List<ParsedRow> rows, Long uploadLogId)`
- 表名来源 `tpl.getPhysicalTable()` → `ds.getPhysicalTable()`
- `buildInsertSql` 去掉 `dataset_id`：

```java
    private String buildInsertSql(String physicalTable, List<DatasetField> fields) {
        String fieldColumns = fields.stream()
                .map(DatasetField::getFieldCode)
                .collect(Collectors.joining(", "));
        String placeholders = fields.stream()
                .map(f -> "?")
                .collect(Collectors.joining(", "));
        return String.format(
                "INSERT INTO %s (upload_log_id, %s) VALUES (?, %s)",
                physicalTable, fieldColumns, placeholders);
    }
```

对应的批量参数绑定处，删掉原先绑定 `ds.getId()` 的那一个占位符，只保留 `uploadLogId` + 各字段值。

- [ ] **Step 5: 改 ExcelParseService / ExcelHeaderMatcher / AnalyticsSchemaTool / AnalyticsProfileTool**

- `ExcelParseService`、`ExcelHeaderMatcher`：把类型 `DatasetTemplateField` 全部换为 `DatasetField`，逻辑不动（`getExcelHeader()` / `getIsNullable()` / `getFieldCode()` 同名存在）。
- `AnalyticsSchemaTool`：删除 `DatasetTemplate t = templateRepo.selectById(...)`，字段查询改为按 `dataset_id`，物理表名取自 dataset：

```java
        List<DatasetField> fields = fieldRepo.selectList(
                new QueryWrapper<DatasetField>()
                        .eq("dataset_id", d.getId())
                        .eq("deleted", 0)
                        .orderByAsc("ordinal"));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("数据集: %s (id=%d, 物理表=%s, 行数=%d)\n字段清单:\n",
                d.getName(), d.getId(), d.getPhysicalTable(), d.getRowCount()));
```

（`workspace_id` 硬编码 `1L` **本期不动** —— 属第三期，避免返工。）

- `AnalyticsProfileTool`：物理表名改取 `ds.getPhysicalTable()`；**删除 `WHERE dataset_id = ?` 子句及其参数绑定**（列已不存在）。原 SQL：

```java
            String sql = String.format(
                    "SELECT ... FROM %s WHERE dataset_id = ?", physicalTable);
```

改为：

```java
            String sql = String.format(
                    "SELECT ... FROM %s", physicalTable);
```

并同步移除 `jdbc.queryForMap(sql, datasetId)` 中的 `datasetId` 实参。

- [ ] **Step 6: 改 DatasetService**

`dataset/DatasetService.java`：
- 去掉 `DatasetTemplateRepository` 注入，改注入 `DatasetFieldRepository`
- `delete(Long id)` 里解析物理表名的逻辑改为 `ds.getPhysicalTable()`；一表一数据集后应 **`DROP TABLE`** 而非删行，同时删掉该 dataset 的 field 行
- 新增 `createWithFields`，把原 `DatasetTemplateService.create` 的 fieldCode 生成与 excelHeader 兜底逻辑搬过来：

```java
    /**
     * Create a dataset together with its schema, then name its physical table after
     * the generated id.
     *
     * <p>fieldCode and excelHeader are derived from fieldName when absent — callers
     * (and the LLM) never supply physical column names.
     *
     * @param ds     dataset to persist; workspaceId and name must be set
     * @param fields ordered field definitions; fieldName and fieldType required
     * @return the persisted dataset with id and physicalTable populated
     */
    public Dataset createWithFields(Dataset ds, List<DatasetField> fields) {
        if (!StringUtils.hasText(ds.getName())) {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("数据集至少需要一个字段");
        }
        for (DatasetField f : fields) {
            if (!StringUtils.hasText(f.getFieldName())) {
                throw new IllegalArgumentException("字段名不能为空");
            }
            FieldType.fromString(f.getFieldType());
        }

        if (ds.getRowCount() == null) {
            ds.setRowCount(0);
        }
        ds.setPhysicalTable("dataset_placeholder");
        datasetRepo.insert(ds);

        ds.setPhysicalTable("dataset_" + ds.getId());
        datasetRepo.updateById(ds);

        Set<String> usedFieldCodes = new HashSet<>();
        for (DatasetField f : fields) {
            if (!StringUtils.hasText(f.getFieldCode())) {
                f.setFieldCode(generateUniqueFieldCodeInBatch(f.getFieldName(), usedFieldCodes));
            }
            usedFieldCodes.add(f.getFieldCode());
            if (!StringUtils.hasText(f.getExcelHeader())) {
                f.setExcelHeader(f.getFieldName());
            }
            if (f.getIsNullable() == null) {
                f.setIsNullable(true);
            }
            f.setDatasetId(ds.getId());
            fieldRepo.insert(f);
        }
        return ds;
    }
```

`generateUniqueFieldCodeInBatch` 从已删除的 `DatasetTemplateService` 原样搬入本类（保持 private）。

> `physical_table` 列为 `NOT NULL`，而表名依赖尚未生成的 id，故先写占位符再回填。两次写入在同一 `@Transactional` 内，外部观察不到中间态。

- [ ] **Step 7: 改两个 Controller 使其编译通过**

- `controller/DatasetUploadController.java`：删除 `templateRepo` / `DatasetTemplate` 相关代码，字段查询改按 `dataset_id`，`dynamicTable.ensureTable(ds, fields)`、`ingest.ingest(ds, fields, rows, uploadLog.getId())`。
- `controller/DatasetController.java`：删除 `CreateDatasetRequest` 中的 `templateId`，暂时保留原 create 端点使其编译通过（Task 3 会用一步上传端点替换它）。

> **inspect 端点会短暂消失。** `ExcelInspectService` 目前**只**被 `DatasetTemplateController` 使用（实测：`grep -rln "ExcelInspectService" controller/` 仅命中它）。本任务删掉该 Controller 后 inspect 端点不存在，直到 Task 3 在 `DatasetController` 里以 `/analytics/datasets/inspect` 重新提供。两个任务之间没有测试或 UI 依赖它，属预期状态 —— 不要因此保留 `DatasetTemplateController`。

- [ ] **Step 8: 运行测试并提交**

```bash
cd mateclaw-server && mvn test -Dtest=DatasetFieldRepositoryTest
```
预期：PASS（Task 1 的测试此时转绿）。

```bash
cd mateclaw-server && mvn test -Dtest='Dataset*Test,Excel*Test,Analytics*Test,DynamicTableServiceTest'
```
预期：全部 PASS。若 `ExcelIngestServiceTest` / `DynamicTableServiceTest` / `AnalyticsProfileToolTest` 因 `dataset_id` 列消失而失败，**改测试**以匹配新结构（这些断言在验证已被有意移除的行为）。

```bash
git add -A
git commit -m "refactor(analytics): collapse dataset template into dataset

One dataset now owns one schema and one physical table named dataset_<id>.
Physical tables no longer carry a dataset_id column: with one table per
dataset it was constant, and its presence was the root of a silent
correctness bug — the schema tool never told the agent the column existed
and 4 of 6 tools ignored it, so queries across datasets sharing a template
merged rows without error.

Also drops columns with no readers anywhere in the codebase: role,
time_granularity, aggregation, compute_hint (written by the template
editor UI, never read), is_partition_key, partition_keys, and the
template code/category/enabled trio.

No production data exists, so V123 drops and recreates rather than
migrating.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 一步上传端点

**Files:**
- Modify: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetController.java`
- Modify: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetUploadController.java`（把 `inspect` 端点迁来）
- Test: `mateclaw-server/src/test/java/vip/mate/analytics/dataset/DatasetCreateFromFileTest.java`

**Interfaces:**
- Consumes: Task 2 的 `DatasetService.createWithFields`、`DynamicTableService.ensureTable(Dataset, List<DatasetField>)`、`ExcelIngestService.ingest(Dataset, List<DatasetField>, List<ParsedRow>, Long)`。
- Produces:
  - `POST /api/v1/analytics/datasets/inspect` → `R<InspectResult>`
  - `POST /api/v1/analytics/datasets`（multipart）→ `R<CreateDatasetResponse>`，其中 `CreateDatasetResponse` 为 `record CreateDatasetResponse(Dataset dataset, DatasetUploadLog uploadLog)`

- [ ] **Step 1: 写失败测试**

`mateclaw-server/src/test/java/vip/mate/analytics/dataset/DatasetCreateFromFileTest.java`：

```java
package vip.mate.analytics.dataset;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vip.mate.analytics.controller.DatasetController;
import vip.mate.common.result.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the one-step upload: a single multipart call must create
 * the dataset, its schema, its physical table, and ingest the rows.
 */
@SpringBootTest(
        classes = vip.mate.MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dataset_create_from_file_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "spring.ai.dashscope.api-key=test-key"
})
class DatasetCreateFromFileTest {

    @Autowired
    DatasetController controller;

    @Autowired
    DatasetRepository datasetRepo;

    @Autowired
    DatasetFieldRepository fieldRepo;

    @Autowired
    JdbcTemplate jdbc;

    /** Builds a 2-column, 2-row .xlsx in memory. */
    private MockMultipartFile xlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("省份");
            header.createCell(1).setCellValue("金额");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("广东");
            r1.createCell(1).setCellValue(100.5);
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("江苏");
            r2.createCell(1).setCellValue(200.25);
            wb.write(out);
            return new MockMultipartFile(
                    "file", "销售.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    @DisplayName("one multipart call creates the dataset, its table, and ingests the rows")
    void createFromFileIngestsRows() throws IOException {
        String fieldsJson = """
                [{"fieldName":"省份","fieldType":"STRING","ordinal":0},
                 {"fieldName":"金额","fieldType":"DECIMAL","ordinal":1}]
                """;

        ResponseEntity<R<DatasetController.CreateDatasetResponse>> resp =
                controller.createFromFile(xlsx(), "季度销售", fieldsJson, null, 9L, 42L);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        DatasetController.CreateDatasetResponse body = resp.getBody().getData();

        // Dataset row created, physical table named after its id
        Long dsId = body.dataset().getId();
        Dataset saved = datasetRepo.selectById(dsId);
        assertThat(saved.getPhysicalTable()).isEqualTo("dataset_" + dsId);
        assertThat(saved.getWorkspaceId()).isEqualTo(9L);

        // Schema persisted, owned by the dataset
        List<DatasetField> fields = fieldRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetField>()
                        .eq("dataset_id", dsId).orderByAsc("ordinal"));
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).getExcelHeader()).isEqualTo("省份");

        // Rows actually landed in the physical table
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM " + saved.getPhysicalTable());
        assertThat(rows).hasSize(2);

        // Upload log reflects the ingest
        assertThat(body.uploadLog().getRowsInserted()).isEqualTo(2);
        assertThat(body.uploadLog().getStatus()).isEqualTo("SUCCESS");

        // The physical table must NOT carry a dataset_id column any more
        assertThat(rows.get(0)).doesNotContainKey("dataset_id");

        jdbc.execute("DROP TABLE IF EXISTS " + saved.getPhysicalTable());
    }

    @Test
    @DisplayName("rejects a non-xlsx file before creating anything")
    void rejectsNonXlsx() {
        MockMultipartFile bad = new MockMultipartFile(
                "file", "data.txt", "text/plain", "省份,金额".getBytes());

        long before = datasetRepo.selectCount(null);

        assertThatThrownBy(() -> controller.createFromFile(
                bad, "坏文件", "[]", null, 9L, 42L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(datasetRepo.selectCount(null)).isEqualTo(before);
    }
}
```

测试头部需补充静态导入：`import static org.assertj.core.api.Assertions.assertThatThrownBy;`

- [ ] **Step 2: 运行测试确认失败**

```bash
cd mateclaw-server && mvn test -Dtest=DatasetCreateFromFileTest
```
预期：编译失败，`cannot find symbol: method createFromFile`。

- [ ] **Step 3: 实现端点**

在 `controller/DatasetController.java` 中新增（`inspect` 端点从 `DatasetTemplateController` 迁来，路径由 `/analytics/templates/inspect-excel` 改为 `/analytics/datasets/inspect`）：

```java
    /** Response of the one-step upload: the dataset plus the log of its first ingest. */
    public record CreateDatasetResponse(Dataset dataset, DatasetUploadLog uploadLog) {}

    /**
     * Inspect a file's headers and infer field definitions. Nothing is persisted —
     * the client shows these for confirmation, then posts them back to {@link #createFromFile}.
     */
    @Operation(summary = "Infer field definitions from a file without persisting")
    @PostMapping("/inspect")
    public ResponseEntity<R<InspectResult>> inspect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheet", required = false) String sheet) throws IOException {
        validateFile(file);
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(R.ok(inspectService.inspect(in, sheet)));
        }
    }

    /**
     * Create a dataset from a file in one call: persist schema, create the physical
     * table, and ingest every row. The file is uploaded exactly once.
     *
     * @param file       multipart .xlsx (required)
     * @param name       dataset name
     * @param fieldsJson JSON array of {fieldName, fieldType, fieldUnit?, ordinal}
     * @param sheet      optional sheet name; first sheet when omitted
     */
    @Operation(summary = "Create a dataset from a file and ingest it in one call")
    @PostMapping
    public ResponseEntity<R<CreateDatasetResponse>> createFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("fields") String fieldsJson,
            @RequestParam(value = "sheet", required = false) String sheet,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) throws IOException {

        validateFile(file);

        List<DatasetField> fields = parseFields(fieldsJson);

        Dataset ds = new Dataset();
        ds.setWorkspaceId(workspaceId);
        ds.setName(name);
        ds.setRowCount(0);
        datasetService.createWithFields(ds, fields);

        dynamicTable.ensureTable(ds, fields);

        DatasetUploadLog uploadLog = new DatasetUploadLog();
        uploadLog.setDatasetId(ds.getId());
        uploadLog.setFileName(file.getOriginalFilename());
        uploadLog.setFileSize(file.getSize());
        uploadLog.setStatus("PROCESSING");
        uploadLog.setUploader(userId);
        uploadLog.setUploadTime(LocalDateTime.now());
        uploadLogRepo.insert(uploadLog);

        try {
            List<ParsedRow> rows = parse.parse(file.getInputStream(), fields, sheet);
            IngestResult result = ingest.ingest(ds, fields, rows, uploadLog.getId());

            uploadLog.setRowsReceived(rows.size());
            uploadLog.setRowsInserted(result.inserted());
            uploadLog.setRowsRejected(result.rejected());
            if (result.rejected() == 0) {
                uploadLog.setStatus("SUCCESS");
            } else if (result.inserted() > 0) {
                uploadLog.setStatus("PARTIAL");
                uploadLog.setErrorSummary(String.join("; ", result.errors()));
            } else {
                uploadLog.setStatus("FAILED");
                uploadLog.setErrorSummary(String.join("; ", result.errors()));
            }

            ds.setRowCount(result.inserted());
            ds.setLastUploadAt(LocalDateTime.now());
            datasetRepo.updateById(ds);
        } catch (IOException e) {
            uploadLog.setStatus("FAILED");
            uploadLog.setErrorSummary("File read error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            uploadLog.setStatus("FAILED");
            uploadLog.setErrorSummary(e.getMessage());
        } finally {
            uploadLogRepo.updateById(uploadLog);
        }

        return ResponseEntity.ok(R.ok(new CreateDatasetResponse(ds, uploadLog)));
    }

    /** Maps the client's field JSON onto entities. Physical column names are derived server-side. */
    private List<DatasetField> parseFields(String fieldsJson) {
        try {
            JsonNode arr = objectMapper.readTree(fieldsJson);
            List<DatasetField> out = new ArrayList<>();
            int i = 0;
            for (JsonNode n : arr) {
                DatasetField f = new DatasetField();
                f.setFieldName(n.path("fieldName").asText());
                f.setFieldType(n.path("fieldType").asText("STRING"));
                if (n.hasNonNull("fieldUnit")) {
                    f.setFieldUnit(n.get("fieldUnit").asText());
                }
                f.setOrdinal(n.path("ordinal").asInt(i));
                f.setIsNullable(true);
                out.add(f);
                i++;
            }
            return out;
        } catch (IOException e) {
            throw new IllegalArgumentException("字段定义格式错误: " + e.getMessage());
        }
    }

    /** Validates file size and extension. Mirrors DatasetUploadController.validateFile. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file must not be empty");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File too large: " + file.getSize() + " bytes (max 50 MB)");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "Only .xlsx files are accepted; received: " + originalName);
        }
    }
```

需注入：`DatasetService datasetService`、`DatasetRepository datasetRepo`、`DatasetUploadLogRepository uploadLogRepo`、`DynamicTableService dynamicTable`、`ExcelParseService parse`、`ExcelIngestService ingest`、`ExcelInspectService inspectService`、`ObjectMapper objectMapper`。

删除旧的 `POST /analytics/datasets`（接收 JSON body 的 `CreateDatasetRequest`）—— 它被本端点取代。

- [ ] **Step 4: 运行测试确认通过**

```bash
cd mateclaw-server && mvn test -Dtest=DatasetCreateFromFileTest
```
预期：PASS，2 个测试。

- [ ] **Step 5: 跑全量 analytics 测试**

```bash
cd mateclaw-server && mvn test -Dtest='Dataset*Test,Excel*Test,Analytics*Test,DynamicTableServiceTest,SqlGuardTest'
```
预期：全部 PASS。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat(analytics): create a dataset from a file in one call

POST /analytics/datasets now takes the file, name, and confirmed fields
in a single multipart request: it persists the schema, creates the
physical table, and ingests every row.

Previously the same file had to be uploaded twice — once to
/templates/inspect-excel to infer the schema (which then discarded the
file and created only a template) and again to /datasets/{id}/upload to
actually ingest it.

The inspect endpoint moves to /analytics/datasets/inspect.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 前端 API 与类型层

**Files:**
- Modify: `mateclaw-ui/src/types/analytics.ts`
- Modify: `mateclaw-ui/src/api/analytics.ts`

**Interfaces:**
- Consumes: Task 3 的两个端点。
- Produces:
  - `inspectFile(file: File, sheet?: string): Promise<{ data: InspectResult }>`
  - `createDatasetFromFile(p: { file: File; name: string; fields: InspectedField[]; sheet?: string }): Promise<{ data: CreateDatasetResponse }>`
  - 类型 `Dataset`（含 `physicalTable`，无 `templateId`）、`DatasetField`、`CreateDatasetResponse`

- [ ] **Step 1: 改类型**

`mateclaw-ui/src/types/analytics.ts`：
- **删除**：`DatasetTemplate`、`DatasetTemplateField`、`CreateTemplateRequest`、`CreateTemplateFieldRequest`、`UpdateTemplateFieldRequest`、`CreateDatasetRequest`
- `Dataset` 去掉 `templateId`，加 `physicalTable: string`
- 新增：

```ts
export interface DatasetField {
  id: string
  datasetId: string
  fieldCode: string
  fieldName: string
  fieldType: FieldType
  fieldUnit?: string
  semantic?: string
  isNullable: boolean
  ordinal: number
  excelHeader: string
}

/** Response of POST /analytics/datasets — the one-step upload. */
export interface CreateDatasetResponse {
  dataset: Dataset
  uploadLog: DatasetUploadLog
}
```

`FieldType`、`UploadStatus`、`Dataset`、`DatasetUploadLog`、`InspectedField`、`InspectResult`、`PageResult` 保留。

- [ ] **Step 2: 改 API**

`mateclaw-ui/src/api/analytics.ts`：
- **删除**：`listTemplates`、`createTemplate`、`getTemplate`、`deleteTemplate`、`listTemplateFields`、`createTemplateField`、`updateTemplateField`、`deleteTemplateField`、`inspectExcel`、`createDataset`
- 新增：

```ts
export function inspectFile(
  file: File,
  sheet?: string
): Promise<{ data: InspectResult }> {
  const formData = new FormData()
  formData.append('file', file)
  return analyticsHttp.post('/analytics/datasets/inspect', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: sheet ? { sheet } : undefined,
  })
}

export function createDatasetFromFile(p: {
  file: File
  name: string
  fields: InspectedField[]
  sheet?: string
}): Promise<{ data: CreateDatasetResponse }> {
  const formData = new FormData()
  formData.append('file', p.file)
  formData.append('name', p.name)
  formData.append('fields', JSON.stringify(p.fields))
  return analyticsHttp.post('/analytics/datasets', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: p.sheet ? { sheet } : undefined,
  })
}
```

`listDatasets`、`getDataset`、`deleteDataset`、`previewDataset`、`listDatasetUploads`、`uploadExcel` 保留不动。

- [ ] **Step 3: 类型检查**

```bash
cd mateclaw-ui && node --max-old-space-size=6144 ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit
```
预期：报错**仅**来自尚未改造的 `TemplateList.vue` / `TemplateEditor.vue` / `InspectTemplateDialog.vue` / `DatasetList.vue`（Task 5 删除或改造），外加**既存**的 `TrialBanner.vue:77`。不应出现 `api/analytics.ts` 或 `types/analytics.ts` 自身的报错。

- [ ] **Step 4: 暂不提交** —— 与 Task 5 一起提交（此刻前端不可编译）。

---

## Task 5: 前端一步上传 UI

**Files:**
- Delete: `mateclaw-ui/src/views/analytics/TemplateList.vue`
- Delete: `mateclaw-ui/src/views/analytics/TemplateEditor.vue`
- Delete: `mateclaw-ui/src/views/analytics/InspectTemplateDialog.vue`
- Modify: `mateclaw-ui/src/views/analytics/UploadDialog.vue`（改造为一步上传）
- Modify: `mateclaw-ui/src/views/analytics/DatasetList.vue`
- Modify: `mateclaw-ui/src/views/analytics/index.vue`
- Modify: `mateclaw-ui/src/router/index.ts`
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`、`en-US.ts`

**Interfaces:**
- Consumes: Task 4 的 `inspectFile`、`createDatasetFromFile`、`CreateDatasetResponse`。
- Produces: 无下游任务。

- [ ] **Step 1: 删除模板三件套与路由**

```bash
cd mateclaw-ui
rm -f src/views/analytics/TemplateList.vue \
      src/views/analytics/TemplateEditor.vue \
      src/views/analytics/InspectTemplateDialog.vue
```

`src/router/index.ts`：删除 `AnalyticsTemplates`（`path: 'templates'`）与 `AnalyticsTemplateEditor`（`path: 'templates/:id/fields'`）两条子路由。

- [ ] **Step 2: 改造 UploadDialog 为一步上传**

`src/views/analytics/UploadDialog.vue` 整体重写。它现在有两步：选文件 → 确认字段 → 创建并分析。

```vue
<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('analytics.uploadData')"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetState"
  >
    <!-- Step 1: pick a file -->
    <div v-if="step === 'pick'" class="upload-body">
      <el-upload
        ref="uploadRef"
        class="upload-dragger"
        drag
        accept=".xlsx"
        :auto-upload="false"
        :limit="1"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
      >
        <div class="upload-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </div>
        <div class="upload-hint">{{ t('analytics.dropFile') }}</div>
        <div class="upload-sub">{{ t('analytics.uploadSub') }}</div>
      </el-upload>
      <div class="sheet-row">
        <span class="sheet-label">{{ t('analytics.sheetName') }}</span>
        <el-input v-model="sheetName" :placeholder="t('analytics.sheetPlaceholder')" size="small" class="sheet-input" />
      </div>
    </div>

    <!-- Step 2: confirm the inferred schema -->
    <div v-else class="upload-body">
      <el-alert
        v-if="inspectResult && !inspectResult.sampleRowAvailable"
        :title="t('analytics.inspectNoSample')"
        type="warning"
        :closable="false"
      />
      <el-form label-position="top">
        <el-form-item :label="t('analytics.datasetName')" required>
          <el-input v-model="datasetName" />
        </el-form-item>
      </el-form>
      <el-table :data="editableFields" size="small" class="field-table">
        <el-table-column :label="t('analytics.fieldName')" min-width="160">
          <template #default="{ row }"><el-input v-model="row.fieldName" size="small" /></template>
        </el-table-column>
        <el-table-column :label="t('analytics.fieldType')" width="140">
          <template #default="{ row }">
            <el-select v-model="row.fieldType" size="small">
              <el-option v-for="ft in FIELD_TYPES" :key="ft" :label="ft" :value="ft" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column width="60">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="editableFields.splice($index, 1)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button v-if="step === 'pick'" type="primary" :loading="inspecting" :disabled="!selectedFile" @click="handleInspect">
        {{ t('common.next') }}
      </el-button>
      <el-button v-else type="primary" :loading="creating" :disabled="!datasetName.trim() || !editableFields.length" @click="handleCreate">
        {{ t('analytics.createAndAnalyze') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { inspectFile, createDatasetFromFile } from '@/api/analytics'
import type { InspectResult, InspectedField, FieldType } from '@/types/analytics'

defineProps<{ modelValue: boolean }>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created', datasetId: string): void
}>()

const { t } = useI18n()

const FIELD_TYPES: FieldType[] = ['STRING', 'INT', 'DECIMAL', 'BOOLEAN', 'DATE']

const step = ref<'pick' | 'confirm'>('pick')
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)
const sheetName = ref('')
const inspecting = ref(false)
const creating = ref(false)
const inspectResult = ref<InspectResult | null>(null)
const datasetName = ref('')
const editableFields = ref<InspectedField[]>([])

function onFileChange(file: UploadFile) {
  if (file.raw) {
    selectedFile.value = file.raw
    // Default the dataset name to the file name without its extension.
    datasetName.value = (file.name || '').replace(/\.[^.]+$/, '')
  }
}

function onFileRemove() {
  selectedFile.value = null
}

async function handleInspect() {
  if (!selectedFile.value) return
  inspecting.value = true
  try {
    const res = await inspectFile(selectedFile.value, sheetName.value.trim() || undefined)
    inspectResult.value = res.data
    editableFields.value = res.data.suggestedFields.map((f) => ({ ...f }))
    step.value = 'confirm'
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    inspecting.value = false
  }
}

async function handleCreate() {
  if (!selectedFile.value) return
  creating.value = true
  try {
    const res = await createDatasetFromFile({
      file: selectedFile.value,
      name: datasetName.value.trim(),
      fields: editableFields.value.map((f, i) => ({ ...f, ordinal: i })),
      sheet: sheetName.value.trim() || undefined,
    })
    const { dataset, uploadLog } = res.data
    if (uploadLog.rowsRejected > 0) {
      ElMessage.warning(
        t('analytics.partialIngest', { ok: uploadLog.rowsInserted, bad: uploadLog.rowsRejected })
      )
    } else {
      ElMessage.success(t('analytics.ingestOk', { ok: uploadLog.rowsInserted }))
    }
    emit('update:modelValue', false)
    emit('created', dataset.id)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    creating.value = false
  }
}

function resetState() {
  step.value = 'pick'
  selectedFile.value = null
  sheetName.value = ''
  inspecting.value = false
  creating.value = false
  inspectResult.value = null
  datasetName.value = ''
  editableFields.value = []
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.upload-body { display: flex; flex-direction: column; gap: 16px; }
.upload-dragger { width: 100%; }
.upload-icon { color: var(--mc-text-secondary); margin-bottom: 8px; }
.upload-hint { font-size: 14px; font-weight: 600; color: var(--mc-text-primary); }
.upload-sub { font-size: 12px; color: var(--mc-text-secondary); margin-top: 4px; }
.sheet-row { display: flex; align-items: center; gap: 12px; }
.sheet-label { flex-shrink: 0; font-size: 13px; color: var(--mc-text-secondary); min-width: 64px; }
.sheet-input { flex: 1; }
.field-table { max-height: 320px; overflow-y: auto; }
</style>
```

- [ ] **Step 3: 改 DatasetList**

`src/views/analytics/DatasetList.vue`：

- 顶部按钮从「新建数据集」改为「上传数据」，点击 `showUploadDialog = true`
- **删除**「新建数据集」`el-dialog`、`createForm`、`createRules`、`createFormRef`、`handleCreate`、`templates` ref、`listTemplates` / `createDataset` 的 import
- 删除表格里的 `templateId` 列
- 删除上一轮为引导卡片加的 `watch(() => route.query.action, ...)`（引导卡片本任务会删除，该 query 不再有来源）
- 表格新增空状态插槽承载拖拽入口：

```vue
        <template #empty>
          <div class="empty-state">
            <div class="empty-hint">{{ t('analytics.emptyHint') }}</div>
            <button class="btn-primary" @click="showUploadDialog = true">
              {{ t('analytics.uploadData') }}
            </button>
          </div>
        </template>
```

```css
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 48px 0; }
.empty-hint { font-size: 13px; color: var(--mc-text-tertiary); }
```

- 行内操作改为：分析 / 预览 / 追加数据 / 上传记录 / 删除。「追加数据」用一个隐藏的 `<input type="file">` 直接触发系统文件选择，选中即调既有 `uploadExcel`，**不再开弹窗** —— 追加时 schema 已定，没有需要用户确认的东西：

```vue
    <input
      ref="appendInputRef"
      type="file"
      accept=".xlsx"
      style="display: none"
      @change="onAppendFilePicked"
    />
```

```ts
const appendInputRef = ref<HTMLInputElement>()
const appendTargetId = ref<string>('')

function openAppend(row: Dataset) {
  appendTargetId.value = row.id
  appendInputRef.value?.click()
}

async function onAppendFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // Reset immediately so picking the same file twice still fires change.
  input.value = ''
  if (!file) return
  try {
    const res = await uploadExcel(appendTargetId.value, file)
    const log = res.data
    if (log.rowsRejected > 0) {
      ElMessage.warning(t('analytics.partialIngest', { ok: log.rowsInserted, bad: log.rowsRejected }))
    } else {
      ElMessage.success(t('analytics.ingestOk', { ok: log.rowsInserted }))
    }
    await loadData()
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}
```

- `UploadDialog` 的使用改为：

```vue
    <UploadDialog v-model="showUploadDialog" @created="onCreated" />
```

```ts
function onCreated(datasetId: string) {
  loadData()
  router.push({ path: '/chat', query: { agentId: ANALYST_AGENT_ID, datasetId } })
}
```

`ANALYST_AGENT_ID` 暂沿用 `'1000000020'` 并加注释说明第三期改为后端下发：

```ts
// Phase 3 will have the backend supply this; see the analytics simplification spec.
const ANALYST_AGENT_ID = '1000000020'
```

`workspaceId()` 的 `'1'` 兜底**本期保留**（属第三期，避免返工）。

- [ ] **Step 4: 改 index.vue**

`src/views/analytics/index.vue`：
- **删除** `guidance-area` 整块（两张引导卡）及其 `<style>`、`goTo`、`goToUpload`
- tab 从三个减为两个：数据集、外部数据源（删除「数据集模板」）

- [ ] **Step 5: 补 i18n**

`src/i18n/locales/zh-CN.ts` 的 `analytics` 段内新增/修改：

```ts
    uploadData: '上传数据',
    dropFile: '拖入 Excel 文件，或点击选择',
    createAndAnalyze: '创建并分析',
    fieldName: '字段名',
    fieldType: '类型',
    appendData: '追加数据',
    ingestOk: '已入库 {ok} 行',
    partialIngest: '已入库 {ok} 行，{bad} 行被拒绝',
    emptyHint: '还没有数据集。上传一个 Excel 文件，就能让数字员工分析它。',
```

**删除**这些已无引用的 key：`guidanceUpload`、`guidanceUploadDesc`、`guidanceConnect`、`guidanceConnectDesc`、`templates`、`createDataset`、`templateCode`、`templateName`、`importFromExcel`、`inspectSheetHint`、`ordinal`、`category`。

`en-US.ts` 同步对应键：

```ts
    uploadData: 'Upload data',
    dropFile: 'Drop an Excel file here, or click to choose',
    createAndAnalyze: 'Create and analyze',
    fieldName: 'Field',
    fieldType: 'Type',
    appendData: 'Append data',
    ingestOk: '{ok} rows ingested',
    partialIngest: '{ok} rows ingested, {bad} rejected',
    emptyHint: 'No datasets yet. Upload an Excel file and your digital worker can analyze it.',
```

> `uploadSub` 当前文案为「仅支持 .xlsx 文件」，与实际一致，保留。第二期加 CSV 时再改。

- [ ] **Step 6: 类型检查**

```bash
cd mateclaw-ui && node --max-old-space-size=6144 ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit
```
预期：**唯一**报错为既存的 `src/views/layout/TrialBanner.vue:77`。其余任何报错都必须修掉。

- [ ] **Step 7: 实跑验证**

调用 `mateclaw-ui/.claude/skills/verify` skill，按其记录的方式起 dev server + Playwright 驱动，验证：
1. 进入数据管理 → 只有两个 tab，无引导卡片
2. 点「上传数据」→ 拖入 .xlsx → 展示推断出的字段 → 改数据集名 → 「创建并分析」
3. 跳转到 chat 且 URL 带 `datasetId`
4. 返回数据管理 → 列表中出现该数据集，行数正确

该 skill 记录了必须处理的四个拦路点（auth guard、onboarding 浮层、API 打桩、`**/api/v1/**` glob 收窄）。本次需真实后端配合（不能全打桩），故先起后端：

```bash
cd mateclaw-server && mvn spring-boot:run
```

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "feat(analytics): collapse the upload flow into a single step

Uploading now takes one dialog: drop a file, confirm the inferred fields,
and the dataset is created, its table built, and its rows ingested — then
straight to the analyst. Templates are gone from the UI entirely; the
schema is derived from the file.

Removes TemplateList, TemplateEditor and InspectTemplateDialog (~1033
lines), the template tab and routes, and the two guidance cards that
advertised three paths when users only need one.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 完成标准

- [ ] `cd mateclaw-server && mvn test -Dtest='Dataset*Test,Excel*Test,Analytics*Test,DynamicTableServiceTest,SqlGuardTest'` 全绿
- [ ] `cd mateclaw-ui && node --max-old-space-size=6144 ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit` 仅剩 `TrialBanner.vue:77` 既存报错
- [ ] Playwright 实跑：拖文件 → 确认字段 → 创建 → 跳 chat → 列表出现数据集且行数正确
- [ ] `grep -rn "template" mateclaw-ui/src/views/analytics/ mateclaw-ui/src/api/analytics.ts` 无命中
- [ ] `grep -rn "dataset_id" mateclaw-server/src/main/java/vip/mate/analytics/` 仅命中 `mate_dataset_field` / `mate_dataset_upload_log` 的元数据查询，物理表 SQL 中不再出现

## 第一期不做（留给二三期）

- CSV 支持（第二期）—— `accept=".xlsx"` 与 `uploadSub` 文案本期保持现状
- `AnalyticsSchemaTool` 的 `workspace_id = 1L` 硬编码、`SqlGuard` 归属校验、前端 `workspaceId()` 的 `'1'` 兜底、agentId 后端下发（第三期）
- 外部数据源（`DatasourceList.vue`）完全不动
