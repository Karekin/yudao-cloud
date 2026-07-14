SELECT 'active_reservation_matches_balance' AS check_name, COUNT(*) AS violations
FROM (
  SELECT b.tenant_id, b.balance_id
  FROM cloudmold_inventory_balance b
  LEFT JOIN cloudmold_inventory_reservation r
    ON r.tenant_id = b.tenant_id AND r.balance_id = b.balance_id AND r.status = 10
  GROUP BY b.tenant_id, b.balance_id, b.reserved_quantity
  HAVING b.reserved_quantity <> COALESCE(SUM(r.quantity), 0)
) mismatch
UNION ALL
SELECT 'ledger_versions_and_balance', COUNT(*)
FROM (
  SELECT b.tenant_id, b.balance_id
  FROM cloudmold_inventory_balance b
  JOIN cloudmold_inventory_ledger_entry e
    ON e.tenant_id = b.tenant_id AND e.balance_id = b.balance_id
  GROUP BY b.tenant_id, b.balance_id, b.version, b.on_hand_quantity, b.reserved_quantity
  HAVING MIN(e.aggregate_version) <> 1
     OR MAX(e.aggregate_version) <> b.version
     OR COUNT(*) <> b.version
     OR SUM(e.delta_on_hand_quantity) <> b.on_hand_quantity
     OR SUM(e.delta_reserved_quantity) <> b.reserved_quantity
) mismatch
UNION ALL
SELECT 'ledger_version_has_outbox', COUNT(*)
FROM cloudmold_inventory_ledger_entry e
LEFT JOIN cloudmold_event_outbox o
  ON o.tenant_id = e.tenant_id
 AND o.aggregate_type = 'inventory_balance'
 AND o.aggregate_id = e.balance_id
 AND o.aggregate_version = e.aggregate_version
 AND o.event_sequence = 1
WHERE o.event_id IS NULL;
