CREATE TABLE `cloudmold_wms_receipt_inventory_bridge` (
  `bridge_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `receipt_order_id` bigint NOT NULL,
  `receipt_order_no` varchar(64) NOT NULL,
  `receipt_order_line_id` bigint NOT NULL,
  `wms_merchant_id` bigint NOT NULL,
  `wms_warehouse_id` bigint NOT NULL,
  `wms_sku_id` bigint NOT NULL,
  `canonical_owner_id` varchar(64) NOT NULL,
  `canonical_sku_id` varchar(64) NOT NULL,
  `warehouse_mapping_id` varchar(64) NOT NULL,
  `canonical_warehouse_id` varchar(64) NOT NULL,
  `canonical_zone_id` varchar(64) NOT NULL,
  `canonical_location_id` varchar(64) NOT NULL,
  `lot_mapping_status` varchar(32) NOT NULL,
  `canonical_lot_id` varchar(64) DEFAULT NULL,
  `receipt_quantity` decimal(24,6) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `inventory_idempotency_key` varchar(128) NOT NULL,
  `inventory_source_event_id` varchar(64) NOT NULL,
  `inventory_business_id` varchar(128) NOT NULL,
  `inventory_business_item_id` varchar(128) NOT NULL,
  `inventory_operation_id` bigint NOT NULL,
  `inventory_ledger_transaction_id` bigint NOT NULL,
  `inventory_balance_id` varchar(64) NOT NULL,
  `inventory_aggregate_version` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`bridge_id`),
  UNIQUE KEY `uk_wms_receipt_inventory_bridge_line`
    (`tenant_id`, `receipt_order_id`, `receipt_order_line_id`),
  UNIQUE KEY `uk_wms_receipt_inventory_bridge_inventory_key`
    (`tenant_id`, `inventory_idempotency_key`),
  KEY `idx_wms_receipt_inventory_bridge_balance`
    (`tenant_id`, `inventory_balance_id`),
  CONSTRAINT `chk_wms_receipt_inventory_bridge_lot_status`
    CHECK (`lot_mapping_status` IN ('NOT_TRACKED', 'RESOLVED')),
  CONSTRAINT `chk_wms_receipt_inventory_bridge_quantity`
    CHECK (`receipt_quantity` > 0),
  CONSTRAINT `chk_wms_receipt_inventory_bridge_lot_pair`
    CHECK (
      (`lot_mapping_status` = 'NOT_TRACKED' AND `canonical_lot_id` IS NULL)
      OR (`lot_mapping_status` = 'RESOLVED' AND `canonical_lot_id` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='WMS receipt to canonical inventory exactly-once evidence';
