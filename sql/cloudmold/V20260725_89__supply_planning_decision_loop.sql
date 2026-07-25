-- P1 supply-planning decision loop: forecast accuracy, constrained scenarios,
-- governed replenishment conversion and rule-driven inventory-health scans.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_forecast_evaluation (
    evaluation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    forecast_id VARCHAR(128) NOT NULL,
    actuals_sha256 CHAR(64) NOT NULL,
    point_count INT NOT NULL,
    forecast_quantity DECIMAL(24, 6) NOT NULL,
    actual_quantity DECIMAL(24, 6) NOT NULL,
    absolute_error DECIMAL(24, 6) NOT NULL,
    signed_error DECIMAL(24, 6) NOT NULL,
    wape_basis_points INT NULL,
    bias_basis_points INT NULL,
    mae DECIMAL(24, 6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (evaluation_id),
    UNIQUE KEY uk_forecast_evaluation_tenant_id (tenant_id, evaluation_id),
    UNIQUE KEY uk_forecast_evaluation_evidence (tenant_id, forecast_id, actuals_sha256),
    KEY idx_forecast_evaluation_model (tenant_id, forecast_id, evaluated_at),
    CONSTRAINT fk_forecast_evaluation_forecast FOREIGN KEY (tenant_id, forecast_id)
        REFERENCES cloudmold_demand_forecast (tenant_id, forecast_id),
    CONSTRAINT ck_forecast_evaluation_hash CHECK (actuals_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_forecast_evaluation_metrics CHECK (
        point_count > 0 AND forecast_quantity >= 0 AND actual_quantity >= 0
        AND absolute_error >= 0 AND mae >= 0
        AND (actual_quantity <> 0 OR (wape_basis_points IS NULL AND bias_basis_points IS NULL))),
    CONSTRAINT ck_forecast_evaluation_status CHECK (status = 'COMPLETED'),
    CONSTRAINT ck_forecast_evaluation_version CHECK (version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable demand-forecast backtest and accuracy summary';

CREATE TABLE IF NOT EXISTS cloudmold_forecast_actual (
    forecast_actual_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    evaluation_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NULL,
    bucket_start DATE NOT NULL,
    forecast_quantity DECIMAL(24, 6) NOT NULL,
    actual_quantity DECIMAL(24, 6) NOT NULL,
    absolute_error DECIMAL(24, 6) NOT NULL,
    signed_error DECIMAL(24, 6) NOT NULL,
    uom_code VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (forecast_actual_id),
    UNIQUE KEY uk_forecast_actual_business
        (tenant_id, evaluation_id, canonical_sku_id, warehouse_id, bucket_start),
    CONSTRAINT fk_forecast_actual_evaluation FOREIGN KEY (tenant_id, evaluation_id)
        REFERENCES cloudmold_forecast_evaluation (tenant_id, evaluation_id),
    CONSTRAINT ck_forecast_actual_quantities CHECK (
        forecast_quantity >= 0 AND actual_quantity >= 0 AND absolute_error >= 0),
    CONSTRAINT ck_forecast_actual_uom CHECK (uom_code REGEXP '^[A-Z][A-Z0-9_]{0,15}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Forecast-to-actual evidence at the governed forecast grain';

CREATE TABLE IF NOT EXISTS cloudmold_supply_plan_scenario (
    scenario_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id VARCHAR(128) NOT NULL,
    scenario_code VARCHAR(64) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    forecast_quantity DECIMAL(24, 6) NOT NULL,
    safety_stock_quantity DECIMAL(24, 6) NOT NULL,
    on_hand_quantity DECIMAL(24, 6) NOT NULL,
    inbound_quantity DECIMAL(24, 6) NOT NULL,
    capacity_quantity DECIMAL(24, 6) NOT NULL,
    minimum_order_quantity DECIMAL(24, 6) NOT NULL,
    unit_cost_minor BIGINT NOT NULL,
    constrained_order_quantity DECIMAL(24, 6) NOT NULL,
    projected_shortage_quantity DECIMAL(24, 6) NOT NULL,
    projected_service_level_basis_points INT NOT NULL,
    projected_cost_minor BIGINT NOT NULL,
    uom_code VARCHAR(16) NOT NULL,
    parameters_sha256 CHAR(64) NOT NULL,
    solver_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (scenario_id),
    UNIQUE KEY uk_supply_scenario_tenant_id (tenant_id, scenario_id),
    UNIQUE KEY uk_supply_scenario_code (tenant_id, plan_id, scenario_code),
    KEY idx_supply_scenario_plan_service
        (tenant_id, plan_id, projected_service_level_basis_points, projected_cost_minor),
    CONSTRAINT fk_supply_scenario_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES cloudmold_supply_plan (tenant_id, plan_id),
    CONSTRAINT ck_supply_scenario_quantities CHECK (
        forecast_quantity >= 0 AND safety_stock_quantity >= 0
        AND on_hand_quantity >= 0 AND inbound_quantity >= 0
        AND capacity_quantity >= 0 AND minimum_order_quantity >= 0
        AND constrained_order_quantity >= 0 AND projected_shortage_quantity >= 0),
    CONSTRAINT ck_supply_scenario_service CHECK (
        projected_service_level_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_supply_scenario_cost CHECK (unit_cost_minor >= 0 AND projected_cost_minor >= 0),
    CONSTRAINT ck_supply_scenario_uom CHECK (uom_code REGEXP '^[A-Z][A-Z0-9_]{0,15}$'),
    CONSTRAINT ck_supply_scenario_hash CHECK (parameters_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_supply_scenario_status CHECK (status = 'EVALUATED'),
    CONSTRAINT ck_supply_scenario_version CHECK (version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Comparable constrained S&OP scenario result';

CREATE TABLE IF NOT EXISTS cloudmold_replenishment_conversion (
    conversion_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    recommendation_id VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_reference VARCHAR(128) NOT NULL,
    requested_quantity DECIMAL(24, 6) NOT NULL,
    uom_code VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    converted_by_principal_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    converted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (conversion_id),
    UNIQUE KEY uk_replenishment_conversion_tenant_id (tenant_id, conversion_id),
    UNIQUE KEY uk_replenishment_conversion_recommendation (tenant_id, recommendation_id),
    UNIQUE KEY uk_replenishment_conversion_target (tenant_id, target_type, target_reference),
    CONSTRAINT fk_replenishment_conversion_recommendation
        FOREIGN KEY (tenant_id, recommendation_id)
        REFERENCES cloudmold_replenishment_recommendation (tenant_id, recommendation_id),
    CONSTRAINT ck_replenishment_conversion_target CHECK (
        target_type IN ('PURCHASE_REQUEST', 'TRANSFER_REQUEST')),
    CONSTRAINT ck_replenishment_conversion_quantity CHECK (requested_quantity > 0),
    CONSTRAINT ck_replenishment_conversion_uom CHECK (
        uom_code REGEXP '^[A-Z][A-Z0-9_]{0,15}$'),
    CONSTRAINT ck_replenishment_conversion_status CHECK (status = 'CREATED'),
    CONSTRAINT ck_replenishment_conversion_version CHECK (version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Governed purchase or transfer request intent created from replenishment';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_health_scan (
    scan_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    policy_sha256 CHAR(64) NOT NULL,
    observation_count INT NOT NULL,
    issue_count INT NOT NULL,
    skipped_active_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (scan_id),
    UNIQUE KEY uk_inventory_health_scan_tenant_id (tenant_id, scan_id),
    KEY idx_inventory_health_scan_policy (tenant_id, policy_code, evaluated_at),
    CONSTRAINT ck_inventory_health_scan_hash CHECK (policy_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_inventory_health_scan_counts CHECK (
        observation_count > 0 AND issue_count >= 0 AND skipped_active_count >= 0
        AND issue_count + skipped_active_count <= observation_count),
    CONSTRAINT ck_inventory_health_scan_status CHECK (status = 'COMPLETED'),
    CONSTRAINT ck_inventory_health_scan_version CHECK (version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Auditable execution of an inventory-health policy snapshot';

ALTER TABLE cloudmold_inventory_health_issue
    ADD COLUMN scan_id VARCHAR(128) NULL AFTER resolution_code,
    ADD COLUMN detection_source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' AFTER scan_id,
    ADD COLUMN active_issue_key VARCHAR(161)
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('OPEN', 'ACKNOWLEDGED')
                THEN CONCAT(source_balance_id, '#', issue_type)
                ELSE NULL
            END
        ) STORED AFTER detection_source,
    ADD KEY idx_inventory_issue_scan (tenant_id, scan_id),
    ADD UNIQUE KEY uk_inventory_issue_active
        (tenant_id, active_issue_key),
    ADD CONSTRAINT fk_inventory_issue_scan FOREIGN KEY (tenant_id, scan_id)
        REFERENCES cloudmold_inventory_health_scan (tenant_id, scan_id),
    ADD CONSTRAINT ck_inventory_issue_detection_source CHECK (
        detection_source IN ('MANUAL', 'RULE_SCAN')),
    ADD CONSTRAINT ck_inventory_issue_scan_source CHECK (
        (detection_source = 'MANUAL' AND scan_id IS NULL)
        OR (detection_source = 'RULE_SCAN' AND scan_id IS NOT NULL));
