-- V115: Analytics module foundation — dataset template + dataset + upload log.
-- Four tables support the data-analyst expert feature:
--   mate_dataset_template        — reusable schema templates with physical table mapping
--   mate_dataset_template_field  — typed field definitions per template
--   mate_dataset                 — dataset instances bound to a template
--   mate_dataset_upload_log      — append-only Excel upload audit log
--
-- IDs are application-assigned (Snowflake/ASSIGN_ID) — no AUTO_INCREMENT.
-- MySQL dialect: TINYINT(1), DATETIME(3) with ON UPDATE CURRENT_TIMESTAMP(3),
-- inline KEY / UNIQUE KEY inside CREATE TABLE, ENGINE=InnoDB utf8mb4.

-- 1. Dataset template — defines the schema and physical table name for a dataset family.
CREATE TABLE IF NOT EXISTS mate_dataset_template (
    id               BIGINT        NOT NULL PRIMARY KEY,
    workspace_id     BIGINT        NOT NULL,
    code             VARCHAR(64)   NOT NULL,   -- unique per workspace; used to derive physical_table
    name             VARCHAR(128)  NOT NULL,
    description      VARCHAR(1024),
    category         VARCHAR(32)   NOT NULL DEFAULT 'CUSTOM',
    partition_keys   VARCHAR(512)  NOT NULL DEFAULT '[]',  -- JSON array of field_code strings
    physical_table   VARCHAR(96)   NOT NULL,               -- derived: "dataset_" + normalized code
    applied_ddl_hash VARCHAR(64),                          -- SHA-256 of field list; tracks DDL sync state
    enabled          TINYINT(1)    NOT NULL DEFAULT 1,
    creator          BIGINT,
    updater          BIGINT,
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dataset_template_workspace_code (workspace_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Dataset schema templates with physical table mapping for the analytics module.';

-- 2. Template field definitions — one row per typed column in the physical dataset table.
CREATE TABLE IF NOT EXISTS mate_dataset_template_field (
    id               BIGINT        NOT NULL PRIMARY KEY,
    template_id      BIGINT        NOT NULL,
    field_code       VARCHAR(64)   NOT NULL,   -- snake_case physical column name
    field_name       VARCHAR(256)  NOT NULL,   -- Chinese display name shown in UI / LLM prompts
    field_type       VARCHAR(16)   NOT NULL,   -- 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'
    field_unit       VARCHAR(32),
    semantic         VARCHAR(512),             -- business meaning injected into LLM system prompts
    is_partition_key TINYINT(1)    NOT NULL DEFAULT 0,
    is_nullable      TINYINT(1)    NOT NULL DEFAULT 1,
    ordinal          INT           NOT NULL,   -- column order (matches Excel column sequence)
    excel_header     VARCHAR(512)  NOT NULL,   -- exact Excel header text for fuzzy matching
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dataset_template_field_code (template_id, field_code),
    KEY idx_dataset_template_field_template (template_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Typed field definitions per dataset template; drives physical DDL and Excel matching.';

-- 3. Dataset instances — bound to a template; each has its own physical data rows.
CREATE TABLE IF NOT EXISTS mate_dataset (
    id             BIGINT        NOT NULL PRIMARY KEY,
    workspace_id   BIGINT        NOT NULL,
    template_id    BIGINT        NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    description    VARCHAR(1024),
    row_count      INT           NOT NULL DEFAULT 0,
    last_upload_at DATETIME(3),
    creator        BIGINT,
    updater        BIGINT,
    create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted        INT           NOT NULL DEFAULT 0,
    KEY idx_dataset_template (template_id),
    KEY idx_dataset_workspace (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Dataset instances bound to a template; row_count is denormalized for fast display.';

-- 4. Upload audit log — append-only; one row per Excel file upload attempt.
CREATE TABLE IF NOT EXISTS mate_dataset_upload_log (
    id             BIGINT        NOT NULL PRIMARY KEY,
    dataset_id     BIGINT        NOT NULL,
    file_name      VARCHAR(256)  NOT NULL,
    file_size      BIGINT        NOT NULL,
    rows_received  INT           NOT NULL DEFAULT 0,
    rows_inserted  INT           NOT NULL DEFAULT 0,
    rows_rejected  INT           NOT NULL DEFAULT 0,
    status         VARCHAR(32)   NOT NULL,   -- 'PROCESSING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
    error_summary  VARCHAR(2048),
    uploader       BIGINT,
    upload_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_dataset_upload_log_dataset (dataset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Append-only Excel upload audit log; tracks per-file row counts and failure summaries.';
