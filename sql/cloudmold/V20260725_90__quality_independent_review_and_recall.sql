-- P1 quality decision loop: independent recheck, third-party adjudication,
-- governed ground truth and lot-scoped recall work.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE cloudmold_inspection_task
    DROP CHECK ck_inspection_status,
    ADD COLUMN secondary_authenticator_principal_id VARCHAR(128) NULL
        AFTER recheck_reason_code,
    ADD COLUMN secondary_decision VARCHAR(16) NULL
        AFTER secondary_authenticator_principal_id,
    ADD COLUMN secondary_defect_code VARCHAR(64) NULL AFTER secondary_decision,
    ADD COLUMN secondary_evidence_ref VARCHAR(256) NULL AFTER secondary_defect_code,
    ADD COLUMN adjudicator_principal_id VARCHAR(128) NULL AFTER secondary_evidence_ref,
    ADD COLUMN ground_truth_decision VARCHAR(16) NULL AFTER adjudicator_principal_id,
    ADD COLUMN ground_truth_defect_code VARCHAR(64) NULL AFTER ground_truth_decision,
    ADD COLUMN ground_truth_evidence_ref VARCHAR(256) NULL AFTER ground_truth_defect_code,
    ADD COLUMN rechecked_at DATETIME(6) NULL AFTER decided_at,
    ADD COLUMN adjudicated_at DATETIME(6) NULL AFTER rechecked_at,
    ADD KEY idx_inspection_secondary_queue
        (tenant_id, secondary_authenticator_principal_id, status, updated_at),
    ADD KEY idx_inspection_ground_truth
        (tenant_id, ground_truth_decision, completed_at),
    ADD CONSTRAINT ck_inspection_status CHECK (
        status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS', 'DECIDED',
                   'RECHECK_REQUIRED', 'CONFLICTED', 'COMPLETED')),
    ADD CONSTRAINT ck_inspection_secondary_decision CHECK (
        secondary_decision IS NULL OR secondary_decision IN ('PASS', 'FAIL')),
    ADD CONSTRAINT ck_inspection_ground_truth_decision CHECK (
        ground_truth_decision IS NULL OR ground_truth_decision IN ('PASS', 'FAIL')),
    ADD CONSTRAINT ck_inspection_secondary_independence CHECK (
        secondary_authenticator_principal_id IS NULL
        OR secondary_authenticator_principal_id <> authenticator_principal_id),
    ADD CONSTRAINT ck_inspection_adjudicator_independence CHECK (
        adjudicator_principal_id IS NULL
        OR (adjudicator_principal_id <> authenticator_principal_id
            AND adjudicator_principal_id <> secondary_authenticator_principal_id)),
    ADD CONSTRAINT ck_inspection_recheck_result CHECK (
        secondary_decision IS NULL
        OR (secondary_authenticator_principal_id IS NOT NULL
            AND secondary_evidence_ref IS NOT NULL
            AND rechecked_at IS NOT NULL
            AND (secondary_decision <> 'FAIL' OR secondary_defect_code IS NOT NULL))),
    ADD CONSTRAINT ck_inspection_conflict CHECK (
        status <> 'CONFLICTED'
        OR (secondary_decision IS NOT NULL
            AND secondary_decision <> decision
            AND ground_truth_decision IS NULL)),
    ADD CONSTRAINT ck_inspection_ground_truth CHECK (
        ground_truth_decision IS NULL
        OR (ground_truth_evidence_ref IS NOT NULL
            AND (ground_truth_decision <> 'FAIL' OR ground_truth_defect_code IS NOT NULL))),
    ADD CONSTRAINT ck_inspection_adjudication CHECK (
        adjudicator_principal_id IS NULL
        OR (ground_truth_decision IS NOT NULL AND adjudicated_at IS NOT NULL));

CREATE TABLE IF NOT EXISTS cloudmold_quality_recall_action (
    recall_action_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inspection_task_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    owner_principal_id VARCHAR(128) NOT NULL,
    resolution_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    acknowledged_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (recall_action_id),
    UNIQUE KEY uk_quality_recall_tenant_id (tenant_id, recall_action_id),
    UNIQUE KEY uk_quality_recall_task (tenant_id, inspection_task_id),
    KEY idx_quality_recall_lot_status (tenant_id, lot_id, status, opened_at),
    CONSTRAINT fk_quality_recall_task FOREIGN KEY (tenant_id, inspection_task_id)
        REFERENCES cloudmold_inspection_task (tenant_id, task_id),
    CONSTRAINT ck_quality_recall_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_quality_recall_version CHECK (version > 0),
    CONSTRAINT ck_quality_recall_ack CHECK (
        status = 'OPEN' OR acknowledged_at IS NOT NULL),
    CONSTRAINT ck_quality_recall_resolution CHECK (
        status NOT IN ('RESOLVED', 'DISMISSED')
        OR (resolution_code IS NOT NULL AND resolved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Lot-scoped quality recall request and operational work item';
