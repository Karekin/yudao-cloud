SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_inventory_control_policy_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_inventory_control_policy_operation_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_inventory_control_policy_operation_id (tenant_id, operation_id),
    CONSTRAINT ck_inventory_control_policy_operation_status CHECK (status IN (0, 10)),
    CONSTRAINT ck_inventory_control_policy_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Accepted idempotent inventory-control policy command';

CREATE TABLE IF NOT EXISTS cloudmold_safety_stock_policy (
    policy_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_network_id VARCHAR(128) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    target_service_level_basis_points INT NOT NULL,
    safety_stock_quantity DECIMAL(24, 6) NOT NULL,
    reorder_point_quantity DECIMAL(24, 6) NOT NULL,
    maximum_stock_quantity DECIMAL(24, 6) NOT NULL,
    replenishment_cycle_days INT NOT NULL,
    lead_time_days INT NOT NULL,
    policy_basis_code VARCHAR(64) NOT NULL,
    policy_sha256 CHAR(64) NOT NULL,
    evidence_ref VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    current_version BIGINT NOT NULL,
    approved_version BIGINT NULL,
    published_version BIGINT NULL,
    active_version_id VARCHAR(128) NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    approved_by_principal_id VARCHAR(128) NULL,
    published_by_principal_id VARCHAR(128) NULL,
    retired_by_principal_id VARCHAR(128) NULL,
    approved_at DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    retired_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_safety_stock_policy_tenant_id (tenant_id, policy_id),
    UNIQUE KEY uk_safety_stock_policy_tenant_code (tenant_id, policy_code),
    UNIQUE KEY uk_safety_stock_policy_scope_exact (
        tenant_id, owner_type, owner_id, canonical_sku_id, warehouse_network_id, effective_from, effective_to
    ),
    KEY idx_safety_stock_policy_scope_status (
        tenant_id, owner_type, owner_id, canonical_sku_id, warehouse_network_id, status, effective_from
    ),
    CONSTRAINT ck_safety_stock_policy_effective_period CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    ),
    CONSTRAINT ck_safety_stock_policy_service_level CHECK (
        target_service_level_basis_points BETWEEN 0 AND 10000
    ),
    CONSTRAINT ck_safety_stock_policy_quantities CHECK (
        safety_stock_quantity >= 0
        AND reorder_point_quantity >= safety_stock_quantity
        AND maximum_stock_quantity >= reorder_point_quantity
    ),
    CONSTRAINT ck_safety_stock_policy_days CHECK (
        replenishment_cycle_days > 0 AND lead_time_days >= 0
    ),
    CONSTRAINT ck_safety_stock_policy_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'RETIRED')
    ),
    CONSTRAINT ck_safety_stock_policy_version CHECK (current_version > 0),
    CONSTRAINT ck_safety_stock_policy_hash CHECK (policy_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Mutable safety-stock policy head with explicit lifecycle pointers';

CREATE TABLE IF NOT EXISTS cloudmold_safety_stock_policy_version (
    policy_version_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_id VARCHAR(128) NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_network_id VARCHAR(128) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    target_service_level_basis_points INT NOT NULL,
    safety_stock_quantity DECIMAL(24, 6) NOT NULL,
    reorder_point_quantity DECIMAL(24, 6) NOT NULL,
    maximum_stock_quantity DECIMAL(24, 6) NOT NULL,
    replenishment_cycle_days INT NOT NULL,
    lead_time_days INT NOT NULL,
    policy_basis_code VARCHAR(64) NOT NULL,
    policy_sha256 CHAR(64) NOT NULL,
    evidence_ref VARCHAR(255) NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    source_operation_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (policy_version_id),
    UNIQUE KEY uk_safety_stock_policy_version_tenant_id (tenant_id, policy_version_id),
    UNIQUE KEY uk_safety_stock_policy_version_number (tenant_id, policy_id, version),
    KEY idx_safety_stock_policy_version_status (tenant_id, policy_id, status, version),
    CONSTRAINT fk_safety_stock_policy_version_head FOREIGN KEY (tenant_id, policy_id)
        REFERENCES cloudmold_safety_stock_policy (tenant_id, policy_id),
    CONSTRAINT ck_safety_stock_policy_version_effective_period CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    ),
    CONSTRAINT ck_safety_stock_policy_version_service_level CHECK (
        target_service_level_basis_points BETWEEN 0 AND 10000
    ),
    CONSTRAINT ck_safety_stock_policy_version_quantities CHECK (
        safety_stock_quantity >= 0
        AND reorder_point_quantity >= safety_stock_quantity
        AND maximum_stock_quantity >= reorder_point_quantity
    ),
    CONSTRAINT ck_safety_stock_policy_version_days CHECK (
        replenishment_cycle_days > 0 AND lead_time_days >= 0
    ),
    CONSTRAINT ck_safety_stock_policy_version_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'RETIRED')
    ),
    CONSTRAINT ck_safety_stock_policy_version_version CHECK (version > 0),
    CONSTRAINT ck_safety_stock_policy_version_hash CHECK (policy_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable safety-stock policy version history';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_health_snapshot (
    snapshot_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    snapshot_code VARCHAR(64) NOT NULL,
    policy_id VARCHAR(128) NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    policy_version_id VARCHAR(128) NOT NULL,
    policy_version BIGINT NOT NULL,
    ledger_watermark_ref VARCHAR(255) NOT NULL,
    ledger_watermark_occurred_at DATETIME(6) NOT NULL,
    stockout_count INT NOT NULL,
    low_stock_count INT NOT NULL,
    overstock_count INT NOT NULL,
    obsolete_count INT NOT NULL,
    aged_count INT NOT NULL,
    shelf_life_risk_count INT NOT NULL,
    shortage_quantity DECIMAL(24, 6) NOT NULL,
    excess_quantity DECIMAL(24, 6) NOT NULL,
    at_risk_quantity DECIMAL(24, 6) NOT NULL,
    issue_count INT NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_inventory_health_snapshot_tenant_id (tenant_id, snapshot_id),
    UNIQUE KEY uk_inventory_health_snapshot_tenant_code (tenant_id, snapshot_code),
    KEY idx_inventory_health_snapshot_policy (tenant_id, policy_id, created_at),
    CONSTRAINT fk_inventory_health_snapshot_policy_version FOREIGN KEY (tenant_id, policy_version_id)
        REFERENCES cloudmold_safety_stock_policy_version (tenant_id, policy_version_id),
    CONSTRAINT ck_inventory_health_snapshot_counts CHECK (
        stockout_count >= 0
        AND low_stock_count >= 0
        AND overstock_count >= 0
        AND obsolete_count >= 0
        AND aged_count >= 0
        AND shelf_life_risk_count >= 0
        AND issue_count >= 0
    ),
    CONSTRAINT ck_inventory_health_snapshot_quantities CHECK (
        shortage_quantity >= 0 AND excess_quantity >= 0 AND at_risk_quantity >= 0
    ),
    CONSTRAINT ck_inventory_health_snapshot_status CHECK (status IN ('CAPTURED')),
    CONSTRAINT ck_inventory_health_snapshot_hash CHECK (snapshot_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable inventory-health snapshot that freezes policy version and ledger watermark';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_health_snapshot_issue_ref (
    snapshot_issue_ref_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    snapshot_id VARCHAR(128) NOT NULL,
    issue_id VARCHAR(128) NOT NULL,
    issue_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_balance_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (snapshot_issue_ref_id),
    UNIQUE KEY uk_inventory_health_snapshot_issue_ref (tenant_id, snapshot_id, issue_id),
    KEY idx_inventory_health_snapshot_issue_ref_issue (tenant_id, issue_id),
    CONSTRAINT fk_inventory_health_snapshot_issue_ref_snapshot FOREIGN KEY (tenant_id, snapshot_id)
        REFERENCES cloudmold_inventory_health_snapshot (tenant_id, snapshot_id),
    CONSTRAINT fk_inventory_health_snapshot_issue_ref_issue FOREIGN KEY (tenant_id, issue_id)
        REFERENCES cloudmold_inventory_health_issue (tenant_id, issue_id),
    CONSTRAINT ck_inventory_health_snapshot_issue_ref_type CHECK (
        issue_type IN ('STOCKOUT', 'LOW_STOCK', 'EXCESS', 'AGED', 'PENDING_QC')
    ),
    CONSTRAINT ck_inventory_health_snapshot_issue_ref_severity CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_inventory_health_snapshot_issue_ref_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Issue references frozen into an inventory-health snapshot';
