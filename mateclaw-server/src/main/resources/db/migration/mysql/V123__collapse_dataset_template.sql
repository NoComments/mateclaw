-- V123: Collapse dataset template into dataset — one dataset = one schema = one physical table.
--
-- Rationale: physical_table lived on the template, so datasets sharing a template wrote
-- into ONE physical table separated only by a dataset_id column. The schema tool never
-- told the agent that column existed and 4 of 6 tools ignored it, so cross-dataset
-- queries silently mixed rows. One table per dataset makes that bug unrepresentable.
--
-- No production data exists (confirmed 2026-07-15), so this drops and recreates.

DROP TABLE IF EXISTS mate_dataset_template_field;
DROP TABLE IF EXISTS mate_dataset_template;
DROP TABLE IF EXISTS mate_dataset;

-- Dataset — now owns its schema and physical table directly.
CREATE TABLE mate_dataset (
    id               BIGINT        NOT NULL PRIMARY KEY,
    workspace_id     BIGINT        NOT NULL,
    name             VARCHAR(128)  NOT NULL,
    description      VARCHAR(1024),
    physical_table   VARCHAR(96)   NOT NULL,   -- "dataset_" + id
    applied_ddl_hash VARCHAR(64),              -- SHA-256 of field list; tracks DDL sync state
    row_count        INT           NOT NULL DEFAULT 0,
    last_upload_at   DATETIME(3),
    creator          BIGINT,
    updater          BIGINT,
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          INT           NOT NULL DEFAULT 0,
    KEY idx_dataset_workspace (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Dataset instances; each owns one physical table and its typed field definitions.';

-- Field definitions — one row per typed column, owned by a dataset.
CREATE TABLE mate_dataset_field (
    id           BIGINT        NOT NULL PRIMARY KEY,
    dataset_id   BIGINT        NOT NULL,
    field_code   VARCHAR(64)   NOT NULL,   -- snake_case physical column name
    field_name   VARCHAR(256)  NOT NULL,   -- display name shown in UI / LLM prompts
    field_type   VARCHAR(16)   NOT NULL,   -- 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'DATE'
    field_unit   VARCHAR(32),              -- read by AnalyticsSchemaTool
    semantic     VARCHAR(512),             -- read by AnalyticsSchemaTool, injected into prompts
    is_nullable  TINYINT(1)    NOT NULL DEFAULT 1,
    ordinal      INT           NOT NULL,
    excel_header VARCHAR(512)  NOT NULL,   -- read by ExcelHeaderMatcher
    create_time  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dataset_field_code (dataset_id, field_code),
    KEY idx_dataset_field_dataset (dataset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Typed field definitions per dataset; drives physical DDL and Excel header matching.';
