<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Analytics</div>
            <h1 class="mc-page-title">{{ t('analytics.uploadLog') }}</h1>
          </div>
          <button class="btn-secondary" @click="router.back()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
            {{ t('common.collapse') }}
          </button>
        </div>

        <div v-loading="loading" class="mc-surface-card table-wrap">
          <el-table :data="logs" style="width: 100%">
            <el-table-column :label="t('analytics.fileName')" min-width="180">
              <template #default="{ row }">
                <span class="file-name">{{ row.fileName }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.fileSize')" width="110">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.status')" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.rowsInserted')" width="110">
              <template #default="{ row }">
                <span class="stat-inserted">{{ row.rowsInserted }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.rowsRejected')" width="110">
              <template #default="{ row }">
                <span :class="row.rowsRejected > 0 ? 'stat-rejected' : ''">{{ row.rowsRejected }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.uploadTime')" min-width="160">
              <template #default="{ row }">
                {{ row.uploadTime }}
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.errorSummary')" min-width="160">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.errorSummary"
                  :content="row.errorSummary"
                  placement="top"
                  :max-width="320"
                >
                  <span class="error-snippet">{{ truncate(row.errorSummary) }}</span>
                </el-tooltip>
                <span v-else class="empty-dash">—</span>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!loading && logs.length === 0" class="empty-state">
            <el-empty :description="t('common.noResults')" />
          </div>
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
import { listDatasetUploads } from '@/api/analytics'
import type { DatasetUploadLog, UploadStatus } from '@/types/analytics'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const datasetId = parseInt(route.params['datasetId'] as string, 10)

const loading = ref(false)
const logs = ref<DatasetUploadLog[]>([])

function statusTagType(status: UploadStatus): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<UploadStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    SUCCESS: 'success',
    PARTIAL: 'warning',
    FAILED: 'danger',
    PROCESSING: 'info',
  }
  return map[status] ?? 'info'
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }
  if (bytes >= 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${bytes} B`
}

function truncate(text: string, maxLen = 60): string {
  return text.length > maxLen ? text.slice(0, maxLen) + '…' : text
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await listDatasetUploads(datasetId)
    logs.value = res.data.slice().reverse()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
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
  transition: background 0.15s;
}
.btn-secondary:hover { background: var(--mc-bg-sunken); }

.table-wrap { padding: 0; overflow: hidden; }

.file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--mc-text-primary);
}

.stat-inserted { color: var(--el-color-success); font-weight: 600; }
.stat-rejected { color: var(--el-color-warning); font-weight: 600; }

.error-snippet {
  font-size: 12px;
  color: var(--mc-danger);
  cursor: pointer;
}

.empty-dash { color: var(--mc-text-tertiary); }

.empty-state { padding: 40px 0; }
</style>
