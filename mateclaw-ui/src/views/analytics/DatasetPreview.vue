<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Analytics</div>
            <h1 class="mc-page-title">{{ dataset?.name ?? t('analytics.preview') }}</h1>
          </div>
          <button class="btn-secondary" @click="router.back()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
            {{ t('common.collapse') }}
          </button>
        </div>

        <!-- Dataset meta -->
        <div v-if="dataset" class="mc-surface-card meta-card">
          <div class="meta-row">
            <span class="meta-label">{{ t('analytics.datasetName') }}</span>
            <span class="meta-value">{{ dataset.name }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('analytics.templateCode') }}</span>
            <span class="meta-value">{{ dataset.templateId }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ t('analytics.rowsInserted') }}</span>
            <span class="meta-value">{{ dataset.rowCount }}</span>
          </div>
        </div>

        <!-- Preview table -->
        <div v-loading="previewLoading" class="mc-surface-card table-wrap">
          <h2 class="section-title">{{ t('analytics.preview') }}</h2>
          <el-table
            v-if="previewColumns.length"
            :data="previewRows"
            style="width: 100%"
            max-height="400"
          >
            <el-table-column
              v-for="col in previewColumns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
            />
          </el-table>
          <el-empty v-else description="暂无数据" />
        </div>

        <!-- Upload history -->
        <div v-loading="uploadsLoading" class="mc-surface-card">
          <h2 class="section-title">{{ t('analytics.uploadLog') }}</h2>
          <el-timeline v-if="uploads.length">
            <el-timeline-item
              v-for="log in uploads"
              :key="log.id"
              :timestamp="log.uploadTime"
              placement="top"
            >
              <div class="log-card">
                <el-tag :type="statusTagType(log.status)" size="small">{{ log.status }}</el-tag>
                <span class="log-file">{{ log.fileName }}</span>
                <span class="log-stat">{{ t('analytics.rowsInserted') }}: {{ log.rowsInserted }}</span>
                <span class="log-stat">{{ t('analytics.rowsRejected') }}: {{ log.rowsRejected }}</span>
                <span v-if="log.errorSummary" class="log-error">{{ log.errorSummary }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无上传记录" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getDataset, previewDataset, listDatasetUploads } from '@/api/analytics'
import type { Dataset, DatasetUploadLog, UploadStatus } from '@/types/analytics'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const rawId = (route.params['datasetId'] ?? route.params['id']) as string
const datasetId = parseInt(rawId, 10)

const dataset = ref<Dataset | null>(null)
const previewLoading = ref(false)
const uploadsLoading = ref(false)
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, unknown>[]>([])
const uploads = ref<DatasetUploadLog[]>([])

function statusTagType(status: UploadStatus): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<UploadStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    SUCCESS: 'success',
    PARTIAL: 'warning',
    FAILED: 'danger',
    PROCESSING: 'info',
  }
  return map[status] ?? 'info'
}

async function loadDataset() {
  try {
    const res = await getDataset(datasetId)
    dataset.value = res.data
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function loadPreview() {
  previewLoading.value = true
  try {
    const res = await previewDataset(datasetId)
    const rows = res.data
    if (rows.length > 0) {
      previewColumns.value = Object.keys(rows[0])
      previewRows.value = rows
    }
  } catch {
    // Preview failure is non-fatal — table stays empty
  } finally {
    previewLoading.value = false
  }
}

async function loadUploads() {
  uploadsLoading.value = true
  try {
    const res = await listDatasetUploads(datasetId)
    uploads.value = res.data.slice().reverse()
  } catch {
    // Non-fatal
  } finally {
    uploadsLoading.value = false
  }
}

onMounted(() => {
  loadDataset()
  loadPreview()
  loadUploads()
})
</script>

<style scoped>
.mc-page-inner { gap: 18px; }

.btn-secondary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  background: var(--mc-bg-elevated);
  color: var(--mc-text-primary);
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
}
.btn-secondary:hover { background: var(--mc-bg-sunken); }

.meta-card { padding: 16px 20px; display: flex; gap: 24px; flex-wrap: wrap; }

.meta-row { display: flex; flex-direction: column; gap: 2px; }

.meta-label { font-size: 11px; font-weight: 600; color: var(--mc-text-tertiary); text-transform: uppercase; letter-spacing: 0.06em; }

.meta-value { font-size: 14px; color: var(--mc-text-primary); font-weight: 500; }

.table-wrap { padding: 0 0 16px; overflow: hidden; }

.section-title { font-size: 14px; font-weight: 600; color: var(--mc-text-primary); padding: 16px 20px 12px; border-bottom: 1px solid var(--mc-border-light); margin: 0; }

.log-card { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

.log-file { font-size: 13px; color: var(--mc-text-primary); font-weight: 500; }

.log-stat { font-size: 12px; color: var(--mc-text-secondary); }

.log-error { font-size: 12px; color: var(--mc-danger); flex-basis: 100%; margin-top: 2px; }
</style>
