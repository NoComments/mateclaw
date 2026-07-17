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
      <el-alert
        v-if="inspectError"
        :title="inspectError"
        type="error"
        :closable="false"
        show-icon
      />
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
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </div>
        <div class="upload-hint">{{ t('analytics.dropFile') }}</div>
        <div class="upload-sub">{{ t('analytics.uploadSub') }}</div>
      </el-upload>

      <div class="sheet-row">
        <span class="sheet-label">{{ t('analytics.sheetName') }}</span>
        <el-input
          v-model="sheetName"
          :placeholder="t('analytics.sheetPlaceholder')"
          size="small"
          class="sheet-input"
        />
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
          <template #default="{ row }"><span>{{ row.fieldName }}</span></template>
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
      <el-button
        v-if="step === 'pick'"
        type="primary"
        :loading="inspecting"
        :disabled="!selectedFile"
        @click="handleInspect"
      >
        {{ t('analytics.nextStep') }}
      </el-button>
      <el-button
        v-else
        type="primary"
        :loading="creating"
        :disabled="!datasetName.trim() || !editableFields.length"
        @click="handleCreate"
      >
        {{ t('analytics.createDataset') }}
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
const inspectError = ref('')
const datasetName = ref('')
const editableFields = ref<InspectedField[]>([])

function onFileChange(file: UploadFile) {
  if (file.raw) {
    selectedFile.value = file.raw
    inspectError.value = ''
    // Default the dataset name to the file name without its extension.
    datasetName.value = (file.name || '').replace(/\.[^.]+$/, '')
  }
}

function onFileRemove() {
  selectedFile.value = null
  inspectError.value = ''
}

async function handleInspect() {
  if (!selectedFile.value) return
  inspecting.value = true
  inspectError.value = ''
  try {
    const res = await inspectFile(selectedFile.value, sheetName.value.trim() || undefined)
    if (!res.data.suggestedFields.length) {
      inspectError.value = t('analytics.inspectNoHeaders')
      return
    }
    inspectResult.value = res.data
    editableFields.value = res.data.suggestedFields.map((f) => ({ ...f }))
    step.value = 'confirm'
  } catch (e: unknown) {
    inspectError.value = e instanceof Error ? e.message : t('analytics.inspectNoHeaders')
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
    if (uploadLog.status === 'FAILED') {
      ElMessage.error(uploadLog.errorSummary || t('analytics.ingestFailed'))
      return
    }
    if (uploadLog.status === 'PARTIAL') {
      ElMessage.warning(
        t('analytics.partialIngest', { ok: uploadLog.rowsInserted, bad: uploadLog.rowsRejected })
      )
    } else if (uploadLog.status === 'SUCCESS') {
      ElMessage.success(t('analytics.ingestOk', { ok: uploadLog.rowsInserted }))
    } else {
      ElMessage.error(t('analytics.ingestFailed'))
      return
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
  inspectError.value = ''
  datasetName.value = ''
  editableFields.value = []
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.upload-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-dragger {
  width: 100%;
}

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
  min-width: 64px;
}

.sheet-input {
  flex: 1;
}

.field-table { max-height: 320px; overflow-y: auto; }
</style>
