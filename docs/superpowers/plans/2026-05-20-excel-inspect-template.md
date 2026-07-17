# Excel 导入构建数据集模板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在模板列表页新增"从 Excel 导入"入口，用户上传 .xlsx 后自动推断字段定义，在预览界面确认调整后一键创建模板。

**Architecture:** 后端新增无副作用的 `ExcelInspectService`（读文件推断类型，不落库）和 `POST /templates/inspect-excel` 端点；前端新增 `InspectTemplateDialog.vue` 组件，`TemplateList.vue` 中"新建模板"拆成两个按钮并引入新对话框。创建模板时复用现有 `POST /templates` 接口，无新表、无迁移。

**Tech Stack:** Java 21 records、Apache POI 5.4.1（已有依赖）、Vue 3 + TypeScript + Element Plus、现有 axios analyticsHttp 客户端

---

## 文件清单

| 操作 | 路径 |
|---|---|
| 新建 | `mateclaw-server/src/main/java/vip/mate/analytics/upload/ExcelInspectService.java` |
| 新建 | `mateclaw-server/src/test/java/vip/mate/analytics/upload/ExcelInspectServiceTest.java` |
| 修改 | `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetTemplateController.java` |
| 修改 | `mateclaw-server/src/test/java/vip/mate/analytics/controller/DatasetTemplateControllerTest.java` |
| 修改 | `mateclaw-ui/src/types/analytics.ts` |
| 修改 | `mateclaw-ui/src/api/analytics.ts` |
| 修改 | `mateclaw-ui/src/i18n/locales/zh-CN.ts` |
| 修改 | `mateclaw-ui/src/i18n/locales/en-US.ts` |
| 新建 | `mateclaw-ui/src/views/analytics/InspectTemplateDialog.vue` |
| 修改 | `mateclaw-ui/src/views/analytics/TemplateList.vue` |

---

## Task 1: 后端 ExcelInspectService

**Files:**
- Create: `mateclaw-server/src/main/java/vip/mate/analytics/upload/ExcelInspectService.java`

- [ ] **Step 1: 新建 ExcelInspectService.java**

```java
package vip.mate.analytics.upload;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an .xlsx file and infers a {@link InspectResult} from its header row
 * and optional first data row. No data is persisted — purely a read operation.
 *
 * <p>Type inference priority:
 * <ol>
 *   <li>NUMERIC cell formatted as date → DATE</li>
 *   <li>NUMERIC cell whose value equals Math.floor(value) → INT</li>
 *   <li>NUMERIC cell with fractional part → DECIMAL</li>
 *   <li>STRING cell matching {@code \d{4}[-/]\d{2}[-/]\d{2}.*} → DATE</li>
 *   <li>Anything else, or no sample row → STRING</li>
 * </ol>
 */
@Service
public class ExcelInspectService {

    /**
     * Inspect the first sheet (or the named sheet) of the workbook.
     *
     * @param in        .xlsx input stream; caller is responsible for closing it
     * @param sheetName sheet to read; {@code null} → first sheet
     * @return inspection result with inferred field definitions
     * @throws IOException              if the stream cannot be read
     * @throws IllegalArgumentException if {@code sheetName} is non-null but not found
     */
    public InspectResult inspect(InputStream in, @Nullable String sheetName) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            XSSFSheet sheet = resolveSheet(wb, sheetName);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return new InspectResult(List.of(), List.of(), false);
            }

            List<String> headers = extractHeaders(headerRow);
            Row sampleRow = sheet.getRow(1);
            boolean hasSample = sampleRow != null;

            List<InspectedField> fields = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header == null || header.isBlank()) continue;
                String type = hasSample ? inferType(sampleRow.getCell(i)) : "STRING";
                fields.add(new InspectedField(header, type, i));
            }

            return new InspectResult(headers, fields, hasSample);
        }
    }

    // ── result types ──────────────────────────────────────────────────────────

    /** A single inferred field definition. */
    public record InspectedField(String fieldName, String fieldType, int ordinal) {}

    /** Full result returned by {@link #inspect}. */
    public record InspectResult(
            List<String> headers,
            List<InspectedField> suggestedFields,
            boolean sampleRowAvailable
    ) {}

    // ── private helpers ───────────────────────────────────────────────────────

    private XSSFSheet resolveSheet(XSSFWorkbook wb, @Nullable String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return wb.getSheetAt(0);
        }
        XSSFSheet sheet = wb.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + sheetName);
        }
        return sheet;
    }

    private List<String> extractHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        int last = headerRow.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = headerRow.getCell(c);
            headers.add(cell == null ? null : cell.getStringCellValue());
        }
        return headers;
    }

    private String inferType(@Nullable Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "STRING";

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return "DATE";
            double v = cell.getNumericCellValue();
            return v == Math.floor(v) ? "INT" : "DECIMAL";
        }

        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}.*")) return "DATE";
        }

        return "STRING";
    }
}
```

