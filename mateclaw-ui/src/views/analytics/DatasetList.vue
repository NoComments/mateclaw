<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Analytics</div>
            <h1 class="mc-page-title">{{ t('analytics.datasets') }}</h1>
          </div>
          <button class="btn-primary" @click="openCreateDialog">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            {{ t('analytics.createDataset') }}
          </button>
        </div>

        <div v-loading="loading" class="mc-surface-card table-wrap">
          <el-table :data="datasets" style="width: 100%">
            <el-table-column prop="name" :label="t('analytics.datasetName')" min-width="160" />
            <el-table-column prop="templateId" :label="t('analytics.templateCode')" width="120" />
            <el-table-column prop="rowCount" :label="t('analytics.rowsInserted')" width="120" />
            <el-table-column prop="createTime" :label="t('common.create')" min-width="160" />
            <el-table-column :label="t('common.edit')" width="300" fixed="right">
              <template #default="{ row }">
                <div class="action-row">
                  <button class="action-btn" @click="openUploadDialog(row)">
                    {{ t('analytics.upload') }}
                  </button>
                  <button class="action-btn" @click="goToPreview(row)">
                    {{ t('analytics.preview') }}
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
          </el-table>
        </div>
      </div>
    </div>

    <!-- Create dataset dialog -->
    <el-dialog
      v-model="showCreateDialog"
      :title="t('analytics.createDataset')"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item :label="t('analytics.datasetName')" prop="name">
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item :label="t('analytics.templateCode')" prop="templateId">
          <el-select v-model="createForm.templateId" style="width: 100%" filterable>
            <el-option
              v-for="tpl in templates"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- Upload dialog -->
    <UploadDialog
      v-if="activeDatasetId > 0"
      v-model="showUploadDialog"
      :dataset-id="activeDatasetId"
      @uploaded="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listDatasets,
  createDataset,
  deleteDataset,
  listTemplates,
} from '@/api/analytics'
import type { Dataset, DatasetTemplate } from '@/types/analytics'
import UploadDialog from './UploadDialog.vue'

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const creating = ref(false)
const datasets = ref<Dataset[]>([])
const templates = ref<DatasetTemplate[]>([])

const showCreateDialog = ref(false)
const showUploadDialog = ref(false)
const createFormRef = ref<FormInstance>()
const activeDatasetId = ref<number>(0)

const createForm = reactive({ name: '', templateId: 0, description: '' })
const createRules: FormRules = {
  name: [{ required: true, trigger: 'blur', message: t('analytics.datasetName') }],
  templateId: [{ required: true, trigger: 'change', message: t('analytics.templateCode'), type: 'number', min: 1 }],
}

function workspaceId(): number {
  const raw = localStorage.getItem('mc-workspace-id')
  const parsed = raw ? parseInt(raw, 10) : NaN
  return isNaN(parsed) ? 1 : parsed
}

async function loadData() {
  loading.value = true
  try {
    const [dsRes, tplRes] = await Promise.all([
      listDatasets({ workspaceId: workspaceId() }),
      listTemplates({ workspaceId: workspaceId() }),
    ])
    datasets.value = dsRes.data.records
    templates.value = tplRes.data.records
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  createForm.name = ''
  createForm.templateId = 0
  createForm.description = ''
  showCreateDialog.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await createDataset({
      workspaceId: workspaceId(),
      templateId: createForm.templateId,
      name: createForm.name,
      description: createForm.description || undefined,
    })
    ElMessage.success(t('common.saved'))
    showCreateDialog.value = false
    await loadData()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    creating.value = false
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

function openUploadDialog(row: Dataset) {
  activeDatasetId.value = row.id
  showUploadDialog.value = true
}

function goToPreview(row: Dataset) {
  router.push(`/analytics/datasets/${row.id}/preview`)
}

function goToUploadHistory(row: Dataset) {
  router.push(`/analytics/datasets/${row.id}/uploads`)
}

function goToChat(row: Dataset) {
  router.push({ path: '/analytics/chat', query: { datasetId: String(row.id) } })
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

.upload-result { margin-top: 16px; }
</style>
