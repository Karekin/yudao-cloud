-- Every result column must be zero. Assessment is evidence only and must not post an opening balance.

SELECT 'inventory_migration_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_operation
WHERE status=10 AND (migration_run_id IS NULL OR result_json IS NULL);

SELECT 'inventory_migration_run_count_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_run run
LEFT JOIN (
  SELECT tenant_id,migration_run_id,COUNT(*) AS candidate_count,
         SUM(decision_status='ELIGIBLE') AS eligible_count,
         SUM(decision_status='BLOCKED') AS blocked_count,
         SUM(decision_status='REJECTED') AS rejected_count
  FROM cloudmold_inventory_migration_candidate
  GROUP BY tenant_id,migration_run_id
) candidate ON candidate.tenant_id=run.tenant_id AND candidate.migration_run_id=run.migration_run_id
WHERE candidate.migration_run_id IS NULL
   OR run.candidate_count<>candidate.candidate_count
   OR run.eligible_count<>candidate.eligible_count
   OR run.blocked_count<>candidate.blocked_count
   OR run.rejected_count<>candidate.rejected_count;

SELECT 'inventory_migration_candidate_source_identity' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate
WHERE source_system<>'CLOUDMOLD_INVENTORY_V1' OR source_type<>'BALANCE'
   OR BINARY source_id<>BINARY legacy_balance_id
   OR legacy_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$';

SELECT 'inventory_migration_candidate_decision_reason_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate
WHERE (decision_status='ELIGIBLE' AND JSON_LENGTH(reason_codes)<>0)
   OR (decision_status<>'ELIGIBLE' AND JSON_LENGTH(reason_codes)=0);

SELECT 'inventory_migration_candidate_quantity_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate
WHERE source_on_hand_quantity<0 OR source_reserved_quantity<0 OR source_in_transit_quantity<0
   OR active_reservation_count<0 OR active_reservation_quantity<0
   OR source_reserved_quantity>source_on_hand_quantity;

SELECT 'inventory_migration_candidate_source_snapshot_changed_before_assessment' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate candidate
LEFT JOIN cloudmold_inventory_balance balance
  ON balance.tenant_id=candidate.tenant_id AND BINARY balance.balance_id=BINARY candidate.legacy_balance_id
WHERE balance.balance_id IS NULL
   OR balance.version<>candidate.legacy_balance_version
   OR balance.updated_at<>candidate.source_updated_at
   OR BINARY balance.owner_id<>BINARY candidate.legacy_owner_id
   OR BINARY balance.canonical_sku_id<>BINARY candidate.legacy_canonical_sku_id
   OR BINARY balance.warehouse_id<>BINARY candidate.legacy_warehouse_id
   OR BINARY balance.stock_status<>BINARY candidate.stock_status
   OR BINARY balance.quality_status<>BINARY candidate.quality_status
   OR BINARY balance.base_uom_code<>BINARY candidate.base_uom_code
   OR balance.on_hand_quantity<>candidate.source_on_hand_quantity
   OR balance.reserved_quantity<>candidate.source_reserved_quantity
   OR balance.in_transit_quantity<>candidate.source_in_transit_quantity;

SELECT 'inventory_migration_candidate_reservation_snapshot_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate candidate
LEFT JOIN (
  SELECT tenant_id,balance_id,COUNT(*) AS active_count,COALESCE(SUM(quantity),0) AS active_quantity
  FROM cloudmold_inventory_reservation WHERE status=10 GROUP BY tenant_id,balance_id
) reservation ON reservation.tenant_id=candidate.tenant_id
              AND BINARY reservation.balance_id=BINARY candidate.legacy_balance_id
WHERE candidate.active_reservation_count<>COALESCE(reservation.active_count,0)
   OR candidate.active_reservation_quantity<>COALESCE(reservation.active_quantity,0);

SELECT 'inventory_migration_candidate_missing_assessment_event' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate candidate
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=candidate.tenant_id
 AND event.event_type='inventory.migration.balance_assessed'
 AND event.aggregate_type='inventory_migration_assessment'
 AND BINARY event.aggregate_id=BINARY candidate.candidate_id
 AND event.aggregate_version=candidate.version
WHERE event.event_id IS NULL;

SELECT 'inventory_migration_candidate_duplicate_assessment_event' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT tenant_id,aggregate_id,aggregate_version,COUNT(*) AS event_count
  FROM cloudmold_event_outbox
  WHERE event_type='inventory.migration.balance_assessed'
  GROUP BY tenant_id,aggregate_id,aggregate_version
  HAVING COUNT(*)<>1
) duplicate_event;

SELECT 'inventory_migration_event_payload_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_candidate candidate
JOIN cloudmold_event_outbox event
  ON event.tenant_id=candidate.tenant_id
 AND event.event_type='inventory.migration.balance_assessed'
 AND BINARY event.aggregate_id=BINARY candidate.candidate_id
WHERE BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.migration_run_id'))<>BINARY candidate.migration_run_id
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.legacy_balance_id'))<>BINARY candidate.legacy_balance_id
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.source_snapshot_hash'))<>BINARY candidate.legacy_snapshot_hash
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.assessment_status'))<>BINARY candidate.decision_status
   OR JSON_LENGTH(JSON_EXTRACT(event.payload,'$.blocker_codes'))<>JSON_LENGTH(candidate.reason_codes);

SELECT 'inventory_migration_assessment_illegal_opening' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_event_outbox event
JOIN cloudmold_inventory_migration_candidate candidate
  ON candidate.tenant_id=event.tenant_id
 AND BINARY candidate.candidate_id=BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.migration_candidate_id'))
WHERE event.event_type='inventory.stock.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.movement_type'))='MIGRATION_OPENING'
  AND NOT EXISTS (
    SELECT 1 FROM cloudmold_inventory_migration_qualification qualification
    WHERE qualification.tenant_id=candidate.tenant_id
      AND BINARY qualification.candidate_id=BINARY candidate.candidate_id
      AND qualification.status='MIGRATED'
  );

SELECT 'inventory_migration_assessment_implicitly_resolved_bridge' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_bridge bridge
JOIN cloudmold_inventory_migration_candidate candidate
  ON candidate.tenant_id=bridge.tenant_id
 AND BINARY candidate.legacy_balance_id=BINARY bridge.legacy_balance_id
WHERE bridge.resolution_status='RESOLVED'
  AND NOT EXISTS (
    SELECT 1 FROM cloudmold_inventory_migration_qualification qualification
    WHERE qualification.tenant_id=candidate.tenant_id
      AND BINARY qualification.candidate_id=BINARY candidate.candidate_id
      AND qualification.status='MIGRATED'
  );
