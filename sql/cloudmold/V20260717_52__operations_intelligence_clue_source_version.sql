-- Govern Y-Shopping clue source corrections independently from physical snapshot/stream delivery.
-- Raw title, summary and clue_info content is never stored; only restricted evidence and digests cross this boundary.
CREATE TABLE cloudmold_intelligence_clue_source_version (
    source_version_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_biz_id VARCHAR(128) NOT NULL,
    business_revision BIGINT NOT NULL,
    supersedes_source_version_id VARCHAR(128) NULL,
    intelligence_type_code VARCHAR(64) NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    source_published_at DATETIME(6) NOT NULL,
    source_valid TINYINT(1) NOT NULL,
    source_deleted TINYINT(1) NOT NULL,
    title_sha256 CHAR(64) NOT NULL,
    summary_sha256 CHAR(64) NOT NULL,
    clue_info_sha256 CHAR(64) NOT NULL,
    clue_info_item_count INT NOT NULL,
    semantic_payload_sha256 CHAR(64) NOT NULL,
    source_observed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, source_version_id),
    UNIQUE KEY uk_int_clue_source_business_revision
        (tenant_id, source_system, source_biz_id, business_revision),
    UNIQUE KEY uk_int_clue_source_supersedes
        (tenant_id, supersedes_source_version_id),
    CONSTRAINT fk_int_clue_source_supersedes FOREIGN KEY
        (tenant_id, supersedes_source_version_id)
        REFERENCES cloudmold_intelligence_clue_source_version (tenant_id, source_version_id),
    CONSTRAINT ck_int_clue_source_revision CHECK (business_revision > 0),
    CONSTRAINT ck_int_clue_source_first_revision CHECK (
        (business_revision = 1 AND supersedes_source_version_id IS NULL)
        OR (business_revision > 1 AND supersedes_source_version_id IS NOT NULL)),
    CONSTRAINT ck_int_clue_source_deleted_valid CHECK (source_deleted = 0 OR source_valid = 0),
    CONSTRAINT ck_int_clue_source_time CHECK (source_published_at <= source_observed_at),
    CONSTRAINT ck_int_clue_source_item_count CHECK (clue_info_item_count BETWEEN 0 AND 10000),
    CONSTRAINT ck_int_clue_source_hashes CHECK (
        title_sha256 REGEXP '^[0-9a-f]{64}$'
        AND summary_sha256 REGEXP '^[0-9a-f]{64}$'
        AND clue_info_sha256 REGEXP '^[0-9a-f]{64}$'
        AND semantic_payload_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Immutable semantic business versions of an intelligence clue source row';

CREATE TABLE cloudmold_intelligence_clue_source_delivery (
    delivery_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_version_id VARCHAR(128) NOT NULL,
    source_dataset_id VARCHAR(128) NOT NULL,
    source_dataset_version BIGINT NOT NULL,
    declared_source_asset VARCHAR(128) NOT NULL,
    physical_source_asset VARCHAR(128) NOT NULL,
    source_transport VARCHAR(24) NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_record_version VARCHAR(128) NOT NULL,
    payload_schema_version VARCHAR(64) NOT NULL,
    source_schema_sha256 CHAR(64) NOT NULL,
    source_evidence_ref VARCHAR(160) NOT NULL,
    source_evidence_sha256 CHAR(64) NOT NULL,
    source_observed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, delivery_id),
    UNIQUE KEY uk_int_clue_source_delivery_record
        (tenant_id, source_dataset_id, source_dataset_version, source_record_key, source_record_version),
    KEY idx_int_clue_source_delivery_version (tenant_id, source_version_id),
    CONSTRAINT fk_int_clue_source_delivery_version FOREIGN KEY (tenant_id, source_version_id)
        REFERENCES cloudmold_intelligence_clue_source_version (tenant_id, source_version_id),
    CONSTRAINT ck_int_clue_source_delivery_dataset_version CHECK (source_dataset_version > 0),
    CONSTRAINT ck_int_clue_source_delivery_transport CHECK (source_transport IN ('SNAPSHOT_DF','KAFKA_RI')),
    CONSTRAINT ck_int_clue_source_delivery_schema_hash CHECK (
        source_schema_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_int_clue_source_delivery_evidence CHECK (
        source_evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'
        AND source_evidence_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Immutable physical snapshot or stream delivery bound to an exact Metadata dataset version';

ALTER TABLE cloudmold_intelligence_clue
    ADD COLUMN source_version_id VARCHAR(128) NULL AFTER tenant_id,
    ADD UNIQUE KEY uk_int_clue_source_version (tenant_id, source_version_id),
    ADD CONSTRAINT fk_int_clue_source_version FOREIGN KEY (tenant_id, source_version_id)
        REFERENCES cloudmold_intelligence_clue_source_version (tenant_id, source_version_id);
