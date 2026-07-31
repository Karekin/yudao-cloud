CREATE TABLE IF NOT EXISTS cloudmold_supplier_performance_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status INT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_supplier_performance_operation (tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Idempotent supplier performance command envelope';

CREATE TABLE IF NOT EXISTS cloudmold_supplier_metric_evidence (
    metric_evidence_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    metric_code VARCHAR(32) NOT NULL,
    numerator DECIMAL(24, 6) NOT NULL,
    denominator DECIMAL(24, 6) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    recorded_by_principal_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (metric_evidence_id),
    UNIQUE KEY uk_supplier_metric_source (tenant_id, supplier_id, source_system, source_record_id, metric_code),
    KEY idx_supplier_metric_period (tenant_id, supplier_id, period_start, period_end, metric_code),
    CONSTRAINT fk_supplier_metric_profile FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES cloudmold_supplier_profile (tenant_id, supplier_id),
    CONSTRAINT ck_supplier_metric_period CHECK (period_end >= period_start),
    CONSTRAINT ck_supplier_metric_source CHECK (source_system IN (
        'CLOUDMOLD_PROCUREMENT','CLOUDMOLD_WAREHOUSE','CLOUDMOLD_QUALITY','CLOUDMOLD_MES',
        'EXTERNAL_FACTORY','EXTERNAL_INSPECTION')),
    CONSTRAINT ck_supplier_metric_code CHECK (metric_code IN (
        'OTIF','QUALITY_PASS_RATE','CAPACITY_ATTAINMENT','CAPA_EFFECTIVENESS')),
    CONSTRAINT ck_supplier_metric_fraction CHECK (numerator >= 0 AND denominator > 0 AND numerator <= denominator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable source-evidenced supplier performance metric facts';

CREATE TABLE IF NOT EXISTS cloudmold_supplier_performance_scorecard (
    scorecard_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    scorecard_version INT NOT NULL,
    otif_bps INT NOT NULL,
    quality_bps INT NOT NULL,
    capacity_bps INT NOT NULL,
    capa_bps INT NOT NULL,
    overall_bps INT NOT NULL,
    assessment VARCHAR(16) NOT NULL,
    evidence_snapshot_sha256 CHAR(64) NOT NULL,
    generated_by_principal_id VARCHAR(128) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (scorecard_id),
    UNIQUE KEY uk_supplier_scorecard_version (tenant_id, supplier_id, period_start, period_end, scorecard_version),
    KEY idx_supplier_scorecard_latest (tenant_id, supplier_id, period_end, scorecard_version),
    CONSTRAINT fk_supplier_scorecard_profile FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES cloudmold_supplier_profile (tenant_id, supplier_id),
    CONSTRAINT ck_supplier_scorecard_period CHECK (period_end >= period_start),
    CONSTRAINT ck_supplier_scorecard_version CHECK (scorecard_version > 0),
    CONSTRAINT ck_supplier_scorecard_bps CHECK (
        otif_bps BETWEEN 0 AND 10000 AND quality_bps BETWEEN 0 AND 10000
        AND capacity_bps BETWEEN 0 AND 10000 AND capa_bps BETWEEN 0 AND 10000
        AND overall_bps BETWEEN 0 AND 10000),
    CONSTRAINT ck_supplier_scorecard_assessment CHECK (assessment IN ('HEALTHY','WATCH','AT_RISK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned supplier scorecard; not an authorization to restrict or restore supply';
