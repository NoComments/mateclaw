# 数据分析专家 Phase 1 — MVP 闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Architecture SSOT**: `docs/architecture/data-analyst.md`（先读它再读本文件，了解 4 阶段路线图与设计决策）
> **Branch**: `feat/data-analyst-expert`（已建好，自 `baseline/v1.3.0`）

**Goal:** 用户登录后台 → 注册一个数据集模板 → 上传一个畜牧业 xlsx 文件 → 在「数据分析」对话页问"按市汇总期末存栏"→ 得到表格 + 柱状图 + 可导出 xlsx。

**Architecture:** 模板登记制（先注册模板再上传数据），同模板多次上传追加到同一动态物理表。后端 `vip.mate.analytics` 提供 5 个 @Tool 给数字员工"数据分析专家"（Agent ID 1000000020）。前端新建 `/analytics` 一级菜单。SQL 安全靠 JSQLParser AST 校验 + 表/列白名单 + 自动 LIMIT。

**Tech Stack:** Spring Boot 3.5 / Java 21 / MyBatis-Plus / Flyway / Apache POI 5.x / JSQLParser（来自 mybatis-plus-jsqlparser）/ Spring AI Alibaba / Vue 3 + Vite + TS / ECharts。

---

## 阅读顺序

1. **必读**：`docs/architecture/data-analyst.md`（§1-§7 关键决策）
2. **必读**：根 `CLAUDE.md`（红线、迁移双写、Web MVC 等）
3. **必读**：`mateclaw-server/CLAUDE.md`（Java/Spring 规则、@Tool、StateGraph 约束）
4. **必读**：`mateclaw-ui/CLAUDE.md`（5 步加页清单、i18n、Pinia 规则）
5. 参考样例：`vip.mate.tool.builtin.DateTimeTool`（@Tool 风格）、`db/migration/h2/V113__skill_builder_agent.sql`（Agent 种子 + 工具绑定写法）、`mateclaw-server/src/main/java/vip/mate/workflow/`（动态 SQL/DDL 处理样例）

---

## 全局约定

- **TDD**：每个 Service/Tool 先写测试再写实现；测试用 H2 内存 + Mockito
- **迁移双写**：每个 migration 必须同时放到 `db/migration/h2/` 和 `db/migration/mysql/`，使用同一版本号
- **下一个迁移号**：从 V115 开始（V114 已被 info-harvester 占用）
- **包名**：`vip.mate.analytics`
- **提交节奏**：每完成一个 Task 提交一次，commit msg 用 `feat(analytics): ...` / `test(analytics): ...`
- **不引入新依赖**：JSQLParser 已通过 `mybatis-plus-jsqlparser` 传递依赖，POI 已用于 docx 生成

---

## 任务依赖图

```
T1 (DB schema) ──┬──> T2 (Template entities) ──┬──> T3 (TemplateService) ──> T4 (TemplateController)
                 └──> T5 (Dataset entities)  ──┴──> T6 (DatasetService)  ──> T7 (DatasetController)

T3,T5 ──> T8 (DynamicTableService) ──> T9 (ExcelHeaderMatcher) ──> T10 (ExcelParseService)
                                                                 ──> T11 (ExcelIngestService) ──> T12 (UploadController)

T6,T8 ──> T13 (SqlGuard) ──> T14 (AnalyticsQueryTool)
T6     ──> T15 (AnalyticsSchemaTool)
T14    ──> T16 (AnalyticsProfileTool)
       ──> T17 (AnalyticsChartTool)
T14    ──> T18 (AnalyticsExportTool)

T15-T18 ──> T19 (V116 seed agent + tool binding) ──> T20 (SKILL.md)

T4,T7,T12 ──> T21 (Frontend types + API client)
T21    ──> T22 (TemplateList + TemplateEditor)
       ──> T23 (DatasetList + UploadDialog + Preview)
       ──> T24 (AnalystChat)
T22-T24 ──> T25 (Sidebar nav + i18n + router)

T25    ──> T26 (E2E smoke with 畜牧业 xls)
```

---

## Task 1: 数据库 Schema — 元数据表

**Files:**
- Create: `mateclaw-server/src/main/resources/db/migration/h2/V115__analytics_foundation.sql`
- Create: `mateclaw-server/src/main/resources/db/migration/mysql/V115__analytics_foundation.sql`

- [ ] **Step 1.1: 编写 H2 迁移**

写入 `db/migration/h2/V115__analytics_foundation.sql`：

```sql
-- V115: 数据分析模块 Phase 1 元数据基础表。
-- 4 张表：模板、模板字段、数据集、上传日志。
-- 动态业务表 dataset_<template.code> 不在此迁移管理，由 DynamicTableService 运行时按需创建。

CREATE TABLE IF NOT EXISTS mate_dataset_template (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    workspace_id        BIGINT       NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(1024),
    category            VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM',
    partition_keys      VARCHAR(256) NOT NULL DEFAULT '[]',
    physical_table      VARCHAR(96)  NOT NULL,
    applied_ddl_hash    VARCHAR(64),
    enabled             TINYINT      NOT NULL DEFAULT 1,
    creator             BIGINT,
    updater             BIGINT,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mate_dataset_template_code ON mate_dataset_template (workspace_id, code);

CREATE TABLE IF NOT EXISTS mate_dataset_template_field (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    template_id         BIGINT       NOT NULL,
    field_code          VARCHAR(64)  NOT NULL,
    field_name          VARCHAR(256) NOT NULL,
    field_type          VARCHAR(16)  NOT NULL,
    field_unit          VARCHAR(32),
    semantic            VARCHAR(512),
    is_partition_key    TINYINT      NOT NULL DEFAULT 0,
    is_nullable         TINYINT      NOT NULL DEFAULT 1,
    ordinal             INT          NOT NULL,
    excel_header        VARCHAR(512) NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mate_dataset_template_field_code ON mate_dataset_template_field (template_id, field_code);
CREATE INDEX IF NOT EXISTS idx_mate_dataset_template_field_template ON mate_dataset_template_field (template_id);

CREATE TABLE IF NOT EXISTS mate_dataset (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    workspace_id        BIGINT       NOT NULL,
    template_id         BIGINT       NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(1024),
    row_count           INT          NOT NULL DEFAULT 0,
    last_upload_at      TIMESTAMP,
    creator             BIGINT,
    updater             BIGINT,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mate_dataset_template ON mate_dataset (template_id);
CREATE INDEX IF NOT EXISTS idx_mate_dataset_workspace ON mate_dataset (workspace_id);

CREATE TABLE IF NOT EXISTS mate_dataset_upload_log (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    dataset_id          BIGINT       NOT NULL,
    file_name           VARCHAR(256) NOT NULL,
    file_size           BIGINT       NOT NULL,
    rows_received       INT          NOT NULL DEFAULT 0,
    rows_inserted       INT          NOT NULL DEFAULT 0,
    rows_rejected       INT          NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL,
    error_summary       VARCHAR(2048),
    uploader            BIGINT,
    upload_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mate_dataset_upload_log_dataset ON mate_dataset_upload_log (dataset_id);
```

- [ ] **Step 1.2: 编写 MySQL 迁移**

