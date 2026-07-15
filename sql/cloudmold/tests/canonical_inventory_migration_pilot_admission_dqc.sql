-- Production pilot admission must stay governance-only until shadow comparison and rollback exist.
SELECT 'inventory_migration_pilot_batch_state_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_batch b
WHERE NOT (
  (b.status='FROZEN' AND b.approval_count=0 AND b.version=1 AND b.executor_id IS NULL)
  OR (b.status='PARTIALLY_APPROVED' AND b.approval_count=1 AND b.version=2 AND b.executor_id IS NULL)
  OR (b.status='APPROVED' AND b.approval_count=2 AND b.version=3 AND b.executor_id IS NULL)
  OR (b.status='ADMISSION_PASSED' AND b.approval_count=2 AND b.version=4 AND b.executor_id IS NOT NULL)
);

SELECT 'inventory_migration_pilot_item_state_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
WHERE NOT ((i.status='FROZEN' AND i.version=1)
        OR (i.status='APPROVED' AND i.version=2)
        OR (i.status='ADMISSION_PASSED' AND i.version=3));

SELECT 'inventory_migration_pilot_denominator_mismatch' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_batch b
LEFT JOIN (
  SELECT tenant_id,batch_id,COUNT(*) item_count,SUM(source_on_hand_quantity) on_hand_quantity,
         SUM(status IN ('APPROVED','ADMISSION_PASSED')) approved_count,
         SUM(CASE WHEN status IN ('APPROVED','ADMISSION_PASSED') THEN source_on_hand_quantity ELSE 0 END) approved_quantity,
         SUM(status='ADMISSION_PASSED') admitted_count,
         SUM(CASE WHEN status='ADMISSION_PASSED' THEN source_on_hand_quantity ELSE 0 END) admitted_quantity
  FROM cloudmold_inventory_migration_pilot_item GROUP BY tenant_id,batch_id
) i ON i.tenant_id=b.tenant_id AND i.batch_id=b.batch_id
WHERE COALESCE(i.item_count,0)<>b.expected_item_count
   OR COALESCE(i.on_hand_quantity,0)<>b.expected_on_hand_quantity
   OR (b.status IN ('APPROVED','ADMISSION_PASSED')
       AND (i.approved_count<>b.expected_item_count OR i.approved_quantity<>b.expected_on_hand_quantity))
   OR (b.status='ADMISSION_PASSED'
       AND (i.admitted_count<>b.expected_item_count OR i.admitted_quantity<>b.expected_on_hand_quantity));

SELECT 'inventory_migration_pilot_approval_shape_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_batch b
LEFT JOIN (
  SELECT tenant_id,batch_id,COUNT(*) approval_count,COUNT(DISTINCT approval_role) role_count,
         COUNT(DISTINCT approver_id) actor_count,
         SUM(status='APPROVED') active_count
  FROM cloudmold_inventory_migration_pilot_approval GROUP BY tenant_id,batch_id
) a ON a.tenant_id=b.tenant_id AND a.batch_id=b.batch_id
WHERE COALESCE(a.approval_count,0)<>b.approval_count
   OR COALESCE(a.role_count,0)<>b.approval_count
   OR COALESCE(a.actor_count,0)<>b.approval_count
   OR COALESCE(a.active_count,0)<>b.approval_count;

SELECT 'inventory_migration_pilot_approval_scope_drift' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_approval a
JOIN cloudmold_inventory_migration_pilot_batch b
  ON b.tenant_id=a.tenant_id AND b.batch_id=a.batch_id
WHERE a.scope_hash<>b.manifest_hash OR a.policy_hash<>b.policy_hash
   OR a.approver_id=b.requester_id OR a.expires_at<=a.approved_at;

SELECT 'inventory_migration_pilot_actor_separation_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_batch b
JOIN cloudmold_inventory_migration_pilot_approval a
  ON a.tenant_id=b.tenant_id AND a.batch_id=b.batch_id
WHERE a.approver_id=b.requester_id
   OR (b.executor_id IS NOT NULL AND (b.executor_id=b.requester_id OR b.executor_id=a.approver_id));

