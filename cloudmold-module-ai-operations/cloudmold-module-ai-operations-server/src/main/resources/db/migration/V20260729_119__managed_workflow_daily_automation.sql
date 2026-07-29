ALTER TABLE cloudmold_ai_ops_temporal_schedule
    ADD COLUMN cron_expression VARCHAR(64) NULL AFTER interval_seconds,
    ADD COLUMN input_strategy VARCHAR(32) NOT NULL DEFAULT 'STATIC' AFTER input_json,
    ADD COLUMN desired_policy_sha256 CHAR(64) NULL AFTER temporal_task_queue,
    ADD COLUMN definition_closure_sha256 CHAR(64) NULL AFTER desired_policy_sha256,
    ADD COLUMN last_reconciled_at DATETIME(6) NULL AFTER definition_closure_sha256,
    ADD COLUMN reconcile_error VARCHAR(500) NULL AFTER last_reconciled_at;

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_automation_candidate (
    tenant_id BIGINT NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    client_request_key VARCHAR(191) NOT NULL,
    skill_id VARCHAR(191) NOT NULL,
    skill_version VARCHAR(64) NOT NULL,
    business_key VARCHAR(191) NOT NULL,
    input_json MEDIUMTEXT NOT NULL,
    due_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    lease_owner VARCHAR(191) NULL,
    lease_until DATETIME(6) NULL,
    temporal_workflow_id VARCHAR(191) NULL,
    dispatched_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, candidate_id),
    UNIQUE KEY uk_ai_ops_automation_candidate_request
        (tenant_id, skill_id, skill_version, client_request_key),
    KEY idx_ai_ops_automation_candidate_due
        (tenant_id, skill_id, skill_version, status, due_at),
    CONSTRAINT ck_ai_ops_automation_candidate_status CHECK (
        status IN ('PENDING', 'CLAIMED', 'DISPATCHED', 'CANCELLED', 'FAILED')
    ),
    CONSTRAINT ck_ai_ops_automation_candidate_version CHECK (version > 0)
);
