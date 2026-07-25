-- Every result column must be zero.

SELECT 'supply_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supply_planning_operation
WHERE status=10 AND (aggregate_type IS NULL OR aggregate_id IS NULL OR result_json IS NULL);

SELECT 'forecast_point_outside_horizon' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_demand_forecast_point point
JOIN cloudmold_demand_forecast forecast
  ON forecast.tenant_id=point.tenant_id AND forecast.forecast_id=point.forecast_id
WHERE point.bucket_start<forecast.horizon_start OR point.bucket_start>forecast.horizon_end
   OR point.forecast_quantity<0
   OR (point.lower_quantity IS NOT NULL AND point.lower_quantity>point.forecast_quantity)
   OR (point.upper_quantity IS NOT NULL AND point.upper_quantity<point.forecast_quantity);

SELECT 'supply_plan_nonpublished_forecast' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supply_plan plan
LEFT JOIN cloudmold_demand_forecast forecast
  ON forecast.tenant_id=plan.tenant_id AND forecast.forecast_id=plan.demand_forecast_id
WHERE forecast.forecast_id IS NULL
   OR (plan.status='APPROVED' AND forecast.status<>'PUBLISHED')
   OR plan.horizon_start<forecast.horizon_start OR plan.horizon_end>forecast.horizon_end;

SELECT 'replenishment_nonapproved_plan' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_replenishment_recommendation recommendation
LEFT JOIN cloudmold_supply_plan plan
  ON plan.tenant_id=recommendation.tenant_id AND plan.plan_id=recommendation.plan_id
WHERE plan.plan_id IS NULL OR plan.status NOT IN ('APPROVED','RELEASED');

SELECT 'inventory_issue_terminal_fields_invalid' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_health_issue
WHERE (status='ACKNOWLEDGED' AND (owner_principal_id IS NULL OR acknowledged_at IS NULL))
   OR (status IN ('RESOLVED','DISMISSED')
       AND (resolution_code IS NULL OR resolved_at IS NULL));

SELECT 'inventory_issue_duplicate_active' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,source_balance_id,issue_type
    FROM cloudmold_inventory_health_issue
    WHERE status IN ('OPEN','ACKNOWLEDGED')
    GROUP BY tenant_id,source_balance_id,issue_type
    HAVING COUNT(*)>1
) duplicate_issue;

SELECT 'supply_event_version_missing' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT operation.tenant_id,operation.aggregate_type,operation.aggregate_id,
           JSON_UNQUOTE(JSON_EXTRACT(operation.result_json,'$.aggregateVersion')) aggregate_version
    FROM cloudmold_supply_planning_operation operation
    WHERE operation.status=10
) succeeded
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=succeeded.tenant_id
 AND event.aggregate_type COLLATE utf8mb4_unicode_ci=
     succeeded.aggregate_type COLLATE utf8mb4_unicode_ci
 AND event.aggregate_id COLLATE utf8mb4_unicode_ci=
     succeeded.aggregate_id COLLATE utf8mb4_unicode_ci
 AND event.aggregate_version=succeeded.aggregate_version
 AND event.source_system COLLATE utf8mb4_unicode_ci='cloudmold-supply-planning'
WHERE event.event_id IS NULL;

SELECT 'forecast_evaluation_rollup_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_forecast_evaluation evaluation
LEFT JOIN (
    SELECT tenant_id,evaluation_id,COUNT(*) point_count,
           SUM(forecast_quantity) forecast_quantity,SUM(actual_quantity) actual_quantity,
           SUM(absolute_error) absolute_error,SUM(signed_error) signed_error
    FROM cloudmold_forecast_actual
    GROUP BY tenant_id,evaluation_id
) actual
  ON actual.tenant_id=evaluation.tenant_id
 AND actual.evaluation_id=evaluation.evaluation_id
WHERE actual.evaluation_id IS NULL
   OR actual.point_count<>evaluation.point_count
   OR actual.forecast_quantity<>evaluation.forecast_quantity
   OR actual.actual_quantity<>evaluation.actual_quantity
   OR actual.absolute_error<>evaluation.absolute_error
   OR actual.signed_error<>evaluation.signed_error;

SELECT 'supply_scenario_constraint_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supply_plan_scenario scenario
JOIN cloudmold_supply_plan plan
  ON plan.tenant_id=scenario.tenant_id AND plan.plan_id=scenario.plan_id
WHERE scenario.constrained_order_quantity>scenario.capacity_quantity
   OR scenario.projected_cost_minor>plan.budget_amount_minor
   OR scenario.projected_service_level_basis_points NOT BETWEEN 0 AND 10000;

SELECT 'replenishment_conversion_state_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_replenishment_conversion conversion
JOIN cloudmold_replenishment_recommendation recommendation
  ON recommendation.tenant_id=conversion.tenant_id
 AND recommendation.recommendation_id=conversion.recommendation_id
WHERE recommendation.status<>'CONVERTED'
   OR recommendation.suggested_quantity<>conversion.requested_quantity
   OR recommendation.uom_code<>conversion.uom_code;

SELECT 'inventory_scan_issue_count_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_health_scan scan
LEFT JOIN (
    SELECT tenant_id,scan_id,COUNT(*) issue_count
    FROM cloudmold_inventory_health_issue
    WHERE detection_source='RULE_SCAN'
    GROUP BY tenant_id,scan_id
) issue
  ON issue.tenant_id=scan.tenant_id AND issue.scan_id=scan.scan_id
WHERE scan.issue_count<>COALESCE(issue.issue_count,0);

SELECT 'supply_plan_selected_scenario_invalid' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supply_plan plan
LEFT JOIN cloudmold_supply_plan_scenario scenario
  ON scenario.tenant_id=plan.tenant_id
 AND scenario.plan_id=plan.plan_id
 AND scenario.scenario_id=plan.selected_scenario_id
WHERE plan.status='RELEASED'
  AND (scenario.scenario_id IS NULL OR scenario.status<>'SELECTED'
       OR plan.release_principal_id IS NULL OR plan.released_at IS NULL);

SELECT 'supply_plan_multiple_selected_scenarios' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,plan_id
    FROM cloudmold_supply_plan_scenario
    WHERE status='SELECTED'
    GROUP BY tenant_id,plan_id
    HAVING COUNT(*)>1
) duplicate_selection;

SELECT 'approved_plan_selected_scenario_count_invalid' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supply_plan plan
LEFT JOIN (
    SELECT tenant_id,plan_id,COUNT(*) selected_count
    FROM cloudmold_supply_plan_scenario
    WHERE status='SELECTED'
    GROUP BY tenant_id,plan_id
) selection
  ON selection.tenant_id=plan.tenant_id AND selection.plan_id=plan.plan_id
WHERE plan.status IN ('APPROVED','RELEASED')
  AND COALESCE(selection.selected_count,0)<>1;

SELECT 'released_plan_missing_replenishment' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supply_plan plan
JOIN cloudmold_supply_plan_scenario scenario
  ON scenario.tenant_id=plan.tenant_id
 AND scenario.plan_id=plan.plan_id
 AND scenario.scenario_id=plan.selected_scenario_id
WHERE plan.status='RELEASED'
  AND scenario.constrained_order_quantity>0
  AND NOT EXISTS (
      SELECT 1 FROM cloudmold_replenishment_recommendation recommendation
      WHERE recommendation.tenant_id=plan.tenant_id
        AND recommendation.plan_id=plan.plan_id
        AND recommendation.canonical_sku_id=scenario.canonical_sku_id
        AND recommendation.warehouse_id=scenario.warehouse_id
        AND recommendation.suggested_quantity=scenario.constrained_order_quantity
  );