SELECT 'inventory_migration_pilot_checkpoint_incomplete' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_batch b
LEFT JOIN (
  SELECT tenant_id,batch_id,COUNT(*) checkpoint_count,MAX(batch_version) max_version
  FROM cloudmold_inventory_migration_pilot_checkpoint GROUP BY tenant_id,batch_id
) c ON c.tenant_id=b.tenant_id AND c.batch_id=b.batch_id
WHERE COALESCE(c.checkpoint_count,0)<>b.version OR COALESCE(c.max_version,0)<>b.version;

SELECT 'inventory_migration_pilot_source_snapshot_drift' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
LEFT JOIN cloudmold_inventory_balance b
  ON b.tenant_id=i.tenant_id AND BINARY b.balance_id=BINARY i.legacy_balance_id
LEFT JOIN cloudmold_inventory_migration_shadow_window w
  ON w.tenant_id=i.tenant_id AND w.batch_id=i.batch_id
WHERE w.window_id IS NULL AND (b.balance_id IS NULL OR b.version<>i.source_version OR b.updated_at<>i.source_updated_at
   OR b.on_hand_quantity<>i.source_on_hand_quantity
   OR b.reserved_quantity<>i.source_reserved_quantity
   OR b.in_transit_quantity<>i.source_in_transit_quantity);

SELECT 'inventory_migration_pilot_active_reservation' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND BINARY r.balance_id=BINARY i.legacy_balance_id AND r.status=10;

SELECT 'inventory_migration_pilot_existing_bridge' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
JOIN cloudmold_inventory_migration_bridge b
  ON b.tenant_id=i.tenant_id AND b.legacy_balance_id=i.legacy_balance_id
 AND b.resolution_status='RESOLVED';

SELECT 'inventory_migration_pilot_existing_qualification' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
JOIN cloudmold_inventory_migration_qualification q
  ON q.tenant_id=i.tenant_id AND q.source_system='CLOUDMOLD_INVENTORY_V1'
 AND q.source_type='BALANCE' AND q.source_id=i.legacy_balance_id;

SELECT 'inventory_migration_pilot_existing_target_balance' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
JOIN cloudmold_inventory_balance_v3 b
  ON b.tenant_id=i.tenant_id AND b.owner_type=i.owner_type AND b.owner_id=i.owner_id
 AND b.canonical_sku_id=i.canonical_sku_id AND b.warehouse_id=i.warehouse_id
 AND b.location_id=i.location_id AND (b.lot_id=i.lot_id OR (b.lot_id IS NULL AND i.lot_id IS NULL))
 AND b.stock_status=i.stock_status AND b.quality_status=i.quality_status;

SELECT 'inventory_migration_pilot_scope_outside_minimum' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_item i
WHERE i.owner_type<>'MERCHANT' OR i.source_on_hand_quantity<=0
   OR i.source_reserved_quantity<>0 OR i.source_in_transit_quantity<>0
   OR i.active_reservation_count<>0 OR i.active_reservation_quantity<>0
   OR i.source_uom_code<>i.base_uom_code OR i.uom_conversion_ratio<>1
   OR i.target_balance_absent<>1 OR i.bridge_absent<>1;

SELECT 'inventory_migration_pilot_execution_leak' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_pilot_batch b
JOIN cloudmold_inventory_migration_pilot_item i
  ON i.tenant_id=b.tenant_id AND i.batch_id=b.batch_id
JOIN cloudmold_inventory_ledger_transaction_v3 t
  ON t.tenant_id=i.tenant_id AND t.command_type='MIGRATION_OPENING'
JOIN cloudmold_inventory_ledger_entry_v3 e
  ON e.tenant_id=t.tenant_id AND e.ledger_transaction_id=t.ledger_transaction_id
WHERE e.balance_id IN (
  SELECT target.balance_id FROM cloudmold_inventory_balance_v3 target
  WHERE target.tenant_id=i.tenant_id AND target.owner_type=i.owner_type AND target.owner_id=i.owner_id
    AND target.canonical_sku_id=i.canonical_sku_id AND target.warehouse_id=i.warehouse_id
    AND target.location_id=i.location_id
);
