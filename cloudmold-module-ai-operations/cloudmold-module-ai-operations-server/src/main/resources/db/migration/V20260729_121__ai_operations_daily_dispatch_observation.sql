CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_daily_dispatch_observation (
    tenant_id BIGINT NOT NULL,
    schedule_id VARCHAR(191) NOT NULL,
    temporal_run_id VARCHAR(191) NOT NULL,
    business_date VARCHAR(10) NOT NULL,
    outcome_code VARCHAR(32) NOT NULL,
    candidate_count INT NOT NULL,
    dispatched_count INT NOT NULL,
    failed_count INT NOT NULL,
    workflow_ids_json JSON NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, temporal_run_id),
    KEY idx_ai_ops_dispatch_schedule
        (tenant_id, schedule_id, observed_at),
    CONSTRAINT ck_ai_ops_dispatch_counts CHECK (
        candidate_count >= 0 AND dispatched_count >= 0 AND failed_count >= 0
    )
);
