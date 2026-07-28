-- Freeze R3 tenant responsibility-role authorization beside the BPM binding.
-- Existing R3 rows remain legacy-null and therefore fail closed in Agent Control.

DELIMITER $$
DROP PROCEDURE IF EXISTS cloudmold_upgrade_agent_r3_countersign$$
CREATE PROCEDURE cloudmold_upgrade_agent_r3_countersign()
BEGIN
    DECLARE has_column INT DEFAULT 0;
    DECLARE has_constraint INT DEFAULT 0;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'responsibility_role_codes_json';
    IF has_column = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN responsibility_role_codes_json JSON NULL
                AFTER terminal_task_definition_key;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'responsibility_approver_user_ids_json';
    IF has_column = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN responsibility_approver_user_ids_json JSON NULL
                AFTER responsibility_role_codes_json;
    END IF;

    SELECT COUNT(*) INTO has_column
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'responsibility_authority_sha256';
    IF has_column = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN responsibility_authority_sha256 CHAR(64) NULL
                AFTER responsibility_approver_user_ids_json;
    END IF;

    SELECT COUNT(*) INTO has_constraint
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND constraint_name = 'ck_agent_approval_workflow_responsibility';
    IF has_constraint = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD CONSTRAINT ck_agent_approval_workflow_responsibility CHECK (
                (responsibility_role_codes_json IS NULL
                    AND responsibility_approver_user_ids_json IS NULL
                    AND responsibility_authority_sha256 IS NULL)
                OR (risk_level = 'R3'
                    AND JSON_TYPE(responsibility_role_codes_json) = 'ARRAY'
                    AND JSON_TYPE(responsibility_approver_user_ids_json) = 'ARRAY'
                    AND JSON_LENGTH(responsibility_role_codes_json) >= 2
                    AND JSON_LENGTH(responsibility_approver_user_ids_json) >= 2
                    AND responsibility_authority_sha256 REGEXP '^[0-9a-f]{64}$'));
    END IF;

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
                AND terminal_task_definition_key IS NOT NULL
                AND ((risk_level <> 'R3'
                        AND terminal_operator_user_id = approver_user_id
                        AND terminal_task_definition_key = 'approval_review')
                    OR (risk_level = 'R3'
                        AND terminal_task_definition_key IN (
                            'approval_review','domain_responsibility_countersign'))))
            OR (status = 'BPM_CANCELLED'
                AND ((terminal_operator_user_id IS NULL
                        AND terminal_task_id IS NULL
                        AND terminal_task_definition_key IS NULL)
                    OR (terminal_operator_user_id IS NOT NULL
                        AND terminal_operator_user_id > 0
                        AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                        AND terminal_task_definition_key IS NOT NULL
                        AND terminal_task_definition_key IN (
                            'approval_review','domain_responsibility_countersign'))))
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
                AND terminal_task_definition_key IS NOT NULL
                AND terminal_task_definition_key IN (
                    'approval_review','domain_responsibility_countersign'))
            OR (observed_status = 'BPM_CANCELLED'
                AND ((terminal_operator_user_id IS NULL
                        AND terminal_task_id IS NULL
                        AND terminal_task_definition_key IS NULL)
                    OR (terminal_operator_user_id IS NOT NULL
                        AND terminal_operator_user_id > 0
                        AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                        AND terminal_task_definition_key IS NOT NULL
                        AND terminal_task_definition_key IN (
                            'approval_review','domain_responsibility_countersign')))));
END$$
DELIMITER ;

CALL cloudmold_upgrade_agent_r3_countersign();
DROP PROCEDURE IF EXISTS cloudmold_upgrade_agent_r3_countersign;
