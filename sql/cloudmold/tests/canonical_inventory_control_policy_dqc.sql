SELECT 'inventory_control_policy_operation_incomplete_success' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_control_policy_operation
WHERE status=10 AND (aggregate_type IS NULL OR aggregate_id IS NULL OR result_json IS NULL);

SELECT 'safety_stock_policy_head_pointer_invalid' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_safety_stock_policy policy
WHERE current_version <= 0
   OR (status='APPROVED' AND (approved_version IS NULL OR approved_at IS NULL OR approved_by_principal_id IS NULL))
   OR (status='PUBLISHED' AND (published_version IS NULL OR published_at IS NULL
                               OR published_by_principal_id IS NULL OR active_version_id IS NULL))
   OR (status='RETIRED' AND (retired_at IS NULL OR retired_by_principal_id IS NULL));

SELECT 'safety_stock_policy_published_version_missing' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_safety_stock_policy policy
LEFT JOIN cloudmold_safety_stock_policy_version version_row
  ON version_row.tenant_id=policy.tenant_id
 AND version_row.policy_id=policy.policy_id
 AND version_row.policy_version_id=policy.active_version_id
 AND version_row.version=policy.published_version
WHERE policy.status='PUBLISHED'
  AND (version_row.policy_version_id IS NULL OR version_row.status<>'PUBLISHED');

SELECT 'safety_stock_policy_published_overlap' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT left_policy.tenant_id,left_policy.policy_id
    FROM cloudmold_safety_stock_policy left_policy
    JOIN cloudmold_safety_stock_policy right_policy
      ON right_policy.tenant_id=left_policy.tenant_id
     AND right_policy.policy_id<>left_policy.policy_id
     AND right_policy.status='PUBLISHED'
     AND left_policy.status='PUBLISHED'
     AND right_policy.owner_type=left_policy.owner_type
     AND right_policy.owner_id=left_policy.owner_id
     AND right_policy.canonical_sku_id=left_policy.canonical_sku_id
     AND right_policy.warehouse_network_id=left_policy.warehouse_network_id
     AND NOT (
         (right_policy.effective_to IS NOT NULL AND right_policy.effective_to < left_policy.effective_from)
      OR (left_policy.effective_to IS NOT NULL AND right_policy.effective_from > left_policy.effective_to)
     )
    GROUP BY left_policy.tenant_id,left_policy.policy_id
) overlap;

SELECT 'safety_stock_policy_event_version_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT operation.tenant_id,operation.aggregate_type,operation.aggregate_id,
           JSON_UNQUOTE(JSON_EXTRACT(operation.result_json,'$.aggregateVersion')) aggregate_version
    FROM cloudmold_inventory_control_policy_operation operation
    WHERE operation.status=10 AND operation.aggregate_type='safety_stock_policy'
) succeeded
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=succeeded.tenant_id
 AND BINARY event.aggregate_type=BINARY succeeded.aggregate_type
 AND BINARY event.aggregate_id=BINARY succeeded.aggregate_id
 AND event.aggregate_version=succeeded.aggregate_version
 AND event.source_system='cloudmold-supply-planning'
WHERE event.event_id IS NULL;

SELECT 'inventory_health_snapshot_policy_version_invalid' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_health_snapshot snapshot
LEFT JOIN cloudmold_safety_stock_policy_version version_row
  ON version_row.tenant_id=snapshot.tenant_id
 AND version_row.policy_version_id=snapshot.policy_version_id
 AND version_row.policy_id=snapshot.policy_id
 AND version_row.version=snapshot.policy_version
WHERE version_row.policy_version_id IS NULL
   OR version_row.status<>'PUBLISHED';

SELECT 'inventory_health_snapshot_issue_count_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_health_snapshot snapshot
LEFT JOIN (
    SELECT tenant_id,snapshot_id,COUNT(*) issue_count
    FROM cloudmold_inventory_health_snapshot_issue_ref
    GROUP BY tenant_id,snapshot_id
) issue_ref
  ON issue_ref.tenant_id=snapshot.tenant_id
 AND issue_ref.snapshot_id=snapshot.snapshot_id
WHERE snapshot.issue_count<>COALESCE(issue_ref.issue_count,0);

SELECT 'inventory_health_snapshot_negative_metric' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_health_snapshot
WHERE stockout_count<0 OR low_stock_count<0 OR overstock_count<0
   OR obsolete_count<0 OR aged_count<0 OR shelf_life_risk_count<0
   OR shortage_quantity<0 OR excess_quantity<0 OR at_risk_quantity<0;

SELECT 'inventory_health_snapshot_event_version_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT operation.tenant_id,operation.aggregate_type,operation.aggregate_id,
           JSON_UNQUOTE(JSON_EXTRACT(operation.result_json,'$.aggregateVersion')) aggregate_version
    FROM cloudmold_inventory_control_policy_operation operation
    WHERE operation.status=10 AND operation.aggregate_type='inventory_health_snapshot'
) succeeded
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=succeeded.tenant_id
 AND BINARY event.aggregate_type=BINARY succeeded.aggregate_type
 AND BINARY event.aggregate_id=BINARY succeeded.aggregate_id
 AND event.aggregate_version=succeeded.aggregate_version
 AND event.source_system='cloudmold-supply-planning'
WHERE event.event_id IS NULL;
