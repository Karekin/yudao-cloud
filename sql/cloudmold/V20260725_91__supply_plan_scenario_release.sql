-- P2 controlled optimization slice: one selected scenario per plan and
-- deterministic release into an actionable replenishment recommendation.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE cloudmold_supply_plan_scenario
    DROP CHECK ck_supply_scenario_status,
    DROP CHECK ck_supply_scenario_version,
    ADD COLUMN selected_by_principal_id VARCHAR(128) NULL AFTER status,
    ADD COLUMN selected_at DATETIME(6) NULL AFTER evaluated_at,
    ADD UNIQUE KEY uk_supply_scenario_plan_identity
        (tenant_id, plan_id, scenario_id),
    ADD CONSTRAINT ck_supply_scenario_status CHECK (
        status IN ('EVALUATED', 'SELECTED')),
    ADD CONSTRAINT ck_supply_scenario_version CHECK (version > 0),
    ADD CONSTRAINT ck_supply_scenario_selection CHECK (
        status = 'EVALUATED'
        OR (selected_by_principal_id IS NOT NULL AND selected_at IS NOT NULL));

ALTER TABLE cloudmold_supply_plan
    ADD COLUMN selected_scenario_id VARCHAR(128) NULL AFTER approver_principal_id,
    ADD COLUMN release_principal_id VARCHAR(128) NULL AFTER selected_scenario_id,
    ADD COLUMN released_at DATETIME(6) NULL AFTER approved_at,
    ADD CONSTRAINT fk_supply_plan_selected_scenario
        FOREIGN KEY (tenant_id, plan_id, selected_scenario_id)
        REFERENCES cloudmold_supply_plan_scenario (tenant_id, plan_id, scenario_id),
    ADD CONSTRAINT ck_supply_plan_release CHECK (
        status <> 'RELEASED'
        OR (selected_scenario_id IS NOT NULL
            AND release_principal_id IS NOT NULL
            AND released_at IS NOT NULL));
