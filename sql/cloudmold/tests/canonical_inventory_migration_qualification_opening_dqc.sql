-- Every result column must be zero. Qualification is restricted to controlled canaries and is the sole opening driver.

SELECT 'inventory_migration_qualification_non_canary' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_inventory_migration_candidate candidate
  ON candidate.tenant_id=qualification.tenant_id
 AND BINARY candidate.candidate_id=BINARY qualification.candidate_id
WHERE candidate.source_classification<>'CONTROLLED_CANARY'
   OR candidate.decision_status<>'BLOCKED';

SELECT 'inventory_migration_qualification_source_snapshot_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_inventory_migration_candidate candidate
  ON candidate.tenant_id=qualification.tenant_id
 AND BINARY candidate.candidate_id=BINARY qualification.candidate_id
LEFT JOIN cloudmold_inventory_balance source_balance
  ON source_balance.tenant_id=qualification.tenant_id
 AND BINARY source_balance.balance_id=BINARY qualification.source_id
WHERE source_balance.balance_id IS NULL
   OR BINARY qualification.migration_run_id<>BINARY candidate.migration_run_id
   OR BINARY qualification.source_id<>BINARY candidate.source_id
   OR BINARY qualification.source_snapshot_hash<>BINARY candidate.legacy_snapshot_hash
   OR qualification.source_version<>candidate.legacy_balance_version
   OR qualification.source_updated_at<>candidate.source_updated_at
   OR qualification.source_version<>source_balance.version
   OR qualification.source_updated_at<>source_balance.updated_at
   OR qualification.source_on_hand_quantity<>source_balance.on_hand_quantity
   OR qualification.source_reserved_quantity<>source_balance.reserved_quantity
   OR qualification.source_in_transit_quantity<>source_balance.in_transit_quantity;

SELECT 'inventory_migration_qualification_dimension_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_warehouse_source_mapping mapping
  ON mapping.tenant_id=qualification.tenant_id
 AND BINARY mapping.mapping_id=BINARY qualification.warehouse_source_mapping_id
JOIN cloudmold_warehouse_location location
  ON location.tenant_id=qualification.tenant_id
 AND BINARY location.location_id=BINARY qualification.location_id
LEFT JOIN cloudmold_inventory_lot lot
  ON lot.tenant_id=qualification.tenant_id
 AND BINARY lot.lot_id=BINARY qualification.lot_id
WHERE mapping.source_system<>'CLOUDMOLD_INVENTORY_V1'
   OR mapping.source_type<>'WAREHOUSE'
   OR mapping.canonical_type<>'WAREHOUSE'
   OR BINARY mapping.canonical_id<>BINARY qualification.warehouse_id
   OR BINARY location.warehouse_id<>BINARY qualification.warehouse_id
   OR (qualification.lot_tracking_policy='NOT_TRACKED' AND qualification.lot_id IS NOT NULL)
   OR (qualification.lot_tracking_policy='TRACKED' AND (
        lot.lot_id IS NULL OR lot.status<>'ACTIVE' OR lot.owner_type<>'MERCHANT'
        OR BINARY lot.owner_id<>BINARY qualification.owner_id
        OR BINARY lot.canonical_sku_id<>BINARY qualification.canonical_sku_id));

SELECT 'inventory_migration_qualification_event_cardinality' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT qualification.tenant_id,qualification.qualification_id,COUNT(event.event_id) AS event_count
  FROM cloudmold_inventory_migration_qualification qualification
  LEFT JOIN cloudmold_event_outbox event
    ON event.tenant_id=qualification.tenant_id
   AND event.event_type='inventory.migration.balance_qualified'
   AND event.schema_version=1
   AND BINARY event.aggregate_id=BINARY qualification.qualification_id
   AND event.aggregate_version=1
  GROUP BY qualification.tenant_id,qualification.qualification_id
  HAVING COUNT(event.event_id)<>1
) mismatch;

SELECT 'inventory_migration_opening_cardinality' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT qualification.tenant_id,qualification.qualification_id,
         COUNT(DISTINCT operation.operation_id) AS operation_count,
         COUNT(DISTINCT transaction.ledger_transaction_id) AS transaction_count,
         COUNT(DISTINCT entry.ledger_entry_id) AS entry_count,
         COUNT(DISTINCT bridge.bridge_id) AS bridge_count,
         COUNT(DISTINCT event.event_id) AS event_count
  FROM cloudmold_inventory_migration_qualification qualification
  LEFT JOIN cloudmold_inventory_operation_v3 operation
    ON operation.tenant_id=qualification.tenant_id
   AND operation.operation_id=qualification.opening_operation_id
   AND operation.command_type='MIGRATION_OPENING' AND operation.status=10
  LEFT JOIN cloudmold_inventory_ledger_transaction_v3 transaction
    ON transaction.tenant_id=qualification.tenant_id
   AND transaction.ledger_transaction_id=qualification.ledger_transaction_id
   AND transaction.operation_id=qualification.opening_operation_id
   AND transaction.command_type='MIGRATION_OPENING'
  LEFT JOIN cloudmold_inventory_ledger_entry_v3 entry
    ON entry.tenant_id=qualification.tenant_id
   AND entry.ledger_transaction_id=qualification.ledger_transaction_id
   AND BINARY entry.balance_id=BINARY qualification.target_balance_id
  LEFT JOIN cloudmold_inventory_migration_bridge bridge
    ON bridge.tenant_id=qualification.tenant_id
   AND BINARY bridge.bridge_id=BINARY qualification.bridge_id
   AND bridge.resolution_status='RESOLVED'
  LEFT JOIN cloudmold_event_outbox event
    ON event.tenant_id=qualification.tenant_id
   AND event.event_type='inventory.stock.changed' AND event.schema_version=4
   AND BINARY event.aggregate_id=BINARY qualification.target_balance_id
   AND event.aggregate_version=1
   AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.migration_qualification_id'))
       =BINARY qualification.qualification_id
  WHERE qualification.status='MIGRATED'
  GROUP BY qualification.tenant_id,qualification.qualification_id
  HAVING operation_count<>1 OR transaction_count<>1 OR entry_count<>1 OR bridge_count<>1 OR event_count<>1
) mismatch;

