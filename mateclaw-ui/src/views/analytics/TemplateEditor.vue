<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Analytics</div>
            <h1 class="mc-page-title">
              {{ t('analytics.fields') }}
              <span v-if="templateName" class="template-label">— {{ templateName }}</span>
            </h1>
          </div>
          <div class="header-actions">
            <button class="btn-secondary" @click="router.back()">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
              {{ t('common.collapse') }}
            </button>
            <button class="btn-primary" @click="openAddFieldDialog">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              {{ t('analytics.addField') }}
            </button>
          </div>
        </div>

        <div v-loading="loading" class="mc-surface-card table-wrap">
          <el-table :data="fields" style="width: 100%">
            <el-table-column prop="ordinal" :label="t('analytics.ordinal')" width="72" />
            <el-table-column prop="fieldCode" :label="t('analytics.fieldCode')" min-width="130" />
            <el-table-column prop="fieldName" :label="t('analytics.fieldName')" min-width="130" />
            <el-table-column :label="t('analytics.fieldType')" width="110">
              <template #default="{ row }">
                <el-tag :type="fieldTypeTagType(row.fieldType)" size="small">
                  {{ row.fieldType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="excelHeader" :label="t('analytics.excelHeader')" min-width="130" />
            <el-table-column prop="unit" :label="t('analytics.unit')" width="80" />
            <el-table-column :label="t('analytics.nullable')" width="80">
              <template #default="{ row }">
                <svg v-if="row.isNullable" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class="check-icon">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.delete')" width="80" fixed="right">
              <template #default="{ row }">
                <button class="action-btn danger" @click="handleDeleteField(row)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                  </svg>
                </button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- Add field dialog -->
    <el-dialog
      v-model="showDialog"
      :title="t('analytics.addField')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
      >
        <el-form-item :label="t('analytics.fieldCode')" prop="fieldCode">
          <el-input v-model="form.fieldCode" placeholder="e.g. feed_amount" />
        </el-form-item>
        <el-form-item :label="t('analytics.fieldName')" prop="fieldName">
          <el-input v-model="form.fieldName" />
        </el-form-item>
        <el-form-item :label="t('analytics.fieldType')" prop="fieldType">
          <el-select v-model="form.fieldType" style="width: 100%">
            <el-option v-for="ft in fieldTypes" :key="ft" :label="ft" :value="ft" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.excelHeader')" prop="excelHeader">
          <el-input v-model="form.excelHeader" />
        </el-form-item>
        <el-form-item :label="t('analytics.unit')">
          <el-input v-model="form.unit" />
        </el-form-item>
        <el-form-item :label="t('analytics.semantic')">
          <el-input v-model="form.semantic" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('analytics.ordinal')" prop="ordinal">
          <el-input-number v-model="form.ordinal" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('analytics.nullable')">
          <el-switch v-model="form.isNullable" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleAddField">
          {{ t('common.add') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getTemplate, listTemplateFields, createTemplateField, deleteTemplateField } from '@/api/analytics'
import type { DatasetTemplateField, FieldType } from '@/types/analytics'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const templateId = parseInt(route.params['id'] as string, 10)

const loading = ref(false)
const saving = ref(false)
const templateName = ref('')
const fields = ref<DatasetTemplateField[]>([])
const showDialog = ref(false)
const formRef = ref<FormInstance>()

const fieldTypes: FieldType[] = ['STRING', 'INT', 'DECIMAL', 'BOOLEAN', 'DATE']

const identifierPattern = /^[a-z0-9_]+$/

const form = reactive({
  fieldCode: '',
  fieldName: '',
  fieldType: 'STRING' as FieldType,
  excelHeader: '',
  unit: '',
  semantic: '',
  ordinal: 0,
  isNullable: true,
})

const formRules: FormRules = {
  fieldCode: [
    { required: true, trigger: 'blur', message: t('analytics.fieldCode') },
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
  fieldName: [{ required: true, trigger: 'blur', message: t('analytics.fieldName') }],
  fieldType: [{ required: true, trigger: 'change', message: t('analytics.fieldType') }],
  excelHeader: [{ required: true, trigger: 'blur', message: t('analytics.excelHeader') }],
  ordinal: [{ required: true, trigger: 'blur', message: t('analytics.ordinal') }],
}

function fieldTypeTagType(ft: FieldType): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<FieldType, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    STRING: 'info',
    INT: 'primary',
    DECIMAL: 'success',
    BOOLEAN: 'warning',
    DATE: 'danger',
  }
  return map[ft] ?? 'info'
}

async function loadTemplate() {
  try {
    const res = await getTemplate(templateId)
    templateName.value = res.data.name
  } catch {
    // Non-fatal — heading shows without name
  }
}

async function loadFields() {
  loading.value = true
  try {
    const res = await listTemplateFields(templateId)
    fields.value = res.data.slice().sort((a, b) => a.ordinal - b.ordinal)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

function openAddFieldDialog() {
  form.fieldCode = ''
  form.fieldName = ''
  form.fieldType = 'STRING'
  form.excelHeader = ''
  form.unit = ''
  form.semantic = ''
  form.ordinal = fields.value.length
  form.isNullable = true
  showDialog.value = true
}

async function handleAddField() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await createTemplateField(templateId, {
      fieldCode: form.fieldCode,
      fieldName: form.fieldName,
      fieldType: form.fieldType,
      excelHeader: form.excelHeader,
      unit: form.unit || undefined,
      semantic: form.semantic || undefined,
      ordinal: form.ordinal,
      isNullable: form.isNullable,
    })
    ElMessage.success(t('common.saved'))
    showDialog.value = false
    await loadFields()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

async function handleDeleteField(row: DatasetTemplateField) {
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
    await deleteTemplateField(templateId, row.id)
    ElMessage.success(t('common.delete'))
    await loadFields()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

onMounted(() => {
  loadTemplate()
  loadFields()
})
</script>

<style scoped>
.mc-page-inner { gap: 18px; }

.template-label {
  font-weight: 400;
  color: var(--mc-text-secondary);
  font-size: 18px;
}

.header-actions { display: flex; align-items: center; gap: 10px; }

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
  transition: background 0.15s;
  box-shadow: var(--mc-shadow-soft);
}
.btn-primary:hover { background: var(--mc-primary-hover); }

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

.table-wrap { padding: 0; overflow: hidden; }

.check-icon { color: var(--mc-primary); display: block; }

.action-btn {
  width: 30px;
  height: 30px;
  border: 1px solid var(--mc-border);
  background: var(--mc-bg-elevated);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--mc-text-secondary);
  transition: all 0.15s;
}
.action-btn.danger:hover {
  background: var(--mc-danger-bg);
  border-color: var(--mc-danger);
  color: var(--mc-danger);
}
</style>
