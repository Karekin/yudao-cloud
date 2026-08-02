SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_inventory_aging_snapshot_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_inventory_aging_snapshot_operation_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_inventory_aging_snapshot_operation_event (tenant_id, source_event_id),
    CONSTRAINT ck_inventory_aging_snapshot_operation_status CHECK (status IN (0, 10)),
    CONSTRAINT ck_inventory_aging_snapshot_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Accepted idempotent inventory aging snapshot command';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_aging_snapshot (
    snapshot_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    snapshot_code VARCHAR(64) NOT NULL,
    owner_type VARCHAR(64) NULL,
    owner_id VARCHAR(128) NULL,
    warehouse_id VARCHAR(128) NULL,
    bucket_policy_code VARCHAR(64) NOT NULL,
    bucket_policy_version VARCHAR(64) NOT NULL,
    bucket_policy_hash CHAR(64) NOT NULL,
    age_fresh_max_days INT NOT NULL,
    age_aging_max_days INT NOT NULL,
    age_stale_max_days INT NOT NULL,
    expiry_warning_max_days INT NOT NULL,
    expiry_critical_max_days INT NOT NULL,
    ledger_watermark_ref VARCHAR(255) NOT NULL,
    ledger_watermark_occurred_at DATETIME(6) NOT NULL,
    snapshot_date DATE NOT NULL,
    line_count INT NOT NULL,
    unknown_age_count INT NOT NULL,
    unknown_expiry_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_inventory_aging_snapshot_code (tenant_id, snapshot_code),
    KEY idx_inventory_aging_snapshot_created (tenant_id, created_at),
    CONSTRAINT ck_inventory_aging_snapshot_thresholds CHECK (
        age_fresh_max_days >= 0
        AND age_aging_max_days >= age_fresh_max_days
        AND age_stale_max_days >= age_aging_max_days
        AND expiry_warning_max_days >= expiry_critical_max_days
        AND expiry_critical_max_days >= 0
    ),
    CONSTRAINT ck_inventory_aging_snapshot_counts CHECK (
        line_count >= 0
        AND unknown_age_count >= 0
        AND unknown_expiry_count >= 0
    ),
    CONSTRAINT ck_inventory_aging_snapshot_status CHECK (status IN ('CAPTURED')),
    CONSTRAINT ck_inventory_aging_snapshot_hash CHECK (bucket_policy_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable inventory aging and shelf-life snapshot header';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_aging_snapshot_line (
    line_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    snapshot_id VARCHAR(64) NOT NULL,
    balance_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    location_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(64) NULL,
    lot_code VARCHAR(128) NULL,
    stock_status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    base_uom_code VARCHAR(16) NOT NULL,
    on_hand_quantity DECIMAL(24, 6) NOT NULL,
    reserved_quantity DECIMAL(24, 6) NOT NULL,
    in_transit_quantity DECIMAL(24, 6) NOT NULL,
    available_quantity DECIMAL(24, 6) NOT NULL,
    balance_version BIGINT NOT NULL,
    manufactured_on DATE NULL,
    expires_on DATE NULL,
    age_basis_type VARCHAR(32) NOT NULL,
    age_basis_at DATETIME(6) NULL,
    age_days INT NULL,
    age_bucket VARCHAR(16) NOT NULL,
    expiry_days_remaining INT NULL,
    expiry_status VARCHAR(16) NOT NULL,
    expiry_bucket VARCHAR(16) NOT NULL,
    risk_classification VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (line_id),
    UNIQUE KEY uk_inventory_aging_snapshot_line_balance (tenant_id, snapshot_id, balance_id),
    KEY idx_inventory_aging_snapshot_line_snapshot (tenant_id, snapshot_id, line_id),
    KEY idx_inventory_aging_snapshot_line_balance (tenant_id, balance_id),
    CONSTRAINT fk_inventory_aging_snapshot_line_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES cloudmold_inventory_aging_snapshot (snapshot_id),
    CONSTRAINT ck_inventory_aging_snapshot_line_quantities CHECK (
        on_hand_quantity >= 0
        AND reserved_quantity >= 0
        AND in_transit_quantity >= 0
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_age_basis CHECK (
        age_basis_type IN ('LOT_RECEIVED_AT', 'LEDGER_FIRST_ENTRY_AT', 'UNKNOWN')
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_age_bucket CHECK (
        age_bucket IN ('FRESH', 'AGING', 'STALE', 'OBSOLETE', 'UNKNOWN')
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_expiry_status CHECK (
        expiry_status IN ('HEALTHY', 'WARNING', 'CRITICAL', 'EXPIRED', 'UNKNOWN')
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_expiry_bucket CHECK (
        expiry_bucket IN ('HEALTHY', 'WARNING', 'CRITICAL', 'EXPIRED', 'UNKNOWN')
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_risk CHECK (
        risk_classification IN ('EXPIRY_CRITICAL', 'QUALITY_AT_RISK', 'STOCK_RESTRICTED',
                                'AGE_OBSOLETE', 'EXPIRY_WARNING', 'AGE_ATTENTION', 'UNKNOWN', 'HEALTHY')
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_unknown_age CHECK (
        (age_basis_type = 'UNKNOWN' AND age_basis_at IS NULL AND age_days IS NULL AND age_bucket = 'UNKNOWN')
        OR (age_basis_type <> 'UNKNOWN' AND age_basis_at IS NOT NULL AND age_days IS NOT NULL AND age_days >= 0)
    ),
    CONSTRAINT ck_inventory_aging_snapshot_line_unknown_expiry CHECK (
        (expiry_status = 'UNKNOWN' AND expiry_days_remaining IS NULL AND expiry_bucket = 'UNKNOWN')
        OR (expiry_status <> 'UNKNOWN' AND expiry_days_remaining IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Frozen inventory aging and shelf-life lines at exact v3 balance grain';
