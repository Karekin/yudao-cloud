-- CloudMold-owned actor-role and exact approver authority grants.
-- Additive red-zone schema; no yudao table is changed and the module remains dormant.

CREATE TABLE IF NOT EXISTS cloudmold_agent_actor_role_grant (
    grant_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    valid_from DATETIME(6) NOT NULL,
    valid_until DATETIME(6) NOT NULL,
    granted_by_user_id BIGINT NOT NULL,
    revoked_by_user_id BIGINT NULL,
    revoke_reason VARCHAR(128) NULL,
    version BIGINT NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (grant_id),
    UNIQUE KEY uk_agent_actor_role_grant_tenant_id (tenant_id, grant_id),
    UNIQUE KEY uk_agent_actor_role_grant_active (tenant_id, actor_user_id, role_code, active_slot),
    KEY idx_agent_actor_role_grant_effective
        (tenant_id, actor_user_id, role_code, status, valid_from, valid_until),
    CONSTRAINT fk_agent_actor_role_grant_role FOREIGN KEY (tenant_id, role_code)
        REFERENCES cloudmold_agent_role_definition (tenant_id, role_code),
    CONSTRAINT ck_agent_actor_role_grant_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_agent_actor_role_grant_validity CHECK (valid_from < valid_until),
    CONSTRAINT ck_agent_actor_role_grant_self CHECK (granted_by_user_id <> actor_user_id),
    CONSTRAINT ck_agent_actor_role_grant_revoke CHECK (
        (status = 'REVOKED' AND revoked_by_user_id IS NOT NULL AND revoked_at IS NOT NULL
            AND revoke_reason IS NOT NULL AND revoked_by_user_id <> actor_user_id) OR
        (status <> 'REVOKED' AND revoked_by_user_id IS NULL AND revoked_at IS NULL AND revoke_reason IS NULL)),
    CONSTRAINT ck_agent_actor_role_grant_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_approval_authority_grant (
    grant_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    approver_user_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    approval_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    scope_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    valid_from DATETIME(6) NOT NULL,
    valid_until DATETIME(6) NOT NULL,
    granted_by_user_id BIGINT NOT NULL,
    revoked_by_user_id BIGINT NULL,
    revoke_reason VARCHAR(128) NULL,
    version BIGINT NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (grant_id),
    UNIQUE KEY uk_agent_approval_authority_tenant_id (tenant_id, grant_id),
    UNIQUE KEY uk_agent_approval_authority_active (tenant_id, approval_id, active_slot),
    KEY idx_agent_approval_authority_effective
        (tenant_id, approver_user_id, approval_id, status, valid_from, valid_until),
    CONSTRAINT fk_agent_approval_authority_approval FOREIGN KEY (tenant_id, approval_id)
        REFERENCES cloudmold_agent_approval (tenant_id, approval_id),
    CONSTRAINT fk_agent_approval_authority_role FOREIGN KEY (tenant_id, role_code)
        REFERENCES cloudmold_agent_role_definition (tenant_id, role_code),
    CONSTRAINT fk_agent_approval_authority_policy FOREIGN KEY (tenant_id, role_code, action_code)
        REFERENCES cloudmold_agent_role_action_policy (tenant_id, role_code, action_code),
    CONSTRAINT ck_agent_approval_authority_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_agent_approval_authority_risk CHECK (risk_level IN ('R0','R1','R2','R3')),
    CONSTRAINT ck_agent_approval_authority_scope CHECK (scope_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_approval_authority_validity CHECK (valid_from < valid_until),
    CONSTRAINT ck_agent_approval_authority_separation CHECK (
        approver_user_id <> requester_user_id AND granted_by_user_id <> approver_user_id
            AND granted_by_user_id <> requester_user_id),
    CONSTRAINT ck_agent_approval_authority_revoke CHECK (
        (status = 'REVOKED' AND revoked_by_user_id IS NOT NULL AND revoked_at IS NOT NULL
            AND revoke_reason IS NOT NULL AND revoked_by_user_id <> approver_user_id) OR
        (status <> 'REVOKED' AND revoked_by_user_id IS NULL AND revoked_at IS NULL AND revoke_reason IS NULL)),
    CONSTRAINT ck_agent_approval_authority_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
