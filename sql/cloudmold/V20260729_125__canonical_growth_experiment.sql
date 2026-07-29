CREATE TABLE IF NOT EXISTS cloudmold_promotion_growth_experiment (
    experiment_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    experiment_code VARCHAR(128) NOT NULL,
    campaign_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    hypothesis VARCHAR(512) NOT NULL,
    primary_metric_code VARCHAR(128) NOT NULL,
    minimum_sample_size_per_variant INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    concluded_at DATETIME(6) NULL,
    decision VARCHAR(32) NULL,
    confidence_basis_points INT NULL,
    guardrail_status VARCHAR(16) NULL,
    conclusion_evidence_ref VARCHAR(512) NULL,
    conclusion_reason VARCHAR(512) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (experiment_id),
    UNIQUE KEY uk_growth_experiment_code (tenant_id, experiment_code),
    KEY idx_growth_experiment_campaign_status (tenant_id, campaign_id, status),
    KEY idx_growth_experiment_due (tenant_id, status, ends_at),
    CONSTRAINT ck_growth_experiment_status CHECK (status IN ('DRAFT','RUNNING','CONCLUDED','CANCELLED')),
    CONSTRAINT ck_growth_experiment_window CHECK (starts_at < ends_at),
    CONSTRAINT ck_growth_experiment_min_sample CHECK (minimum_sample_size_per_variant >= 30),
    CONSTRAINT ck_growth_experiment_confidence CHECK (
        confidence_basis_points IS NULL OR confidence_basis_points BETWEEN 0 AND 10000
    ),
    CONSTRAINT ck_growth_experiment_terminal CHECK (
        (status IN ('DRAFT','RUNNING') AND concluded_at IS NULL AND decision IS NULL)
        OR (status='CONCLUDED' AND concluded_at IS NOT NULL AND decision IS NOT NULL
            AND confidence_basis_points IS NOT NULL AND guardrail_status IS NOT NULL
            AND conclusion_evidence_ref IS NOT NULL)
        OR (status='CANCELLED' AND concluded_at IS NOT NULL AND decision IS NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cloudmold_promotion_growth_experiment_variant (
    variant_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    experiment_id VARCHAR(64) NOT NULL,
    variant_code VARCHAR(64) NOT NULL,
    variant_kind VARCHAR(16) NOT NULL,
    allocation_basis_points INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (variant_id),
    UNIQUE KEY uk_growth_experiment_variant (tenant_id, experiment_id, variant_code),
    KEY idx_growth_experiment_variant_experiment (tenant_id, experiment_id),
    CONSTRAINT ck_growth_experiment_variant_kind CHECK (variant_kind IN ('CONTROL','TREATMENT')),
    CONSTRAINT ck_growth_experiment_variant_allocation CHECK (allocation_basis_points BETWEEN 1 AND 9999)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cloudmold_promotion_growth_experiment_exposure (
    exposure_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    exposure_key VARCHAR(128) NOT NULL,
    experiment_id VARCHAR(64) NOT NULL,
    variant_code VARCHAR(64) NOT NULL,
    principal_hash CHAR(64) NOT NULL,
    assignment_version VARCHAR(64) NOT NULL,
    exposed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (exposure_id),
    UNIQUE KEY uk_growth_experiment_exposure_key (tenant_id, exposure_key),
    UNIQUE KEY uk_growth_experiment_principal (tenant_id, experiment_id, principal_hash),
    KEY idx_growth_experiment_exposure_variant (tenant_id, experiment_id, variant_code, exposed_at),
    CONSTRAINT ck_growth_experiment_principal_hash CHECK (CHAR_LENGTH(principal_hash)=64)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cloudmold_promotion_growth_experiment_metric_snapshot (
    snapshot_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    snapshot_key VARCHAR(128) NOT NULL,
    experiment_id VARCHAR(64) NOT NULL,
    variant_code VARCHAR(64) NOT NULL,
    metric_code VARCHAR(128) NOT NULL,
    measured_from DATETIME(6) NOT NULL,
    measured_to DATETIME(6) NOT NULL,
    sample_count INT NOT NULL,
    metric_value_micros BIGINT NOT NULL,
    data_fresh_until DATETIME(6) NOT NULL,
    evidence_ref VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_growth_experiment_snapshot_key (tenant_id, snapshot_key),
    KEY idx_growth_experiment_snapshot_latest (
        tenant_id, experiment_id, metric_code, variant_code, measured_to
    ),
    CONSTRAINT ck_growth_experiment_snapshot_window CHECK (measured_from < measured_to),
    CONSTRAINT ck_growth_experiment_snapshot_sample CHECK (sample_count > 0)
) ENGINE=InnoDB;
