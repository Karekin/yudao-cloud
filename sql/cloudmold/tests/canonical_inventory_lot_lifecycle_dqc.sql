-- Every result column must be zero. Lot is a canonical identity; it is never inferred from legacy balance text.

SELECT 'inventory_lot_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot_operation
WHERE status=10 AND (lot_id IS NULL OR result_json IS NULL);

SELECT 'inventory_lot_mapping_status_interval_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot_source_mapping
WHERE (status='ACTIVE' AND valid_to IS NOT NULL)
   OR (status='ENDED' AND valid_to IS NULL);

SELECT 'inventory_lot_mapping_interval_overlap' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot_source_mapping a
JOIN cloudmold_inventory_lot_source_mapping b
  ON b.tenant_id=a.tenant_id AND b.source_system=a.source_system
 AND b.source_type=a.source_type AND b.source_id=a.source_id
 AND b.mapping_id>a.mapping_id
 AND a.valid_from < COALESCE(b.valid_to,'9999-12-31 23:59:59.999999')
 AND b.valid_from < COALESCE(a.valid_to,'9999-12-31 23:59:59.999999');

SELECT 'inventory_lot_mapping_target_identity_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot_source_mapping mapping
LEFT JOIN cloudmold_inventory_lot lot
  ON lot.tenant_id=mapping.tenant_id AND lot.lot_id=mapping.lot_id
WHERE lot.lot_id IS NULL OR mapping.lot_id=mapping.source_id;

SELECT 'inventory_lot_lifecycle_version_event_mismatch' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT lot.tenant_id,lot.lot_id,lot.version
  FROM cloudmold_inventory_lot lot
  LEFT JOIN cloudmold_event_outbox event
    ON event.tenant_id=lot.tenant_id
   AND event.event_type='inventory.lot.lifecycle.changed'
   AND event.aggregate_type='inventory_lot'
   AND event.aggregate_id COLLATE utf8mb4_unicode_ci=lot.lot_id
  GROUP BY lot.tenant_id,lot.lot_id,lot.version
  HAVING COUNT(event.event_id)<>lot.version
      OR COUNT(DISTINCT event.aggregate_version)<>lot.version
      OR MIN(event.aggregate_version)<>1 OR MAX(event.aggregate_version)<>lot.version
) violation;

SELECT 'inventory_lot_mapping_version_event_mismatch' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT mapping.tenant_id,mapping.mapping_id,mapping.version
  FROM cloudmold_inventory_lot_source_mapping mapping
  LEFT JOIN cloudmold_event_outbox event
    ON event.tenant_id=mapping.tenant_id
   AND event.event_type='inventory.lot.source_mapping.changed'
   AND event.aggregate_type='inventory_lot_source_mapping'
   AND event.aggregate_id COLLATE utf8mb4_unicode_ci=mapping.mapping_id
  GROUP BY mapping.tenant_id,mapping.mapping_id,mapping.version
  HAVING COUNT(event.event_id)<>mapping.version
      OR COUNT(DISTINCT event.aggregate_version)<>mapping.version
      OR MIN(event.aggregate_version)<>1 OR MAX(event.aggregate_version)<>mapping.version
) violation;

SELECT 'inventory_lot_closed_nonzero_quantity' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot lot
JOIN cloudmold_inventory_balance_v3 balance
  ON balance.tenant_id=lot.tenant_id AND balance.lot_id=lot.lot_id
WHERE lot.status='CLOSED'
  AND (balance.on_hand_quantity<>0 OR balance.reserved_quantity<>0 OR balance.in_transit_quantity<>0);

SELECT 'inventory_lot_closed_active_allocation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot lot
JOIN cloudmold_inventory_balance_v3 balance
  ON balance.tenant_id=lot.tenant_id AND balance.lot_id=lot.lot_id
JOIN cloudmold_inventory_reservation_allocation_v3 allocation
  ON allocation.tenant_id=balance.tenant_id AND allocation.balance_id=balance.balance_id
WHERE lot.status='CLOSED' AND allocation.status=10;

SELECT 'inventory_lot_recalled_or_expired_reserved_exceeds_onhand' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot lot
JOIN cloudmold_inventory_balance_v3 balance
  ON balance.tenant_id=lot.tenant_id AND balance.lot_id=lot.lot_id
WHERE (lot.status='RECALLED' OR lot.expires_on<UTC_DATE())
  AND balance.reserved_quantity>balance.on_hand_quantity;

SELECT 'inventory_lot_fake_identity_sentinel' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_lot
WHERE UPPER(lot_code) IN ('NO_LOT','DEFAULT','UNKNOWN','N/A','NULL')
   OR lot_code REGEXP '^[[:space:]]*$';

SELECT 'inventory_lot_legacy_balance_implicitly_migrated' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_bridge
WHERE resolution_status='RESOLVED' AND (
  target_balance_id IS NULL OR source_system IS NULL OR source_type IS NULL OR source_id IS NULL);
