# 设计文档：从 Excel 导入构建数据集模板

**日期**：2026-05-20  
**分支**：feat/data-analyst-expert  
**状态**：待实施

---

## 背景与目标

数据集模板页当前只支持手动逐字段录入。业务用户通常手持 Excel 报表，让他们对照 Excel 手填 20+ 字段违反直觉。

目标：新增"从 Excel 导入"入口，自动读取 Excel 表头 + 首行样本数据，推断字段定义，用户在预览界面确认/调整后一键创建模板。

---

## 用户流程

```
模板列表页
  ├─ [手动创建] → 现有弹框（不变）
  └─ [从 Excel 导入] → InspectTemplateDialog
        ├─ Step 1: 上传 Excel（拖拽或点选，.xlsx，可选 sheet 名）
        ├─ Step 2: 预览+调整（模板名称 + 字段表格，可改类型/单位/删行）
        └─ Step 3: 点"创建模板" → 复用现有 POST /templates → 成功关闭
```

两个入口并存，手动创建保持原样不动。

---

## 后端设计

### 新增 API

```
POST /api/v1/analytics/templates/inspect-excel
Content-Type: multipart/form-data

参数：
  file       required  .xlsx 文件
  sheetName  optional  sheet 名，空则取第 0 个 sheet

返回：
{
  "headers": ["日期", "猪舍编号", "头数", "日增重"],
  "suggestedFields": [
    { "fieldName": "日期",     "fieldType": "DATE",    "ordinal": 0 },
    { "fieldName": "猪舍编号", "fieldType": "STRING",  "ordinal": 1 },
    { "fieldName": "头数",     "fieldType": "INT",     "ordinal": 2 },
    { "fieldName": "日增重",   "fieldType": "DECIMAL", "ordinal": 3 }
  ],
  "sampleRowAvailable": true
}
```

**不落库，无副作用**。仅读文件，返回推断结果。

### 类型推断规则（按样本行第 1 行）

| 样本值特征 | 推断类型 |
|---|---|
| POI CellType = NUMERIC + `getLocalDateTimeCellValue()` 不抛异常 | DATE |
| POI CellType = NUMERIC + 值为整数（`v == Math.floor(v)`） | INT |
| POI CellType = NUMERIC（含小数） | DECIMAL |
| 字符串匹配 `\d{4}[-/]\d{2}[-/]\d{2}` | DATE |
| 其他 / 样本行缺失 | STRING |

样本行缺失时 `sampleRowAvailable = false`，前端展示提示"无样本数据，类型已默认 STRING，请手动调整"。

### 实现位置

新建 `vip.mate.analytics.upload.ExcelInspectService`（轻量，复用 POI，不依赖 ExcelParseService 现有逻辑以避免耦合）。  
Controller 方法追加到 `DatasetTemplateController`，保持现有路由风格。

---

## 前端设计

### 模板列表页改动

`TemplateList.vue`："新建模板"拆成两个按钮：

```
[手动创建]  [从 Excel 导入]
```

### 新增组件

`InspectTemplateDialog.vue`（独立文件，约 250 行）

**两步 UI**（同一弹框内切换，无 Step 指示器）：

**Step 1 — 上传区**
- el-upload 拖拽区（.xlsx，单文件）
- sheet 名输入（可选，placeholder "留空取第一个 sheet"）
- "解析" 按钮（触发 POST inspect-excel，loading 状态）
- 解析失败显示错误信息，不跳转

**Step 2 — 预览调整区**（解析成功后替换 Step 1 内容）
- 顶部：模板名称输入（必填）+ 分类输入（可选）
- 字段表格：
  - 列：序号 / 字段名（输入框）/ 类型（select）/ 单位（输入框）/ 删除按钮
  - 行：每个推断字段一行，可编辑
  - 无样本时顶部 el-alert 提示类型已默认 STRING
- 底部：[取消] [← 重新上传] [创建模板（loading）]

**"创建模板"逻辑**：组装 `CreateTemplateRequest { template: {name, category}, fields: [{fieldName, fieldType, fieldUnit, ordinal, isNullable: true}] }` → 调用现有 `createTemplate()` → 成功后关闭、刷新列表。

### i18n

两个语言包（zh-CN / en-US）同步新增以下 key：
- `analytics.importFromExcel`
- `analytics.manualCreate`
- `analytics.inspectParsing`
- `analytics.inspectNoSample`
- `analytics.reUpload`

---

## 不做的事（防止 scope creep）

- 不支持 .xls（仅 .xlsx，POI XSSF）
- 不支持多 sheet 预览切换（用户自己填 sheet 名）
- 不支持合并单元格/多行表头解析（row 0 必须是单行表头）
- 不修改 ExcelParseService 和上传流程
- 不持久化"导入方案"
- 不在字段行内支持拖拽排序（ordinal 从 0 自增，用户可在字段编辑页后续调整）

---

## 测试要求

- `ExcelInspectServiceTest`：覆盖正常推断、纯中文表头（STRING 兜底）、无样本行、未知 sheet 名抛异常 4 个用例。
- `DatasetTemplateControllerTest`：inspect-excel 端点返回 200 + 正确结构（mock ExcelInspectService）。
- 前端无自动化测试要求（现有项目无 vitest 配置），手动烟测覆盖。