同样内容写到 `db/migration/mysql/V115__analytics_foundation.sql`，只需把 `TINYINT` 字段加上 `(1)`、`TIMESTAMP DEFAULT CURRENT_TIMESTAMP` 改为 `DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)`（对 update_time 列），并加 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`：

```sql
-- 与 H2 同结构，MySQL 方言。
CREATE TABLE IF NOT EXISTS mate_dataset_template (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    workspace_id        BIGINT       NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(1024),
    category            VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM',
    partition_keys      VARCHAR(256) NOT NULL DEFAULT '[]',
    physical_table      VARCHAR(96)  NOT NULL,
    applied_ddl_hash    VARCHAR(64),
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    creator             BIGINT,
    updater             BIGINT,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_mate_dataset_template_code (workspace_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ... 同理 template_field / dataset / upload_log（按 H2 结构改方言）
```

- [ ] **Step 1.3: 启动 dev profile 验证迁移**

```bash
cd mateclaw-server
mvn spring-boot:run
```

预期：日志出现 `Successfully applied 1 migration to schema "PUBLIC", now at version v115`。无报错即停止进程。

- [ ] **Step 1.4: Commit**

```bash
git add mateclaw-server/src/main/resources/db/migration/h2/V115__analytics_foundation.sql \
        mateclaw-server/src/main/resources/db/migration/mysql/V115__analytics_foundation.sql
git commit -m "feat(analytics): add V115 metadata schema for dataset template and upload log"
```

---

## Task 2: Template 实体 + Repository

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplate.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateField.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateRepository.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateFieldRepository.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/template/FieldType.java`

参考样例：`vip.mate.workflow` 包下的实体 + Repository 写法（MyBatis-Plus `@TableName` + `BaseMapper`）。

- [ ] **Step 2.1: FieldType 枚举**

```java
package vip.mate.analytics.template;

public enum FieldType {
    STRING, INT, DECIMAL, BOOLEAN, DATE;

    public static FieldType fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("Unknown FieldType: " + s); }
    }
}
```

- [ ] **Step 2.2: DatasetTemplate 实体**

```java
package vip.mate.analytics.template;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("mate_dataset_template")
public class DatasetTemplate {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long workspaceId;
    private String code;
    private String name;
    private String description;
    private String category;
    private String partitionKeys;   // JSON array string
    private String physicalTable;
    private String appliedDdlHash;
    private Integer enabled;
    private Long creator;
    private Long updater;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2.3: DatasetTemplateField 实体**

类似 2.2，字段映射 V115 中 `mate_dataset_template_field` 表所有列。`fieldType` 字段为 `String`（在 Service 层转 `FieldType` 枚举）。

- [ ] **Step 2.4: Repository（Mapper）**

```java
package vip.mate.analytics.template;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetTemplateRepository extends BaseMapper<DatasetTemplate> {}
```

同理 `DatasetTemplateFieldRepository`。

- [ ] **Step 2.5: 编译验证**

```bash
cd mateclaw-server && mvn -q compile
```

预期：BUILD SUCCESS。

- [ ] **Step 2.6: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/analytics/template/
git commit -m "feat(analytics): add DatasetTemplate entities and repositories"
```

---

## Task 3: DatasetTemplateService — CRUD + 字段编辑保护

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateService.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/template/DatasetTemplateServiceTest.java`

**职责**：
- 创建模板（同时插入 fields）
- 列表 / 详情 / 启停
- 字段追加（only append）
- 拒绝破坏性变更（删除/改类型）当模板已被任何 Dataset 引用
- `physicalTable` 由 code 推导：`dataset_` + `code.replace('-', '_').toLowerCase()`

- [ ] **Step 3.1: 写测试 `DatasetTemplateServiceTest`**

```java
package vip.mate.analytics.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.analytics.dataset.DatasetRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetTemplateServiceTest {

    @Mock DatasetTemplateRepository templateRepo;
    @Mock DatasetTemplateFieldRepository fieldRepo;
    @Mock DatasetRepository datasetRepo;
    @InjectMocks DatasetTemplateService service;

    @Test
    void create_assignsPhysicalTableFromCode() {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode("Livestock-Poultry");
        t.setName("家禽季报");
        service.create(t, List.of(stringField("farm_code", "养殖场编码", 0)));
        assertThat(t.getPhysicalTable()).isEqualTo("dataset_livestock_poultry");
    }

    @Test
    void rejectFieldRemovalWhenDatasetExists() {
        when(datasetRepo.countByTemplateId(1L)).thenReturn(3L);
        assertThatThrownBy(() -> service.removeField(1L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("已有数据集");
    }

    @Test
    void appendField_allowedWhenDatasetExists() {
        when(datasetRepo.countByTemplateId(1L)).thenReturn(3L);
        DatasetTemplateField newField = stringField("new_col", "新增列", 99);
        service.appendField(1L, newField);
        // 验证调用了 fieldRepo.insert
        org.mockito.Mockito.verify(fieldRepo).insert(any());
    }

    private DatasetTemplateField stringField(String code, String name, int ordinal) {
        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode(code); f.setFieldName(name);
        f.setFieldType("STRING"); f.setOrdinal(ordinal);
        f.setExcelHeader(name);
        return f;
    }
}
```

- [ ] **Step 3.2: 跑测试验证失败**

```bash
mvn test -Dtest=DatasetTemplateServiceTest
```
预期：编译失败（Service/DatasetRepository 尚未存在）。

- [ ] **Step 3.3: 实现 Service（最小可过测试）**

```java
package vip.mate.analytics.template;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.analytics.dataset.DatasetRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DatasetTemplateService {
    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;
    private final DatasetRepository datasetRepo;

    @Transactional
    public DatasetTemplate create(DatasetTemplate t, List<DatasetTemplateField> fields) {
        t.setPhysicalTable(toPhysicalTable(t.getCode()));
        if (t.getEnabled() == null) t.setEnabled(1);
        templateRepo.insert(t);
        int i = 0;
        for (DatasetTemplateField f : fields) {
            f.setTemplateId(t.getId());
            if (f.getOrdinal() == null) f.setOrdinal(i++);
            FieldType.fromString(f.getFieldType()); // 校验
            fieldRepo.insert(f);
        }
        return t;
    }

    public void appendField(Long templateId, DatasetTemplateField field) {
        field.setTemplateId(templateId);
        FieldType.fromString(field.getFieldType());
        fieldRepo.insert(field);
    }

    public void removeField(Long templateId, Long fieldId) {
        if (datasetRepo.countByTemplateId(templateId) > 0) {
            throw new IllegalStateException("模板下已有数据集，禁止删除字段（仅允许追加）");
        }
        fieldRepo.deleteById(fieldId);
    }

    static String toPhysicalTable(String code) {
        return "dataset_" + code.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
```

> `DatasetRepository.countByTemplateId` 暂时不存在 — Task 5 创建时补上（先用 `@Mock` 占位 OK，正式跑要 Task 5 完成）。本任务测试只验证 Service 逻辑，不连数据库。

- [ ] **Step 3.4: 先创建 DatasetRepository 占位**

为了让测试编译过，先建一个最小空 mapper（Task 5 再补全）：

```java
// mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetRepository.java
package vip.mate.analytics.dataset;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatasetRepository {
    @Select("SELECT COUNT(*) FROM mate_dataset WHERE template_id = #{tid}")
    long countByTemplateId(@Param("tid") Long tid);
}
```

- [ ] **Step 3.5: 跑测试通过**

```bash
mvn test -Dtest=DatasetTemplateServiceTest
```
预期：PASS。

- [ ] **Step 3.6: Commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/analytics/template/DatasetTemplateService.java \
        mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetRepository.java \
        mateclaw-server/src/test/java/vip/mate/analytics/template/DatasetTemplateServiceTest.java
git commit -m "feat(analytics): DatasetTemplateService with append-only field protection"
```

---

## Task 4: DatasetTemplateController — REST API

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetTemplateController.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/controller/DatasetTemplateControllerTest.java`

参考样例：`vip.mate.workflow.controller.WorkflowController`（同项目 REST 控制器风格、统一返回包装、AuthContext 取 workspace）。

**接口**：
| Method | Path | Body | 说明 |
|---|---|---|---|
| GET    | `/api/analytics/templates` | - | 列出当前 workspace 的模板 |
| POST   | `/api/analytics/templates` | `{template, fields[]}` | 创建模板 + 字段 |
| GET    | `/api/analytics/templates/{id}` | - | 模板详情（含 fields） |
| POST   | `/api/analytics/templates/{id}/fields` | field | 追加字段 |
| DELETE | `/api/analytics/templates/{id}/fields/{fieldId}` | - | 删除字段（受保护） |
| PUT    | `/api/analytics/templates/{id}/enabled` | `{enabled:bool}` | 启停 |

- [ ] **Step 4.1: 写控制器测试**

用 `@WebMvcTest` + `MockMvc` 写 1 个 happy path 测试（GET list）+ 1 个负面（DELETE field on locked template 返回 409）。完整代码见参考样例 `WorkflowControllerTest`。

- [ ] **Step 4.2: 实现 Controller**

```java
package vip.mate.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.analytics.template.*;
import vip.mate.auth.AuthContext;
import vip.mate.common.api.R;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics/templates")
@RequiredArgsConstructor
public class DatasetTemplateController {
    private final DatasetTemplateService service;
    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;

    @GetMapping
    public R<List<DatasetTemplate>> list() {
        return R.ok(templateRepo.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetTemplate>()
                .eq("workspace_id", AuthContext.currentWorkspaceId())));
    }

    public record CreateRequest(DatasetTemplate template, List<DatasetTemplateField> fields) {}

    @PostMapping
    public R<DatasetTemplate> create(@RequestBody CreateRequest req) {
        req.template().setWorkspaceId(AuthContext.currentWorkspaceId());
        req.template().setCreator(AuthContext.currentUserId());
        return R.ok(service.create(req.template(), req.fields()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        DatasetTemplate t = templateRepo.selectById(id);
        List<DatasetTemplateField> fields = fieldRepo.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DatasetTemplateField>()
                .eq("template_id", id).orderByAsc("ordinal"));
        return R.ok(Map.of("template", t, "fields", fields));
    }

    @PostMapping("/{id}/fields")
    public R<Void> appendField(@PathVariable Long id, @RequestBody DatasetTemplateField f) {
        service.appendField(id, f);
        return R.ok();
    }

    @DeleteMapping("/{id}/fields/{fid}")
    public R<Void> removeField(@PathVariable Long id, @PathVariable Long fid) {
        service.removeField(id, fid);
        return R.ok();
    }
}
```

> 若 `R`、`AuthContext` 类名不一致，按 Repo 实际类调整。先 `grep "class R " mateclaw-server/src/main/java/vip/mate/common/` 确认。

- [ ] **Step 4.3: 跑测试**

```bash
mvn test -Dtest=DatasetTemplateControllerTest
```

- [ ] **Step 4.4: Commit**

```
feat(analytics): REST API for dataset template management
```

---

## Task 5: Dataset + UploadLog 实体 / Repository / Service

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/Dataset.java`
- Update: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetRepository.java`（升级到 BaseMapper）
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetUploadLog.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetUploadLogRepository.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/dataset/DatasetService.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/dataset/DatasetServiceTest.java`

按 Task 2 模式建实体；`DatasetRepository` 改为 `extends BaseMapper<Dataset>` 并新增 `@Select("...") long countByTemplateId(Long)`。

`DatasetService` 职责：创建空 Dataset（绑定 template）、按 workspace 列表、详情、删除（同时 DELETE FROM 动态表 WHERE dataset_id=?）。

- [ ] **Step 5.1: 写测试**（验证 create 自动填 workspace_id；delete 同时清理动态表）
- [ ] **Step 5.2: 实现实体 + Repository + Service**
- [ ] **Step 5.3: 跑测试**：`mvn test -Dtest=DatasetServiceTest`
- [ ] **Step 5.4: Commit**：`feat(analytics): Dataset entity, upload log and service`

---

## Task 6: DatasetController — REST API

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetController.java`

接口：
| Method | Path | 说明 |
|---|---|---|
| GET    | `/api/analytics/datasets` | 列出 |
| POST   | `/api/analytics/datasets` | 创建（带 template_id + name） |
| GET    | `/api/analytics/datasets/{id}` | 详情 |
| GET    | `/api/analytics/datasets/{id}/preview?limit=100` | 前 N 行 |
| GET    | `/api/analytics/datasets/{id}/uploads` | 上传历史 |
| DELETE | `/api/analytics/datasets/{id}` | 删除 |

- [ ] **Step 6.1**: 写测试
- [ ] **Step 6.2**: 实现 Controller
- [ ] **Step 6.3**: `mvn test -Dtest=DatasetControllerTest`
- [ ] **Step 6.4**: Commit `feat(analytics): REST API for dataset lifecycle`

---

## Task 7: DynamicTableService — 动态 DDL

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/storage/PhysicalColumnType.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/storage/DynamicTableService.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/storage/DynamicTableServiceTest.java`

**职责**：
1. `ensureTable(DatasetTemplate template, List<DatasetTemplateField> fields)` — 若物理表不存在则 CREATE；若 fields 比当前列多，ALTER ADD COLUMN（不删/不改）
2. `applied_ddl_hash` 字段保存当前 fields 的指纹（SHA256(JSON)），跳过重复 DDL
3. DDL 仅识别白名单 SQL 关键字，反斜杠/分号/注释一律拒绝
4. 表名/列名再做一次正则白名单 `^[a-z][a-z0-9_]{0,62}$`

- [ ] **Step 7.1: PhysicalColumnType 映射**

```java
package vip.mate.analytics.storage;

import vip.mate.analytics.template.FieldType;

public final class PhysicalColumnType {
    public static String sqlType(FieldType t) {
        return switch (t) {
            case STRING  -> "VARCHAR(512)";
            case INT     -> "BIGINT";
            case DECIMAL -> "DECIMAL(20,4)";
            case BOOLEAN -> "TINYINT";
            case DATE    -> "DATE";
        };
    }
}
```

- [ ] **Step 7.2: 写测试** — 三个 case：
  - 首次 ensureTable 生成 CREATE TABLE 语句包含所有字段
  - 第二次同 fields，hash 命中，不执行 DDL
  - 追加字段时生成 ALTER TABLE ADD COLUMN

测试用 H2 + `JdbcTemplate` 真实执行，验证 information_schema 中表/列存在。

```java
@SpringBootTest
@ActiveProfiles("test")
class DynamicTableServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DynamicTableService service;

    @Test
    void ensureTable_createsPhysicalTable() {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode("test_t1"); t.setPhysicalTable("dataset_test_t1");
        t.setPartitionKeys("[\"period\"]");
        service.ensureTable(t, List.of(
            field("farm_code","STRING",0,true),
            field("end_stock","DECIMAL",1,false)
        ));
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='DATASET_TEST_T1'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }
    // ... 另两个 case
}
```

- [ ] **Step 7.3: 实现 DynamicTableService**

核心代码骨架：

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicTableService {
    private static final Pattern SAFE = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private final JdbcTemplate jdbc;
    private final DatasetTemplateRepository templateRepo;

    public void ensureTable(DatasetTemplate t, List<DatasetTemplateField> fields) {
        String table = t.getPhysicalTable();
        if (!SAFE.matcher(table).matches()) throw new IllegalArgumentException("unsafe table: " + table);

        String hash = hash(fields);
        if (hash.equals(t.getAppliedDdlHash()) && tableExists(table)) return;

        if (!tableExists(table)) {
            jdbc.execute(buildCreate(table, fields));
        } else {
            Set<String> existing = existingColumns(table);
            for (DatasetTemplateField f : fields) {
                if (!existing.contains(f.getFieldCode().toLowerCase())) {
                    if (!SAFE.matcher(f.getFieldCode()).matches())
                        throw new IllegalArgumentException("unsafe column: " + f.getFieldCode());
                    jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + f.getFieldCode()
                        + " " + PhysicalColumnType.sqlType(FieldType.fromString(f.getFieldType())));
                }
            }
        }
        t.setAppliedDdlHash(hash);
        templateRepo.updateById(t);
    }

    private String buildCreate(String table, List<DatasetTemplateField> fields) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(table).append(" (\n")
            .append("  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n")
            .append("  dataset_id BIGINT NOT NULL,\n")
            .append("  upload_log_id BIGINT NOT NULL,\n");
        for (DatasetTemplateField f : fields) {
            if (!SAFE.matcher(f.getFieldCode()).matches())
                throw new IllegalArgumentException("unsafe column: " + f.getFieldCode());
            sb.append("  ").append(f.getFieldCode()).append(" ")
              .append(PhysicalColumnType.sqlType(FieldType.fromString(f.getFieldType())))
              .append(f.getIsNullable() != null && f.getIsNullable() == 0 ? " NOT NULL" : "")
              .append(",\n");
        }
        sb.append("  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP\n)");
        return sb.toString();
    }

    private boolean tableExists(String table) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME)=UPPER(?)",
            Integer.class, table);
        return c != null && c > 0;
    }

    private Set<String> existingColumns(String table) {
        return new HashSet<>(jdbc.queryForList(
            "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME)=UPPER(?)",
            String.class, table));
    }

    private String hash(List<DatasetTemplateField> fields) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (DatasetTemplateField f : fields) {
                md.update((f.getFieldCode() + "|" + f.getFieldType() + "\n").getBytes());
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
```

- [ ] **Step 7.4**: `mvn test -Dtest=DynamicTableServiceTest` → PASS
- [ ] **Step 7.5**: Commit `feat(analytics): DynamicTableService for safe DDL on dataset_* tables`

---

## Task 8: ExcelHeaderMatcher

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/upload/ExcelHeaderMatcher.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/upload/ExcelHeaderMatcherTest.java`

**职责**：把上传 xlsx 的第一行（表头）匹配到 `DatasetTemplateField.excelHeader`，返回 `Map<columnIndex, fieldCode>`。匹配策略：
1. 完全相等（去空格）
2. 去掉括号内容后相等（如 `"期末存栏（只）"` → `"期末存栏"`）
3. 找不到 → 抛 `ExcelHeaderMismatchException` 列出缺失的字段

- [ ] **Step 8.1: 写测试**

```java
@Test
void match_exactHeader() {
    var fields = List.of(field("farm_code", "养殖场编码"), field("end_stock", "期末存栏（只）"));
    var headers = List.of("养殖场编码", "期末存栏（只）");
    var result = ExcelHeaderMatcher.match(headers, fields);
    assertThat(result).containsEntry(0, "farm_code").containsEntry(1, "end_stock");
}

@Test
void match_throwsOnMissingField() {
    var fields = List.of(field("farm_code", "养殖场编码"), field("end_stock", "期末存栏（只）"));
    var headers = List.of("养殖场编码"); // 缺 end_stock
    assertThatThrownBy(() -> ExcelHeaderMatcher.match(headers, fields))
        .hasMessageContaining("end_stock");
}
```

- [ ] **Step 8.2: 实现**

```java
public final class ExcelHeaderMatcher {
    public static Map<Integer, String> match(List<String> headers, List<DatasetTemplateField> fields) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Set<String> matched = new HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).trim();
            for (DatasetTemplateField f : fields) {
                if (matched.contains(f.getFieldCode())) continue;
                if (h.equals(f.getExcelHeader().trim())
                    || stripParens(h).equals(stripParens(f.getExcelHeader()))) {
                    result.put(i, f.getFieldCode());
                    matched.add(f.getFieldCode());
                    break;
                }
            }
        }
        List<String> missing = fields.stream()
            .filter(f -> !matched.contains(f.getFieldCode()))
            .map(DatasetTemplateField::getFieldCode)
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Excel 缺失字段: " + String.join(", ", missing));
        }
        return result;
    }

    private static String stripParens(String s) {
        return s == null ? "" : s.replaceAll("[（(].*?[)）]", "").trim();
    }
}
```

- [ ] **Step 8.3**: 跑测试 → PASS
- [ ] **Step 8.4**: Commit `feat(analytics): ExcelHeaderMatcher with paren-tolerant matching`

---

## Task 9: ExcelParseService

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/upload/ParsedRow.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/upload/ExcelParseService.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/upload/ExcelParseServiceTest.java`

**职责**：用 POI `XSSFWorkbook` 读取 xlsx 第一个 sheet（或指定 sheet 名），按 header matcher 把每行转为 `ParsedRow(Map<fieldCode, Object>)`。类型转换：INT → Long、DECIMAL → BigDecimal、BOOLEAN → 0/1、DATE → LocalDate；空 cell → null。

- [ ] **Step 9.1: 写测试**（用 classpath 测试资源 `src/test/resources/analytics/poultry-mini.xlsx`，3 行家禽数据）。资源用 POI 编程生成在 `@BeforeAll`：

```java
@BeforeAll
static void prepare() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook(); var fos = new FileOutputStream("target/poultry-mini.xlsx")) {
        var sh = wb.createSheet("家禽");
        var head = sh.createRow(0);
        head.createCell(0).setCellValue("养殖场编码");
        head.createCell(1).setCellValue("期末存栏（只）");
        var r1 = sh.createRow(1);
        r1.createCell(0).setCellValue("4101001");
        r1.createCell(1).setCellValue(325600);
        wb.write(fos);
    }
}

@Test
void parse_returnsAllRowsMappedByFieldCode() { ... }
```

- [ ] **Step 9.2: 实现 ExcelParseService**

```java
@Service
public class ExcelParseService {
    public List<ParsedRow> parse(InputStream in, List<DatasetTemplateField> fields, String sheetName) {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = sheetName != null ? wb.getSheet(sheetName) : wb.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell c = headerRow.getCell(i);
                headers.add(c == null ? "" : c.getStringCellValue());
            }
            Map<Integer, String> mapping = ExcelHeaderMatcher.match(headers, fields);
            Map<String, FieldType> typeByCode = fields.stream().collect(
                Collectors.toMap(DatasetTemplateField::getFieldCode,
                    f -> FieldType.fromString(f.getFieldType())));

            List<ParsedRow> result = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, Object> values = new LinkedHashMap<>();
                for (var e : mapping.entrySet()) {
                    values.put(e.getValue(), convert(row.getCell(e.getKey()), typeByCode.get(e.getValue())));
                }
                result.add(new ParsedRow(r, values));
            }
            return result;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private Object convert(Cell c, FieldType t) {
        if (c == null || c.getCellType() == CellType.BLANK) return null;
        return switch (t) {
            case STRING -> c.getCellType() == CellType.STRING ? c.getStringCellValue()
                          : c.getCellType() == CellType.NUMERIC ? String.valueOf((long) c.getNumericCellValue())
                          : c.toString();
            case INT -> (long) c.getNumericCellValue();
            case DECIMAL -> BigDecimal.valueOf(c.getNumericCellValue());
            case BOOLEAN -> c.getBooleanCellValue() ? 1 : 0;
            case DATE -> c.getLocalDateTimeCellValue().toLocalDate();
        };
    }
}

