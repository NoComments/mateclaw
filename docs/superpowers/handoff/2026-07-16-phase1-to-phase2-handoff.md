# 工作交接：数据管理简化 · 第一期 → 第二期(多文件格式:CSV / .xls)

**日期**：2026-07-16
**交接人**：Claude(本会话)
**分支**：`feat/analytics-simplification`（13 commit，已推 fork `justbin-coder/mateclaw-j`，与上游 `brand-rename-surveymind` 零冲突）
**下一步负责人**：待定

---

## 一、第一期到底做完了什么（交接的起点）

一句话：**把「建模板 → 建数据集 → 上传文件」三步合并为一步上传，模板概念从 UI/代码/数据库/路由/类型中彻底删除。**

已完成并验证（后端 136 测试绿；前端类型检查仅剩既存的 `TrialBanner.vue:77`；真实端到端实跑通过）：

- **数据模型合并**：一数据集 = 一 schema = 一物理表 `dataset_<id>`。删除 `mate_dataset_template` / `mate_dataset_template_field` 两表（迁移 `V123`，drop+recreate，**建立在「无存量数据」前提上**）。物理表**去掉 `dataset_id` 列** —— 一表一数据集后它恒为常量，它的存在正是「共用模板的数据集被静默混算」这个 bug 的根源。
- **一步上传**：`POST /api/v1/analytics/datasets`（multipart）一次完成建库+建表+入库。`POST /api/v1/analytics/datasets/inspect` 推断字段不落库。文件只上传一次。
- **一路修掉的 bug**（都实测复现过，非推测）：保留字表头（`Order`）导致 CREATE TABLE 崩溃 + 僵尸数据集；括号列（`金额(元)`/`金额(万元)`）静默错配；FAILED 入库却报成功；保留字清单改为 JDBC 驱动上报（`SqlReservedWords`）；`.xls` 改名 `.xlsx` 漏 POI 黑话。

设计与计划：
- Spec：`docs/superpowers/specs/2026-07-15-analytics-simplification-design.md`
- 第一期计划：`docs/superpowers/plans/2026-07-15-analytics-simplification-phase1.md`
- 执行全过程与所有评审发现：`.superpowers/sdd/progress.md`（git-ignored，本机 scratch）

---

## 二、第二期目标：支持 CSV 与 .xls

**本质需求不变**：用户上传 csv/excel，存储后由数字员工分析。第一期只放行 `.xlsx`，第二期把格式门槛拆掉。

Spec 原文（`…-design.md` §同批修复的 bug → CSV 支持）已把 CSV 列为第二期，理由是：引导文案曾写「手里有 Excel/CSV？」，但后端零 CSV 支持，`accept=".xlsx"` 把 CSV 用户挡在文件选择器外。第一期已把那句误导文案删除，所以**现在不是「骗用户」，只是「还没支持」**。

`.xls` 是本会话中用户追加的需求（2026-07-16），一并纳入第二期 —— 因为两者都要动同一段解析代码，合并做边际成本几乎为零。

### 范围
1. `.xlsx`（已支持，不动）
2. `.xls`（Excel 97-2003，OLE2 格式）
3. `.csv`（含编码探测）

---

## 三、动手前必须知道的锚点（本会话已核实的事实）

### 依赖：基本现成，只差一个显式声明
- **`.xls` 零成本**：`poi-ooxml`（`mateclaw-server/pom.xml`）已传递依赖 `poi` 核心，**含 HSSF**（.xls 实现）。不用加依赖。
- **CSV**：`commons-csv` 1.12.0 已在本地 m2（经 tika 传递依赖），但**建议在 `pom.xml` 显式声明**，不要依赖传递依赖的稳定性。

### 要改的两个文件（各 6 处绑死在 XSSF 具体类型上）
- `mateclaw-server/.../analytics/upload/ExcelParseService.java` — 6 处 `XSSF*`
- `mateclaw-server/.../analytics/upload/ExcelInspectService.java` — 6 处 `XSSF*`

**.xls 的改法**（低风险，POI 自动识别格式）：
- `new XSSFWorkbook(in)` → `WorkbookFactory.create(in)`（返回 `Workbook` 接口）
- `XSSFSheet` / `XSSFRow` / `XSSFCell` → `Sheet` / `Row` / `Cell` 接口
- 其余逻辑不变。`WorkbookFactory` 会按魔数自动选 HSSF/XSSF。

### ⚠️ CSV 的类型推断是真正的设计难点，不能直接复用
现有类型推断（`ExcelInspectService`）**依赖 POI 的 `CellType`**：
```
NUMERIC cell + 日期格式 → DATE
NUMERIC cell + 整数     → INT
NUMERIC cell + 小数     → DECIMAL
STRING cell 匹配 \d{4}[-/]\d{2}[-/]\d{2} → DATE
```
**CSV 全是纯文本，没有 cell 类型。** 所以 CSV 必须走一套**基于字符串内容**的推断：尝试 parse 成 int / decimal / date（用正则或 `NumberFormat`），失败则 STRING。这套逻辑要新写，或把现有 STRING 分支（第 27 行那个日期正则）抽出来复用。**这是第二期最容易出 bug 的地方，建议 TDD 覆盖：整数、小数、日期、带千分位、科学计数法、空单元格、纯文本。**

### 契约：这两个方法的签名要保持不变（下游依赖）
```java
ExcelParseService.parse(InputStream in, List<DatasetField> fields, String sheetName) → List<ParsedRow>
ExcelInspectService.inspect(InputStream in, String sheetName) → InspectResult
```
`DatasetController` 的一步上传 + inspect 端点都调它们。CSV 没有「sheet」概念，`sheetName` 参数对 CSV 忽略即可（不要报错）。

