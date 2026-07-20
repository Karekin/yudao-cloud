-- CloudMold-owned role work control plane. Additive red-zone schema; no yudao tables are changed.

CREATE TABLE IF NOT EXISTS cloudmold_agent_role_definition (
    role_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    responsibility_json JSON NOT NULL,
    kpi_json JSON NOT NULL,
    approval_boundary_json JSON NOT NULL,
    memory_policy_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_agent_role_tenant_code (tenant_id, role_code),
    UNIQUE KEY uk_agent_role_tenant_id (tenant_id, role_id),
    CONSTRAINT ck_agent_role_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_agent_role_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_role_action_policy (
    policy_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    permission_mode VARCHAR(16) NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    approval_required BIT(1) NOT NULL,
    enabled BIT(1) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_agent_policy_tenant_action (tenant_id, role_code, action_code),
    UNIQUE KEY uk_agent_policy_tenant_id (tenant_id, policy_id),
    CONSTRAINT fk_agent_policy_role FOREIGN KEY (tenant_id, role_code)
        REFERENCES cloudmold_agent_role_definition (tenant_id, role_code),
    CONSTRAINT ck_agent_policy_mode CHECK (permission_mode IN ('ALLOW','DENY')),
    CONSTRAINT ck_agent_policy_risk CHECK (risk_level IN ('R0','R1','R2','R3')),
    CONSTRAINT ck_agent_policy_r3_approval CHECK (risk_level <> 'R3' OR approval_required = b'1'),
    CONSTRAINT ck_agent_policy_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_work_order (
    work_order_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    business_context_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    assignee_user_id BIGINT NULL,
    approval_id VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (work_order_id),
    UNIQUE KEY uk_agent_work_order_tenant_id (tenant_id, work_order_id),
    KEY idx_agent_work_order_queue (tenant_id, role_code, status, updated_at),
    CONSTRAINT fk_agent_work_order_role FOREIGN KEY (tenant_id, role_code)
        REFERENCES cloudmold_agent_role_definition (tenant_id, role_code),
    CONSTRAINT fk_agent_work_order_policy FOREIGN KEY (tenant_id, role_code, action_code)
        REFERENCES cloudmold_agent_role_action_policy (tenant_id, role_code, action_code),
    CONSTRAINT ck_agent_work_order_status CHECK (
        status IN ('WAITING_APPROVAL','READY','IN_PROGRESS','BLOCKED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_agent_work_order_version CHECK (version > 0),
    CONSTRAINT ck_agent_work_order_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (status <> 'COMPLETED' AND completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_role_handoff (
    handoff_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id VARCHAR(64) NOT NULL,
    from_role_code VARCHAR(64) NOT NULL,
    from_action_code VARCHAR(128) NOT NULL,
    to_role_code VARCHAR(64) NOT NULL,
    to_action_code VARCHAR(128) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    accepted_by_user_id BIGINT NULL,
    version BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    PRIMARY KEY (handoff_id),
    UNIQUE KEY uk_agent_handoff_tenant_id (tenant_id, handoff_id),
    KEY idx_agent_handoff_queue (tenant_id, to_role_code, status, requested_at),
    CONSTRAINT fk_agent_handoff_work_order FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES cloudmold_agent_work_order (tenant_id, work_order_id),
    CONSTRAINT fk_agent_handoff_from_role FOREIGN KEY (tenant_id, from_role_code)
        REFERENCES cloudmold_agent_role_definition (tenant_id, role_code),
    CONSTRAINT fk_agent_handoff_to_role FOREIGN KEY (tenant_id, to_role_code)
        REFERENCES cloudmold_agent_role_definition (tenant_id, role_code),
    CONSTRAINT fk_agent_handoff_from_policy FOREIGN KEY (tenant_id, from_role_code, from_action_code)
        REFERENCES cloudmold_agent_role_action_policy (tenant_id, role_code, action_code),
    CONSTRAINT fk_agent_handoff_to_policy FOREIGN KEY (tenant_id, to_role_code, to_action_code)
        REFERENCES cloudmold_agent_role_action_policy (tenant_id, role_code, action_code),
    CONSTRAINT ck_agent_handoff_roles CHECK (from_role_code <> to_role_code),
    CONSTRAINT ck_agent_handoff_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    CONSTRAINT ck_agent_handoff_acceptance CHECK (
        (status = 'ACCEPTED' AND accepted_by_user_id IS NOT NULL AND accepted_at IS NOT NULL) OR
        (status <> 'ACCEPTED' AND accepted_by_user_id IS NULL AND accepted_at IS NULL)),
    CONSTRAINT ck_agent_handoff_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_approval (
    approval_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id VARCHAR(64) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    approver_user_id BIGINT NULL,
    scope_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_agent_approval_tenant_id (tenant_id, approval_id),
    UNIQUE KEY uk_agent_approval_work_order (tenant_id, work_order_id),
    KEY idx_agent_approval_queue (tenant_id, status, requested_at),
    CONSTRAINT fk_agent_approval_work_order FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES cloudmold_agent_work_order (tenant_id, work_order_id),
    CONSTRAINT ck_agent_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_agent_approval_separation CHECK (
        approver_user_id IS NULL OR approver_user_id <> requester_user_id),
    CONSTRAINT ck_agent_approval_scope_hash CHECK (scope_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_approval_decision CHECK (
        (status = 'PENDING' AND approver_user_id IS NULL AND decided_at IS NULL) OR
        (status IN ('APPROVED','REJECTED') AND approver_user_id IS NOT NULL AND decided_at IS NOT NULL) OR
        status IN ('EXPIRED','CANCELLED')),
    CONSTRAINT ck_agent_approval_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_business_result (
    result_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id VARCHAR(64) NOT NULL,
    outcome_code VARCHAR(128) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    evidence_ref VARCHAR(256) NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (result_id),
    UNIQUE KEY uk_agent_result_tenant_id (tenant_id, result_id),
    UNIQUE KEY uk_agent_result_work_order (tenant_id, work_order_id),
    CONSTRAINT fk_agent_result_work_order FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES cloudmold_agent_work_order (tenant_id, work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_audit_event (
    audit_event_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    detail_json JSON NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (audit_event_id),
    UNIQUE KEY uk_agent_audit_aggregate_event
        (tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type),
    KEY idx_agent_audit_timeline (tenant_id, aggregate_type, aggregate_id, occurred_at),
    CONSTRAINT ck_agent_audit_version CHECK (aggregate_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_control_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(64) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_agent_control_operation_key (tenant_id, idempotency_key),
    CONSTRAINT ck_agent_control_operation_status CHECK (status IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
