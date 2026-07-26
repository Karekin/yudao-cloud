-- Every row returned by this query must have violation_count = 0.
-- Technical-token checks normalize persisted text with the same lowercase/alphanumeric-only rule used by
-- AgentControlServiceImpl before matching the fixed token list.

SELECT 'agent_control_r3_without_approval' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_agent_role_action_policy
WHERE risk_level = 'R3' AND approval_required = b'0'
UNION ALL
SELECT 'agent_control_work_order_without_enabled_allow_policy', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_role_action_policy p
  ON p.tenant_id = w.tenant_id AND p.role_code = w.role_code AND p.action_code = w.action_code
WHERE p.policy_id IS NULL OR p.permission_mode <> 'ALLOW' OR p.enabled = b'0'
UNION ALL
SELECT 'agent_control_approval_requester_mismatch', COUNT(*)
FROM cloudmold_agent_approval a
JOIN cloudmold_agent_work_order w
  ON w.tenant_id = a.tenant_id AND w.work_order_id = a.work_order_id
WHERE a.requester_user_id <> w.requester_user_id OR a.action_code <> w.action_code
UNION ALL
SELECT 'agent_control_non_independent_approval', COUNT(*)
FROM cloudmold_agent_approval
WHERE approver_user_id IS NOT NULL AND approver_user_id = requester_user_id
UNION ALL
SELECT 'agent_control_approval_scope_drift', COUNT(*)
FROM cloudmold_agent_approval a
JOIN cloudmold_agent_work_order w
  ON w.tenant_id = a.tenant_id AND w.work_order_id = a.work_order_id
WHERE LOWER(a.scope_hash) <>
      CASE WHEN w.action_policy_id IS NULL THEN
          SHA2(CONCAT(w.action_code, '\n', SHA2(CAST(w.business_context_json AS CHAR), 256)), 256)
      ELSE SHA2(CONCAT(
          w.tenant_id, '\n', w.work_order_id, '\n', w.role_code, '\n', w.action_code, '\n',
          w.action_policy_id, '\n', w.action_policy_version, '\n', COALESCE(w.skill_id, '-'), '\n',
          COALESCE(w.skill_version, '-'), '\n', COALESCE(w.skill_definition_closure_sha256, '-'), '\n',
          COALESCE(w.execution_input_sha256, '-'), '\n', COALESCE(w.risk_level, '-')), 256) END
UNION ALL
SELECT 'agent_control_work_order_approval_binding_mismatch', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_approval a
  ON a.tenant_id = w.tenant_id AND a.approval_id = w.approval_id AND a.work_order_id = w.work_order_id
WHERE w.approval_id IS NOT NULL AND a.approval_id IS NULL
UNION ALL
SELECT 'agent_control_completed_work_order_without_one_result', COUNT(*)
FROM (
    SELECT w.tenant_id, w.work_order_id, COUNT(r.result_id) AS result_count
    FROM cloudmold_agent_work_order w
    LEFT JOIN cloudmold_agent_business_result r
      ON r.tenant_id = w.tenant_id AND r.work_order_id = w.work_order_id
    WHERE w.status = 'COMPLETED'
    GROUP BY w.tenant_id, w.work_order_id
    HAVING COUNT(r.result_id) <> 1
) violations
UNION ALL
SELECT 'agent_control_result_for_non_completed_work_order', COUNT(*)
FROM cloudmold_agent_business_result r
JOIN cloudmold_agent_work_order w
  ON w.tenant_id = r.tenant_id AND w.work_order_id = r.work_order_id
WHERE w.status <> 'COMPLETED'
UNION ALL
SELECT 'agent_control_accepted_handoff_without_independent_actor', COUNT(*)
FROM cloudmold_agent_role_handoff
WHERE status = 'ACCEPTED' AND accepted_by_user_id = requested_by_user_id
UNION ALL
SELECT 'agent_control_handoff_technical_protocol_leak', COUNT(*)
FROM cloudmold_agent_role_handoff
WHERE REGEXP_REPLACE(LOWER(summary), '[^a-z0-9]', '')
      REGEXP 'tenantid|operatorid|idempotencykey|clientrequestkey|cloudmoldskilltask'