public record ParsedRow(int rowNumber, Map<String, Object> values) {}
```

- [ ] **Step 9.3**: 跑测试 PASS
- [ ] **Step 9.4**: Commit `feat(analytics): ExcelParseService for xlsx → ParsedRow streaming`

---

## Task 10: ExcelIngestService — 批量写库

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/upload/IngestResult.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/upload/ExcelIngestService.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/upload/ExcelIngestServiceTest.java`

**职责**：拿到 `List<ParsedRow>` + Dataset + Template + Fields，组装 INSERT，500 行一批用 `JdbcTemplate.batchUpdate`，捕获每行异常计入 `rows_rejected`，最后更新 `mate_dataset.row_count`、`last_upload_at`。

```java
@Service
@RequiredArgsConstructor
public class ExcelIngestService {
    private static final int BATCH = 500;
    private final JdbcTemplate jdbc;
    private final DatasetRepository datasetRepo;

    @Transactional
    public IngestResult ingest(Dataset ds, DatasetTemplate tpl,
                               List<DatasetTemplateField> fields, List<ParsedRow> rows,
                               Long uploadLogId) {
        if (rows.isEmpty()) return new IngestResult(0, 0, List.of());
        String sql = buildInsert(tpl.getPhysicalTable(), fields);
        int inserted = 0, rejected = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += BATCH) {
            List<ParsedRow> batch = rows.subList(i, Math.min(i + BATCH, rows.size()));
            try {
                jdbc.batchUpdate(sql, batch, batch.size(), (ps, row) -> {
                    ps.setLong(1, ds.getId());
                    ps.setLong(2, uploadLogId);
                    int idx = 3;
                    for (DatasetTemplateField f : fields) {
                        ps.setObject(idx++, row.values().get(f.getFieldCode()));
                    }
                });
                inserted += batch.size();
            } catch (DataAccessException e) {
                rejected += batch.size();
                errors.add("行 " + batch.get(0).rowNumber() + "+: " + e.getMostSpecificCause().getMessage());
            }
        }
        ds.setRowCount(ds.getRowCount() + inserted);
        ds.setLastUploadAt(LocalDateTime.now());
        datasetRepo.updateById(ds);
        return new IngestResult(inserted, rejected, errors);
    }

    private String buildInsert(String table, List<DatasetTemplateField> fields) {
        String cols = fields.stream().map(DatasetTemplateField::getFieldCode).collect(Collectors.joining(", "));
        String marks = fields.stream().map(f -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (dataset_id, upload_log_id, " + cols + ") VALUES (?, ?, " + marks + ")";
    }
}

public record IngestResult(int inserted, int rejected, List<String> errors) {}
```

