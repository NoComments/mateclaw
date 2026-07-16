<template>
  <div>
    <div class="sub-page-header">
      <button class="btn-primary" @click="showUploadDialog = true">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        {{ t('analytics.uploadData') }}
      </button>
    </div>

    <div v-loading="loading" class="mc-surface-card table-wrap">
          <el-table :data="datasets" style="width: 100%">
            <el-table-column prop="name" :label="t('analytics.datasetName')" min-width="160" />
            <el-table-column prop="rowCount" :label="t('analytics.rowsInserted')" width="120" />
            <el-table-column prop="createTime" :label="t('common.create')" min-width="160" />
            <el-table-column :label="t('common.edit')" width="360" fixed="right">
              <template #default="{ row }">
                <div class="action-row">
                  <button class="action-btn accent" @click="goToAnalysis(row)">
                    {{ t('analytics.analyze') }}
                  </button>
                  <button class="action-btn" @click="goToPreview(row)">
                    {{ t('analytics.preview') }}
                  </button>
                  <button class="action-btn" @click="openAppend(row)">
                    {{ t('analytics.appendData') }}
                  </button>
                  <button class="action-btn" @click="goToUploadHistory(row)">
                    {{ t('analytics.uploadLog') }}
                  </button>
                  <button class="action-btn danger" @click="handleDelete(row)">
                    {{ t('common.delete') }}
                  </button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-state">
                <div class="empty-hint">{{ t('analytics.emptyHint') }}</div>
                <button class="btn-primary" @click="showUploadDialog = true">
                  {{ t('analytics.uploadData') }}
                </button>
              </div>
            </template>
          </el-table>
    </div>

    <UploadDialog v-model="showUploadDialog" @created="onCreated" />
    <input
      ref="appendInputRef"
      type="file"
      accept=".xlsx"
      style="display: none"
      @change="onAppendFilePicked"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDatasets, deleteDataset, uploadExcel } from '@/api/analytics'
import type { Dataset } from '@/types/analytics'
import UploadDialog from './UploadDialog.vue'

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const datasets = ref<Dataset[]>([])

const showUploadDialog = ref(false)
const appendInputRef = ref<HTMLInputElement>()
const appendTargetId = ref<string>('')

// Phase 3 will have the backend supply this; see the analytics simplification spec.
const ANALYST_AGENT_ID = '1000000020'

function workspaceId(): string {
  const raw = localStorage.getItem('mc-workspace-id')
  return raw && raw.trim() ? raw : '1'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listDatasets({ workspaceId: workspaceId() })
    datasets.value = res.data
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

async function handleDelete(row: Dataset) {
  try {
    await ElMessageBox.confirm(t('analytics.deleteConfirm'), t('common.confirm'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  try {
    await deleteDataset(row.id)
    ElMessage.success(t('common.delete'))
    await loadData()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

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

function goToPreview(row: Dataset) {
  router.push(`/analytics/datasets/${row.id}/preview`)
}

function goToUploadHistory(row: Dataset) {
  router.push(`/analytics/datasets/${row.id}/uploads`)
}

function goToAnalysis(row: Dataset) {
  router.push({ path: '/chat', query: { agentId: ANALYST_AGENT_ID } })
}

function onCreated(datasetId: string) {
  loadData()
  router.push({ path: '/chat', query: { agentId: ANALYST_AGENT_ID, datasetId } })
}

onMounted(loadData)
</script>

<style scoped>
.mc-page-inner { gap: 18px; }

.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--mc-shadow-soft);
}
.btn-primary:hover { opacity: 0.9; }

.table-wrap { padding: 0; overflow: hidden; }

.empty-state { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 48px 0; }
.empty-hint { font-size: 13px; color: var(--mc-text-tertiary); }

.action-row { display: flex; gap: 6px; flex-wrap: wrap; }

.action-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--mc-border);
  border-radius: 6px;
  background: var(--mc-bg-elevated);
  color: var(--mc-text-primary);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.action-btn:hover { border-color: var(--mc-primary); color: var(--mc-primary); }
.action-btn.accent { border-color: var(--mc-primary); color: var(--mc-primary); background: var(--mc-primary-bg); }
.action-btn.accent:hover { opacity: 0.85; }
.action-btn.danger:hover { border-color: var(--mc-danger); color: var(--mc-danger); background: var(--mc-danger-bg); }

</style>