UNION ALL
SELECT 'agent_control_business_context_technical_protocol_leak', COUNT(*)
FROM cloudmold_agent_work_order
WHERE REGEXP_REPLACE(LOWER(CAST(business_context_json AS CHAR)), '[^a-z0-9]', '')
      REGEXP 'tenantid|operatorid|idempotencykey|clientrequestkey|cloudmoldskilltask'
UNION ALL
SELECT 'agent_control_work_order_missing_create_audit', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_audit_event e
  ON e.tenant_id = w.tenant_id AND e.aggregate_type = 'role_work_order'
 AND e.aggregate_id = w.work_order_id AND e.aggregate_version = 1
 AND e.event_type = 'agent_control.work_order.created'
WHERE e.audit_event_id IS NULL
UNION ALL
SELECT 'agent_control_handoff_missing_create_audit', COUNT(*)
FROM cloudmold_agent_role_handoff h
LEFT JOIN cloudmold_agent_audit_event e
  ON e.tenant_id = h.tenant_id AND e.aggregate_type = 'role_handoff'
 AND e.aggregate_id = h.handoff_id AND e.aggregate_version = 1
 AND e.event_type = 'agent_control.handoff.created'
WHERE e.audit_event_id IS NULL
UNION ALL
SELECT 'agent_control_approval_missing_request_audit', COUNT(*)
FROM cloudmold_agent_approval a
LEFT JOIN cloudmold_agent_audit_event e
  ON e.tenant_id = a.tenant_id AND e.aggregate_type = 'role_approval'
 AND e.aggregate_id = a.approval_id AND e.aggregate_version = 1
 AND e.event_type = 'agent_control.approval.requested'
WHERE e.audit_event_id IS NULL
UNION ALL
SELECT 'agent_control_result_missing_audit', COUNT(*)
FROM cloudmold_agent_business_result r
LEFT JOIN cloudmold_agent_audit_event e
  ON e.tenant_id = r.tenant_id AND e.aggregate_type = 'business_result'
 AND e.aggregate_id = r.result_id AND e.aggregate_version = 1
 AND e.event_type = 'agent_control.business_result.recorded'
WHERE e.audit_event_id IS NULL
UNION ALL
SELECT 'agent_control_business_result_technical_protocol_leak', COUNT(*)
FROM cloudmold_agent_business_result
WHERE REGEXP_REPLACE(LOWER(summary), '[^a-z0-9]', '')
      REGEXP 'tenantid|operatorid|idempotencykey|clientrequestkey|cloudmoldskilltask'
UNION ALL
SELECT 'agent_control_actor_role_grant_invalid_lifecycle', COUNT(*)
FROM cloudmold_agent_actor_role_grant
WHERE valid_from >= valid_until OR granted_by_user_id = actor_user_id
   OR (status = 'REVOKED' AND (revoked_by_user_id IS NULL OR revoked_at IS NULL OR revoke_reason IS NULL
       OR revoked_by_user_id = actor_user_id))
   OR (status <> 'REVOKED' AND (revoked_by_user_id IS NOT NULL OR revoked_at IS NOT NULL
       OR revoke_reason IS NOT NULL))
UNION ALL
SELECT 'agent_control_actor_role_grant_wrong_tenant_or_role', COUNT(*)
FROM cloudmold_agent_actor_role_grant g
LEFT JOIN cloudmold_agent_role_definition r
  ON r.tenant_id = g.tenant_id AND r.role_code = g.role_code
WHERE r.role_id IS NULL
UNION ALL
SELECT 'agent_control_work_order_actor_without_historical_role_grant', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_actor_role_grant g
  ON g.tenant_id = w.tenant_id AND g.actor_user_id = COALESCE(w.assignee_user_id, w.requester_user_id)
 AND g.role_code = w.role_code AND g.valid_from <= w.updated_at AND g.valid_until > w.updated_at
