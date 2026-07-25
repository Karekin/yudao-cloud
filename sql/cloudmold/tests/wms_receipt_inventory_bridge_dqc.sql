-- Every result column must be zero.

SELECT 'wms_receipt_inventory_bridge_table_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT 1
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'cloudmold_wms_receipt_inventory_bridge'
    )
) missing_table;

SELECT 'wms_receipt_inventory_bridge_required_column_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT 'tenant_id' AS column_name
    UNION ALL SELECT 'receipt_order_id'
    UNION ALL SELECT 'receipt_order_line_id'
    UNION ALL SELECT 'wms_merchant_id'
    UNION ALL SELECT 'wms_warehouse_id'
    UNION ALL SELECT 'wms_sku_id'
    UNION ALL SELECT 'canonical_owner_id'
    UNION ALL SELECT 'canonical_sku_id'
    UNION ALL SELECT 'warehouse_mapping_id'
    UNION ALL SELECT 'canonical_warehouse_id'
    UNION ALL SELECT 'canonical_zone_id'
    UNION ALL SELECT 'canonical_location_id'
    UNION ALL SELECT 'lot_mapping_status'
    UNION ALL SELECT 'receipt_quantity'
    UNION ALL SELECT 'base_uom_code'
    UNION ALL SELECT 'inventory_idempotency_key'
    UNION ALL SELECT 'inventory_source_event_id'
    UNION ALL SELECT 'inventory_business_id'
    UNION ALL SELECT 'inventory_business_item_id'
    UNION ALL SELECT 'inventory_operation_id'
    UNION ALL SELECT 'inventory_ledger_transaction_id'
    UNION ALL SELECT 'inventory_balance_id'
    UNION ALL SELECT 'inventory_aggregate_version'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'cloudmold_wms_receipt_inventory_bridge'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;

SELECT 'wms_receipt_inventory_bridge_required_column_nullable' AS check_name,
       COUNT(*) AS violation_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_wms_receipt_inventory_bridge'
  AND column_name IN (
      'tenant_id','receipt_order_id','receipt_order_line_id',
      'wms_merchant_id','wms_warehouse_id','wms_sku_id',
      'canonical_owner_id','canonical_sku_id',
      'warehouse_mapping_id','canonical_warehouse_id',
      'canonical_zone_id','canonical_location_id',
      'lot_mapping_status','receipt_quantity','base_uom_code',
      'inventory_idempotency_key','inventory_source_event_id',
      'inventory_business_id','inventory_business_item_id',
      'inventory_operation_id','inventory_ledger_transaction_id',
      'inventory_balance_id','inventory_aggregate_version'
  )
  AND is_nullable <> 'NO';

SELECT 'wms_receipt_inventory_bridge_unique_line_constraint_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT 1
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'cloudmold_wms_receipt_inventory_bridge'
          AND index_name = 'uk_wms_receipt_inventory_bridge_line'
        GROUP BY index_name
        HAVING SUM(CASE WHEN column_name = 'tenant_id' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN column_name = 'receipt_order_id' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN column_name = 'receipt_order_line_id' THEN 1 ELSE 0 END) = 1
    )
) missing_unique_line;

SELECT 'wms_receipt_inventory_bridge_unique_inventory_key_constraint_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT 1
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'cloudmold_wms_receipt_inventory_bridge'
          AND index_name = 'uk_wms_receipt_inventory_bridge_inventory_key'
        GROUP BY index_name
        HAVING SUM(CASE WHEN column_name = 'tenant_id' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN column_name = 'inventory_idempotency_key' THEN 1 ELSE 0 END) = 1
    )
) missing_unique_inventory_key;

SELECT 'wms_receipt_inventory_bridge_balance_index_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
    SELECT 1
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'cloudmold_wms_receipt_inventory_bridge'
          AND index_name = 'idx_wms_receipt_inventory_bridge_balance'
        GROUP BY index_name
        HAVING SUM(CASE WHEN column_name = 'tenant_id' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN column_name = 'inventory_balance_id' THEN 1 ELSE 0 END) = 1
    )
) missing_balance_index;