- [ ] **Step 2: 编写 ExcelInspectServiceTest.java（用 POI 内存建 workbook，无需文件）**

```java
package vip.mate.analytics.upload;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ExcelInspectServiceTest {

    private final ExcelInspectService service = new ExcelInspectService();

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Build an in-memory .xlsx and return its bytes. */
    private byte[] xlsx(String sheetName, String[] headers, Object[] sampleValues) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            if (sampleValues != null) {
                Row s = sheet.createRow(1);
                for (int i = 0; i < sampleValues.length; i++) {
                    if (sampleValues[i] instanceof Number n) s.createCell(i).setCellValue(n.doubleValue());
                    else if (sampleValues[i] instanceof String str) s.createCell(i).setCellValue(str);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void inspect_infersMixedTypes() throws IOException {
        byte[] data = xlsx("Sheet1",
                new String[]{"日期", "头数", "日增重", "备注"},
                new Object[]{"2024-01-01", 120, 0.45, "正常"});

        var result = service.inspect(new ByteArrayInputStream(data), null);

        assertThat(result.sampleRowAvailable()).isTrue();
        assertThat(result.suggestedFields()).hasSize(4);

        var byName = result.suggestedFields().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExcelInspectService.InspectedField::fieldName,
                        ExcelInspectService.InspectedField::fieldType));

        assertThat(byName).containsEntry("日期", "DATE")
                          .containsEntry("头数", "INT")
                          .containsEntry("日增重", "DECIMAL")
                          .containsEntry("备注", "STRING");
    }

    @Test
    void inspect_defaultsToStringWhenNoSampleRow() throws IOException {
        byte[] data = xlsx("Sheet1", new String[]{"字段A", "字段B"}, null);

        var result = service.inspect(new ByteArrayInputStream(data), null);

        assertThat(result.sampleRowAvailable()).isFalse();
        assertThat(result.suggestedFields())
                .extracting(ExcelInspectService.InspectedField::fieldType)
                .containsOnly("STRING");
    }

    @Test
    void inspect_skipsBlankHeaderColumns() throws IOException {
        byte[] data = xlsx("Sheet1",
                new String[]{"有效列", "", "另一列"},
                new Object[]{"x", null, "y"});

        var result = service.inspect(new ByteArrayInputStream(data), null);

        assertThat(result.suggestedFields())
                .extracting(ExcelInspectService.InspectedField::fieldName)
                .containsExactly("有效列", "另一列");
    }

    @Test
    void inspect_throwsWhenSheetNotFound() throws IOException {
        byte[] data = xlsx("Sheet1", new String[]{"A"}, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.inspect(new ByteArrayInputStream(data), "不存在的Sheet"))
                .withMessageContaining("Sheet not found");
    }
}
```

- [ ] **Step 3: 验证测试（在有 JDK 的环境运行）**

```bash
cd mateclaw-server
mvn test -Dtest=ExcelInspectServiceTest
```

Expected: 4 tests PASS