WHERE g.grant_id IS NULL
UNION ALL
SELECT 'agent_control_approval_authority_invalid_lifecycle', COUNT(*)
FROM cloudmold_agent_approval_authority_grant
WHERE valid_from >= valid_until OR approver_user_id = requester_user_id
   OR granted_by_user_id = approver_user_id OR granted_by_user_id = requester_user_id
   OR (status = 'REVOKED' AND (revoked_by_user_id IS NULL OR revoked_at IS NULL OR revoke_reason IS NULL
       OR revoked_by_user_id = approver_user_id))
   OR (status <> 'REVOKED' AND (revoked_by_user_id IS NOT NULL OR revoked_at IS NOT NULL
       OR revoke_reason IS NOT NULL))
UNION ALL
SELECT 'agent_control_approval_authority_scope_mismatch', COUNT(*)
FROM cloudmold_agent_approval_authority_grant g
JOIN cloudmold_agent_approval a
  ON a.tenant_id = g.tenant_id AND a.approval_id = g.approval_id
JOIN cloudmold_agent_work_order w
  ON w.tenant_id = a.tenant_id AND w.work_order_id = a.work_order_id
LEFT JOIN cloudmold_agent_role_action_policy p
  ON p.tenant_id = w.tenant_id AND p.role_code = w.role_code AND p.action_code = w.action_code
WHERE g.requester_user_id <> a.requester_user_id OR g.role_code <> w.role_code
   OR g.action_code <> a.action_code OR g.action_code <> w.action_code
   OR g.scope_hash <> a.scope_hash OR g.risk_level <> w.risk_level
   OR w.action_policy_id <> p.policy_id OR w.action_policy_version <> p.version
UNION ALL
SELECT 'agent_control_decided_approval_without_exact_effective_grant', COUNT(*)
FROM cloudmold_agent_approval a
JOIN cloudmold_agent_work_order w
  ON w.tenant_id = a.tenant_id AND w.work_order_id = a.work_order_id
JOIN cloudmold_agent_role_action_policy p
  ON p.tenant_id = w.tenant_id AND p.role_code = w.role_code AND p.action_code = w.action_code
LEFT JOIN cloudmold_agent_approval_authority_grant g
  ON g.tenant_id = a.tenant_id AND g.approver_user_id = a.approver_user_id
 AND g.approval_id = a.approval_id AND g.role_code = w.role_code AND g.action_code = w.action_code
 AND g.risk_level = w.risk_level AND g.scope_hash = a.scope_hash
 AND g.granted_at <= a.decided_at AND g.valid_from <= a.decided_at AND g.valid_until > a.decided_at
WHERE a.status IN ('APPROVED','REJECTED') AND g.grant_id IS NULL
UNION ALL
SELECT 'agent_control_authority_grant_missing_audit', COUNT(*)
FROM (
    SELECT tenant_id, grant_id, CASE WHEN status = 'REVOKED' THEN version ELSE 1 END AS version,
           'actor_role_grant' AS aggregate_type,
           CASE WHEN status = 'REVOKED' THEN 'agent_control.actor_role_grant.revoked'
                ELSE 'agent_control.actor_role_grant.granted' END AS event_type
    FROM cloudmold_agent_actor_role_grant
    UNION ALL
    SELECT tenant_id, grant_id, CASE WHEN status = 'REVOKED' THEN version ELSE 1 END,
           'approval_authority_grant',
           CASE WHEN status = 'REVOKED' THEN 'agent_control.approval_authority_grant.revoked'
                ELSE 'agent_control.approval_authority_grant.granted' END
    FROM cloudmold_agent_approval_authority_grant
) g
LEFT JOIN cloudmold_agent_audit_event e
  ON e.tenant_id = g.tenant_id AND e.aggregate_type = g.aggregate_type
 AND e.aggregate_id = g.grant_id AND e.aggregate_version = g.version AND e.event_type = g.event_type
WHERE e.audit_event_id IS NULL
UNION ALL
SELECT 'agent_control_executable_policy_without_skill_identity', COUNT(*)
FROM cloudmold_agent_role_action_policy
WHERE (execution_required=b'1' AND (skill_id IS NULL OR skill_version IS NULL
       OR skill_definition_closure_sha256 NOT REGEXP '^[0-9a-f]{64}$'))
   OR (execution_required=b'0' AND (skill_id IS NOT NULL OR skill_version IS NOT NULL
       OR skill_definition_closure_sha256 IS NOT NULL))
