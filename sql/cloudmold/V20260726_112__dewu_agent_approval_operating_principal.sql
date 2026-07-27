-- Separate the Agent runtime identity from the business operating principal.
-- Agent runs request approval; Dewu Operations makes the first business decision;
-- the AI governance user only issues the exact, short-lived approval authority.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

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

SET @cloudmold_dewu_tenant_id := (
    SELECT id FROM system_tenant
    WHERE name = '得物V1' AND status = 0 AND deleted = b'0'
    ORDER BY id LIMIT 1
);
SET @cloudmold_agent_requester_id := (
    SELECT id FROM system_users
    WHERE tenant_id = @cloudmold_dewu_tenant_id
      AND username = 'aiopsgov162' AND status = 0 AND deleted = b'0'
    ORDER BY id LIMIT 1
);
SET @cloudmold_operating_principal_id := (
    SELECT id FROM system_users
    WHERE tenant_id = @cloudmold_dewu_tenant_id
      AND username = 'dewuadmin' AND status = 0 AND deleted = b'0'
    ORDER BY id LIMIT 1
);
SET @cloudmold_authority_governance_id := (
    SELECT id FROM system_users
    WHERE tenant_id = @cloudmold_dewu_tenant_id
      AND username = 'aiopsapp162' AND status = 0 AND deleted = b'0'
    ORDER BY id LIMIT 1
);

INSERT INTO cloudmold_ai_ops_approval_policy
    (tenant_id, requester_user_id, approver_user_id, governance_user_id,
     status, version, created_at, updated_at)
SELECT @cloudmold_dewu_tenant_id, @cloudmold_agent_requester_id,
       @cloudmold_operating_principal_id, @cloudmold_authority_governance_id,
       'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE @cloudmold_dewu_tenant_id IS NOT NULL
  AND @cloudmold_agent_requester_id IS NOT NULL
  AND @cloudmold_operating_principal_id IS NOT NULL
  AND @cloudmold_authority_governance_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    requester_user_id = VALUES(requester_user_id),
    approver_user_id = VALUES(approver_user_id),
    governance_user_id = VALUES(governance_user_id),
    status = 'ACTIVE',
    version = version + 1,
    updated_at = UTC_TIMESTAMP(6);

INSERT INTO cloudmold_agent_actor_role_grant
    (grant_id, tenant_id, actor_user_id, role_code, status,
     valid_from, valid_until, granted_by_user_id, version, granted_at, updated_at)
SELECT CONCAT('grant-aiops-', @cloudmold_agent_requester_id, '-merchandising'),
       @cloudmold_dewu_tenant_id, @cloudmold_agent_requester_id,
       'merchandising', 'ACTIVE',
       UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 YEAR),
       @cloudmold_authority_governance_id, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE @cloudmold_dewu_tenant_id IS NOT NULL
  AND @cloudmold_agent_requester_id IS NOT NULL
  AND @cloudmold_authority_governance_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM cloudmold_agent_role_definition
      WHERE tenant_id = @cloudmold_dewu_tenant_id
        AND role_code = 'merchandising'
        AND status = 'ACTIVE'
  )
  AND NOT EXISTS (
      SELECT 1 FROM cloudmold_agent_actor_role_grant
      WHERE tenant_id = @cloudmold_dewu_tenant_id
        AND actor_user_id = @cloudmold_agent_requester_id
        AND role_code = 'merchandising'
        AND status = 'ACTIVE'
  );

UPDATE bpm_user_group
SET user_ids = CAST(JSON_ARRAY(@cloudmold_operating_principal_id) AS CHAR CHARACTER SET utf8mb4),
    description = 'R2/R3 高风险动作首先由得物运营中心审批；成员由得物V1租户管理员维护。',
    updater = 'CloudMold:operating-principal-policy',
    update_time = UTC_TIMESTAMP(6)
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent高风险动作审批组'
  AND creator = 'CloudMold'
  AND @cloudmold_operating_principal_id IS NOT NULL
  AND deleted = b'0';