- [ ] **Step 10.1**: 写 `@SpringBootTest` 测试（H2，真实建表 + 写入 + 查回）
- [ ] **Step 10.2**: 实现
- [ ] **Step 10.3**: 测试 PASS
- [ ] **Step 10.4**: Commit `feat(analytics): ExcelIngestService for batched insert with per-batch error capture`

---

## Task 11: UploadController — 上传端点

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetUploadController.java`

```java
@RestController
@RequestMapping("/api/analytics/datasets")
@RequiredArgsConstructor
public class DatasetUploadController {
    private final DatasetRepository datasetRepo;
    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;
    private final ExcelParseService parse;
    private final ExcelIngestService ingest;
    private final DynamicTableService dynamicTable;
    private final DatasetUploadLogRepository uploadLogRepo;

    @PostMapping(value = "/{id}/upload", consumes = MULTIPART_FORM_DATA_VALUE)
    public R<DatasetUploadLog> upload(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "sheet", required = false) String sheet) throws IOException {
        if (file.getSize() > 50L * 1024 * 1024) throw new IllegalArgumentException("文件超过 50MB");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx"))
            throw new IllegalArgumentException("仅支持 .xlsx 格式");

        Dataset ds = datasetRepo.selectById(id);
        DatasetTemplate tpl = templateRepo.selectById(ds.getTemplateId());
        List<DatasetTemplateField> fields = fieldRepo.selectList(
            new QueryWrapper<DatasetTemplateField>().eq("template_id", tpl.getId()).orderByAsc("ordinal"));
        dynamicTable.ensureTable(tpl, fields);

        DatasetUploadLog log = new DatasetUploadLog();
        log.setDatasetId(id); log.setFileName(name); log.setFileSize(file.getSize());
        log.setStatus("PROCESSING"); log.setUploader(AuthContext.currentUserId());
        uploadLogRepo.insert(log);

        try (var in = file.getInputStream()) {
            List<ParsedRow> rows = parse.parse(in, fields, sheet);
            log.setRowsReceived(rows.size());
            IngestResult result = ingest.ingest(ds, tpl, fields, rows, log.getId());
            log.setRowsInserted(result.inserted());
            log.setRowsRejected(result.rejected());
            log.setStatus(result.rejected() == 0 ? "SUCCESS" : "PARTIAL");
            log.setErrorSummary(String.join("; ", result.errors()));
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorSummary(e.getMessage());
        } finally {
            uploadLogRepo.updateById(log);
        }
        return R.ok(log);
    }
}
```

- [ ] **Step 11.1**: 写 `MockMvc` 测试（上传 mini xlsx，断言 returns SUCCESS + rows_inserted > 0）
- [ ] **Step 11.2**: 实现
- [ ] **Step 11.3**: Commit `feat(analytics): /api/analytics/datasets/{id}/upload endpoint`

---

## Task 12: SqlGuard

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/guard/SqlGuardException.java`
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/guard/SqlGuard.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/tool/guard/SqlGuardTest.java`

**职责**：单一入口 `String safen(String sql)`，做以下校验：
1. JSQLParser 解析必须成功
2. 必须是 `Select` 单语句
3. 所有 FROM/JOIN 的表名必须以 `dataset_` 开头 或 在白名单 `mate_dataset, mate_dataset_template, mate_dataset_template_field`
4. 无 `INFORMATION_SCHEMA` / `MYSQL.` / `SYS.` 等
5. 无写关键字（INSERT/UPDATE/DELETE/DROP/ALTER/CREATE/GRANT/TRUNCATE）—— 解析器若返回 Select 实际已排除，但仍 regex 兜底
6. 若没有 LIMIT，追加 `LIMIT 10000`；若 LIMIT > 10000，改写为 10000

- [ ] **Step 12.1: 写测试**（10+ case，每个红线一个 negative）

```java
@Test
void rejectInsert() {
    assertThatThrownBy(() -> SqlGuard.safen("INSERT INTO dataset_x VALUES (1)"))
        .isInstanceOf(SqlGuardException.class);
}

