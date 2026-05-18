// ==================== Analytics Module Types ====================

export type FieldType = 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'

export type UploadStatus = 'PROCESSING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'

export interface DatasetTemplate {
  id: number
  workspaceId: number
  code: string
  name: string
  physicalTable: string
  description?: string
  enabled: boolean
  category: string
  /** JSON array string, e.g. "[]" */
  partitionKeys: string
  appliedDdlHash?: string
  createTime: string
  updateTime: string
  deleted: number
}

export interface DatasetTemplateField {
  id: number
  templateId: number
  fieldCode: string
  fieldName: string
  fieldType: FieldType
  excelHeader: string
  unit?: string
  semantic?: string
  ordinal: number
  isNullable: boolean
  createTime: string
  updateTime: string
  deleted: number
}

export interface Dataset {
  id: number
  workspaceId: number
  templateId: number
  name: string
  description?: string
  rowCount: number
  createTime: string
  updateTime: string
  deleted: number
}

export interface DatasetUploadLog {
  id: number
  datasetId: number
  fileName: string
  fileSize: number
  status: UploadStatus
  rowsReceived: number
  rowsInserted: number
  rowsRejected: number
  errorSummary?: string
  uploader?: number
  uploadTime: string
  createTime: string
  updateTime: string
  deleted: number
}

// ==================== Request Types ====================

export interface CreateTemplateRequest {
  workspaceId: number
  code: string
  name: string
  physicalTable: string
  description?: string
  category?: string
}

export interface CreateTemplateFieldRequest {
  fieldCode: string
  fieldName: string
  fieldType: FieldType
  excelHeader: string
  unit?: string
  semantic?: string
  ordinal: number
  isNullable?: boolean
}

export interface CreateDatasetRequest {
  workspaceId: number
  templateId: number
  name: string
  description?: string
}

// ==================== Pagination ====================

/** Mirrors backend IPage<T> */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