### 文件校验的唯一入口（第二期要放宽它）
`mateclaw-server/.../analytics/controller/DatasetFileValidator.java`
- 现在：扩展名必须 `.xlsx` + 内容嗅探（ZIP_MAGIC 放行，OLE2_MAGIC 报「另存为 .xlsx」，其余拒绝）
- 第二期：扩展名放宽到 `.xlsx/.xls/.csv`；内容嗅探改为「ZIP→xlsx 路径，OLE2→xls 路径，纯文本→csv 路径」。
- **注意**：第一期给 OLE2 写的那句「请另存为 .xlsx」的中文提示，第二期支持 .xls 后要**删掉或改写** —— 否则自相矛盾（既支持 .xls 又叫人别用 .xls）。
- 测试在 `DatasetFileValidatorTest.java`，第二期要相应更新（现在有一条测试专门断言 OLE2 被拒 + 不含 POI 黑话，那条要改）。

### 前端两处小改
- `mateclaw-ui/src/views/analytics/UploadDialog.vue:23` — `accept=".xlsx"` → `accept=".xlsx,.xls,.csv"`
- `mateclaw-ui/src/i18n/locales/zh-CN.ts:3599` — `uploadSub: '仅支持 .xlsx 文件'` → 改成支持的格式列表（中英都要改，`en-US.ts` 对应键）

---

## 四、CSV 特有的坑（务必设计到）

1. **编码探测**：中文 CSV 常见 GBK/GB18030，不是 UTF-8。直接按 UTF-8 读会乱码入库且不报错（又一个「静默错误」）。需要探测（可用已在依赖里的 `juniversalchardet`，见 tika 传递依赖），探测失败时**明确提示用户另存为 UTF-8**，而不是静默乱码。Spec §错误处理已写明这条。
2. **分隔符**：逗号是默认，但也有分号（欧洲 Excel 导出）、Tab。第一版可只支持逗号，但要在校验/提示里说清。
3. **表头行**：CSV 没有「sheet」，第一行即表头。空文件 / 只有表头无数据行 → 复用第一期已有的「无字段 → 400 明确报错」路径（`ExcelInspectService` 抛异常，`DatasetController` 转 400）。
4. **引号与转义**：用 `commons-csv` 的 `CSVFormat`，不要手写 split（会被字段内逗号/换行搞崩）。

---

## 五、建议的第二期做法

1. **先写 spec 增补或直接进 writing-plans**（这是新功能，按项目流程应先 brainstorming → 计划）。本交接不替代 spec。
2. **抽象出格式无关的解析接口**：`parse` / `inspect` 内部按魔数分派到 xlsx / xls / csv 三个实现，对外签名不变。这样一步上传的整条链路（`DatasetController` → `prepareFields` → `parse` → `ingest`）完全不用动。
3. **TDD 覆盖类型推断**（见上,CSV 最易错）。
4. **实跑验证**：本仓库有 `mateclaw-ui/.claude/skills/verify` skill 记录了怎么起后端+前端+Playwright 驱动。第二期至少要实跑三种格式各一次（真实 CSV/xls/xlsx 各上传一遍，确认入库行数正确）。**不要只靠单测** —— 第一期的两个 Critical 都是单测全绿却实跑才暴露的。

---

## 六、与第二期无关、但接手人应知道的遗留项

这些在 `.superpowers/sdd/progress.md` 有完整记录，**都是第三期或独立事项，第二期不要碰**：

- **第三期（workspace 隔离）**：`AnalyticsSchemaTool` 硬编码 `workspace_id = 1L`（非 1 号工作区的数据集数字员工看不见）；`SqlGuard` 无跨工作区隔离；前端 `workspaceId()` 兜底 `'1'`；`agentId '1000000020'` 前端硬编码。
- **license 模块两个坏味道**（本会话诊断，**非功能故障，实测横幅正常显示**）：`LicenseController` 返回裸 DTO 而非 `R<T>`，前端靠 `res.data || res` 兜底（`TrialBanner.vue:75`，即那个既存 `TrialBanner.vue:77` 类型错误的根源）；`daysRemaining` 后端 `long` 序列化成字符串、前端当 number 比较（当前靠 JS 隐式转换恰好工作）。修不修是代码卫生决定，无时间压力。
- **`is_nullable` 列当前无生产读取者**（deliberately dormant）：`parseFields` 恒设 `true`，唯一设 `false` 的 `TemplateEditor` 已删。第二期若引入「必填字段」UI 概念，是把它接上的自然时机；否则继续休眠。不要当它是活的保障。
- **`DatasetUploadHistory.vue` + 独立路由**与预览页的上传日志 tab 重复（spec 说要合并，第一期未做）。

---

## 七、当前收尾状态（交接时的快照）

- 分支 `feat/analytics-simplification`：13 commit，工作区干净，已推 fork，与上游零冲突。
- 后端 136 测试绿；前端类型检查仅剩既存 `TrialBanner.vue:77`。
- PR 链接（若未建）：`https://github.com/NoComments/mateclaw/compare/brand-rename-surveymind...justbin-coder:mateclaw-j:feat/analytics-simplification`
- **未推上游主仓**：`justbin-coder` 对 `NoComments/mateclaw` 无写权限，走的是 fork PR 流程。
- ⚠️ **环境提醒**：本机那张试用 license 是 24 小时的，`2026-07-16 19:52` 到期，过期后 `LicenseFilter` 会让所有 `/api/` 返回 403。继续开发前用 `./generate-lic.sh`（默认 30 天）续期并重启后端。
