SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @cloudmold_dewu_tenant_id := (
    SELECT id FROM system_tenant
    WHERE name = '得物V1' AND deleted = b'0'
    ORDER BY id LIMIT 1
);
SET @cloudmold_previous_approver_id := (
    SELECT id FROM system_users
    WHERE tenant_id = @cloudmold_dewu_tenant_id
      AND username = 'aiopsapp162' AND deleted = b'0'
    ORDER BY id LIMIT 1
);
SET @cloudmold_agent_requester_id := (
    SELECT id FROM system_users
    WHERE tenant_id = @cloudmold_dewu_tenant_id
      AND username = 'aiopsgov162' AND deleted = b'0'
    ORDER BY id LIMIT 1
);

UPDATE bpm_user_group
SET user_ids = CAST(JSON_ARRAY(@cloudmold_previous_approver_id) AS CHAR CHARACTER SET utf8mb4),
    description = 'R2/R3 高风险动作的人工审批候选人池；成员由得物V1租户管理员维护。',
    updater = 'CloudMold:agent-bpm-support',
    update_time = UTC_TIMESTAMP(6)
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent高风险动作审批组'
  AND creator = 'CloudMold'
  AND @cloudmold_previous_approver_id IS NOT NULL
  AND deleted = b'0';

DELETE FROM cloudmold_agent_actor_role_grant
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND actor_user_id = @cloudmold_agent_requester_id
  AND role_code = 'merchandising'
  AND grant_id = CONCAT('grant-aiops-', @cloudmold_agent_requester_id, '-merchandising');

DELETE FROM cloudmold_ai_ops_approval_policy
WHERE tenant_id = @cloudmold_dewu_tenant_id;

DROP TABLE IF EXISTS cloudmold_ai_ops_approval_policy;