UNION ALL
SELECT 'agent_control_executable_completion_without_accepted_binding', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_execution_binding b
  ON b.tenant_id=w.tenant_id AND b.work_order_id=w.work_order_id AND b.status='EXECUTION_SUCCEEDED'
WHERE w.execution_required=b'1' AND w.status='COMPLETED'
  AND (b.binding_id IS NULL OR b.skill_id<>w.skill_id OR b.skill_version<>w.skill_version
       OR b.skill_definition_closure_sha256<>w.skill_definition_closure_sha256
       OR b.input_sha256<>w.execution_input_sha256 OR b.terminal_result_sha256 IS NULL)
UNION ALL
SELECT 'agent_control_binding_for_non_executable_work', COUNT(*)
FROM cloudmold_agent_execution_binding b
JOIN cloudmold_agent_work_order w
  ON w.tenant_id=b.tenant_id AND w.work_order_id=b.work_order_id
WHERE w.execution_required=b'0'
UNION ALL
SELECT 'agent_control_mission_cross_tenant_or_missing_work', COUNT(*)
FROM cloudmold_agent_work_dependency d
LEFT JOIN cloudmold_agent_work_order p
  ON p.tenant_id=d.tenant_id AND p.work_order_id=d.predecessor_work_order_id AND p.mission_id=d.mission_id
LEFT JOIN cloudmold_agent_work_order s
  ON s.tenant_id=d.tenant_id AND s.work_order_id=d.successor_work_order_id AND s.mission_id=d.mission_id
WHERE p.work_order_id IS NULL OR s.work_order_id IS NULL
UNION ALL
SELECT 'agent_control_waiting_work_without_wakeup', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_event_subscription e
  ON e.tenant_id=w.tenant_id AND e.work_order_id=w.work_order_id AND e.status='ACTIVE'
LEFT JOIN cloudmold_agent_mission_timer t
  ON t.tenant_id=w.tenant_id AND t.work_order_id=w.work_order_id AND t.status='SCHEDULED'
LEFT JOIN cloudmold_agent_metric_subscription ms
  ON ms.tenant_id=w.tenant_id AND ms.work_order_id=w.work_order_id AND ms.status='ACTIVE'
WHERE (w.status='WAITING_EVENT' AND e.subscription_id IS NULL)
   OR (w.status='WAITING_METRIC' AND ms.subscription_id IS NULL)
   OR (w.status='WAITING_TIMER' AND t.timer_id IS NULL)
UNION ALL
SELECT 'agent_control_metric_match_without_observation', COUNT(*)
FROM cloudmold_agent_metric_subscription subscription
LEFT JOIN cloudmold_agent_metric_observation_inbox observation
  ON observation.tenant_id=subscription.tenant_id
 AND observation.observation_id=subscription.matched_observation_id
WHERE subscription.status='MATCHED'
  AND (observation.observation_id IS NULL
       OR observation.metric_id<>subscription.metric_id
       OR observation.metric_version<>subscription.metric_version
       OR observation.dimension_hash<>subscription.dimension_hash
       OR observation.unit_code<>subscription.unit_code
       OR observation.evidence_sha256<>subscription.matched_evidence_sha256)
UNION ALL
SELECT 'agent_control_active_lease_for_non_running_work', COUNT(*)
FROM cloudmold_agent_run_lease l
JOIN cloudmold_agent_work_order w
  ON w.tenant_id=l.tenant_id AND w.work_order_id=l.work_order_id