- [ ] **Step 4: commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/analytics/upload/ExcelInspectService.java
git add mateclaw-server/src/test/java/vip/mate/analytics/upload/ExcelInspectServiceTest.java
git commit -m "feat(analytics): add ExcelInspectService for header/type inference"
```

---

## Task 2: 后端 Controller 端点

**Files:**
- Modify: `mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetTemplateController.java`
- Modify: `mateclaw-server/src/test/java/vip/mate/analytics/controller/DatasetTemplateControllerTest.java`

- [ ] **Step 1: 在 DatasetTemplateController 新增依赖注入和端点**

在 `DatasetTemplateController.java` 中，在类顶部增加字段（构造注入由 `@RequiredArgsConstructor` 处理）：

```java
// 在 import 区追加
import org.springframework.web.multipart.MultipartFile;
import vip.mate.analytics.upload.ExcelInspectService;
import vip.mate.analytics.upload.ExcelInspectService.InspectResult;
```

将类字段从：
```java
private final DatasetTemplateService templateService;
```
改为：
```java
private final DatasetTemplateService templateService;
private final ExcelInspectService inspectService;
```

在 `// ------------------------------------------------------------------ toggle enabled` 注释之前追加端点方法：

```java
// ------------------------------------------------------------------ inspect excel

@Operation(summary = "Inspect an .xlsx file and return inferred field definitions (no data persisted)")
@PostMapping("/inspect-excel")
public ResponseEntity<R<InspectResult>> inspectExcel(
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String sheetName) {
    try {
        InspectResult result = inspectService.inspect(file.getInputStream(), sheetName);
        return ResponseEntity.ok(R.ok(result));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(R.fail(e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(R.fail("文件解析失败: " + e.getMessage()));
    }
}
```

- [ ] **Step 2: 在 DatasetTemplateControllerTest 追加单元测试**

在 `DatasetTemplateControllerTest.java` 中，在 `setUp()` 处增加 `inspectService` mock，并在类末尾追加测试：

```java
// 在类顶部 import 区追加
import vip.mate.analytics.upload.ExcelInspectService;
import vip.mate.analytics.upload.ExcelInspectService.InspectResult;
import vip.mate.analytics.upload.ExcelInspectService.InspectedField;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import java.io.IOException;
// any() / eq() 来自已有 import static org.mockito.Mockito.*
```

修改 `setUp()` 方法：

```java
private DatasetTemplateService service;
private ExcelInspectService inspectService;
private DatasetTemplateController controller;

@BeforeEach
void setUp() {
    service = mock(DatasetTemplateService.class);
    inspectService = mock(ExcelInspectService.class);
    controller = new DatasetTemplateController(service, inspectService);
}
```

在文件末尾追加（`// ------------------------------------------------------------------ setEnabled` 测试之后）：

```java
// ------------------------------------------------------------------ inspect-excel

@Test
@DisplayName("POST /inspect-excel returns 200 with inferred fields on success")
void inspectExcel_returns200WhenSucceeds() throws Exception {
    var fields = List.of(
            new InspectedField("日期", "DATE", 0),
            new InspectedField("头数", "INT", 1));
    var result = new InspectResult(List.of("日期", "头数"), fields, true);
    when(inspectService.inspect(any(), any())).thenReturn(result);

    MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{});

    ResponseEntity<R<InspectResult>> response = controller.inspectExcel(file, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().suggestedFields()).hasSize(2);
}

@Test
@DisplayName("POST /inspect-excel returns 400 when sheet not found")
void inspectExcel_returns400WhenSheetNotFound() throws Exception {
    when(inspectService.inspect(any(), eq("missing")))
            .thenThrow(new IllegalArgumentException("Sheet not found: missing"));

    MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{});

    ResponseEntity<R<InspectResult>> response = controller.inspectExcel(file, "missing");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}
```

- [ ] **Step 3: 验证测试**

```bash
cd mateclaw-server
mvn test -Dtest='ExcelInspectServiceTest,DatasetTemplateControllerTest'
```

Expected: all tests PASS

- [ ] **Step 4: commit**

```bash
git add mateclaw-server/src/main/java/vip/mate/analytics/controller/DatasetTemplateController.java
git add mateclaw-server/src/test/java/vip/mate/analytics/controller/DatasetTemplateControllerTest.java
git commit -m "feat(analytics): add POST /templates/inspect-excel endpoint"
```

