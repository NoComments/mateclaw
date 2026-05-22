// ==================== Analytics Module Types ====================

export type FieldType = 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'

export type UploadStatus = 'PROCESSING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'

export interface DatasetTemplate {
  id: string
  workspaceId: string
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
  id: string
  templateId: string
  fieldCode: string
  fieldName: string
  fieldType: FieldType
  excelHeader: string
  fieldUnit?: string
  semantic?: string
  role?: 'DIMENSION' | 'MEASURE' | 'TIME_KEY'
  timeGranularity?: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
  computeHint?: string
  ordinal: number
  isNullable: boolean
  createTime: string
  updateTime: string
  deleted: number
}

export interface Dataset {
  id: string
  workspaceId: string
  templateId: string
  name: string
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

// ==================== Request Types ====================

/**
 * Body shape for {@code POST /analytics/templates}.
 *
 * <p>Mirrors the backend's {@code CreateTemplateRequest(template, fields)} record:
 * the server stamps {@code workspaceId} / {@code creator} from request headers and
 * auto-derives {@code code} (from {@code name}) and {@code physicalTable} server-side,
 * so the client only supplies user-facing schema metadata.
 */
export interface CreateTemplateRequest {
  template: {
    name: string
    description?: string
    category?: string
  }
  fields: CreateTemplateFieldRequest[]
}

export interface UpdateTemplateFieldRequest {
  fieldName?: string
  fieldUnit?: string
  semantic?: string
  ordinal?: number
  isNullable?: boolean
  role?: 'DIMENSION' | 'MEASURE' | 'TIME_KEY'
  timeGranularity?: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
  computeHint?: string
}

/**
 * Field definition payload. {@code fieldCode} and {@code excelHeader} are intentionally
 * omitted — the server derives both from {@code fieldName} (slugify + default-to-name).
 */
export interface CreateTemplateFieldRequest {
  fieldName: string
  fieldType: FieldType
  fieldUnit?: string
  semantic?: string
  ordinal: number
  isNullable?: boolean
  role?: 'DIMENSION' | 'MEASURE' | 'TIME_KEY'
  timeGranularity?: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'
  aggregation?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
  computeHint?: string
}

export interface CreateDatasetRequest {
  workspaceId: string | number
  templateId: string | number
  name: string
  description?: string
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

// ==================== Pagination ====================

/** Mirrors backend IPage<T> */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
