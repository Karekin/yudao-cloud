-- Canonical authentication and quality first slice.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_quality_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_quality_operation_tenant_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_quality_operation_tenant_id (tenant_id, operation_id),
    CONSTRAINT ck_quality_operation_status CHECK (status IN (0, 10)),
    CONSTRAINT ck_quality_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Accepted idempotent quality command';

CREATE TABLE IF NOT EXISTS cloudmold_quality_standard (
    standard_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    standard_code VARCHAR(64) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    brand_code VARCHAR(64) NULL,
    applicable_sku_id VARCHAR(128) NULL,
    draft_content_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (standard_id),
    UNIQUE KEY uk_quality_standard_tenant_id (tenant_id, standard_id),
    UNIQUE KEY uk_quality_standard_tenant_code (tenant_id, standard_code),
    KEY idx_quality_standard_scope (tenant_id, category_code, brand_code, applicable_sku_id, status),
    CONSTRAINT ck_quality_standard_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_quality_standard_version CHECK (current_version >= 0),
    CONSTRAINT ck_quality_standard_hash CHECK (draft_content_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Current authentication and inspection standard head';

CREATE TABLE IF NOT EXISTS cloudmold_quality_standard_version (
    standard_version_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    standard_id VARCHAR(128) NOT NULL,
    standard_version BIGINT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    approver_principal_id VARCHAR(128) NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (standard_version_id),
    UNIQUE KEY uk_quality_standard_version_tenant_id (tenant_id, standard_version_id),
    UNIQUE KEY uk_quality_standard_version_number (tenant_id, standard_id, standard_version),
    UNIQUE KEY uk_quality_standard_version_identity
        (tenant_id, standard_id, standard_version, standard_version_id),
    CONSTRAINT fk_quality_standard_version_head FOREIGN KEY (tenant_id, standard_id)
        REFERENCES cloudmold_quality_standard (tenant_id, standard_id),
    CONSTRAINT ck_quality_standard_version_positive CHECK (standard_version > 0),
    CONSTRAINT ck_quality_standard_version_hash CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable published quality standard version';

CREATE TABLE IF NOT EXISTS cloudmold_authenticator_certification (
    certification_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    authenticator_principal_id VARCHAR(128) NOT NULL,
    standard_id VARCHAR(128) NOT NULL,
    certification_level VARCHAR(16) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (certification_id),
    UNIQUE KEY uk_auth_cert_tenant_id (tenant_id, certification_id),
    KEY idx_auth_cert_active
        (tenant_id, authenticator_principal_id, standard_id, status, effective_from, effective_to),
    CONSTRAINT fk_auth_cert_standard FOREIGN KEY (tenant_id, standard_id)
        REFERENCES cloudmold_quality_standard (tenant_id, standard_id),
    CONSTRAINT ck_auth_cert_level CHECK (certification_level IN ('JUNIOR', 'SENIOR', 'EXPERT')),
    CONSTRAINT ck_auth_cert_window CHECK (effective_to >= effective_from),
    CONSTRAINT ck_auth_cert_hash CHECK (evidence_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auth_cert_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_auth_cert_version CHECK (version > 0),
    CONSTRAINT ck_auth_cert_revoked CHECK (
        status <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoke_reason_code IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned authenticator certification';

CREATE TABLE IF NOT EXISTS cloudmold_inspection_task (
    task_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    standard_id VARCHAR(128) NOT NULL,
    standard_version BIGINT NOT NULL,
    standard_version_id VARCHAR(128) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_ref VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    authenticator_principal_id VARCHAR(128) NULL,
    decision VARCHAR(16) NULL,
    defect_code VARCHAR(64) NULL,
    evidence_ref VARCHAR(256) NULL,
    recheck_reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    assigned_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    decided_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_inspection_task_tenant_id (tenant_id, task_id),
    UNIQUE KEY uk_inspection_task_subject (tenant_id, subject_type, subject_ref),
    KEY idx_inspection_task_queue (tenant_id, status, priority, created_at),
    KEY idx_inspection_task_authenticator (tenant_id, authenticator_principal_id, status, updated_at),
    CONSTRAINT fk_inspection_task_standard_version FOREIGN KEY
        (tenant_id, standard_id, standard_version, standard_version_id)
        REFERENCES cloudmold_quality_standard_version
        (tenant_id, standard_id, standard_version, standard_version_id),
    CONSTRAINT ck_inspection_subject_type CHECK (
        subject_type IN ('INBOUND_ITEM', 'RETURN_ITEM', 'LISTING_SAMPLE', 'RISK_SAMPLE')),
    CONSTRAINT ck_inspection_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_inspection_status CHECK (
        status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS', 'DECIDED',
                   'RECHECK_REQUIRED', 'COMPLETED')),
    CONSTRAINT ck_inspection_decision CHECK (
        decision IS NULL OR decision IN ('PASS', 'FAIL')),
    CONSTRAINT ck_inspection_version CHECK (version > 0),
    CONSTRAINT ck_inspection_assignment CHECK (
        status = 'CREATED'
        OR (authenticator_principal_id IS NOT NULL AND assigned_at IS NOT NULL)),
    CONSTRAINT ck_inspection_result CHECK (
        status NOT IN ('DECIDED', 'RECHECK_REQUIRED', 'COMPLETED')
        OR (decision IS NOT NULL AND evidence_ref IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_inspection_failure CHECK (
        decision <> 'FAIL' OR defect_code IS NOT NULL),
    CONSTRAINT ck_inspection_completion CHECK (
        status <> 'COMPLETED' OR completed_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Authentication and quality inspection task';

CREATE TABLE IF NOT EXISTS cloudmold_inspection_task_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    task_version BIGINT NOT NULL,
    previous_status VARCHAR(24) NULL,
    current_status VARCHAR(24) NOT NULL,
    actor_principal_id VARCHAR(128) NULL,
    reason_code VARCHAR(64) NULL,
    operation_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_inspection_history_version (tenant_id, task_id, task_version),
    CONSTRAINT fk_inspection_history_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES cloudmold_inspection_task (tenant_id, task_id),
    CONSTRAINT fk_inspection_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_quality_operation (tenant_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable inspection task state history';

CREATE TABLE IF NOT EXISTS cloudmold_quality_capa (
    capa_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inspection_task_id VARCHAR(128) NOT NULL,
    root_cause_code VARCHAR(64) NOT NULL,
    owner_principal_id VARCHAR(128) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    effectiveness_evidence_ref VARCHAR(256) NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (capa_id),
    UNIQUE KEY uk_quality_capa_tenant_id (tenant_id, capa_id),
    UNIQUE KEY uk_quality_capa_task (tenant_id, inspection_task_id),
    KEY idx_quality_capa_status_due (tenant_id, status, due_date),
    CONSTRAINT fk_quality_capa_task FOREIGN KEY (tenant_id, inspection_task_id)
        REFERENCES cloudmold_inspection_task (tenant_id, task_id),
    CONSTRAINT ck_quality_capa_status CHECK (status IN ('OPEN', 'VERIFIED', 'CLOSED')),
    CONSTRAINT ck_quality_capa_version CHECK (version > 0),
    CONSTRAINT ck_quality_capa_resolution CHECK (
        status = 'OPEN'
        OR (effectiveness_evidence_ref IS NOT NULL AND resolved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Corrective and preventive action case';
