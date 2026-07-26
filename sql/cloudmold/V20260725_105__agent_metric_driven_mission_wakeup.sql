-- P5 first slice: governed metric observations can wake a waiting Mission work order.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE cloudmold_agent_work_order
    DROP CHECK ck_agent_work_order_status,
    ADD CONSTRAINT ck_agent_work_order_status CHECK (status IN (
        'WAITING_DEPENDENCY','READY','IN_PROGRESS','WAITING_EVENT','WAITING_METRIC','WAITING_TIMER',
        'WAITING_APPROVAL','WAITING_HANDOFF','BLOCKED','NEEDS_REVIEW','COMPLETED','CANCELLED'));

CREATE TABLE IF NOT EXISTS cloudmold_agent_metric_subscription (
    subscription_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    work_order_id VARCHAR(64) NOT NULL,
    metric_id VARCHAR(256) NOT NULL,
    metric_version VARCHAR(64) NOT NULL,
    dimension_hash CHAR(64) NOT NULL,
    comparison_operator VARCHAR(8) NOT NULL,
    threshold_value DECIMAL(38,12) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    max_age_seconds INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    matched_observation_id VARCHAR(191) NULL,
    matched_value DECIMAL(38,12) NULL,
    matched_evidence_sha256 CHAR(64) NULL,
    matched_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (subscription_id),
    UNIQUE KEY uk_agent_metric_subscription_tenant_id (tenant_id, subscription_id),
    KEY idx_agent_metric_subscription_match (
        tenant_id, status, metric_id, metric_version, dimension_hash, unit_code),
    CONSTRAINT fk_agent_metric_subscription_mission
        FOREIGN KEY (tenant_id, mission_id)
        REFERENCES cloudmold_agent_business_mission (tenant_id, mission_id),
    CONSTRAINT fk_agent_metric_subscription_work
        FOREIGN KEY (tenant_id, work_order_id)
        REFERENCES cloudmold_agent_work_order (tenant_id, work_order_id),
    CONSTRAINT ck_agent_metric_subscription_operator CHECK (
        comparison_operator IN ('GT','GTE','LT','LTE','EQ')),
    CONSTRAINT ck_agent_metric_subscription_status CHECK (
        status IN ('ACTIVE','MATCHED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_agent_metric_subscription_dimension CHECK (
        dimension_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_metric_subscription_age CHECK (
        max_age_seconds BETWEEN 1 AND 86400),
    CONSTRAINT ck_agent_metric_subscription_match CHECK (
        status <> 'MATCHED'
        OR (matched_observation_id IS NOT NULL
            AND matched_value IS NOT NULL
            AND matched_evidence_sha256 REGEXP '^[0-9a-f]{64}$'
            AND matched_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_metric_observation_inbox (
    tenant_id BIGINT NOT NULL,
    observation_id VARCHAR(191) NOT NULL,
    metric_id VARCHAR(256) NOT NULL,
    metric_version VARCHAR(64) NOT NULL,
    dimension_hash CHAR(64) NOT NULL,
    metric_value DECIMAL(38,12) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    source_query_id VARCHAR(256) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    observation_sha256 CHAR(64) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, observation_id),
    KEY idx_agent_metric_observation (
        tenant_id, metric_id, metric_version, dimension_hash, observed_at),
    CONSTRAINT ck_agent_metric_observation_dimension CHECK (
        dimension_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_metric_observation_evidence CHECK (
        evidence_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_metric_observation_hash CHECK (
        observation_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_metric_observation_time CHECK (
        observed_at <= received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