SELECT 'inventory_migration_opening_quantity_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_inventory_ledger_entry_v3 entry
  ON entry.tenant_id=qualification.tenant_id
 AND entry.ledger_transaction_id=qualification.ledger_transaction_id
 AND BINARY entry.balance_id=BINARY qualification.target_balance_id
JOIN cloudmold_inventory_migration_bridge bridge
  ON bridge.tenant_id=qualification.tenant_id
 AND BINARY bridge.bridge_id=BINARY qualification.bridge_id
WHERE qualification.status='MIGRATED'
  AND (entry.aggregate_version<>1 OR entry.entry_role<>'SINGLE'
    OR entry.before_on_hand_quantity<>0 OR entry.delta_on_hand_quantity<>qualification.source_on_hand_quantity
    OR entry.after_on_hand_quantity<>qualification.source_on_hand_quantity
    OR entry.before_reserved_quantity<>0 OR entry.delta_reserved_quantity<>0 OR entry.after_reserved_quantity<>0
    OR entry.before_in_transit_quantity<>0 OR entry.delta_in_transit_quantity<>0 OR entry.after_in_transit_quantity<>0
    OR bridge.source_on_hand_quantity<>qualification.source_on_hand_quantity
    OR bridge.target_on_hand_quantity<>qualification.source_on_hand_quantity
    OR bridge.source_reserved_quantity<>0 OR bridge.target_reserved_quantity<>0
    OR bridge.source_in_transit_quantity<>0 OR bridge.target_in_transit_quantity<>0);

-- A resolved migration bridge is the v1 write fence. Version comparison is used instead of
-- timestamps so an old writer that started before MIGRATE but waited on the balance lock is
-- still detected after it commits.
SELECT 'inventory_migration_legacy_source_frozen' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_inventory_migration_bridge bridge
  ON bridge.tenant_id=qualification.tenant_id
 AND BINARY bridge.bridge_id=BINARY qualification.bridge_id
 AND bridge.resolution_status='RESOLVED'
JOIN cloudmold_inventory_balance source_balance
  ON source_balance.tenant_id=qualification.tenant_id
 AND BINARY source_balance.balance_id=BINARY qualification.source_id
WHERE qualification.status='MIGRATED'
  AND (source_balance.version<>qualification.source_version
    OR source_balance.updated_at<>qualification.source_updated_at
    OR source_balance.on_hand_quantity<>qualification.source_on_hand_quantity
    OR source_balance.reserved_quantity<>qualification.source_reserved_quantity
    OR source_balance.in_transit_quantity<>qualification.source_in_transit_quantity
    OR EXISTS (
      SELECT 1
      FROM cloudmold_inventory_ledger_entry legacy_entry
      WHERE legacy_entry.tenant_id=qualification.tenant_id
        AND BINARY legacy_entry.balance_id=BINARY qualification.source_id
        AND legacy_entry.aggregate_version>qualification.source_version
    ));

SELECT 'inventory_migration_opening_event_payload_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_event_outbox event
  ON event.tenant_id=qualification.tenant_id
 AND event.event_type='inventory.stock.changed' AND event.schema_version=4
 AND BINARY event.aggregate_id=BINARY qualification.target_balance_id
WHERE qualification.status='MIGRATED'
  AND (JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.movement_type'))<>'MIGRATION_OPENING'
    OR JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.opening_driver'))<>'INVENTORY_MIGRATION_QUALIFICATION'
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.migration_run_id'))<>BINARY qualification.migration_run_id
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.migration_qualification_id'))<>BINARY qualification.qualification_id
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.source_snapshot_hash'))<>BINARY qualification.source_snapshot_hash
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.delta_on_hand_quantity')) AS DECIMAL(24,6))
       <>qualification.source_on_hand_quantity
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.delta_reserved_quantity')) AS DECIMAL(24,6))<>0
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.delta_in_transit_quantity')) AS DECIMAL(24,6))<>0);

SELECT 'inventory_migration_existing_controlled_history_qualified' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inventory_migration_qualification qualification
JOIN cloudmold_inventory_migration_candidate candidate
  ON candidate.tenant_id=qualification.tenant_id
 AND BINARY candidate.candidate_id=BINARY qualification.candidate_id
WHERE candidate.source_classification IN ('CONTROLLED_FIXTURE','CONTROLLED_SCENARIO','CONCURRENCY_PROBE');