SET @wms_receipt_bridge_table_exists := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'cloudmold_wms_receipt_inventory_bridge'
);

SET @tenant_semantics_sql := IF(
    @wms_receipt_bridge_table_exists = 1,
    "SELECT 'wms_receipt_inventory_bridge_tenant_semantics_invalid' AS check_name, COUNT(*) AS violation_count
     FROM cloudmold_wms_receipt_inventory_bridge
     WHERE tenant_id IS NULL
        OR tenant_id <= 0
        OR CHAR_LENGTH(TRIM(canonical_owner_id)) = 0
        OR CHAR_LENGTH(TRIM(canonical_sku_id)) = 0
        OR CHAR_LENGTH(TRIM(warehouse_mapping_id)) = 0
        OR CHAR_LENGTH(TRIM(canonical_warehouse_id)) = 0
        OR CHAR_LENGTH(TRIM(canonical_zone_id)) = 0
        OR CHAR_LENGTH(TRIM(canonical_location_id)) = 0
        OR CHAR_LENGTH(TRIM(base_uom_code)) = 0
        OR CHAR_LENGTH(TRIM(inventory_idempotency_key)) = 0
        OR CHAR_LENGTH(TRIM(inventory_source_event_id)) = 0
        OR CHAR_LENGTH(TRIM(inventory_business_id)) = 0
        OR CHAR_LENGTH(TRIM(inventory_business_item_id)) = 0",
    "SELECT 'wms_receipt_inventory_bridge_tenant_semantics_invalid' AS check_name, 0 AS violation_count"
);
PREPARE wms_receipt_bridge_tenant_semantics_stmt FROM @tenant_semantics_sql;
EXECUTE wms_receipt_bridge_tenant_semantics_stmt;
DEALLOCATE PREPARE wms_receipt_bridge_tenant_semantics_stmt;

SET @exactly_once_sql := IF(
    @wms_receipt_bridge_table_exists = 1,
    "SELECT 'wms_receipt_inventory_bridge_exactly_once_duplicate_violation' AS check_name, COUNT(*) AS violation_count
     FROM (
         SELECT tenant_id, receipt_order_id, receipt_order_line_id, inventory_idempotency_key
         FROM cloudmold_wms_receipt_inventory_bridge
         GROUP BY tenant_id, receipt_order_id, receipt_order_line_id, inventory_idempotency_key
         HAVING COUNT(*) > 1
     ) duplicate_rows",
    "SELECT 'wms_receipt_inventory_bridge_exactly_once_duplicate_violation' AS check_name, 0 AS violation_count"
);
PREPARE wms_receipt_bridge_exactly_once_stmt FROM @exactly_once_sql;
EXECUTE wms_receipt_bridge_exactly_once_stmt;
DEALLOCATE PREPARE wms_receipt_bridge_exactly_once_stmt;

SET @lot_quantity_sql := IF(
    @wms_receipt_bridge_table_exists = 1,
    "SELECT 'wms_receipt_inventory_bridge_lot_and_quantity_invalid' AS check_name, COUNT(*) AS violation_count
     FROM cloudmold_wms_receipt_inventory_bridge
     WHERE receipt_quantity <= 0
        OR inventory_operation_id <= 0
        OR inventory_ledger_transaction_id <= 0
        OR inventory_aggregate_version <= 0
        OR lot_mapping_status NOT IN ('NOT_TRACKED', 'RESOLVED')
        OR (lot_mapping_status = 'NOT_TRACKED' AND canonical_lot_id IS NOT NULL)
        OR (lot_mapping_status = 'RESOLVED' AND canonical_lot_id IS NULL)",
    "SELECT 'wms_receipt_inventory_bridge_lot_and_quantity_invalid' AS check_name, 0 AS violation_count"
);
PREPARE wms_receipt_bridge_lot_quantity_stmt FROM @lot_quantity_sql;
EXECUTE wms_receipt_bridge_lot_quantity_stmt;
DEALLOCATE PREPARE wms_receipt_bridge_lot_quantity_stmt;
