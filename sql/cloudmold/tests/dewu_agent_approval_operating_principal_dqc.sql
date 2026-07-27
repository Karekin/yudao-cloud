SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT CASE
         WHEN COUNT(*) = 1
              AND MAX(requester_user_id) = 226
              AND MAX(approver_user_id) = 225
              AND MAX(governance_user_id) = 227
              AND MAX(status) = 'ACTIVE'
           THEN 'PASS'
         ELSE CONCAT('FAIL: approval policy rows=', COUNT(*))
       END AS dewu_agent_approval_policy_check
FROM cloudmold_ai_ops_approval_policy
WHERE tenant_id = 162;

SELECT CASE
         WHEN COUNT(*) = 1
              AND JSON_VALID(MAX(user_ids))
              AND JSON_CONTAINS(MAX(user_ids), CAST(225 AS JSON))
              AND NOT JSON_CONTAINS(MAX(user_ids), CAST(227 AS JSON))
           THEN 'PASS'
         ELSE CONCAT('FAIL: operating-principal approval group rows=', COUNT(*))
       END AS dewu_operating_principal_group_check
FROM bpm_user_group
WHERE tenant_id = 162
  AND name = 'Agent高风险动作审批组'
  AND status = 0
  AND deleted = b'0';

SELECT CASE
         WHEN COUNT(*) = 1 THEN 'PASS'
         ELSE CONCAT('FAIL: Agent requester role grants=', COUNT(*))
       END AS dewu_agent_requester_role_grant_check
FROM cloudmold_agent_actor_role_grant
WHERE tenant_id = 162
  AND actor_user_id = 226
  AND role_code = 'merchandising'
  AND status = 'ACTIVE'
  AND granted_by_user_id = 227
  AND valid_from <= UTC_TIMESTAMP(6)
  AND valid_until > UTC_TIMESTAMP(6);
