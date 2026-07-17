-- Versioned intelligence event taxonomy derived from the Y-Shopping event-code/level source.
-- A source level string is normalized into an ordered set of opaque level codes; no severity is inferred.

CREATE TABLE cloudmold_risk_intelligence_taxonomy (
    taxonomy_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_definition_version BIGINT NOT NULL,
    version BIGINT NOT NULL,
    retired_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (taxonomy_id),
    UNIQUE KEY uk_risk_int_tax_tenant_id (tenant_id, taxonomy_id),
    UNIQUE KEY uk_risk_int_tax_event (tenant_id, event_code),
    CONSTRAINT ck_risk_int_tax_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT ck_risk_int_tax_versions CHECK (
        version > 0 AND current_definition_version >= 0 AND current_definition_version < version),
    CONSTRAINT ck_risk_int_tax_lifecycle CHECK (
        (status='DRAFT' AND current_definition_version=0 AND retired_at IS NULL)
        OR (status='PUBLISHED' AND current_definition_version>0 AND retired_at IS NULL)
        OR (status='RETIRED' AND current_definition_version>0 AND retired_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current head of a governed intelligence event taxonomy';

CREATE TABLE cloudmold_risk_intelligence_taxonomy_version (
    taxonomy_version_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    taxonomy_id CHAR(36) NOT NULL,
    definition_version BIGINT NOT NULL,
    level_count INT NOT NULL,
    levels_sha256 CHAR(64) NOT NULL,
    approved_by_principal_id VARCHAR(128) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_table VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    source_observed_at DATETIME(6) NOT NULL,
    source_evidence_ref VARCHAR(160) NOT NULL,
    source_evidence_sha256 CHAR(64) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (taxonomy_version_id),
    UNIQUE KEY uk_risk_int_tax_ver_tenant_id (tenant_id, taxonomy_version_id),
    UNIQUE KEY uk_risk_int_tax_ver_number (tenant_id, taxonomy_id, definition_version),
    UNIQUE KEY uk_risk_int_tax_ver_identity (
        tenant_id, taxonomy_version_id, taxonomy_id, definition_version),
    CONSTRAINT fk_risk_int_tax_ver_head FOREIGN KEY (tenant_id, taxonomy_id)
        REFERENCES cloudmold_risk_intelligence_taxonomy (tenant_id, taxonomy_id),
    CONSTRAINT ck_risk_int_tax_ver_number CHECK (definition_version > 0),
    CONSTRAINT ck_risk_int_tax_ver_count CHECK (level_count BETWEEN 1 AND 32),
    CONSTRAINT ck_risk_int_tax_ver_hash CHECK (
        levels_sha256 REGEXP '^[0-9a-f]{64}$' AND source_evidence_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_risk_int_tax_ver_evidence CHECK (
        source_evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'),
    CONSTRAINT ck_risk_int_tax_ver_time CHECK (source_observed_at <= published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable approved taxonomy definition with source evidence';

CREATE TABLE cloudmold_risk_intelligence_taxonomy_level (
    level_definition_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    taxonomy_version_id CHAR(36) NOT NULL,
    taxonomy_id CHAR(36) NOT NULL,
    definition_version BIGINT NOT NULL,
    level_sequence INT NOT NULL,
    level_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (level_definition_id),
    UNIQUE KEY uk_risk_int_tax_level_tenant_id (tenant_id, level_definition_id),
    UNIQUE KEY uk_risk_int_tax_level_code (tenant_id, taxonomy_version_id, level_code),
    UNIQUE KEY uk_risk_int_tax_level_seq (tenant_id, taxonomy_version_id, level_sequence),
    UNIQUE KEY uk_risk_int_tax_level_ref (
        tenant_id, taxonomy_id, definition_version, level_code),
    CONSTRAINT fk_risk_int_tax_level_ver FOREIGN KEY (
        tenant_id, taxonomy_version_id, taxonomy_id, definition_version)
        REFERENCES cloudmold_risk_intelligence_taxonomy_version (
            tenant_id, taxonomy_version_id, taxonomy_id, definition_version),
    CONSTRAINT ck_risk_int_tax_level_seq CHECK (level_sequence BETWEEN 1 AND 32),
    CONSTRAINT ck_risk_int_tax_level_code CHECK (level_code REGEXP '^[A-Z][A-Z0-9_-]{0,31}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ordered opaque level codes in an immutable taxonomy version';

CREATE TABLE cloudmold_risk_intelligence_taxonomy_retirement (
    retirement_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    taxonomy_id CHAR(36) NOT NULL,
    taxonomy_version BIGINT NOT NULL,
    retired_by_principal_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_table VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_record_key VARCHAR(128) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    source_observed_at DATETIME(6) NOT NULL,
    source_evidence_ref VARCHAR(160) NOT NULL,
    source_evidence_sha256 CHAR(64) NOT NULL,
    retired_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (retirement_id),
    UNIQUE KEY uk_risk_int_tax_ret_tenant_id (tenant_id, retirement_id),
    UNIQUE KEY uk_risk_int_tax_ret_head (tenant_id, taxonomy_id),
    CONSTRAINT fk_risk_int_tax_ret_head FOREIGN KEY (tenant_id, taxonomy_id)
        REFERENCES cloudmold_risk_intelligence_taxonomy (tenant_id, taxonomy_id),
    CONSTRAINT ck_risk_int_tax_ret_ver CHECK (taxonomy_version > 1),
    CONSTRAINT ck_risk_int_tax_ret_hash CHECK (
        source_evidence_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_risk_int_tax_ret_evidence CHECK (
        source_evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'),
    CONSTRAINT ck_risk_int_tax_ret_time CHECK (
        source_observed_at <= retired_at AND retired_at <= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable source-backed retirement of a taxonomy';

