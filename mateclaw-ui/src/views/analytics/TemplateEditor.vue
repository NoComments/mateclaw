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
            <el-table-column prop="fieldUnit" :label="t('analytics.unit')" width="80" />
            <el-table-column :label="t('analytics.nullable')" width="80">
              <template #default="{ row }">
                <svg v-if="row.isNullable" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class="check-icon">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </template>
            </el-table-column>
            <el-table-column prop="role" :label="t('analytics.role')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.role" size="small">
                  {{ t(`analytics.role${row.role === 'TIME_KEY' ? 'TimeKey' : row.role === 'MEASURE' ? 'Measure' : 'Dimension'}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.timeGranularity')" width="100">
              <template #default="{ row }">
                <span v-if="row.role === 'TIME_KEY' && row.timeGranularity">{{ row.timeGranularity }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('analytics.aggregation')" width="100">
              <template #default="{ row }">
                <span v-if="row.role === 'MEASURE' && row.aggregation">{{ row.aggregation }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="computeHint" :label="t('analytics.computeHint')" min-width="120" />
            <el-table-column :label="t('common.edit')" width="110" fixed="right">
              <template #default="{ row }">
                <div style="display:flex;gap:6px">
                  <button class="action-btn" @click="openEditDialog(row)" :title="t('common.edit')">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button class="action-btn danger" @click="handleDeleteField(row)" :title="t('common.delete')">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                    </svg>
                  </button>
                </div>
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
        <el-form-item :label="t('analytics.fieldName')" prop="fieldName">
          <el-input v-model="form.fieldName" />
        </el-form-item>
        <el-form-item :label="t('analytics.fieldType')" prop="fieldType">
          <el-select v-model="form.fieldType" style="width: 100%">
            <el-option v-for="ft in fieldTypes" :key="ft" :label="ft" :value="ft" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.unit')">
          <el-input v-model="form.fieldUnit" />
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
        <el-form-item :label="t('analytics.role')">
          <el-select v-model="form.role" clearable style="width: 100%">
            <el-option label="Dimension" value="DIMENSION" />
            <el-option label="Measure" value="MEASURE" />
            <el-option label="Time Key" value="TIME_KEY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'TIME_KEY'" :label="t('analytics.timeGranularity')">
          <el-select v-model="form.timeGranularity" clearable style="width: 100%">
            <el-option v-for="g in ['DAY','WEEK','MONTH','QUARTER','YEAR']" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'MEASURE'" :label="t('analytics.aggregation')">
          <el-select v-model="form.aggregation" clearable style="width: 100%">
            <el-option v-for="a in ['SUM','AVG','COUNT','MAX','MIN']" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.computeHint')">
          <el-input v-model="form.computeHint" type="textarea" :rows="2"
            :placeholder="t('analytics.computeHintPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleAddField">
          {{ t('common.add') }}
        </el-button>
      </template>
    </el-dialog>
    <!-- Edit field dialog -->
    <el-dialog
      v-model="showEditDialog"
      :title="t('analytics.editField')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        label-position="top"
      >
        <el-form-item :label="t('analytics.fieldCode')">
          <el-input :value="editingField?.fieldCode" disabled />
        </el-form-item>
        <el-form-item :label="t('analytics.fieldType')">
          <el-input :value="editingField?.fieldType" disabled />
        </el-form-item>
        <el-form-item :label="t('analytics.fieldName')" prop="fieldName"
          :rules="[{ required: true, trigger: 'blur', message: t('analytics.fieldName') }]">
          <el-input v-model="editForm.fieldName" />
        </el-form-item>
        <el-form-item :label="t('analytics.unit')">
          <el-input v-model="editForm.fieldUnit" />
        </el-form-item>
        <el-form-item :label="t('analytics.semantic')">
          <el-input v-model="editForm.semantic" type="textarea" :rows="2"
            :placeholder="t('analytics.semanticHint')" />
        </el-form-item>
        <el-form-item :label="t('analytics.ordinal')">
          <el-input-number v-model="editForm.ordinal" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item :label="t('analytics.nullable')">
          <el-switch v-model="editForm.isNullable" />
        </el-form-item>
        <el-form-item :label="t('analytics.role')">
          <el-select v-model="editForm.role" clearable style="width: 100%">
            <el-option label="Dimension" value="DIMENSION" />
            <el-option label="Measure" value="MEASURE" />
            <el-option label="Time Key" value="TIME_KEY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.role === 'TIME_KEY'" :label="t('analytics.timeGranularity')">
          <el-select v-model="editForm.timeGranularity" clearable style="width: 100%">
            <el-option v-for="g in ['DAY','WEEK','MONTH','QUARTER','YEAR']" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.role === 'MEASURE'" :label="t('analytics.aggregation')">
          <el-select v-model="editForm.aggregation" clearable style="width: 100%">
            <el-option v-for="a in ['SUM','AVG','COUNT','MAX','MIN']" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('analytics.computeHint')">
          <el-input v-model="editForm.computeHint" type="textarea" :rows="2"
            :placeholder="t('analytics.computeHintPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditField">
          {{ t('common.save') }}
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
import { getTemplate, listTemplateFields, createTemplateField, updateTemplateField, deleteTemplateField } from '@/api/analytics'
import type { DatasetTemplate, DatasetTemplateField, FieldType, CreateTemplateFieldRequest, UpdateTemplateFieldRequest } from '@/types/analytics'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const templateId = route.params['id'] as string

const loading = ref(false)
const saving = ref(false)
const editSaving = ref(false)
const templateName = ref('')
const fields = ref<DatasetTemplateField[]>([])
const showDialog = ref(false)
const showEditDialog = ref(false)
const formRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const editingField = ref<DatasetTemplateField | null>(null)

const fieldTypes: FieldType[] = ['STRING', 'INT', 'DECIMAL', 'BOOLEAN', 'DATE']

const form = reactive({
  fieldName: '',
  fieldType: 'STRING' as FieldType,
  fieldUnit: '',
  semantic: '',
  ordinal: 0,
  isNullable: true,
  role: '' as DatasetTemplateField['role'] | '',
  timeGranularity: '' as DatasetTemplateField['timeGranularity'] | '',
  aggregation: '' as DatasetTemplateField['aggregation'] | '',
  computeHint: '',
})

const editForm = reactive({
  fieldName: '',
  fieldUnit: '',
  semantic: '',
  ordinal: 0,
  isNullable: true,
  role: '' as DatasetTemplateField['role'] | '',
  timeGranularity: '' as DatasetTemplateField['timeGranularity'] | '',
  aggregation: '' as DatasetTemplateField['aggregation'] | '',
  computeHint: '',
})

const formRules: FormRules = {
  fieldName: [{ required: true, trigger: 'blur', message: t('analytics.fieldName') }],
  fieldType: [{ required: true, trigger: 'change', message: t('analytics.fieldType') }],
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
    // Backend returns TemplateWithFields = { template, fields }
    const wrapped = res.data as unknown as { template?: DatasetTemplate; name?: string }
    templateName.value = wrapped.template?.name ?? wrapped.name ?? ''
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
  form.fieldName = ''
  form.fieldType = 'STRING'
  form.fieldUnit = ''
  form.semantic = ''
  form.ordinal = fields.value.length
  form.isNullable = true
  form.role = ''
  form.timeGranularity = ''
  form.aggregation = ''
  form.computeHint = ''
  showDialog.value = true
}

async function handleAddField() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // Server derives fieldCode (slug of fieldName) and defaults excelHeader = fieldName.
    await createTemplateField(templateId, {
      fieldName: form.fieldName,
      fieldType: form.fieldType,
      fieldUnit: form.fieldUnit || undefined,
      semantic: form.semantic || undefined,
      ordinal: form.ordinal,
      isNullable: form.isNullable,
      role: (form.role || undefined) as CreateTemplateFieldRequest['role'],
      timeGranularity: (form.timeGranularity || undefined) as CreateTemplateFieldRequest['timeGranularity'],
      aggregation: (form.aggregation || undefined) as CreateTemplateFieldRequest['aggregation'],
      computeHint: form.computeHint || undefined,
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

function openEditDialog(row: DatasetTemplateField) {
  editingField.value = row
  editForm.fieldName = row.fieldName
  editForm.fieldUnit = row.fieldUnit ?? ''
  editForm.semantic = row.semantic ?? ''
  editForm.ordinal = row.ordinal
  editForm.isNullable = row.isNullable ?? true
  editForm.role = row.role ?? ''
  editForm.timeGranularity = row.timeGranularity ?? ''
  editForm.aggregation = row.aggregation ?? ''
  editForm.computeHint = row.computeHint ?? ''
  showEditDialog.value = true
}

async function handleEditField() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !editingField.value) return
  editSaving.value = true
  try {
    await updateTemplateField(templateId, editingField.value.id, {
      fieldName: editForm.fieldName,
      fieldUnit: editForm.fieldUnit || undefined,
      semantic: editForm.semantic || undefined,
      ordinal: editForm.ordinal,
      isNullable: editForm.isNullable,
      role: (editForm.role || undefined) as UpdateTemplateFieldRequest['role'],
      timeGranularity: (editForm.timeGranularity || undefined) as UpdateTemplateFieldRequest['timeGranularity'],
      aggregation: (editForm.aggregation || undefined) as UpdateTemplateFieldRequest['aggregation'],
      computeHint: editForm.computeHint || undefined,
    })
    ElMessage.success(t('common.saved'))
    showEditDialog.value = false
    await loadFields()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    editSaving.value = false
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
