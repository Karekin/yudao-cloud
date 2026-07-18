-- DreamPlant normalized knowledge graph and recoverable exploration worker.
-- Typed entities are the write model; cloudmold_dreamplant_snapshot remains an immutable frontend projection.

ALTER TABLE cloudmold_dreamplant_snapshot
    MODIFY COLUMN payload_json LONGTEXT NOT NULL,
    ADD COLUMN canonical_hash_verified BIT(1) NOT NULL DEFAULT b'0' AFTER payload_sha256;

ALTER TABLE cloudmold_dreamplant_exploration
    MODIFY COLUMN context_json LONGTEXT NOT NULL,
    MODIFY COLUMN outcome_json LONGTEXT NULL,
    ADD COLUMN outcome_hash_verified BIT(1) NOT NULL DEFAULT b'0' AFTER evidence_ref,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER version,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 5 AFTER attempt_count,
    ADD COLUMN next_retry_at DATETIME(6) NULL AFTER max_attempts,
    ADD COLUMN lease_owner VARCHAR(128) NULL AFTER next_retry_at,
    ADD COLUMN lease_until DATETIME(6) NULL AFTER lease_owner,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER lease_until,
    ADD COLUMN last_error_message VARCHAR(1000) NULL AFTER last_error_code,
    ADD COLUMN started_at DATETIME(6) NULL AFTER last_error_message,
    ADD COLUMN completed_at DATETIME(6) NULL AFTER started_at,
    ADD KEY idx_dreamplant_exploration_due (status, next_retry_at, lease_until);

