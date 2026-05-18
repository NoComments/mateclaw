<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Analytics</div>
            <h1 class="mc-page-title">{{ t('analytics.templates') }}</h1>
          </div>
          <button class="btn-primary" @click="openCreateDialog">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            {{ t('analytics.createTemplate') }}
          </button>
        </div>

        <div v-loading="loading" class="mc-surface-card table-wrap">
          <el-table :data="templates" style="width: 100%">
            <el-table-column prop="name" :label="t('analytics.templateName')" min-width="140" />
            <el-table-column prop="code" :label="t('analytics.templateCode')" min-width="130" />
            <el-table-column prop="physicalTable" :label="t('analytics.physicalTable')" min-width="140" />
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
        <el-form-item :label="t('analytics.templateCode')" prop="code">
          <el-input v-model="form.code" placeholder="e.g. livestock_daily" />
        </el-form-item>
        <el-form-item :label="t('analytics.physicalTable')" prop="physicalTable">
          <el-input v-model="form.physicalTable" placeholder="e.g. dataset_livestock_daily" />
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

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const templates = ref<DatasetTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const showDialog = ref(false)
const formRef = ref<FormInstance>()

const workspaceId = (): number => {
  const raw = localStorage.getItem('mc-workspace-id')
  const parsed = raw ? parseInt(raw, 10) : NaN
  return isNaN(parsed) ? 1 : parsed
}

const identifierPattern = /^[a-z0-9_]+$/

const form = reactive({
  name: '',
  code: '',
  physicalTable: '',
  category: '',
  description: '',
})

const formRules: FormRules = {
  name: [{ required: true, trigger: 'blur', message: t('analytics.templateName') }],
  code: [
    { required: true, trigger: 'blur', message: t('analytics.templateCode') },
    {
      trigger: 'blur',
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (!identifierPattern.test(value)) {
          callback(new Error('Only lowercase letters, digits, underscores'))
        } else {
          callback()
        }
      },
    },
  ],
  physicalTable: [
    { required: true, trigger: 'blur', message: t('analytics.physicalTable') },
    {
      trigger: 'blur',
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (!identifierPattern.test(value)) {
          callback(new Error('Only lowercase letters, digits, underscores'))
        } else {
          callback()
        }
      },
    },
  ],
}

async function loadTemplates() {
  loading.value = true
  try {
    const res = await listTemplates({ workspaceId: workspaceId(), page: currentPage.value, size: pageSize })
    templates.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.name = ''
  form.code = ''
  form.physicalTable = ''
  form.category = ''
  form.description = ''
  showDialog.value = true
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await createTemplate({
      workspaceId: workspaceId(),
      name: form.name,
      code: form.code,
      physicalTable: form.physicalTable,
      category: form.category,
      description: form.description || undefined,
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
</style>
