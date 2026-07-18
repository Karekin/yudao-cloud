-- Canonical mirror of the DreamPlant module migration.
-- Source: cloudmold-module-dreamplant-server/src/main/resources/db/migration/V20260718_58__cloudmold_dreamplant_control_plane.sql

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_dreamplant_operation_tenant_id (tenant_id, operation_id),
    UNIQUE KEY uk_dreamplant_operation_idempotency (tenant_id, idempotency_key),
    CONSTRAINT ck_dreamplant_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_world_map (
    tenant_id BIGINT NOT NULL,
    map_key VARCHAR(64) NOT NULL,
    current_version BIGINT NOT NULL,
    publicly_readable BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, map_key),
    KEY idx_dreamplant_public_map (map_key, publicly_readable, updated_at),
    CONSTRAINT ck_dreamplant_map_version CHECK (current_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_snapshot (
    tenant_id BIGINT NOT NULL,
    map_key VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    schema_version VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    source_ref VARCHAR(192) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    operation_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, map_key, version),
    UNIQUE KEY uk_dreamplant_snapshot_operation (tenant_id, operation_id),
    CONSTRAINT fk_dreamplant_snapshot_map FOREIGN KEY (tenant_id, map_key)
        REFERENCES cloudmold_dreamplant_world_map (tenant_id, map_key),
    CONSTRAINT fk_dreamplant_snapshot_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_dreamplant_operation (tenant_id, operation_id),
    CONSTRAINT ck_dreamplant_snapshot_version CHECK (version > 0),
    CONSTRAINT ck_dreamplant_snapshot_source_ref CHECK
        (source_ref LIKE 'restricted:%' OR source_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_exploration (
    tenant_id BIGINT NOT NULL,
    exploration_run_id VARCHAR(128) NOT NULL,
    map_key VARCHAR(64) NOT NULL,
    intent TEXT NOT NULL,
    context_json JSON NOT NULL,
    requested_by_principal_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    outcome_json JSON NULL,
    evidence_ref VARCHAR(192) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    operation_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, exploration_run_id),
    UNIQUE KEY uk_dreamplant_exploration_operation (tenant_id, operation_id),
    KEY idx_dreamplant_exploration_status (tenant_id, status, updated_at),
    CONSTRAINT fk_dreamplant_exploration_map FOREIGN KEY (tenant_id, map_key)
        REFERENCES cloudmold_dreamplant_world_map (tenant_id, map_key),
    CONSTRAINT fk_dreamplant_exploration_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_dreamplant_operation (tenant_id, operation_id),
    CONSTRAINT ck_dreamplant_exploration_status CHECK
        (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','NEEDS_REVIEW','CANCELLED')),
    CONSTRAINT ck_dreamplant_exploration_version CHECK (version > 0),
    CONSTRAINT ck_dreamplant_exploration_evidence CHECK
        (evidence_ref IS NULL OR evidence_ref LIKE 'restricted:%' OR evidence_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
