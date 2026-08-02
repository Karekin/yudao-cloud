-- Allow terminal evidence from the dedicated AI OR-sign task and from audited
-- AI takeover of the two legacy governed task types. Identity, tenant, risk
-- allowlist and separation-of-duties checks remain enforced by the attestation
-- gate; these database checks preserve the structural evidence invariant.

DROP PROCEDURE IF EXISTS cloudmold_upgrade_agent_approval_ai_or_sign;
DELIMITER $$
CREATE PROCEDURE cloudmold_upgrade_agent_approval_ai_or_sign()
BEGIN
    DECLARE has_constraint INT DEFAULT 0;

    SELECT COUNT(*) INTO has_constraint
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND constraint_name = 'ck_agent_approval_workflow_terminal_evidence';
    IF has_constraint > 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            DROP CHECK ck_agent_approval_workflow_terminal_evidence;
    END IF;
    ALTER TABLE cloudmold_agent_approval_workflow_binding
        ADD CONSTRAINT ck_agent_approval_workflow_terminal_evidence CHECK (
            (status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
                AND terminal_operator_user_id IS NOT NULL
                AND terminal_operator_user_id > 0
                AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                AND terminal_task_definition_key IN (
                    'approval_review','domain_responsibility_countersign','ai_approval_review'))
            OR (status = 'BPM_CANCELLED'
                AND ((terminal_operator_user_id IS NULL
                        AND terminal_task_id IS NULL
                        AND terminal_task_definition_key IS NULL)
                    OR (terminal_operator_user_id IS NOT NULL
                        AND terminal_operator_user_id > 0
                        AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                        AND terminal_task_definition_key IN (
                            'approval_review','domain_responsibility_countersign','ai_approval_review'))))
            OR (status NOT IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
                AND terminal_operator_user_id IS NULL
                AND terminal_task_id IS NULL
                AND terminal_task_definition_key IS NULL));

    SELECT COUNT(*) INTO has_constraint
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_event'
      AND constraint_name = 'ck_agent_approval_workflow_event_terminal_evidence';
    IF has_constraint > 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_event
            DROP CHECK ck_agent_approval_workflow_event_terminal_evidence;
    END IF;
    ALTER TABLE cloudmold_agent_approval_workflow_event
        ADD CONSTRAINT ck_agent_approval_workflow_event_terminal_evidence CHECK (
            (observed_status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
                AND terminal_operator_user_id IS NOT NULL
                AND terminal_operator_user_id > 0
                AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                AND terminal_task_definition_key IN (
                    'approval_review','domain_responsibility_countersign','ai_approval_review'))
            OR (observed_status = 'BPM_CANCELLED'
                AND ((terminal_operator_user_id IS NULL
                        AND terminal_task_id IS NULL
                        AND terminal_task_definition_key IS NULL)
                    OR (terminal_operator_user_id IS NOT NULL
                        AND terminal_operator_user_id > 0
                        AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                        AND terminal_task_definition_key IN (
                            'approval_review','domain_responsibility_countersign','ai_approval_review')))));
END$$
DELIMITER ;

CALL cloudmold_upgrade_agent_approval_ai_or_sign();
DROP PROCEDURE IF EXISTS cloudmold_upgrade_agent_approval_ai_or_sign;
