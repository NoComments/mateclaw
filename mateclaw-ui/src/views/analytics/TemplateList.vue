<template>
  <div>
    <div class="sub-page-header">
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
    </div>

    <div v-loading="loading" class="mc-surface-card table-wrap">
          <el-table :data="templates" style="width: 100%">
            <el-table-column prop="name" :label="t('analytics.templateName')" min-width="140" />
            <el-table-column prop="code" :label="t('analytics.templateCode')" min-width="130" />
            <el-table-column prop="category" :label="t('analytics.category')" min-width="100" />
            <el-table-column :label="t('analytics.enabled')" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('common.create')" min-width="160" />
            <el-table-column :label="t('common.edit')" width="160" fixed="right">
              <template #default="{ row }">
                <div class="action-row">
                  <button class="action-btn" @click="goToEditor(row)">
                    {{ t('analytics.fields') }}
                  </button>
                  <button class="action-btn danger" @click="handleDelete(row)">
                    {{ t('common.delete') }}
                  </button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="total > pageSize" class="pagination-bar">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              @current-change="loadTemplates"
            />
          </div>
    </div>

    <!-- Create template dialog -->
    <el-dialog
      v-model="showDialog"
      :title="t('analytics.createTemplate')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
      >
        <el-form-item :label="t('analytics.templateName')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('analytics.category')" prop="category">
          <el-input v-model="form.category" />
        </el-form-item>
        <el-form-item :label="t('analytics.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">
          {{ t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <InspectTemplateDialog
      v-model="showInspectDialog"
      @created="loadTemplates"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { listTemplates, createTemplate, deleteTemplate } from '@/api/analytics'
import type { DatasetTemplate } from '@/types/analytics'
import InspectTemplateDialog from './InspectTemplateDialog.vue'

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const templates = ref<DatasetTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const showDialog = ref(false)
const showInspectDialog = ref(false)
const formRef = ref<FormInstance>()

const workspaceId = (): string => {
  const raw = localStorage.getItem('mc-workspace-id')
  return raw && raw.trim() ? raw : '1'
}

const form = reactive({
  name: '',
  category: '',
  description: '',
})

const formRules: FormRules = {
  name: [{ required: true, trigger: 'blur', message: t('analytics.templateName') }],
}

async function loadTemplates() {
  loading.value = true
  try {
    const res = await listTemplates({ workspaceId: workspaceId() })
    templates.value = res.data
    total.value = res.data.length
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.name = ''
  form.category = ''
  form.description = ''
  showDialog.value = true
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // Backend stamps workspaceId / creator from headers and auto-derives code + physicalTable.
    await createTemplate({
      template: {
        name: form.name,
        category: form.category || undefined,
        description: form.description || undefined,
      },
      fields: [],
    })
    ElMessage.success(t('common.saved'))
    showDialog.value = false
    await loadTemplates()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: DatasetTemplate) {
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
    await deleteTemplate(row.id)
    ElMessage.success(t('common.delete'))
    await loadTemplates()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

function goToEditor(row: DatasetTemplate) {
  router.push(`/analytics/templates/${row.id}/fields`)
}

onMounted(loadTemplates)
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
  transition: background 0.15s, transform 0.15s;
  box-shadow: var(--mc-shadow-soft);
}
.btn-primary:hover { background: var(--mc-primary-hover); }

.table-wrap { padding: 0; overflow: hidden; }

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
  border-top: 1px solid var(--mc-border-light);
}

.action-row { display: flex; gap: 8px; }

.action-btn {
  padding: 4px 12px;
  font-size: 13px;
  border: 1px solid var(--mc-border);
  border-radius: 6px;
  background: var(--mc-bg-elevated);
  color: var(--mc-text-primary);
  cursor: pointer;
  transition: all 0.15s;
}
.action-btn:hover { border-color: var(--mc-primary); color: var(--mc-primary); }
.action-btn.danger:hover { border-color: var(--mc-danger); color: var(--mc-danger); background: var(--mc-danger-bg); }

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

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
</style>
