-- V115: Analytics module foundation — dataset template + dataset + upload log.
-- Four tables support the data-analyst expert feature:
--   mate_dataset_template        — reusable schema templates with physical table mapping
--   mate_dataset_template_field  — typed field definitions per template
--   mate_dataset                 — dataset instances bound to a template
--   mate_dataset_upload_log      — append-only Excel upload audit log
--
-- IDs are application-assigned (Snowflake/ASSIGN_ID) — no AUTO_INCREMENT.
-- H2 dialect: TINYINT (not TINYINT(1)), TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
-- BOOLEAN, separate CREATE INDEX statements.

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
    enabled          TINYINT       NOT NULL DEFAULT 1,
    creator          BIGINT,
    updater          BIGINT,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT           NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dataset_template_workspace_code
    ON mate_dataset_template (workspace_id, code);

-- 2. Template field definitions — one row per typed column in the physical dataset table.
CREATE TABLE IF NOT EXISTS mate_dataset_template_field (
    id               BIGINT        NOT NULL PRIMARY KEY,
    template_id      BIGINT        NOT NULL,
    field_code       VARCHAR(64)   NOT NULL,   -- snake_case physical column name
    field_name       VARCHAR(256)  NOT NULL,   -- Chinese display name shown in UI / LLM prompts
    field_type       VARCHAR(16)   NOT NULL,   -- 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'
    field_unit       VARCHAR(32),
    semantic         VARCHAR(512),             -- business meaning injected into LLM system prompts
    is_partition_key TINYINT       NOT NULL DEFAULT 0,
    is_nullable      TINYINT       NOT NULL DEFAULT 1,
    ordinal          INT           NOT NULL,   -- column order (matches Excel column sequence)
    excel_header     VARCHAR(512)  NOT NULL,   -- exact Excel header text for fuzzy matching
    create_time      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT           NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dataset_template_field_code
    ON mate_dataset_template_field (template_id, field_code);
CREATE INDEX IF NOT EXISTS idx_dataset_template_field_template
    ON mate_dataset_template_field (template_id);

-- 3. Dataset instances — bound to a template; each has its own physical data rows.
CREATE TABLE IF NOT EXISTS mate_dataset (
    id             BIGINT        NOT NULL PRIMARY KEY,
    workspace_id   BIGINT        NOT NULL,
    template_id    BIGINT        NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    description    VARCHAR(1024),
    row_count      INT           NOT NULL DEFAULT 0,
    last_upload_at TIMESTAMP,
    creator        BIGINT,
    updater        BIGINT,
    create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT           NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_dataset_template
    ON mate_dataset (template_id);
CREATE INDEX IF NOT EXISTS idx_dataset_workspace
    ON mate_dataset (workspace_id);

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
    upload_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dataset_upload_log_dataset
    ON mate_dataset_upload_log (dataset_id);
