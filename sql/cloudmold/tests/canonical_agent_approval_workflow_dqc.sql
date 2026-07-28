-- Every terminal BPM observation must resolve to exactly one frozen workflow binding.
SELECT 'agent_approval_workflow_orphan_event' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_agent_approval_workflow_event e
LEFT JOIN cloudmold_agent_approval_workflow_binding b
  ON b.tenant_id = e.tenant_id
 AND BINARY b.approval_id = BINARY e.approval_id
 AND BINARY b.process_instance_id = BINARY e.process_instance_id
WHERE b.approval_id IS NULL
UNION ALL
-- A BPM approval remains candidate evidence until an independent Agent Control
-- attestation approves or rejects the exact frozen approval scope.
SELECT 'agent_approval_workflow_bpm_bypassed_attestation', COUNT(*)
FROM cloudmold_agent_approval_workflow_binding b
JOIN cloudmold_agent_approval a
  ON a.tenant_id = b.tenant_id
 AND BINARY a.approval_id = BINARY b.approval_id
WHERE b.status = 'BPM_APPROVED_PENDING_ATTESTATION'
  AND a.status = 'APPROVED'
  AND (a.approver_user_id IS NULL
       OR a.approver_user_id <> b.terminal_operator_user_id
       OR (b.risk_level <> 'R3'
           AND (b.terminal_operator_user_id <> b.approver_user_id
                OR BINARY b.terminal_task_definition_key <> BINARY 'approval_review'))
       OR (b.risk_level = 'R3'
           AND (BINARY b.terminal_task_definition_key <> BINARY 'domain_responsibility_countersign'
                OR b.responsibility_authority_sha256 IS NULL
                OR NOT EXISTS (
                    SELECT 1
                    FROM JSON_TABLE(
                        b.responsibility_approver_user_ids_json,
                        '$[*]' COLUMNS (user_id BIGINT PATH '$')
                    ) responsible
                    WHERE responsible.user_id = b.terminal_operator_user_id
                )))
       OR BINARY a.scope_hash <> BINARY b.scope_hash)
UNION ALL
SELECT 'agent_approval_workflow_terminal_event_mismatch', COUNT(*)
FROM (
    SELECT b.tenant_id, b.approval_id, COUNT(e.event_id) AS matching_event_count
    FROM cloudmold_agent_approval_workflow_binding b
    LEFT JOIN cloudmold_agent_approval_workflow_event e
      ON e.tenant_id = b.tenant_id
     AND BINARY e.approval_id = BINARY b.approval_id
     AND BINARY e.process_instance_id = BINARY b.process_instance_id
     AND e.bpm_status = b.last_bpm_status
     AND BINARY e.reason_sha256 = BINARY b.last_reason_sha256
     AND e.terminal_operator_user_id <=> b.terminal_operator_user_id
     AND BINARY e.terminal_task_id <=> BINARY b.terminal_task_id
     AND BINARY e.terminal_task_definition_key <=> BINARY b.terminal_task_definition_key
    WHERE b.status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
    GROUP BY b.tenant_id, b.approval_id
    HAVING matching_event_count <> 1
) invalid_terminal
UNION ALL
SELECT 'agent_approval_workflow_unfrozen_approver', COUNT(*)
FROM cloudmold_agent_approval_workflow_binding
WHERE (status = 'START_REQUESTED' AND approver_user_id IS NOT NULL)
   OR (status <> 'START_REQUESTED'
       AND (approver_user_id IS NULL OR approver_user_id = requester_user_id))
UNION ALL
SELECT 'agent_approval_workflow_invalid_terminal_attestation', COUNT(*)
FROM cloudmold_agent_approval_workflow_binding
WHERE (status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
       AND (terminal_operator_user_id IS NULL
            OR terminal_task_id IS NULL OR terminal_task_id = ''
            OR terminal_task_definition_key IS NULL
            OR (risk_level <> 'R3'
                AND (terminal_operator_user_id <> approver_user_id
                     OR BINARY terminal_task_definition_key <> BINARY 'approval_review'))
            OR (risk_level = 'R3'
                AND (responsibility_authority_sha256 IS NULL
                     OR responsibility_role_codes_json IS NULL
                     OR responsibility_approver_user_ids_json IS NULL
                     OR (BINARY terminal_task_definition_key = BINARY 'approval_review'
                         AND terminal_operator_user_id <> approver_user_id)
                     OR (BINARY terminal_task_definition_key = BINARY 'domain_responsibility_countersign'
                         AND NOT EXISTS (
                             SELECT 1
                             FROM JSON_TABLE(
                                 responsibility_approver_user_ids_json,
                                 '$[*]' COLUMNS (user_id BIGINT PATH '$')
                             ) responsible
                             WHERE responsible.user_id = terminal_operator_user_id
                         ))
                     OR terminal_task_definition_key NOT IN (
                         'approval_review','domain_responsibility_countersign')))))
   OR (status NOT IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
       AND (terminal_operator_user_id IS NOT NULL
            OR terminal_task_id IS NOT NULL
            OR terminal_task_definition_key IS NOT NULL))
UNION ALL
SELECT 'agent_approval_workflow_invalid_terminal_event_evidence', COUNT(*)
FROM cloudmold_agent_approval_workflow_event e
JOIN cloudmold_agent_approval_workflow_binding b
  ON b.tenant_id = e.tenant_id
 AND BINARY b.approval_id = BINARY e.approval_id
WHERE e.observed_status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
  AND (e.terminal_operator_user_id IS NULL
       OR e.terminal_task_id IS NULL OR e.terminal_task_id = ''
       OR e.terminal_task_definition_key IS NULL
       OR (b.risk_level <> 'R3'
           AND (e.terminal_operator_user_id <> b.approver_user_id
                OR BINARY e.terminal_task_definition_key <> BINARY 'approval_review'))
       OR (b.risk_level = 'R3'
           AND ((BINARY e.terminal_task_definition_key = BINARY 'approval_review'
                 AND e.terminal_operator_user_id <> b.approver_user_id)
                OR (BINARY e.terminal_task_definition_key = BINARY 'domain_responsibility_countersign'
                    AND NOT EXISTS (
                        SELECT 1
                        FROM JSON_TABLE(
                            b.responsibility_approver_user_ids_json,
                            '$[*]' COLUMNS (user_id BIGINT PATH '$')
                        ) responsible
                        WHERE responsible.user_id = e.terminal_operator_user_id
                    ))
                OR e.terminal_task_definition_key NOT IN (
                    'approval_review','domain_responsibility_countersign'))));
