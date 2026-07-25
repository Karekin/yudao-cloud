SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS cloudmold_quality_recall_action;

UPDATE cloudmold_inspection_task
SET status='RECHECK_REQUIRED'
WHERE status='CONFLICTED';

ALTER TABLE cloudmold_inspection_task
    DROP CHECK ck_inspection_adjudication,
    DROP CHECK ck_inspection_ground_truth,
    DROP CHECK ck_inspection_conflict,
    DROP CHECK ck_inspection_recheck_result,
    DROP CHECK ck_inspection_adjudicator_independence,
    DROP CHECK ck_inspection_secondary_independence,
    DROP CHECK ck_inspection_ground_truth_decision,
    DROP CHECK ck_inspection_secondary_decision,
    DROP CHECK ck_inspection_status,
    DROP INDEX idx_inspection_ground_truth,
    DROP INDEX idx_inspection_secondary_queue,
    DROP COLUMN adjudicated_at,
    DROP COLUMN rechecked_at,
    DROP COLUMN ground_truth_evidence_ref,
    DROP COLUMN ground_truth_defect_code,
    DROP COLUMN ground_truth_decision,
    DROP COLUMN adjudicator_principal_id,
    DROP COLUMN secondary_evidence_ref,
    DROP COLUMN secondary_defect_code,
    DROP COLUMN secondary_decision,
    DROP COLUMN secondary_authenticator_principal_id,
    ADD CONSTRAINT ck_inspection_status CHECK (
        status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS', 'DECIDED',
                   'RECHECK_REQUIRED', 'COMPLETED'));