@Test
void rejectInfoSchema() {
    assertThatThrownBy(() -> SqlGuard.safen("SELECT * FROM INFORMATION_SCHEMA.TABLES"))
        .isInstanceOf(SqlGuardException.class);
}

@Test
void rejectMultipleStatements() {
    assertThatThrownBy(() -> SqlGuard.safen("SELECT 1 FROM dataset_x; DROP TABLE dataset_x"))
        .isInstanceOf(SqlGuardException.class);
}

@Test
void rejectNonDatasetTable() {
    assertThatThrownBy(() -> SqlGuard.safen("SELECT * FROM mate_agent"))
        .isInstanceOf(SqlGuardException.class);
}

@Test
void acceptWhitelistedMetaTable() {
    String safe = SqlGuard.safen("SELECT * FROM mate_dataset");
    assertThat(safe).contains("LIMIT 10000");
}

@Test
void appendsLimitWhenAbsent() {
    String safe = SqlGuard.safen("SELECT * FROM dataset_x");
    assertThat(safe).endsWith(" LIMIT 10000");
}

@Test
void capsLimitTo10000() {
    String safe = SqlGuard.safen("SELECT * FROM dataset_x LIMIT 50000");
    assertThat(safe).contains("LIMIT 10000").doesNotContain("50000");
}

@Test
void acceptsJoinBetweenDatasetTables() {
    SqlGuard.safen("SELECT a.farm_code FROM dataset_a a JOIN dataset_b b ON a.id=b.id");
}
```

- [ ] **Step 12.2: 实现**

```java
package vip.mate.analytics.tool.guard;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.expression.LongValue;

import java.util.*;
import java.util.regex.Pattern;

public final class SqlGuard {
    private static final int MAX_LIMIT = 10000;
    private static final Set<String> META_WHITELIST = Set.of(
        "mate_dataset", "mate_dataset_template", "mate_dataset_template_field",
        "mate_dataset_upload_log");
    private static final Pattern WRITE_KEYWORD = Pattern.compile(
        "(?i)\\b(insert|update|delete|drop|alter|create|grant|truncate|replace|merge)\\b");

    public static String safen(String sql) {
        if (sql == null || sql.isBlank()) throw new SqlGuardException("空 SQL");
        if (sql.contains(";") && !sql.strip().endsWith(";")) throw new SqlGuardException("禁止多语句");
        if (WRITE_KEYWORD.matcher(sql).find()) throw new SqlGuardException("禁止写操作关键字");

        Statement stmt;
        try { stmt = CCJSqlParserUtil.parse(sql); }
        catch (Exception e) { throw new SqlGuardException("SQL 解析失败: " + e.getMessage()); }
        if (!(stmt instanceof Select select)) throw new SqlGuardException("仅允许 SELECT");

        // 检查所有表名
        new TableNamesFinder().visit(select).forEach(SqlGuard::assertTableAllowed);

        // 处理 LIMIT
        PlainSelect ps = select.getPlainSelect();
        if (ps == null) throw new SqlGuardException("仅支持 PlainSelect");
        Limit limit = ps.getLimit();
        if (limit == null) {
            Limit l = new Limit(); l.setRowCount(new LongValue(MAX_LIMIT)); ps.setLimit(l);
        } else if (limit.getRowCount() instanceof LongValue lv && lv.getValue() > MAX_LIMIT) {
            limit.setRowCount(new LongValue(MAX_LIMIT));
        }
        return select.toString();
    }

    private static void assertTableAllowed(String name) {
        String n = name.toLowerCase().replaceAll("`|\"", "");
        if (n.startsWith("information_schema") || n.startsWith("mysql.") || n.startsWith("sys.")
            || n.startsWith("performance_schema")) {
            throw new SqlGuardException("禁止访问系统表: " + n);
        }
        if (n.startsWith("dataset_")) return;
        if (META_WHITELIST.contains(n)) return;
        throw new SqlGuardException("非白名单表: " + n);
    }
}

// 简单实现：用 JSQLParser TablesNamesFinder
class TableNamesFinder {
    List<String> visit(Select select) {
        return new ArrayList<>(new net.sf.jsqlparser.util.TablesNamesFinder().getTableList(select));
    }
}
```

- [ ] **Step 12.3**: `mvn test -Dtest=SqlGuardTest` → 全 PASS
- [ ] **Step 12.4**: Commit `feat(analytics): SqlGuard with JSQLParser AST validation and LIMIT enforcement`

---

## Task 13: AnalyticsSchemaTool

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsSchemaTool.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsSchemaToolTest.java`

```java
@Component
@RequiredArgsConstructor
public class AnalyticsSchemaTool {
    private final DatasetRepository datasetRepo;
    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;

    @Tool(description = "List available datasets in current workspace, or describe one dataset's columns when datasetId is provided. Always call this first before analytics_query.")
    public String analyticsSchema(@ToolParam(description = "Optional dataset id; omit to list all", required = false) Long datasetId) {
        if (datasetId == null) {
            List<Dataset> all = datasetRepo.selectList(
                new QueryWrapper<Dataset>().eq("workspace_id", AuthContext.currentWorkspaceId()));
            return all.stream()
                .map(d -> String.format("- id=%d name=%s rows=%d template_id=%d",
                    d.getId(), d.getName(), d.getRowCount(), d.getTemplateId()))
                .collect(Collectors.joining("\n"));
        }
        Dataset d = datasetRepo.selectById(datasetId);
        DatasetTemplate t = templateRepo.selectById(d.getTemplateId());
        List<DatasetTemplateField> fields = fieldRepo.selectList(
            new QueryWrapper<DatasetTemplateField>().eq("template_id", t.getId()).orderByAsc("ordinal"));
        StringBuilder sb = new StringBuilder();
        sb.append("Dataset: ").append(d.getName()).append(" (table=").append(t.getPhysicalTable())
          .append(", rows=").append(d.getRowCount()).append(")\nColumns:\n");
        for (DatasetTemplateField f : fields) {
            sb.append("- ").append(f.getFieldCode()).append(" (").append(f.getFieldType()).append(")")
              .append(" — ").append(f.getFieldName());
            if (f.getFieldUnit() != null) sb.append(" 单位:").append(f.getFieldUnit());
            if (f.getSemantic() != null) sb.append(" — ").append(f.getSemantic());
            sb.append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 13.1**: 写测试（断言列表/详情两种调用返回串内容）
- [ ] **Step 13.2**: 实现
- [ ] **Step 13.3**: Commit `feat(analytics): analytics_schema tool`

---

## Task 14: AnalyticsQueryTool

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsQueryTool.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsQueryToolTest.java`

