SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE cloudmold_inventory_health_issue
    DROP CHECK ck_inventory_issue_scan_source,
    DROP CHECK ck_inventory_issue_detection_source,
    DROP FOREIGN KEY fk_inventory_issue_scan,
    DROP INDEX uk_inventory_issue_active,
    DROP INDEX idx_inventory_issue_scan,
    DROP COLUMN active_issue_key,
    DROP COLUMN detection_source,
    DROP COLUMN scan_id;

DROP TABLE IF EXISTS cloudmold_inventory_health_scan;
DROP TABLE IF EXISTS cloudmold_replenishment_conversion;
DROP TABLE IF EXISTS cloudmold_supply_plan_scenario;
DROP TABLE IF EXISTS cloudmold_forecast_actual;
DROP TABLE IF EXISTS cloudmold_forecast_evaluation;
