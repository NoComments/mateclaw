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
