-- Destructive manual rollback for V20260725_85.
-- Export the affected tenant rows and stop command traffic before executing.

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS cloudmold_inventory_health_issue;
DROP TABLE IF EXISTS cloudmold_replenishment_recommendation;
DROP TABLE IF EXISTS cloudmold_supply_plan;
DROP TABLE IF EXISTS cloudmold_demand_forecast_point;
DROP TABLE IF EXISTS cloudmold_demand_forecast;
DROP TABLE IF EXISTS cloudmold_supply_planning_operation;
SET FOREIGN_KEY_CHECKS = 1;
