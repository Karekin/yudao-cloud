-- Central deployment mirror for the isolated yudao BPM approval adapter.
-- BPM terminal status is evidence only: it never grants Agent Control approval
-- and never signs a SkillTask execution permit.
CREATE TABLE IF NOT EXISTS cloudmold_agent_approval_workflow_binding (
    approval_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id VARCHAR(64) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    approver_user_id BIGINT NULL,
    scope_hash CHAR(64) NOT NULL,
    process_definition_key VARCHAR(128) NOT NULL,
    process_instance_id VARCHAR(64) NULL,
    business_key VARCHAR(191) NOT NULL,
    status VARCHAR(48) NOT NULL,
    last_bpm_status INT NULL,
    last_reason_sha256 CHAR(64) NULL,
    start_attempt_token CHAR(36) NULL,
    start_attempt_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    start_attempted_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    terminal_at DATETIME(6) NULL,
    last_error_code VARCHAR(128) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_agent_approval_workflow_tenant_id (tenant_id, approval_id),
    UNIQUE KEY uk_agent_approval_workflow_business_key (business_key),
    UNIQUE KEY uk_agent_approval_workflow_process_instance (process_instance_id),
    KEY idx_agent_approval_workflow_queue (status, requested_at, tenant_id),
    CONSTRAINT fk_agent_approval_workflow_approval FOREIGN KEY (tenant_id, approval_id)
        REFERENCES cloudmold_agent_approval (tenant_id, approval_id),
    CONSTRAINT fk_agent_approval_workflow_work_order FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES cloudmold_agent_work_order (tenant_id, work_order_id),
    CONSTRAINT ck_agent_approval_workflow_scope_hash CHECK (scope_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_approval_workflow_risk CHECK (risk_level IN ('R0','R1','R2','R3')),
    CONSTRAINT ck_agent_approval_workflow_status CHECK (status IN (
        'START_REQUESTED','STARTING','RUNNING','START_UNCERTAIN',
        'BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')),
    CONSTRAINT ck_agent_approval_workflow_attempt CHECK (start_attempt_count BETWEEN 0 AND 1),
    CONSTRAINT ck_agent_approval_workflow_approver CHECK (
        (status = 'START_REQUESTED' AND approver_user_id IS NULL)
        OR (status <> 'START_REQUESTED' AND approver_user_id IS NOT NULL
            AND approver_user_id <> requester_user_id)),
    CONSTRAINT ck_agent_approval_workflow_instance CHECK (
        (status IN ('RUNNING','BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
            AND process_instance_id IS NOT NULL)
        OR status IN ('START_REQUESTED','STARTING','START_UNCERTAIN')),
    CONSTRAINT ck_agent_approval_workflow_terminal CHECK (
        (status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
            AND terminal_at IS NOT NULL AND last_bpm_status IS NOT NULL AND last_reason_sha256 IS NOT NULL)
        OR (status NOT IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
            AND terminal_at IS NULL)),
    CONSTRAINT ck_agent_approval_workflow_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_approval_workflow_event (
    event_id CHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    approval_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    bpm_status INT NOT NULL,
    observed_status VARCHAR(48) NOT NULL,
    reason_sha256 CHAR(64) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_agent_approval_workflow_event_tenant (tenant_id, event_id),
    KEY idx_agent_approval_workflow_event_binding (tenant_id, approval_id, observed_at),
    CONSTRAINT fk_agent_approval_workflow_event_binding FOREIGN KEY (tenant_id, approval_id)
        REFERENCES cloudmold_agent_approval_workflow_binding (tenant_id, approval_id),
    CONSTRAINT ck_agent_approval_workflow_event_id CHECK (event_id REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_approval_workflow_event_reason CHECK (reason_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_approval_workflow_event_status CHECK (
        (bpm_status = 2 AND observed_status = 'BPM_APPROVED_PENDING_ATTESTATION')
        OR (bpm_status = 3 AND observed_status = 'BPM_REJECTED')
        OR (bpm_status = 4 AND observed_status = 'BPM_CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
