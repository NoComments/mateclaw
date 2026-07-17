import axios from 'axios'
import { handleAuthFailure, updateTokenFromHeader } from '@/utils/auth'
import type {
  Dataset,
  DatasetUploadLog,
  InspectResult,
  InspectedField,
  CreateDatasetResponse,
  PreviewResponse,
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

// ==================== Datasets ====================

export function listDatasets(params?: {
  workspaceId?: string | number
}): Promise<{ data: Dataset[] }> {
  return analyticsHttp.get('/analytics/datasets', { params })
}

export function getDataset(id: string | number): Promise<{ data: Dataset }> {
  return analyticsHttp.get(`/analytics/datasets/${id}`)
}

export function deleteDataset(id: string | number): Promise<{ data: void }> {
  return analyticsHttp.delete(`/analytics/datasets/${id}`)
}

export function previewDataset(
  id: string | number
): Promise<{ data: PreviewResponse }> {
  return analyticsHttp.get(`/analytics/datasets/${id}/preview`)
}

export function listDatasetUploads(
  id: string | number
): Promise<{ data: DatasetUploadLog[] }> {
  return analyticsHttp.get(`/analytics/datasets/${id}/uploads`)
}

export function inspectFile(
  file: File,
  sheet?: string
): Promise<{ data: InspectResult }> {
  const formData = new FormData()
  formData.append('file', file)
  return analyticsHttp.post('/analytics/datasets/inspect', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: sheet ? { sheet } : undefined,
  })
}

export function createDatasetFromFile(p: {
  file: File
  name: string
  fields: InspectedField[]
  sheet?: string
}): Promise<{ data: CreateDatasetResponse }> {
  const formData = new FormData()
  formData.append('file', p.file)
  formData.append('name', p.name)
  formData.append('fields', JSON.stringify(p.fields))
  return analyticsHttp.post('/analytics/datasets', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: p.sheet ? { sheet: p.sheet } : undefined,
  })
}

// ==================== Upload ====================

export function uploadExcel(
  datasetId: string | number,
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
