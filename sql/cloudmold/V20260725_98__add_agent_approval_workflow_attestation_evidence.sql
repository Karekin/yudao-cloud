-- Additive repair for databases that already applied V20260725_06.
-- Existing non-started/terminal rows cannot be backfilled safely because the
-- original schema did not persist the assigned approver or terminal task actor.
DROP PROCEDURE IF EXISTS cloudmold_upgrade_agent_approval_attestation;

DELIMITER $$
CREATE PROCEDURE cloudmold_upgrade_agent_approval_attestation()
BEGIN
    DECLARE has_approver INT DEFAULT 0;
    DECLARE has_terminal_operator INT DEFAULT 0;
    DECLARE has_binding_task_id INT DEFAULT 0;
    DECLARE has_binding_task_key INT DEFAULT 0;
    DECLARE has_event_operator INT DEFAULT 0;
    DECLARE has_event_task_id INT DEFAULT 0;
    DECLARE has_event_task_key INT DEFAULT 0;
    DECLARE has_approver_constraint INT DEFAULT 0;
    DECLARE has_terminal_constraint INT DEFAULT 0;
    DECLARE has_event_terminal_constraint INT DEFAULT 0;

    SELECT COUNT(*) INTO has_approver
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'approver_user_id';
    SELECT COUNT(*) INTO has_terminal_operator
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'terminal_operator_user_id';
    SELECT COUNT(*) INTO has_binding_task_id
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'terminal_task_id';
    SELECT COUNT(*) INTO has_binding_task_key
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND column_name = 'terminal_task_definition_key';
    SELECT COUNT(*) INTO has_event_operator
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_event'
      AND column_name = 'terminal_operator_user_id';
    SELECT COUNT(*) INTO has_event_task_id
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_event'
      AND column_name = 'terminal_task_id';
    SELECT COUNT(*) INTO has_event_task_key
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_event'
      AND column_name = 'terminal_task_definition_key';

    IF has_approver = 0 AND EXISTS (
        SELECT 1
        FROM cloudmold_agent_approval_workflow_binding
        WHERE status <> 'START_REQUESTED'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'approval workflow attestation migration requires reconciliation of already-started bindings';
    END IF;
    IF (has_terminal_operator = 0 OR has_binding_task_id = 0 OR has_binding_task_key = 0)
       AND EXISTS (
        SELECT 1
        FROM cloudmold_agent_approval_workflow_binding
        WHERE status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'approval workflow attestation migration cannot infer terminal binding evidence';
    END IF;
    IF (has_event_operator = 0 OR has_event_task_id = 0 OR has_event_task_key = 0)
       AND EXISTS (
        SELECT 1
        FROM cloudmold_agent_approval_workflow_event
        WHERE observed_status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'approval workflow attestation migration cannot infer terminal event evidence';
    END IF;

    IF has_approver = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN approver_user_id BIGINT NULL AFTER requester_user_id;
    END IF;
    IF has_terminal_operator = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN terminal_operator_user_id BIGINT NULL AFTER last_reason_sha256;
    END IF;
    IF has_binding_task_id = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN terminal_task_id VARCHAR(64) NULL AFTER terminal_operator_user_id;
    END IF;
    IF has_binding_task_key = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD COLUMN terminal_task_definition_key VARCHAR(128) NULL AFTER terminal_task_id;
    END IF;
    IF has_event_operator = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_event
            ADD COLUMN terminal_operator_user_id BIGINT NULL AFTER reason_sha256;
    END IF;
    IF has_event_task_id = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_event
            ADD COLUMN terminal_task_id VARCHAR(64) NULL AFTER terminal_operator_user_id;
    END IF;
    IF has_event_task_key = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_event
            ADD COLUMN terminal_task_definition_key VARCHAR(128) NULL AFTER terminal_task_id;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM cloudmold_agent_approval_workflow_binding
        WHERE (status = 'START_REQUESTED' AND approver_user_id IS NOT NULL)
           OR (status <> 'START_REQUESTED'
               AND (approver_user_id IS NULL OR approver_user_id = requester_user_id))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'approval workflow binding approver evidence violates the hardened invariant';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM cloudmold_agent_approval_workflow_binding
        WHERE (status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
               AND (terminal_operator_user_id IS NULL
                    OR terminal_operator_user_id <> approver_user_id
                    OR terminal_task_id IS NULL OR terminal_task_id = ''
                    OR terminal_task_definition_key <> 'approval_review'))
           OR (status NOT IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
               AND (terminal_operator_user_id IS NOT NULL
                    OR terminal_task_id IS NOT NULL
                    OR terminal_task_definition_key IS NOT NULL))
           OR NOT (
               (terminal_operator_user_id IS NULL
                AND terminal_task_id IS NULL
                AND terminal_task_definition_key IS NULL)
               OR (terminal_operator_user_id IS NOT NULL
                   AND terminal_task_id IS NOT NULL
                   AND terminal_task_definition_key IS NOT NULL))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'approval workflow binding terminal evidence violates the hardened invariant';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM cloudmold_agent_approval_workflow_event
        WHERE (observed_status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
               AND (terminal_operator_user_id IS NULL
                    OR terminal_task_id IS NULL OR terminal_task_id = ''
                    OR terminal_task_definition_key <> 'approval_review'))
           OR NOT (
               (terminal_operator_user_id IS NULL
                AND terminal_task_id IS NULL
                AND terminal_task_definition_key IS NULL)
               OR (terminal_operator_user_id IS NOT NULL
                   AND terminal_task_id IS NOT NULL
                   AND terminal_task_definition_key IS NOT NULL))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'approval workflow event terminal evidence violates the hardened invariant';
    END IF;

    SELECT COUNT(*) INTO has_approver_constraint
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND constraint_name = 'ck_agent_approval_workflow_approver';
    IF has_approver_constraint = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD CONSTRAINT ck_agent_approval_workflow_approver CHECK (
                (status = 'START_REQUESTED' AND approver_user_id IS NULL)
                OR (status <> 'START_REQUESTED' AND approver_user_id IS NOT NULL
                    AND approver_user_id <> requester_user_id));
    END IF;

    SELECT COUNT(*) INTO has_terminal_constraint
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_binding'
      AND constraint_name = 'ck_agent_approval_workflow_terminal_evidence';
    IF has_terminal_constraint = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_binding
            ADD CONSTRAINT ck_agent_approval_workflow_terminal_evidence CHECK (
                (status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
                    AND terminal_operator_user_id = approver_user_id
                    AND terminal_operator_user_id > 0
                    AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                    AND terminal_task_definition_key = 'approval_review')
                OR (status = 'BPM_CANCELLED'
                    AND ((terminal_operator_user_id IS NULL
                          AND terminal_task_id IS NULL
                          AND terminal_task_definition_key IS NULL)
                         OR (terminal_operator_user_id IS NOT NULL
                             AND terminal_task_id IS NOT NULL
                             AND terminal_task_id <> ''
                             AND terminal_task_definition_key = 'approval_review')))
                OR (status NOT IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED','BPM_CANCELLED')
                    AND terminal_operator_user_id IS NULL
                    AND terminal_task_id IS NULL
                    AND terminal_task_definition_key IS NULL));
    END IF;

    SELECT COUNT(*) INTO has_event_terminal_constraint
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'cloudmold_agent_approval_workflow_event'
      AND constraint_name = 'ck_agent_approval_workflow_event_terminal_evidence';
    IF has_event_terminal_constraint = 0 THEN
        ALTER TABLE cloudmold_agent_approval_workflow_event
            ADD CONSTRAINT ck_agent_approval_workflow_event_terminal_evidence CHECK (
                (observed_status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
                    AND terminal_operator_user_id IS NOT NULL
                    AND terminal_operator_user_id > 0
                    AND terminal_task_id IS NOT NULL AND terminal_task_id <> ''
                    AND terminal_task_definition_key = 'approval_review')
                OR (observed_status = 'BPM_CANCELLED'
                    AND ((terminal_operator_user_id IS NULL
                          AND terminal_task_id IS NULL
                          AND terminal_task_definition_key IS NULL)
                         OR (terminal_operator_user_id IS NOT NULL
                             AND terminal_operator_user_id > 0
                             AND terminal_task_id IS NOT NULL
                             AND terminal_task_id <> ''
                             AND terminal_task_definition_key = 'approval_review'))));
    END IF;
END$$
DELIMITER ;

CALL cloudmold_upgrade_agent_approval_attestation();
DROP PROCEDURE IF EXISTS cloudmold_upgrade_agent_approval_attestation;
