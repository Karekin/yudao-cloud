CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_automation_candidate_event (
    tenant_id BIGINT NOT NULL,
    consumer_id VARCHAR(128) NOT NULL,
    candidate_id VARCHAR(64) NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    causation_id VARCHAR(36) NULL,
    payload_hash CHAR(64) NOT NULL,
    disposition VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(191) NULL,
    occurred_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, consumer_id, event_id),
    KEY idx_ai_ops_candidate_event_candidate (tenant_id, candidate_id, processed_at),
    KEY idx_ai_ops_candidate_event_route (tenant_id, consumer_id, disposition, processed_at),
    CONSTRAINT ck_ai_ops_candidate_event_disposition CHECK (
        disposition IN ('MATERIALIZED', 'COALESCED', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-route immutable event provenance and poison-event disposition';