---

## Task 3: 前端类型 + API

**Files:**
- Modify: `mateclaw-ui/src/types/analytics.ts`
- Modify: `mateclaw-ui/src/api/analytics.ts`

- [ ] **Step 1: 在 types/analytics.ts 末尾 PageResult 之前追加新类型**

在 `export interface PageResult<T>` 之前插入：

```typescript
export interface InspectedField {
  fieldName: string
  fieldType: FieldType
  ordinal: number
}

export interface InspectResult {
  headers: string[]
  suggestedFields: InspectedField[]
  sampleRowAvailable: boolean
}
```

- [ ] **Step 2: 在 api/analytics.ts 的 `// ==================== Upload ====================` 之前追加**

```typescript
// ==================== Template Inspect ====================

export function inspectExcel(
  file: File,
  sheetName?: string
): Promise<{ data: InspectResult }> {
  const formData = new FormData()
  formData.append('file', file)
  return analyticsHttp.post('/analytics/templates/inspect-excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: sheetName ? { sheetName } : undefined,
  })
}
```

同时在文件顶部 import 中添加 `InspectResult`：

```typescript
import type {
  DatasetTemplate,
  DatasetTemplateField,
  Dataset,
  DatasetUploadLog,
  CreateTemplateRequest,
  CreateTemplateFieldRequest,
  UpdateTemplateFieldRequest,
  CreateDatasetRequest,
  InspectResult,
} from '@/types/analytics'
```

- [ ] **Step 3: 验证类型检查**

```bash
cd mateclaw-ui
pnpm build 2>&1 | grep -E "error TS|error:"
```

Expected: 无输出（无类型错误）

- [ ] **Step 4: commit**

```bash
git add mateclaw-ui/src/types/analytics.ts mateclaw-ui/src/api/analytics.ts
git commit -m "feat(analytics-ui): add InspectResult types and inspectExcel API"
```

---

## Task 4: i18n

**Files:**
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts`

- [ ] **Step 1: 在 zh-CN.ts 的 `analytics` 对象末尾（`errorSummary` 之后，`},` 之前）追加**

```typescript
    manualCreate: '手动创建',
    importFromExcel: '从 Excel 导入',
    inspectParsing: '解析中…',
    inspectNoSample: '文件无数据行，字段类型已默认 STRING，请手动调整',
    reUpload: '重新上传',
    inspectSheetHint: '留空取第一个 Sheet',
    createFromInspect: '创建模板',
```

- [ ] **Step 2: 在 en-US.ts 同位置追加**

```typescript
    manualCreate: 'Manual Create',
    importFromExcel: 'Import from Excel',
    inspectParsing: 'Parsing…',
    inspectNoSample: 'No data rows found — field types defaulted to STRING, please adjust manually',
    reUpload: 'Re-upload',
    inspectSheetHint: 'Leave blank for first sheet',
    createFromInspect: 'Create Template',
