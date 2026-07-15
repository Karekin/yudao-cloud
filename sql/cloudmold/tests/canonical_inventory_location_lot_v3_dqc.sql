-- Every row must return zero violations after Inventory v3 migration/reconciliation.

SELECT 'v3_location_belongs_to_warehouse' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_balance_v3 b
LEFT JOIN cloudmold_warehouse w
  ON w.tenant_id = b.tenant_id AND w.warehouse_id = b.warehouse_id
LEFT JOIN cloudmold_warehouse_location l
  ON l.tenant_id = b.tenant_id
 AND l.warehouse_id = b.warehouse_id
 AND l.location_id = b.location_id
WHERE w.warehouse_id IS NULL OR l.location_id IS NULL
UNION ALL
SELECT 'v3_lot_matches_owner_and_sku', COUNT(*)
FROM cloudmold_inventory_balance_v3 b
JOIN cloudmold_inventory_lot l
  ON l.tenant_id = b.tenant_id AND l.lot_id = b.lot_id
WHERE l.owner_type <> b.owner_type
   OR l.owner_id <> b.owner_id
   OR l.canonical_sku_id <> b.canonical_sku_id
UNION ALL
SELECT 'v3_no_empty_or_fake_lot_id', COUNT(*)
FROM cloudmold_inventory_balance_v3
WHERE lot_id IS NOT NULL AND CHAR_LENGTH(TRIM(lot_id)) = 0
UNION ALL
SELECT 'v3_active_allocations_match_reserved', COUNT(*)
FROM (
  SELECT b.tenant_id, b.balance_id
  FROM cloudmold_inventory_balance_v3 b
  LEFT JOIN cloudmold_inventory_reservation_allocation_v3 a
    ON a.tenant_id = b.tenant_id AND a.balance_id = b.balance_id AND a.status = 10
  GROUP BY b.tenant_id, b.balance_id, b.reserved_quantity
  HAVING b.reserved_quantity <> COALESCE(SUM(a.quantity), 0)
) mismatch
UNION ALL
SELECT 'v3_reservation_allocation_totals', COUNT(*)
FROM (
  SELECT r.tenant_id, r.reservation_id
  FROM cloudmold_inventory_reservation_v3 r
  LEFT JOIN cloudmold_inventory_reservation_allocation_v3 a
    ON a.tenant_id = r.tenant_id AND a.reservation_id = r.reservation_id
  GROUP BY r.tenant_id, r.reservation_id, r.quantity, r.status
  HAVING SUM(a.quantity) IS NULL
     OR SUM(a.quantity) <> r.quantity
     OR SUM(a.status = r.status) <> COUNT(a.allocation_id)
) mismatch
UNION ALL
SELECT 'v3_no_expired_or_recalled_active_allocation', COUNT(*)
FROM cloudmold_inventory_reservation_allocation_v3 a
JOIN cloudmold_inventory_balance_v3 b
  ON b.tenant_id = a.tenant_id AND b.balance_id = a.balance_id
JOIN cloudmold_inventory_lot l
  ON l.tenant_id = b.tenant_id AND l.lot_id = b.lot_id
WHERE a.status = 10
  AND (l.status = 'RECALLED' OR (l.expires_on IS NOT NULL AND l.expires_on < UTC_DATE()))
UNION ALL
SELECT 'v3_ledger_equations_and_dimensions', COUNT(*)
FROM cloudmold_inventory_ledger_entry_v3 e
JOIN cloudmold_inventory_balance_v3 b
  ON b.tenant_id = e.tenant_id AND b.balance_id = e.balance_id
JOIN cloudmold_inventory_ledger_transaction_v3 t
  ON t.tenant_id = e.tenant_id AND t.ledger_transaction_id = e.ledger_transaction_id
WHERE e.movement_group_id <> t.movement_group_id
   OR e.base_uom_code <> b.base_uom_code
   OR e.after_on_hand_quantity <> e.before_on_hand_quantity + e.delta_on_hand_quantity
   OR e.after_reserved_quantity <> e.before_reserved_quantity + e.delta_reserved_quantity
   OR e.after_in_transit_quantity <> e.before_in_transit_quantity + e.delta_in_transit_quantity