```java
@Component
@RequiredArgsConstructor
public class AnalyticsQueryTool {
    private final JdbcTemplate jdbc;

    @Tool(description = "Execute a read-only SELECT on dataset_* tables. Always call analytics_schema first to know column names. Auto-injects LIMIT 10000.")
    public Map<String, Object> analyticsQuery(@ToolParam(description = "SQL SELECT statement") String sql) {
        String safe = SqlGuard.safen(sql);
        jdbc.setQueryTimeout(30);
        List<Map<String, Object>> rows = jdbc.queryForList(safe);
        return Map.of(
            "executedSql", safe,
            "rowCount", rows.size(),
            "rows", rows
        );
    }
}
```

- [ ] **Step 14.1**: 写 `@SpringBootTest` 测试，先建一张 `dataset_test` 表插几行数据，调 tool 验证返回；再测一个 INSERT SQL 返回 SqlGuardException
- [ ] **Step 14.2**: 实现
- [ ] **Step 14.3**: Commit `feat(analytics): analytics_query tool with SqlGuard enforcement`

---

## Task 15: AnalyticsProfileTool

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsProfileTool.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsProfileToolTest.java`

**职责**：给定 datasetId + fieldCodes[]，对每个字段返回 `{count, nullCount, distinct, min, max, mean(数值字段), top5(类别字段)}`。底层用 SQL 聚合，复用 SqlGuard。

```java
@Tool(description = "Profile column(s) of a dataset: count/null%/distinct/min/max/mean for numerics, top values for strings.")
public Map<String, Object> analyticsProfile(@ToolParam Long datasetId, @ToolParam List<String> fieldCodes) {
    Dataset d = datasetRepo.selectById(datasetId);
    DatasetTemplate t = templateRepo.selectById(d.getTemplateId());
    Map<String, DatasetTemplateField> byCode = fieldRepo.selectList(
        new QueryWrapper<DatasetTemplateField>().eq("template_id", t.getId())).stream()
        .collect(Collectors.toMap(DatasetTemplateField::getFieldCode, f -> f));

    Map<String, Object> result = new LinkedHashMap<>();
    for (String code : fieldCodes) {
        DatasetTemplateField f = byCode.get(code);
        if (f == null) { result.put(code, Map.of("error", "unknown field")); continue; }
        // 对数值字段：COUNT(*)/COUNT(col)/MIN/MAX/AVG
        // 对字符串：COUNT(*)/COUNT(col)/COUNT(DISTINCT col) + top5
        // 全部走 SqlGuard
        // ... 略，按 FieldType 分支
        result.put(code, profileOneField(t.getPhysicalTable(), d.getId(), f));
    }
    return result;
}
```

- [ ] **Step 15.1**: 写测试
- [ ] **Step 15.2**: 实现
- [ ] **Step 15.3**: Commit `feat(analytics): analytics_profile tool`

---

## Task 16: AnalyticsChartTool

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsChartTool.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsChartToolTest.java`

**职责**：接受 `chartType` (`bar`/`line`/`pie`/`scatter`/`heatmap`)、`data: List<Map>`（通常是 query 工具的输出）、`xField`、`yField`、可选 `seriesField`，返回 ECharts option JSON。

```java
@Tool(description = "Build an ECharts option JSON for visualization. Supported chart types: bar, line, pie, scatter, heatmap.")
public Map<String, Object> analyticsChart(
    @ToolParam String chartType,
    @ToolParam List<Map<String, Object>> data,
    @ToolParam String xField,
    @ToolParam String yField,
    @ToolParam(required = false) String seriesField) {
    return switch (chartType.toLowerCase()) {
        case "bar"    -> bar(data, xField, yField, seriesField);
        case "line"   -> line(data, xField, yField, seriesField);
        case "pie"    -> pie(data, xField, yField);
        case "scatter" -> scatter(data, xField, yField);
        case "heatmap" -> heatmap(data, xField, yField);
        default -> throw new IllegalArgumentException("Unsupported chartType: " + chartType);
    };
}
// 每种图表方法返回 Map.of("xAxis", ..., "yAxis", ..., "series", ...)
```

- [ ] **Step 16.1**: 写测试（每种 chartType 一个 happy case）
- [ ] **Step 16.2**: 实现
- [ ] **Step 16.3**: Commit `feat(analytics): analytics_chart tool with ECharts option output`

---

## Task 17: AnalyticsExportTool

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/tool/AnalyticsExportTool.java`
- Create: `mateclaw-server/src/test/java/vip/mate/analytics/tool/AnalyticsExportToolTest.java`

**职责**：执行 SQL（经 SqlGuard），结果写到临时 xlsx 文件，返回下载 URL（复用 mateclaw 现有静态资源服务，例如把生成文件放 `${app.upload-root}/analytics/exports/` 并返回 `/api/analytics/exports/{filename}` 用 streaming endpoint 兜底）。

- [ ] **Step 17.1**: 复用项目已有的 `XlsxRenderTool` 风格写出（POI XSSFWorkbook）
- [ ] **Step 17.2**: 写测试（断言文件存在 + 行数正确）
- [ ] **Step 17.3**: 增加 `ExportDownloadController` 提供下载端点
- [ ] **Step 17.4**: Commit `feat(analytics): analytics_export tool with xlsx download`

---

## Task 18: V116 — 种子数字员工"数据分析专家"

**Files:**
- Create: `mateclaw-server/src/main/resources/db/migration/h2/V116__data_analyst_agent.sql`
- Create: `mateclaw-server/src/main/resources/db/migration/mysql/V116__data_analyst_agent.sql`

参考样例：完整看一遍 `db/migration/h2/V113__skill_builder_agent.sql`，照葫芦画瓢。

- [ ] **Step 18.1: 编写 H2 迁移**

```sql
-- V116: 种子「数据分析专家」数字员工
-- ID 1000000020，绑定 analytics_* 5 个工具
-- data-analyst skill 由 BuiltinSkillSeedService 在 Flyway 之后扫描 classpath:skills/data-analyst/