WHERE l.status='ACTIVE' AND (w.status<>'IN_PROGRESS' OR w.active_run_id<>l.run_id)
UNION ALL
SELECT 'agent_control_checkpoint_fencing_mismatch', COUNT(*)
FROM cloudmold_agent_mission_checkpoint c
JOIN cloudmold_agent_run r ON r.tenant_id=c.tenant_id AND r.run_id=c.run_id
WHERE c.fencing_token<>r.fencing_token
UNION ALL
SELECT 'agent_control_completed_mission_with_open_work', COUNT(*)
FROM cloudmold_agent_business_mission m
JOIN cloudmold_agent_work_order w ON w.tenant_id=m.tenant_id AND w.mission_id=m.mission_id
WHERE m.status='COMPLETED' AND w.status NOT IN ('COMPLETED','CANCELLED')
UNION ALL
SELECT 'agent_control_execution_binding_generation_invalid', COUNT(*)
FROM (
    SELECT tenant_id,work_order_id,COUNT(*) AS binding_count,MAX(execution_generation) AS max_generation,
           SUM(status IN ('BOUND','EXECUTION_SUCCEEDED')) AS current_count
    FROM cloudmold_agent_execution_binding GROUP BY tenant_id,work_order_id
) x
WHERE binding_count<>max_generation OR current_count<>1
UNION ALL
SELECT 'agent_control_mission_waiting_approval_without_request', COUNT(*)
FROM cloudmold_agent_work_order w
LEFT JOIN cloudmold_agent_approval a
  ON a.tenant_id=w.tenant_id AND a.work_order_id=w.work_order_id AND a.status='PENDING'
WHERE w.mission_id IS NOT NULL AND w.status='WAITING_APPROVAL'
  AND (w.approval_id IS NULL OR a.approval_id<>w.approval_id)
UNION ALL
SELECT 'agent_control_mission_executable_successor_without_structured_predecessor', COUNT(*)
FROM cloudmold_agent_work_dependency d
JOIN cloudmold_agent_work_order p
  ON p.tenant_id=d.tenant_id AND p.work_order_id=d.predecessor_work_order_id
JOIN cloudmold_agent_work_order s
  ON s.tenant_id=d.tenant_id AND s.work_order_id=d.successor_work_order_id
LEFT JOIN cloudmold_agent_mission_checkpoint c
  ON c.tenant_id=p.tenant_id AND c.work_order_id=p.work_order_id AND c.run_id=p.active_run_id
WHERE p.status='COMPLETED' AND s.execution_required=b'1' AND c.checkpoint_id IS NULL
UNION ALL
SELECT 'agent_control_mission_supervisor_is_operational_assignee', COUNT(*)
FROM cloudmold_agent_business_mission m
JOIN cloudmold_agent_work_order w
  ON w.tenant_id=m.tenant_id AND w.mission_id=m.mission_id
WHERE w.assignee_user_id=m.supervisor_user_id
UNION ALL
SELECT 'agent_control_legacy_purchase_bridge_invalid_event_identity', COUNT(*)
FROM cloudmold_agent_legacy_purchase_in_bridge
WHERE event_id NOT REGEXP '^[0-9a-f]{64}$'
UNION ALL
SELECT 'agent_control_legacy_purchase_bridge_missing_outbox', COUNT(*)
FROM cloudmold_agent_legacy_purchase_in_bridge b
LEFT JOIN cloudmold_agent_outbox o
  ON o.tenant_id=b.tenant_id AND o.event_id=b.event_id
 AND o.aggregate_type='legacy_erp_purchase_in'
 AND o.aggregate_id=CAST(b.purchase_in_id AS CHAR)
 AND o.event_type='legacy.erp.purchase_in.status_changed'
WHERE o.event_id IS NULL
UNION ALL
SELECT 'agent_control_legacy_purchase_bridge_unmarked_payload', COUNT(*)
FROM cloudmold_agent_legacy_purchase_in_bridge b
JOIN cloudmold_agent_outbox o
  ON o.tenant_id=b.tenant_id AND o.event_id=b.event_id
WHERE JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.adapterMode'))<>'LEGACY_ERP_ADAPTER'
   OR JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.authority'))<>'LOCAL_TEST'
UNION ALL
SELECT 'agent_control_legacy_purchase_outbox_without_cursor', COUNT(*)
FROM cloudmold_agent_outbox o
LEFT JOIN cloudmold_agent_legacy_purchase_in_bridge b
  ON b.tenant_id=o.tenant_id AND b.event_id=o.event_id
WHERE o.event_type='legacy.erp.purchase_in.status_changed' AND b.event_id IS NULL;