UNION ALL
SELECT 'v3_ledger_versions_and_balance', COUNT(*)
FROM (
  SELECT b.tenant_id, b.balance_id
  FROM cloudmold_inventory_balance_v3 b
  JOIN cloudmold_inventory_ledger_entry_v3 e
    ON e.tenant_id = b.tenant_id AND e.balance_id = b.balance_id
  GROUP BY b.tenant_id, b.balance_id, b.version,
           b.on_hand_quantity, b.reserved_quantity, b.in_transit_quantity
  HAVING MIN(e.aggregate_version) <> 1
     OR MAX(e.aggregate_version) <> b.version
     OR COUNT(*) <> b.version
     OR SUM(e.delta_on_hand_quantity) <> b.on_hand_quantity
     OR SUM(e.delta_reserved_quantity) <> b.reserved_quantity
     OR SUM(e.delta_in_transit_quantity) <> b.in_transit_quantity
) mismatch
UNION ALL
SELECT 'v3_ledger_version_has_inventory_outbox', COUNT(*)
FROM cloudmold_inventory_ledger_entry_v3 e
LEFT JOIN cloudmold_event_outbox o
  ON o.tenant_id = e.tenant_id
 AND o.event_type = 'inventory.stock.changed'
 AND o.schema_version IN (3,4)
 AND o.aggregate_type = 'inventory_balance_v3'
 AND BINARY o.aggregate_id = BINARY e.balance_id
 AND o.aggregate_version = e.aggregate_version
 AND o.event_sequence = 1
WHERE o.event_id IS NULL
UNION ALL
SELECT 'v3_outbox_canonical_dimensions', COUNT(*)
FROM cloudmold_event_outbox o
JOIN cloudmold_inventory_balance_v3 b
  ON b.tenant_id = o.tenant_id AND BINARY b.balance_id = BINARY o.aggregate_id
WHERE o.event_type = 'inventory.stock.changed'
  AND o.schema_version = 3
  AND o.aggregate_type = 'inventory_balance_v3'
  AND (
    BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.owner_type')) <> BINARY b.owner_type
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.owner_id')) <> BINARY b.owner_id
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.canonical_sku_id')) <> BINARY b.canonical_sku_id
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.warehouse_id')) <> BINARY b.warehouse_id
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.location_id')) <> BINARY b.location_id
    OR NOT (BINARY NULLIF(JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.lot_id')), 'null') <=> BINARY b.lot_id)
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.stock_status')) <> BINARY b.stock_status
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.quality_status')) <> BINARY b.quality_status
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.base_uom_code')) <> BINARY b.base_uom_code
    OR JSON_EXTRACT(o.payload, '$.ledger_transaction_id') IS NULL
    OR JSON_EXTRACT(o.payload, '$.movement_group_id') IS NULL
  )
UNION ALL
SELECT 'v3_lot_source_mapping_identity', COUNT(*)
FROM cloudmold_inventory_lot_source_mapping
WHERE CHAR_LENGTH(TRIM(source_system)) = 0
   OR CHAR_LENGTH(TRIM(source_type)) = 0
   OR CHAR_LENGTH(TRIM(source_id)) = 0
   OR CHAR_LENGTH(TRIM(verification_ref)) = 0
   OR BINARY source_system <> BINARY UPPER(TRIM(source_system))
   OR BINARY source_type <> BINARY UPPER(TRIM(source_type))
   OR BINARY source_id <> BINARY TRIM(source_id)
   OR lot_id = source_id
UNION ALL
SELECT 'v3_migration_bridge_reconciled', COUNT(*)
FROM cloudmold_inventory_migration_bridge m
LEFT JOIN cloudmold_inventory_balance_v3 b
  ON b.tenant_id = m.tenant_id AND b.balance_id = m.target_balance_id
WHERE m.resolution_status = 'RESOLVED'
  AND (b.balance_id IS NULL
    OR m.source_on_hand_quantity <> m.target_on_hand_quantity
    OR m.source_reserved_quantity <> m.target_reserved_quantity
    OR m.source_in_transit_quantity <> m.target_in_transit_quantity);
