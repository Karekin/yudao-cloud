SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE cloudmold_supply_plan
SET status='APPROVED'
WHERE status='RELEASED';

ALTER TABLE cloudmold_supply_plan
    DROP CHECK ck_supply_plan_release,
    DROP FOREIGN KEY fk_supply_plan_selected_scenario,
    DROP COLUMN released_at,
    DROP COLUMN release_principal_id,
    DROP COLUMN selected_scenario_id;

UPDATE cloudmold_supply_plan_scenario
SET status='EVALUATED'
WHERE status='SELECTED';

ALTER TABLE cloudmold_supply_plan_scenario
    DROP CHECK ck_supply_scenario_selection,
    DROP CHECK ck_supply_scenario_version,
    DROP CHECK ck_supply_scenario_status,
    DROP INDEX uk_supply_scenario_plan_identity,
    DROP COLUMN selected_at,
    DROP COLUMN selected_by_principal_id,
    ADD CONSTRAINT ck_supply_scenario_status CHECK (status = 'EVALUATED'),
    ADD CONSTRAINT ck_supply_scenario_version CHECK (version = 1);