```

- [ ] **Step 3: commit**

```bash
git add mateclaw-ui/src/i18n/locales/zh-CN.ts mateclaw-ui/src/i18n/locales/en-US.ts
git commit -m "feat(analytics-ui): add i18n keys for Excel inspect flow"
```

---

## Task 5: InspectTemplateDialog.vue

**Files:**
- Create: `mateclaw-ui/src/views/analytics/InspectTemplateDialog.vue`

- [ ] **Step 1: 新建 InspectTemplateDialog.vue**

```vue
<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('analytics.importFromExcel')"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetState"
  >
    <!-- Step 1: upload -->
    <div v-if="step === 'upload'" class="inspect-body">
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
        <div class="upload-hint">{{ t('analytics.upload') }}</div>
        <div class="upload-sub">{{ t('analytics.uploadSub') }}</div>
      </el-upload>
      <div class="sheet-row">
        <span class="sheet-label">Sheet</span>
        <el-input
          v-model="sheetName"
          :placeholder="t('analytics.inspectSheetHint')"
          size="small"
          class="sheet-input"
        />
      </div>
    </div>

    <!-- Step 2: preview -->
    <div v-else class="inspect-body">
      <el-alert
        v-if="!inspectResult!.sampleRowAvailable"
        :title="t('analytics.inspectNoSample')"
        type="warning"
        :closable="false"
        class="no-sample-alert"
      />
      <el-form :model="meta" label-position="top" class="meta-form">
        <el-form-item :label="t('analytics.templateName')" required>
          <el-input v-model="meta.name" />
        </el-form-item>
        <el-form-item :label="t('analytics.category')">
          <el-input v-model="meta.category" />
        </el-form-item>
      </el-form>
      <el-table :data="editableFields" size="small" class="field-table">
        <el-table-column :label="t('analytics.ordinal')" width="60">
          <template #default="{ $index }">{{ $index }}</template>
        </el-table-column>
        <el-table-column :label="t('analytics.fieldName')" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.fieldName" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="t('analytics.fieldType')" width="120">
          <template #default="{ row }">
            <el-select v-model="row.fieldType" size="small">
              <el-option v-for="ft in fieldTypes" :key="ft" :label="ft" :value="ft" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('analytics.unit')" width="90">
          <template #default="{ row }">
            <el-input v-model="row.fieldUnit" size="small" />
          </template>
        </el-table-column>
        <el-table-column width="50" fixed="right">
          <template #default="{ $index }">
            <button class="del-btn" @click="removeRow($index)" title="删除">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
              </svg>
            </button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <template v-if="step === 'upload'">
        <el-button type="primary" :loading="parsing" :disabled="!selectedFile" @click="handleParse">
          {{ parsing ? t('analytics.inspectParsing') : t('common.confirm') }}
        </el-button>
      </template>
      <template v-else>
        <el-button @click="step = 'upload'">{{ t('analytics.reUpload') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="!meta.name.trim()" @click="handleCreate">
          {{ t('analytics.createFromInspect') }}
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { inspectExcel, createTemplate } from '@/api/analytics'
import type { FieldType, InspectResult } from '@/types/analytics'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created'): void
}>()

const { t } = useI18n()
const fieldTypes: FieldType[] = ['STRING', 'INT', 'DECIMAL', 'BOOLEAN', 'DATE']

type Step = 'upload' | 'preview'

const uploadRef = ref<UploadInstance>()
const step = ref<Step>('upload')
const selectedFile = ref<File | null>(null)
const sheetName = ref('')
const parsing = ref(false)
const saving = ref(false)
const inspectResult = ref<InspectResult | null>(null)

const meta = reactive({ name: '', category: '' })

interface EditableField {
  fieldName: string
  fieldType: FieldType
  fieldUnit: string
  ordinal: number
}
const editableFields = ref<EditableField[]>([])

function onFileChange(file: UploadFile) {
  if (file.raw) selectedFile.value = file.raw
}
function onFileRemove() {
  selectedFile.value = null
}
function removeRow(index: number) {
  editableFields.value.splice(index, 1)
}

async function handleParse() {
  if (!selectedFile.value) return
  parsing.value = true
  try {
    const res = await inspectExcel(selectedFile.value, sheetName.value.trim() || undefined)
    inspectResult.value = res.data
    editableFields.value = res.data.suggestedFields.map(f => ({
      fieldName: f.fieldName,
      fieldType: f.fieldType as FieldType,
      fieldUnit: '',
      ordinal: f.ordinal,
    }))
    step.value = 'preview'
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    parsing.value = false
  }
}

async function handleCreate() {
  if (!meta.name.trim()) return
  saving.value = true
  try {
    await createTemplate({
      template: {
        name: meta.name.trim(),
        category: meta.category.trim() || undefined,
      },
      fields: editableFields.value.map((f, i) => ({
        fieldName: f.fieldName,
        fieldType: f.fieldType,
        fieldUnit: f.fieldUnit || undefined,
        ordinal: i,
        isNullable: true,
      })),
    })
    ElMessage.success(t('common.saved'))
    emit('update:modelValue', false)
    emit('created')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

function resetState() {
  step.value = 'upload'
  selectedFile.value = null
  sheetName.value = ''
  parsing.value = false
  saving.value = false
  inspectResult.value = null
  meta.name = ''
  meta.category = ''
  editableFields.value = []
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.inspect-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-dragger { width: 100%; }

.upload-icon {
  color: var(--mc-text-secondary);
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 14px;
  font-weight: 600;
  color: var(--mc-text-primary);
}

.upload-sub {
  font-size: 12px;
  color: var(--mc-text-secondary);
  margin-top: 4px;
}

.sheet-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sheet-label {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--mc-text-secondary);
  min-width: 40px;
}

.sheet-input { flex: 1; }

.no-sample-alert { margin-bottom: 4px; }

.meta-form { padding-bottom: 4px; }

.field-table { border: 1px solid var(--mc-border-light); border-radius: 8px; overflow: hidden; }

.del-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--mc-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.15s;
}
.del-btn:hover {
  background: var(--mc-danger-bg);
  color: var(--mc-danger);
}
</style>
```

- [ ] **Step 2: commit**

```bash
git add mateclaw-ui/src/views/analytics/InspectTemplateDialog.vue
git commit -m "feat(analytics-ui): add InspectTemplateDialog component"
```

---

## Task 6: 接入 TemplateList.vue

**Files:**
- Modify: `mateclaw-ui/src/views/analytics/TemplateList.vue`

- [ ] **Step 1: 在 template 顶部 header-actions 区替换按钮**

将：
```html
          <button class="btn-primary" @click="openCreateDialog">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            {{ t('analytics.createTemplate') }}
          </button>
```

替换为：
```html
          <div class="header-actions">
            <button class="btn-secondary" @click="openCreateDialog">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              {{ t('analytics.manualCreate') }}
            </button>
            <button class="btn-primary" @click="showInspectDialog = true">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              {{ t('analytics.importFromExcel') }}
            </button>
          </div>
```

- [ ] **Step 2: 在 `</el-dialog>` 结束标签（手动创建对话框）之后追加 InspectTemplateDialog**

```html
    <InspectTemplateDialog
      v-model="showInspectDialog"
      @created="loadTemplates"
    />
```

- [ ] **Step 3: 在 script setup 中增加引入和状态**

在 import 区追加：
```typescript
import InspectTemplateDialog from './InspectTemplateDialog.vue'
```

在 `const showDialog = ref(false)` 之后追加：
```typescript
const showInspectDialog = ref(false)
```

- [ ] **Step 4: 在 style 区追加 header-actions 样式**

```css
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
```

- [ ] **Step 5: 全量 build 验证**

```bash
cd mateclaw-ui
pnpm build 2>&1 | grep -E "error TS|error:" | head -20
```

Expected: 无输出

- [ ] **Step 6: commit**

```bash
git add mateclaw-ui/src/views/analytics/TemplateList.vue
git commit -m "feat(analytics-ui): wire InspectTemplateDialog into TemplateList"
```

---

## Task 7: 手动烟测

- [ ] 启动后端：`mvn spring-boot:run`（或已有开发环境）
- [ ] 启动前端：`cd mateclaw-ui && pnpm dev`
- [ ] 登录 `admin / admin123` → Analytics → 数据集模板
- [ ] 验证按钮显示：页面右上角出现"手动创建"和"从 Excel 导入"两个按钮
- [ ] 验证手动创建：点"手动创建" → 弹框只有名称/分类/描述，确认创建成功
- [ ] 验证 Excel 导入：点"从 Excel 导入" → 上传一个带表头和数据行的 .xlsx → 点确认 → Step 2 显示字段表格，类型已推断 → 填写模板名称 → 点"创建模板" → 模板列表出现新条目
- [ ] 验证无样本行：上传只有表头（无第 2 行）的 .xlsx → Step 2 顶部出现黄色提示 → 字段类型全为 STRING
- [ ] 验证 sheet 不存在：填写不存在的 sheet 名 → 点确认 → 弹出错误提示，停留在 Step 1
