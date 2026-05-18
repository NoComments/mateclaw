import axios from 'axios'
import { handleAuthFailure, updateTokenFromHeader } from '@/utils/auth'
import type {
  DatasetTemplate,
  DatasetTemplateField,
  Dataset,
  DatasetUploadLog,
  CreateTemplateRequest,
  CreateTemplateFieldRequest,
  CreateDatasetRequest,
} from '@/types/analytics'

const analyticsHttp = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

// 请求拦截器：注入 Token + Workspace ID + Accept-Language
analyticsHttp.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const workspaceId = localStorage.getItem('mc-workspace-id')
  if (workspaceId) {
    config.headers['X-Workspace-Id'] = workspaceId
  }
  const locale = localStorage.getItem('qingwenclaws_locale')
  if (locale) {
    config.headers['Accept-Language'] = locale
  }
  return config
})

// 响应拦截器：适配后端 R<T> { code, msg, data } 格式
analyticsHttp.interceptors.response.use(
  (res) => {
    updateTokenFromHeader(res.headers)

    const data = res.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) return data
      if (data.code === 401) {
        handleAuthFailure()
        return Promise.reject(new Error(data.msg || 'Unauthorized'))
      }
      return Promise.reject(new Error(data.msg || 'Request failed'))
    }
    return data
  },
  (err) => {
    if (err.response?.status === 401) {
      handleAuthFailure()
    }
    const wrapped = new Error(err.response?.data?.msg || err.message || 'Request failed')
    ;(wrapped as Error & { response?: unknown }).response = err.response
    return Promise.reject(wrapped)
  }
)

// ==================== Templates ====================

export function listTemplates(params?: {
  workspaceId?: number
}): Promise<{ data: DatasetTemplate[] }> {
  return analyticsHttp.get('/analytics/templates', { params })
}

export function createTemplate(
  data: CreateTemplateRequest
): Promise<{ data: DatasetTemplate }> {
  return analyticsHttp.post('/analytics/templates', data)
}

export function getTemplate(id: number): Promise<{ data: DatasetTemplate }> {
  return analyticsHttp.get(`/analytics/templates/${id}`)
}

export function deleteTemplate(id: number): Promise<{ data: void }> {
  return analyticsHttp.delete(`/analytics/templates/${id}`)
}

// ==================== Template Fields ====================

export function listTemplateFields(
  templateId: number
): Promise<{ data: DatasetTemplateField[] }> {
  return analyticsHttp.get(`/analytics/templates/${templateId}/fields`)
}

export function createTemplateField(
  templateId: number,
  data: CreateTemplateFieldRequest
): Promise<{ data: DatasetTemplateField }> {
  return analyticsHttp.post(`/analytics/templates/${templateId}/fields`, data)
}

export function deleteTemplateField(
  templateId: number,
  fieldId: number
): Promise<{ data: void }> {
  return analyticsHttp.delete(`/analytics/templates/${templateId}/fields/${fieldId}`)
}

// ==================== Datasets ====================

export function listDatasets(params?: {
  workspaceId?: number
  templateId?: number
}): Promise<{ data: Dataset[] }> {
  return analyticsHttp.get('/analytics/datasets', { params })
}

export function createDataset(
  data: CreateDatasetRequest
): Promise<{ data: Dataset }> {
  return analyticsHttp.post('/analytics/datasets', data)
}

export function getDataset(id: number): Promise<{ data: Dataset }> {
  return analyticsHttp.get(`/analytics/datasets/${id}`)
}

export function deleteDataset(id: number): Promise<{ data: void }> {
  return analyticsHttp.delete(`/analytics/datasets/${id}`)
}

export function previewDataset(
  id: number
): Promise<{ data: Record<string, unknown>[] }> {
  return analyticsHttp.get(`/analytics/datasets/${id}/preview`)
}

export function listDatasetUploads(
  id: number
): Promise<{ data: DatasetUploadLog[] }> {
  return analyticsHttp.get(`/analytics/datasets/${id}/uploads`)
}

// ==================== Upload ====================

export function uploadExcel(
  datasetId: number,
  file: File,
  sheet?: string
): Promise<{ data: DatasetUploadLog }> {
  const formData = new FormData()
  formData.append('file', file)
  return analyticsHttp.post(`/analytics/datasets/${datasetId}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: sheet ? { sheet } : undefined,
  })
}
