SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT CASE
         WHEN COUNT(*) = 1
              AND JSON_VALID(MAX(user_ids))
              AND JSON_CONTAINS(MAX(user_ids), CAST(225 AS JSON))
           THEN 'PASS'
         ELSE CONCAT('FAIL: Agent approval group rows=', COUNT(*))
       END AS dewu_agent_approval_group_check
FROM bpm_user_group
WHERE tenant_id = 162
  AND name = 'Agent高风险动作审批组'
  AND status = 0
  AND deleted = b'0';

SELECT CASE
         WHEN COUNT(*) = 1 THEN 'PASS'
         ELSE CONCAT('FAIL: Agent approval listener rows=', COUNT(*))
       END AS dewu_agent_approval_listener_check
FROM bpm_process_listener
WHERE tenant_id = 162
  AND name = 'Agent审批终态对账监听模板'
  AND type = 'execution'
  AND event = 'end'
  AND value_type = 'expression'
  AND value = '${agentApprovalWorkflowTerminalDecisionReconciler.reconcile()}'
  AND status = 0
  AND deleted = b'0';

SELECT CASE
         WHEN COUNT(*) = 1
              AND JSON_VALID(MAX(conf))
              AND JSON_VALID(MAX(fields))
              AND JSON_LENGTH(MAX(fields)) = 10
           THEN 'PASS'
         ELSE CONCAT('FAIL: Agent approval form template rows=', COUNT(*))
       END AS dewu_agent_approval_form_check
FROM bpm_form
WHERE tenant_id = 162
  AND name = 'Agent高风险动作审批字段模板'
  AND status = 0
  AND deleted = b'0';
