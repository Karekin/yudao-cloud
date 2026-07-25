-- Canonical supply-planning first slice. All business aggregates are tenant
-- scoped, idempotent, optimistic-versioned and emit transactional Outbox events.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supply_planning_operation (
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
    UNIQUE KEY uk_supply_operation_tenant_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_supply_operation_tenant_id (tenant_id, operation_id),
    CONSTRAINT ck_supply_operation_status CHECK (status IN (0, 10)),
    CONSTRAINT ck_supply_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Accepted idempotent supply-planning command';

CREATE TABLE IF NOT EXISTS cloudmold_demand_forecast (
    forecast_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    forecast_code VARCHAR(64) NOT NULL,
    horizon_start DATE NOT NULL,
    horizon_end DATE NOT NULL,
    bucket_type VARCHAR(16) NOT NULL,
    model_ref VARCHAR(128) NOT NULL,
    baseline_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (forecast_id),
    UNIQUE KEY uk_demand_forecast_tenant_id (tenant_id, forecast_id),
    UNIQUE KEY uk_demand_forecast_tenant_code (tenant_id, forecast_code),
    KEY idx_demand_forecast_status_horizon (tenant_id, status, horizon_start, horizon_end),
    CONSTRAINT ck_demand_forecast_horizon CHECK (horizon_end >= horizon_start),
    CONSTRAINT ck_demand_forecast_bucket CHECK (bucket_type IN ('DAY', 'WEEK', 'MONTH')),
    CONSTRAINT ck_demand_forecast_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_demand_forecast_version CHECK (version > 0),
    CONSTRAINT ck_demand_forecast_hash CHECK (baseline_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned demand forecast head';

CREATE TABLE IF NOT EXISTS cloudmold_demand_forecast_point (
    forecast_point_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    forecast_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NULL,
    bucket_start DATE NOT NULL,
    forecast_quantity DECIMAL(24, 6) NOT NULL,
    lower_quantity DECIMAL(24, 6) NULL,
    upper_quantity DECIMAL(24, 6) NULL,
    uom_code VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (forecast_point_id),
    UNIQUE KEY uk_demand_point_business
        (tenant_id, forecast_id, canonical_sku_id, warehouse_id, bucket_start),
    KEY idx_demand_point_sku_bucket (tenant_id, canonical_sku_id, bucket_start),
    CONSTRAINT fk_demand_point_forecast FOREIGN KEY (tenant_id, forecast_id)
        REFERENCES cloudmold_demand_forecast (tenant_id, forecast_id),
    CONSTRAINT ck_demand_point_quantity CHECK (
        forecast_quantity >= 0
        AND (lower_quantity IS NULL OR lower_quantity >= 0)
        AND (upper_quantity IS NULL OR upper_quantity >= forecast_quantity)
        AND (lower_quantity IS NULL OR lower_quantity <= forecast_quantity)
    ),
    CONSTRAINT ck_demand_point_uom CHECK (uom_code REGEXP '^[A-Z][A-Z0-9_]{0,15}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable forecast points belonging to a forecast version';

CREATE TABLE IF NOT EXISTS cloudmold_supply_plan (
    plan_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_code VARCHAR(64) NOT NULL,
    demand_forecast_id VARCHAR(128) NOT NULL,
    horizon_start DATE NOT NULL,
    horizon_end DATE NOT NULL,
    target_service_level_basis_points INT NOT NULL,
    budget_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    constraints_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    approver_principal_id VARCHAR(128) NULL,
    version BIGINT NOT NULL,
    approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (plan_id),
    UNIQUE KEY uk_supply_plan_tenant_id (tenant_id, plan_id),
    UNIQUE KEY uk_supply_plan_tenant_code (tenant_id, plan_code),
    KEY idx_supply_plan_status_horizon (tenant_id, status, horizon_start, horizon_end),
    CONSTRAINT fk_supply_plan_forecast FOREIGN KEY (tenant_id, demand_forecast_id)
        REFERENCES cloudmold_demand_forecast (tenant_id, forecast_id),
    CONSTRAINT ck_supply_plan_horizon CHECK (horizon_end >= horizon_start),
    CONSTRAINT ck_supply_plan_service_level CHECK (
        target_service_level_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_supply_plan_budget CHECK (budget_amount_minor >= 0),
    CONSTRAINT ck_supply_plan_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_supply_plan_hash CHECK (constraints_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_supply_plan_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT ck_supply_plan_version CHECK (version > 0),
    CONSTRAINT ck_supply_plan_approval CHECK (
        status = 'DRAFT'
        OR (approver_principal_id IS NOT NULL AND approved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Governed S&OP supply plan';

CREATE TABLE IF NOT EXISTS cloudmold_replenishment_recommendation (
    recommendation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    suggested_quantity DECIMAL(24, 6) NOT NULL,
    uom_code VARCHAR(16) NOT NULL,
    need_by_date DATE NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    decision_principal_id VARCHAR(128) NULL,
    decision_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (recommendation_id),
    UNIQUE KEY uk_replenishment_tenant_id (tenant_id, recommendation_id),
    KEY idx_replenishment_plan_status (tenant_id, plan_id, status),
    KEY idx_replenishment_sku_need (tenant_id, canonical_sku_id, warehouse_id, need_by_date),
    CONSTRAINT fk_replenishment_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES cloudmold_supply_plan (tenant_id, plan_id),
    CONSTRAINT ck_replenishment_quantity CHECK (suggested_quantity > 0),
    CONSTRAINT ck_replenishment_uom CHECK (uom_code REGEXP '^[A-Z][A-Z0-9_]{0,15}$'),
    CONSTRAINT ck_replenishment_status CHECK (
        status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'CONVERTED')),
    CONSTRAINT ck_replenishment_version CHECK (version > 0),
    CONSTRAINT ck_replenishment_decision CHECK (
        status = 'PROPOSED'
        OR (decision_principal_id IS NOT NULL AND decision_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Human-decidable replenishment recommendation';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_health_issue (
    issue_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_balance_id VARCHAR(128) NOT NULL,
    issue_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    owner_principal_id VARCHAR(128) NULL,
    resolution_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    acknowledged_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (issue_id),
    UNIQUE KEY uk_inventory_issue_tenant_id (tenant_id, issue_id),
    KEY idx_inventory_issue_source_status
        (tenant_id, source_balance_id, issue_type, status),
    KEY idx_inventory_issue_status_severity (tenant_id, status, severity, opened_at),
    CONSTRAINT ck_inventory_issue_type CHECK (
        issue_type IN ('STOCKOUT', 'LOW_STOCK', 'EXCESS', 'AGED', 'PENDING_QC')),
    CONSTRAINT ck_inventory_issue_severity CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_inventory_issue_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_inventory_issue_version CHECK (version > 0),
    CONSTRAINT ck_inventory_issue_ack CHECK (
        status = 'OPEN'
        OR (owner_principal_id IS NOT NULL AND acknowledged_at IS NOT NULL)),
    CONSTRAINT ck_inventory_issue_resolution CHECK (
        status NOT IN ('RESOLVED', 'DISMISSED')
        OR (resolution_code IS NOT NULL AND resolved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Actionable inventory-health issue';
