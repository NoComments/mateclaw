<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('analytics.upload')"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetState"
  >
    <div class="upload-body">
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
        <div class="upload-hint">{{ t('analytics.upload') }}</div>
        <div class="upload-sub">{{ t('analytics.uploadSub') }}</div>
      </el-upload>

      <div class="sheet-row">
        <span class="sheet-label">{{ t('analytics.sheetName') }}</span>
        <el-input
          v-model="sheetName"
          :placeholder="t('analytics.sheetPlaceholder')"
          size="small"
          class="sheet-input"
          :disabled="uploading"
        />
      </div>

      <el-progress
        v-if="uploading"
        :percentage="uploadProgress"
        status="active"
        class="upload-progress"
      />

      <div v-if="uploadResult" class="result-box">
        <div class="result-row">
          <span class="result-label">{{ t('analytics.rowsInserted') }}</span>
          <span class="result-value success">{{ uploadResult.rowsInserted }}</span>
        </div>
        <div class="result-row">
          <span class="result-label">{{ t('analytics.rowsRejected') }}</span>
          <span class="result-value" :class="uploadResult.rowsRejected > 0 ? 'warn' : ''">
            {{ uploadResult.rowsRejected }}
          </span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="!selectedFile"
        @click="handleUpload"
      >
        {{ t('analytics.startUpload') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { uploadExcel } from '@/api/analytics'
import type { DatasetUploadLog } from '@/types/analytics'

const props = defineProps<{
  datasetId: number
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'uploaded'): void
}>()

const { t } = useI18n()

const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)
const sheetName = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadResult = ref<DatasetUploadLog | null>(null)

function onFileChange(file: UploadFile) {
  if (file.raw) {
    selectedFile.value = file.raw
  }
}

function onFileRemove() {
  selectedFile.value = null
}

async function handleUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  uploadProgress.value = 10
  uploadResult.value = null
  try {
    uploadProgress.value = 40
    const res = await uploadExcel(
      props.datasetId,
      selectedFile.value,
      sheetName.value.trim() || undefined
    )
    uploadProgress.value = 100
    uploadResult.value = res.data
    emit('uploaded')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    uploading.value = false
  }
}

function resetState() {
  selectedFile.value = null
  sheetName.value = ''
  uploading.value = false
  uploadProgress.value = 0
  uploadResult.value = null
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

.upload-progress {
  margin-top: 4px;
}

.result-box {
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.result-label {
  color: var(--mc-text-secondary);
}

.result-value {
  font-weight: 600;
  color: var(--mc-text-primary);
}

.result-value.success {
  color: var(--el-color-success);
}

.result-value.warn {
  color: var(--el-color-warning);
}
</style>
