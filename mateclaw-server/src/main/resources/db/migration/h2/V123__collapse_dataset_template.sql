-- V123: Collapse dataset template into dataset — H2 dialect.
-- See the mysql/ counterpart for full rationale.

DROP TABLE IF EXISTS mate_dataset_template_field;
DROP TABLE IF EXISTS mate_dataset_template;
DROP TABLE IF EXISTS mate_dataset;

CREATE TABLE mate_dataset (
    id               BIGINT        NOT NULL PRIMARY KEY,
    workspace_id     BIGINT        NOT NULL,
    name             VARCHAR(128)  NOT NULL,
    description      VARCHAR(1024),
    physical_table   VARCHAR(96)   NOT NULL,
    applied_ddl_hash VARCHAR(64),
    row_count        INT           NOT NULL DEFAULT 0,
    last_upload_at   TIMESTAMP,
    creator          BIGINT,
    updater          BIGINT,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT           NOT NULL DEFAULT 0
);
CREATE INDEX idx_dataset_workspace ON mate_dataset (workspace_id);

CREATE TABLE mate_dataset_field (
    id           BIGINT        NOT NULL PRIMARY KEY,
    dataset_id   BIGINT        NOT NULL,
    field_code   VARCHAR(64)   NOT NULL,
    field_name   VARCHAR(256)  NOT NULL,
    field_type   VARCHAR(16)   NOT NULL,
    field_unit   VARCHAR(32),
    semantic     VARCHAR(512),
    is_nullable  TINYINT       NOT NULL DEFAULT 1,
    ordinal      INT           NOT NULL,
    excel_header VARCHAR(512)  NOT NULL,
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INT           NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_dataset_field_code ON mate_dataset_field (dataset_id, field_code);
CREATE INDEX idx_dataset_field_dataset ON mate_dataset_field (dataset_id);