UPDATE cloudmold_dreamplant_snapshot
SET payload_sha256=LOWER(SHA2(payload_json,256)),canonical_hash_verified=b'1';
UPDATE cloudmold_dreamplant_exploration
SET evidence_ref=CONCAT('sha256:',LOWER(SHA2(outcome_json,256))),outcome_hash_verified=b'1'
WHERE outcome_json IS NOT NULL AND evidence_ref LIKE 'sha256:%';
UPDATE cloudmold_dreamplant_exploration SET next_retry_at=created_at WHERE next_retry_at IS NULL;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_capability (
    tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL, asset_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL, display_name VARCHAR(256) NOT NULL, status VARCHAR(32) NOT NULL,
    lifecycle_stage VARCHAR(64) NULL, owner_principal_id VARCHAR(128) NULL, canonical_key VARCHAR(192) NOT NULL,
    details_json LONGTEXT NOT NULL, details_sha256 CHAR(64) NOT NULL, source_ref VARCHAR(192) NOT NULL,
    evidence_ref VARCHAR(192) NULL, effective_at DATETIME(6) NULL, observed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id,map_key,asset_id), UNIQUE KEY uk_dreamplant_capability_key (tenant_id,map_key,canonical_key),
    KEY idx_dreamplant_capability_status (tenant_id,map_key,status),
    CONSTRAINT fk_dreamplant_capability_map FOREIGN KEY (tenant_id,map_key)
      REFERENCES cloudmold_dreamplant_world_map (tenant_id,map_key),
    CONSTRAINT ck_dreamplant_capability_version CHECK (version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_product LIKE cloudmold_dreamplant_capability;
CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_operation_agent LIKE cloudmold_dreamplant_capability;
CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_solution LIKE cloudmold_dreamplant_capability;
CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_agent_role LIKE cloudmold_dreamplant_capability;
CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_phase_roadmap LIKE cloudmold_dreamplant_capability;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_asset_revision (
    revision_id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL,
    asset_type VARCHAR(32) NOT NULL, asset_id VARCHAR(128) NOT NULL, asset_version BIGINT NOT NULL,
    details_json LONGTEXT NOT NULL, details_sha256 CHAR(64) NOT NULL, source_ref VARCHAR(192) NOT NULL,
    evidence_ref VARCHAR(192) NULL, occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (revision_id), UNIQUE KEY uk_dreamplant_asset_revision (tenant_id,map_key,asset_type,asset_id,asset_version),
    KEY idx_dreamplant_asset_revision_lookup (tenant_id,map_key,asset_type,asset_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_product_capability (
    tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL, relation_id VARCHAR(128) NOT NULL,
    from_asset_id VARCHAR(128) NOT NULL, to_asset_id VARCHAR(128) NOT NULL, relation_type VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL, status VARCHAR(32) NOT NULL, details_json LONGTEXT NOT NULL,
    details_sha256 CHAR(64) NOT NULL, source_ref VARCHAR(192) NOT NULL, evidence_ref VARCHAR(192) NULL,
    effective_at DATETIME(6) NULL, observed_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL, PRIMARY KEY (tenant_id,map_key,relation_id),
    UNIQUE KEY uk_dreamplant_product_capability_pair (tenant_id,map_key,from_asset_id,to_asset_id,relation_type),
    KEY idx_dreamplant_product_capability_to (tenant_id,map_key,to_asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_operation_agent_product LIKE cloudmold_dreamplant_product_capability;
CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_solution_operation_agent LIKE cloudmold_dreamplant_product_capability;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_evidence (
    tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL, evidence_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL, subject_type VARCHAR(32) NOT NULL, subject_id VARCHAR(128) NOT NULL,
    evidence_type VARCHAR(64) NOT NULL, title VARCHAR(256) NOT NULL, summary TEXT NULL,
    content_ref VARCHAR(192) NOT NULL, content_sha256 CHAR(64) NULL, details_json LONGTEXT NOT NULL,
    details_sha256 CHAR(64) NOT NULL, source_ref VARCHAR(192) NOT NULL, observed_at DATETIME(6) NULL,
    captured_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id,map_key,evidence_id), KEY idx_dreamplant_evidence_subject (tenant_id,map_key,subject_type,subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_metric_observation (
    tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL, metric_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL, subject_type VARCHAR(32) NOT NULL, subject_id VARCHAR(128) NOT NULL,
    metric_code VARCHAR(128) NOT NULL, metric_name VARCHAR(256) NOT NULL, metric_value DECIMAL(30,8) NOT NULL,
    metric_unit VARCHAR(32) NULL, status VARCHAR(32) NOT NULL, dimension_json LONGTEXT NOT NULL,
    details_json LONGTEXT NOT NULL, details_sha256 CHAR(64) NOT NULL, source_ref VARCHAR(192) NOT NULL,
    evidence_ref VARCHAR(192) NULL, window_start_at DATETIME(6) NULL, window_end_at DATETIME(6) NULL,
    measured_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id,map_key,metric_id),
    KEY idx_dreamplant_metric_subject (tenant_id,map_key,subject_type,subject_id,metric_code,measured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_sync_checkpoint (
    tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL, sync_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL, source_system VARCHAR(128) NOT NULL, source_namespace VARCHAR(192) NOT NULL,
    source_cursor VARCHAR(512) NULL, source_checkpoint_ref VARCHAR(192) NULL, target_projection VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL, details_json LONGTEXT NOT NULL, details_sha256 CHAR(64) NOT NULL,
    source_ref VARCHAR(192) NOT NULL, evidence_ref VARCHAR(192) NULL, checkpoint_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id,map_key,sync_key), KEY idx_dreamplant_sync_source (tenant_id,map_key,source_system,checkpoint_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_drift_record (
    tenant_id BIGINT NOT NULL, map_key VARCHAR(64) NOT NULL, drift_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL, subject_type VARCHAR(32) NOT NULL, subject_id VARCHAR(128) NOT NULL,
    drift_type VARCHAR(64) NOT NULL, severity VARCHAR(16) NOT NULL, status VARCHAR(32) NOT NULL,
    baseline_ref VARCHAR(192) NOT NULL, observed_ref VARCHAR(192) NOT NULL, details_json LONGTEXT NOT NULL,
    details_sha256 CHAR(64) NOT NULL, source_ref VARCHAR(192) NOT NULL, evidence_ref VARCHAR(192) NULL,
    detected_at DATETIME(6) NOT NULL, resolved_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL, PRIMARY KEY (tenant_id,map_key,drift_id),
    KEY idx_dreamplant_drift_subject (tenant_id,map_key,subject_type,subject_id,severity,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_dreamplant_exploration_step (
    step_id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, exploration_run_id VARCHAR(128) NOT NULL,
    exploration_version BIGINT NOT NULL, step_code VARCHAR(64) NOT NULL, step_status VARCHAR(32) NOT NULL,
    detail_json LONGTEXT NOT NULL, occurred_at DATETIME(6) NOT NULL, PRIMARY KEY (step_id),
    KEY idx_dreamplant_exploration_step (tenant_id,exploration_run_id,occurred_at),
    CONSTRAINT fk_dreamplant_exploration_step FOREIGN KEY (tenant_id,exploration_run_id)
      REFERENCES cloudmold_dreamplant_exploration (tenant_id,exploration_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