MERGE INTO mate_agent (id, name, description, agent_type, system_prompt, model_name, max_iterations, enabled, icon, tags, workspace_id, create_time, update_time, deleted)
KEY (id)
VALUES (
    1000000020,
    '数据分析专家',
    '上传 Excel 数据后通过对话进行汇总、画像、出图、校验、导出',
    'react',
    '你是「数据分析专家」，负责帮助调查员、辅调员对企业季报、住户问卷等结构化数据做分析。

【核心工作方式】
1. 用户提问后，第一件事必须调用 analytics_schema 了解当前 workspace 有哪些数据集和字段语义。
2. 然后用 analytics_query 跑 SQL。SQL 必须基于 schema 给出的物理列名 field_code。SQL 必带 WHERE dataset_id = ? 过滤。
3. 需要列分布时调 analytics_profile；需要可视化时调 analytics_chart；用户要下载结果时调 analytics_export。
4. 输出报告格式：先结论（一段话），再支撑数据（表格/图），最后给 1-3 条建议或追问。

【绝对禁止】
- 不要试图执行写操作 SQL（INSERT/UPDATE/DELETE/DROP），SqlGuard 会拦截，浪费往返
- 不要凭空编造字段名 — 必须以 analytics_schema 返回的为准
- 字段口径不清时（如「期末存栏」指什么时点）主动问用户

【输出规范】
- 数字带单位（来自字段 unit）
- 比较用百分比时保留两位小数
- 表格用 markdown，长表格只展示前 10 行 + 总数提示
',
    'qwen-plus',
    20,
    1,
    'mdi-chart-bar',
    '数据分析,Excel,数字员工',
    1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

-- 工具绑定（参考 V113 的 mate_agent_tool 表结构）
MERGE INTO mate_agent_tool (agent_id, tool_name) KEY (agent_id, tool_name) VALUES (1000000020, 'analyticsSchema');
MERGE INTO mate_agent_tool (agent_id, tool_name) KEY (agent_id, tool_name) VALUES (1000000020, 'analyticsQuery');
MERGE INTO mate_agent_tool (agent_id, tool_name) KEY (agent_id, tool_name) VALUES (1000000020, 'analyticsProfile');
MERGE INTO mate_agent_tool (agent_id, tool_name) KEY (agent_id, tool_name) VALUES (1000000020, 'analyticsChart');
MERGE INTO mate_agent_tool (agent_id, tool_name) KEY (agent_id, tool_name) VALUES (1000000020, 'analyticsExport');
```

> 工具名按 Spring AI 默认从方法名取 camelCase。**实际表结构请先 Read V113 确认 mate_agent_tool 字段名**（可能是 tool_name 也可能是 tool_code），保持一致。

- [ ] **Step 18.2**: MySQL 迁移内容同 H2，去掉 H2 专属语法
- [ ] **Step 18.3**: 启动 dev profile 验证迁移成功 + 在前端可以看到这个 Agent
- [ ] **Step 18.4**: Commit `feat(analytics): V116 seed data-analyst agent with tool bindings`

---

## Task 19: data-analyst SKILL.md

**Files:**
- Create: `mateclaw-server/src/main/resources/skills/data-analyst/SKILL.md`
- Create: `mateclaw-server/src/main/resources/skills/data-analyst/LESSONS.md`（空文件，留给自演进）

**SKILL.md 内容骨架**：

```markdown
---
name: data-analyst
description: 数据分析方法论 — 当你被绑定为数据分析专家时遵循此 SKILL 完成 Excel 数据集的分析、画像、校验、出图与导出。
---

# 数据分析方法论

## 调用顺序约定
任何分析任务都遵循 4 步：
1. **Inventory**：调用 `analytics_schema`（无参）查看 workspace 内所有数据集
2. **Locate**：根据用户描述定位到目标 dataset（通常通过 name 关键字 + 用户确认）
3. **Inspect**：调用 `analytics_schema(datasetId)` 拿字段清单与语义
4. **Execute**：按需调用 query / profile / chart / export

## SQL 编写规范
- 所有 query 必须以 `WHERE dataset_id = <id>` 起步
- 聚合查询使用 GROUP BY + 业务意义明确的列别名（如 `SUM(end_stock_total) AS 期末存栏合计`）
- 不要 SELECT * — 显式列字段
- 不要超过 5 层 JOIN

## 报告模板

### 简短回答（默认）
> 结论：[一句话]。基于数据：[一个数字/比例]。

### 完整分析报告
1. 结论（一段）
2. 关键数字（表格 5-10 行）
3. 可视化（1-2 张图，调用 analytics_chart 生成）
4. 数据质量提示（如果发现缺失值/异常）
5. 建议（1-3 条）

## 口径术语表
- "期末存栏"：报告期最后一日尚在饲养的活体数量
- "自宰数量"：报告期内由养殖户自行屠宰未上市流通的数量
- "代养户"：受公司委托饲养、不拥有产权的农户
- ……（按字段 semantic 持续扩充）

## 异常处理
- SQL 报错时：先看是否被 SqlGuard 拦截（看错误前缀 "SqlGuard"），如果是，检查表名/写关键字
- 字段不存在时：重新调用 analytics_schema 核对 field_code
- 用户口径模糊时：先问"您指的 X 是 [选项 A] 还是 [选项 B]？"再继续

## 自演进
当用户纠正你的分析口径或方法时，把可复用的规则追加到 LESSONS.md。
```

- [ ] **Step 19.1**: 写 SKILL.md
- [ ] **Step 19.2**: 创建空 LESSONS.md（含一行 `# Lessons` 标题）
- [ ] **Step 19.3**: 启动 dev 验证 skill 被扫描入库（看 `mate_skill` 表是否新增一行 name=data-analyst）
- [ ] **Step 19.4**: 在 V116 后做一次手工 SQL 把 data-analyst skill 绑定到 agent 1000000020 — 或者扩展 `BuiltinSkillSeedService`。参考 V113 的方式（`SkillBuilderAgentSeedService` 在 Flyway 之后绑定 skill）。

> **决策点**：选 (a) 修改 `BuiltinSkillSeedService` 增加 data-analyst 的特殊绑定逻辑（与 skill-creator 同样），或 (b) 新建 `DataAnalystAgentSeedService`，Order 130。**推荐 b**（避免改通用 service 增加耦合）。

- [ ] **Step 19.5**: 实现 `DataAnalystAgentSeedService` + 单测
- [ ] **Step 19.6**: Commit `feat(analytics): data-analyst skill and agent-skill seed binding`

---

## Task 20: 前端 — 类型 + API 客户端

**Files:**
- Create: `mateclaw-ui/src/types/analytics.ts`
- Create: `mateclaw-ui/src/api/analytics.ts`

参考样例：`mateclaw-ui/src/api/agents.ts`（项目的 API 客户端风格）+ `mateclaw-ui/src/types/agent.ts`。

```typescript
// types/analytics.ts
export type FieldType = 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'

export interface DatasetTemplate {
  id: number
  workspaceId: number
  code: string
  name: string
  description?: string
  category: 'LIVESTOCK' | 'HOUSEHOLD' | 'CUSTOM'
  partitionKeys: string  // JSON array
  physicalTable: string
  enabled: 1 | 0
}

export interface DatasetTemplateField {
  id?: number
  templateId?: number
  fieldCode: string
  fieldName: string
  fieldType: FieldType
  fieldUnit?: string
  semantic?: string
  isPartitionKey: 1 | 0
  isNullable: 1 | 0
  ordinal: number
  excelHeader: string
}

export interface Dataset {
  id: number
  workspaceId: number
  templateId: number
  name: string
  description?: string
  rowCount: number
  lastUploadAt?: string
}

export interface DatasetUploadLog {
  id: number
  datasetId: number
  fileName: string
  fileSize: number
  rowsReceived: number
  rowsInserted: number
  rowsRejected: number
  status: 'PROCESSING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
  errorSummary?: string
  uploadTime: string
}
```

```typescript
// api/analytics.ts
import { http } from './http'
import type { DatasetTemplate, DatasetTemplateField, Dataset, DatasetUploadLog } from '@/types/analytics'

export const analyticsApi = {
  // templates
  listTemplates: () => http.get<DatasetTemplate[]>('/api/analytics/templates'),
  getTemplate: (id: number) => http.get<{template: DatasetTemplate, fields: DatasetTemplateField[]}>(`/api/analytics/templates/${id}`),
  createTemplate: (body: {template: Partial<DatasetTemplate>, fields: DatasetTemplateField[]}) =>
    http.post<DatasetTemplate>('/api/analytics/templates', body),
  appendField: (templateId: number, field: DatasetTemplateField) =>
    http.post(`/api/analytics/templates/${templateId}/fields`, field),
  removeField: (templateId: number, fieldId: number) =>
    http.delete(`/api/analytics/templates/${templateId}/fields/${fieldId}`),

  // datasets
  listDatasets: () => http.get<Dataset[]>('/api/analytics/datasets'),
  getDataset: (id: number) => http.get<Dataset>(`/api/analytics/datasets/${id}`),
  createDataset: (body: Partial<Dataset>) => http.post<Dataset>('/api/analytics/datasets', body),
  previewDataset: (id: number, limit = 100) =>
    http.get<{columns: string[], rows: any[]}>(`/api/analytics/datasets/${id}/preview?limit=${limit}`),
  listUploads: (id: number) => http.get<DatasetUploadLog[]>(`/api/analytics/datasets/${id}/uploads`),
  deleteDataset: (id: number) => http.delete(`/api/analytics/datasets/${id}`),

  // upload
  upload: (id: number, file: File, sheet?: string) => {
    const fd = new FormData()
    fd.append('file', file)
    if (sheet) fd.append('sheet', sheet)
    return http.post<DatasetUploadLog>(`/api/analytics/datasets/${id}/upload`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
```

- [ ] **Step 20.1**: 实现两个文件
- [ ] **Step 20.2**: `pnpm -C mateclaw-ui build` 验证类型通过
- [ ] **Step 20.3**: Commit `feat(analytics-ui): types and api client`

---

## Task 21: 前端 — TemplateList + TemplateEditor 页面

**Files:**
- Create: `mateclaw-ui/src/views/Analytics/TemplateList.vue`
- Create: `mateclaw-ui/src/views/Analytics/TemplateEditor.vue`
- Create: `mateclaw-ui/src/views/Analytics/index.vue`（仅 `<router-view />`）

**功能**：
- TemplateList：表格展示，新建按钮→跳 editor，编辑按钮→跳 editor，启停 toggle，删除（保护提示）
- TemplateEditor：上半部分模板基础信息（code/name/description/category），下半部分字段表格（行级编辑 code/name/type/unit/semantic/ordinal），保存

参考样例：`mateclaw-ui/src/views/Agents.vue`、`mateclaw-ui/src/views/McpServers.vue`（项目的 admin CRUD 页风格 + 用 Element Plus）。

- [ ] **Step 21.1**: 写 TemplateList（200 行内，使用 `<el-table>` + `analyticsApi.listTemplates`）
- [ ] **Step 21.2**: 写 TemplateEditor（含字段表格，模板字段为新建模式时可任意编辑，编辑既有模板时已有字段只读 + 仅允许追加）
- [ ] **Step 21.3**: 写 index.vue（`<router-view />` 占位）
- [ ] **Step 21.4**: `pnpm -C mateclaw-ui dev` 手工验证页面渲染
- [ ] **Step 21.5**: Commit `feat(analytics-ui): template list and editor pages`

---

## Task 22: 前端 — DatasetList + UploadDialog + Preview 页面

**Files:**
- Create: `mateclaw-ui/src/views/Analytics/DatasetList.vue`
- Create: `mateclaw-ui/src/views/Analytics/DatasetUploadDialog.vue`
- Create: `mateclaw-ui/src/views/Analytics/DatasetPreview.vue`

**DatasetList 功能**：表格列出所有 dataset、行数、最近上传时间；"新建数据集"按钮（选 template + 起名）；"上传"按钮唤起 UploadDialog；"预览"按钮跳 Preview 页；"删除"按钮（强提示）；"AI 分析此数据集"按钮 → 跳 `/analytics/chat?datasetId=...`

**UploadDialog 功能**：选数据集 → 选文件（拖拽 + 点击）→ 可选 sheet 名 → 上传 → 显示进度和结果（rowsReceived/rowsInserted/errors）

**Preview 功能**：上半部分基础信息 + 字段列表；中间 100 行预览；下面上传历史时间线

- [ ] **Step 22.1**: 实现 3 个页面（每个 < 250 行）
- [ ] **Step 22.2**: 手工验证：用 reDocs 中的 `畜牧业调查数据格式.xls` 转成 .xlsx（**注意**：项目只接受 .xlsx，需先在 Excel/WPS 中"另存为 .xlsx"）然后跑完整流程
- [ ] **Step 22.3**: Commit `feat(analytics-ui): dataset list, upload dialog and preview pages`

---

## Task 23: 前端 — AnalystChat 页

**Files:**
- Create: `mateclaw-ui/src/views/Analytics/AnalystChat.vue`

**职责**：复用项目现有 ChatPanel 组件（先 `find mateclaw-ui/src/components -name "ChatPanel*"` 确认组件名），传入 `agentId=1000000020`；若 URL query 带 `datasetId`，在首条用户消息前自动注入一条系统提示"当前关注数据集 id=X"。

- [ ] **Step 23.1**: 探一下现有 chat 组件，决定复用方式
- [ ] **Step 23.2**: 实现 AnalystChat.vue
- [ ] **Step 23.3**: 手工验证：能正常对话，工具调用可见
- [ ] **Step 23.4**: Commit `feat(analytics-ui): analyst chat page bound to agent 1000000020`

---

## Task 24: 路由 + 侧边栏导航 + i18n

**Files:**
- Modify: `mateclaw-ui/src/router/index.ts`（按 docs/architecture/data-analyst.md §5 的路由配置）
- Modify: `mateclaw-ui/src/views/layout/MainLayout.vue` 或对应的侧边栏配置文件
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts` 和 `en-US.ts`

参考 `mateclaw-ui/CLAUDE.md` 中的「5 步加页清单」（路由 + 菜单 + i18n + 权限 + 图标）。

- [ ] **Step 24.1**: 加路由（含 5 个子路由 + index 重定向到 datasets）
- [ ] **Step 24.2**: 加侧边栏菜单项（图标 `mdi-chart-bar`、title 走 i18n key `nav.analytics`）
- [ ] **Step 24.3**: 加 i18n key（zh-CN: `数据分析`、en-US: `Analytics`，子项同理）
- [ ] **Step 24.4**: `pnpm -C mateclaw-ui build` 通过；`pnpm dev` 手工验证菜单出现
- [ ] **Step 24.5**: Commit `feat(analytics-ui): wire routes, sidebar nav and i18n for analytics module`

---

## Task 25: E2E Smoke — 畜牧业 xls 真实跑通

- [ ] **Step 25.1**: 把 `/Users/justbin/project/2BPro/QingClaws/reDocs/raw-req/畜牧业调查数据格式.xls` 在 Excel/WPS 中"另存为 .xlsx"得到 `畜牧业调查数据格式.xlsx`

- [ ] **Step 25.2**: 启动 dev 全栈
```bash
cd mateclaw-server && mvn spring-boot:run &
cd mateclaw-ui && pnpm dev
```

- [ ] **Step 25.3**: 浏览器 http://localhost:5173，默认账号登录

- [ ] **Step 25.4**: 进入 "数据分析 → 模板管理"，新建模板：
  - code: `livestock_poultry`
  - name: `畜牧家禽季报`
  - 分区键 partitionKeys: `["province","city","county"]`
  - 字段（按 xls "家禽"sheet 的列添加）：`province/city/county/farm_code/is_contract/end_stock_total/end_stock_meat_total/end_stock_meat_chicken/end_stock_egg_total/end_stock_egg_chicken/self_slaughter_total/self_slaughter_chicken`，类型按数值给 DECIMAL，编码/省市给 STRING，is_contract 给 INT
  - excel_header 严格用 xls 表头原文（如 `1;期末存栏（只）-肉鸡（只）`）

- [ ] **Step 25.5**: 进入 "数据集管理"，新建数据集 "郑州市畜牧家禽 2025Q1"，绑定上面的 template

- [ ] **Step 25.6**: 上传 `畜牧业调查数据格式.xlsx`，sheet 选 "家禽"，预期 status=SUCCESS, rowsInserted ≈ 100

- [ ] **Step 25.7**: 进入 "AI 对话"，发问 "按市汇总期末存栏数（肉鸡）"，预期 Agent：
  1. 调用 `analytics_schema(datasetId=X)`
  2. 调用 `analytics_query` 执行 GROUP BY city
  3. 调用 `analytics_chart` 出柱状图
  4. 返回结论 + 表格 + 图

- [ ] **Step 25.8**: 测试导出：再问 "把这个结果导出 xlsx"，预期 Agent 调用 `analytics_export`，前端展示下载链接，下载验证内容

- [ ] **Step 25.9**: 如果以上步骤任一失败，停止 → 修复 → 更新本 plan 进度段 → 再跑

- [ ] **Step 25.10**: 全部成功后，在 `docs/architecture/data-analyst.md` §9 进度表把 Phase 1 状态改为 ✅，加上完成日期

- [ ] **Step 25.11**: Commit `test(analytics): Phase 1 E2E smoke verified with poultry quarterly xlsx`

---

## Self-Review 结果

**Spec 覆盖**：对照 architecture doc §3 Phase 1 范围每一项 ✅
- 模板管理 → T2/T3/T4/T21
- Excel 上传解析 → T8/T9/T10/T11
- 动态落表 → T7
- 5 个 Tool → T13-T17
- Agent + Skill → T18/T19
- 前端三页 → T21/T22/T23
- 左侧导航 → T24
- E2E → T25

**未覆盖（按设计排除）**：校验规则、统计云、OCR、台账 → 这些都在 Phase 2-4，文档已说明。

**Placeholder 扫描**：T15 (profile)、T16 (chart) 中的"按 FieldType 分支"、"每种 chartType 一个方法"为简写，但已给出 method signature 和分支结构，subagent 可补全实现。T21/T22/T23 的具体 .vue 内容未逐行展开（前端组件量太大），但每个步骤都说清楚组件复用对象（Element Plus / 现有 ChatPanel）+ 行数预期 + 验收方式。**这是显式权衡，不是失误**。

**类型一致性**：DatasetTemplate / DatasetTemplateField / Dataset / DatasetUploadLog 前后端字段名一致；Tool 方法名 = camelCase（analyticsSchema/Query/Profile/Chart/Export）与 V116 工具绑定串一致。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-18-data-analyst-phase-1.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
