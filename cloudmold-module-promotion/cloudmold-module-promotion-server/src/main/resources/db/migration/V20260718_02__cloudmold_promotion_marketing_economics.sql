CREATE TABLE cloudmold_promotion_advertising_ledger (
    ledger_entry_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ledger_entry_code VARCHAR(128) NOT NULL,
    campaign_id VARCHAR(64) NOT NULL,
    placement_id VARCHAR(64) NULL,
    merchant_id VARCHAR(64) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    charge_model VARCHAR(32) NOT NULL,
    source_interaction_id VARCHAR(64) NULL,
    order_ref VARCHAR(128) NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ledger_entry_id),
    UNIQUE KEY uk_promotion_ad_ledger_code (tenant_id, ledger_entry_code),
    KEY idx_promotion_ad_ledger_campaign_time (tenant_id, campaign_id, occurred_at),
    KEY idx_promotion_ad_ledger_placement_time (tenant_id, placement_id, occurred_at),
    CONSTRAINT ck_promotion_ad_ledger_type CHECK (entry_type IN ('SPEND', 'REVENUE')),
    CONSTRAINT ck_promotion_ad_ledger_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_promotion_ad_ledger_currency CHECK (currency_code = 'CNY')
) ENGINE=InnoDB;

CREATE TABLE cloudmold_promotion_experiment_result (
    experiment_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    experiment_code VARCHAR(128) NOT NULL,
    campaign_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    measured_from DATETIME(6) NOT NULL,
    measured_to DATETIME(6) NOT NULL,
    baseline_contribution_profit_minor BIGINT NOT NULL,
    treatment_contribution_profit_minor BIGINT NOT NULL,
    incremental_contribution_profit_minor BIGINT NOT NULL,
    promotion_cost_minor BIGINT NOT NULL,
    eligible_population_count INT NOT NULL,
    treatment_population_count INT NOT NULL,
    control_population_count INT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    methodology_ref VARCHAR(256) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (experiment_id),
    UNIQUE KEY uk_promotion_experiment_code (tenant_id, experiment_code),
    KEY idx_promotion_experiment_campaign (tenant_id, campaign_id, measured_to),
    CONSTRAINT ck_promotion_experiment_window CHECK (measured_from < measured_to),
    CONSTRAINT ck_promotion_experiment_baseline CHECK (baseline_contribution_profit_minor >= 0),
    CONSTRAINT ck_promotion_experiment_treatment CHECK (treatment_contribution_profit_minor >= 0),
    CONSTRAINT ck_promotion_experiment_incremental CHECK (
        incremental_contribution_profit_minor = treatment_contribution_profit_minor - baseline_contribution_profit_minor
    ),
    CONSTRAINT ck_promotion_experiment_cost CHECK (promotion_cost_minor > 0),
    CONSTRAINT ck_promotion_experiment_population CHECK (
        eligible_population_count > 0
        AND treatment_population_count > 0
        AND control_population_count > 0
        AND treatment_population_count + control_population_count <= eligible_population_count
    ),
    CONSTRAINT ck_promotion_experiment_currency CHECK (currency_code = 'CNY')
) ENGINE=InnoDB;
