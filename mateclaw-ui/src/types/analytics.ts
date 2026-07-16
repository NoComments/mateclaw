// ==================== Analytics Module Types ====================

export type FieldType = 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'

export type UploadStatus = 'PROCESSING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'

export interface Dataset {
  id: string
  workspaceId: string
  name: string
  physicalTable: string
  description?: string
  rowCount: number
  createTime: string
  updateTime: string
  deleted: number
}

export interface DatasetUploadLog {
  id: string
  datasetId: string
  fileName: string
  fileSize: number
  status: UploadStatus
  rowsReceived: number
  rowsInserted: number
  rowsRejected: number
  errorSummary?: string
  uploader?: string
  uploadTime: string
  createTime: string
  updateTime: string
  deleted: number
}

export interface InspectedField {
  fieldName: string
  fieldType: FieldType
  ordinal: number
}

export interface InspectResult {
  headers: string[]
  suggestedFields: InspectedField[]
  sampleRowAvailable: boolean
}

/** Response of POST /analytics/datasets — the one-step upload. */
export interface CreateDatasetResponse {
  dataset: Dataset
  uploadLog: DatasetUploadLog
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
