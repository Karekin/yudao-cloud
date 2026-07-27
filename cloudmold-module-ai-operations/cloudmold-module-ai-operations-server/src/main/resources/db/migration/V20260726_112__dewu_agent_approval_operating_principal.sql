-- Module-owned schema. Tenant-specific identities are seeded by the canonical
-- deployment migration sql/cloudmold/V20260726_112__dewu_agent_approval_operating_principal.sql.
CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_approval_policy (
    tenant_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    approver_user_id BIGINT NOT NULL,
    governance_user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT ck_ai_ops_approval_policy_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_ai_ops_approval_policy_separation CHECK (
        requester_user_id <> approver_user_id
        AND governance_user_id <> requester_user_id
        AND governance_user_id <> approver_user_id
    ),
    CONSTRAINT ck_ai_ops_approval_policy_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
