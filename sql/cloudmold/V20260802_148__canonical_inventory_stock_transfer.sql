-- Canonical Inventory v3 warehouse-to-warehouse stock-transfer ledger.
-- The movement group freezes exact stock identity and supports independent idempotent partial dispatch/receipt.

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_stock_transfer_operation_v3` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(36) DEFAULT NULL,
  `movement_group_id` varchar(36) NOT NULL,
  `operation_type` varchar(16) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `ledger_transaction_id` bigint DEFAULT NULL,
  `source_balance_id` varchar(36) DEFAULT NULL,
  `source_aggregate_version` bigint DEFAULT NULL,
  `source_on_hand_quantity` decimal(24,6) DEFAULT NULL,
  `source_in_transit_quantity` decimal(24,6) DEFAULT NULL,
  `target_balance_id` varchar(36) DEFAULT NULL,
  `target_aggregate_version` bigint DEFAULT NULL,
  `target_on_hand_quantity` decimal(24,6) DEFAULT NULL,
  `target_in_transit_quantity` decimal(24,6) DEFAULT NULL,
  `cumulative_dispatched_quantity` decimal(24,6) DEFAULT NULL,
  `cumulative_received_quantity` decimal(24,6) DEFAULT NULL,
  `outstanding_quantity` decimal(24,6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_inv_st_op_tenant_id` (`tenant_id`,`operation_id`),
  UNIQUE KEY `uk_cm_inv_st_op_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_inv_st_op_source_event` (`tenant_id`,`source_event_id`),
  KEY `idx_cm_inv_st_op_group` (`tenant_id`,`movement_group_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_st_op_source_balance` FOREIGN KEY (`tenant_id`,`source_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `fk_cm_inv_st_op_target_balance` FOREIGN KEY (`tenant_id`,`target_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `ck_cm_inv_st_op_type` CHECK (`operation_type` IN ('DISPATCH','RECEIVE')),
  CONSTRAINT `ck_cm_inv_st_op_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_inv_st_op_versions` CHECK (
    (`source_aggregate_version` IS NULL OR `source_aggregate_version` >= 1)
    AND (`target_aggregate_version` IS NULL OR `target_aggregate_version` >= 0)
  ),
  CONSTRAINT `ck_cm_inv_st_op_quantities` CHECK (
    (`source_on_hand_quantity` IS NULL OR `source_on_hand_quantity` >= 0)
    AND (`source_in_transit_quantity` IS NULL OR `source_in_transit_quantity` >= 0)
    AND (`target_on_hand_quantity` IS NULL OR `target_on_hand_quantity` >= 0)
    AND (`target_in_transit_quantity` IS NULL OR `target_in_transit_quantity` >= 0)
    AND (`cumulative_dispatched_quantity` IS NULL OR `cumulative_dispatched_quantity` >= 0)
    AND (`cumulative_received_quantity` IS NULL OR `cumulative_received_quantity` >= 0)
    AND (`outstanding_quantity` IS NULL OR `outstanding_quantity` >= 0)
  ),
  CONSTRAINT `ck_cm_inv_st_op_result_shape` CHECK (
    (`status` = 0
      AND `ledger_transaction_id` IS NULL
      AND `source_balance_id` IS NULL AND `source_aggregate_version` IS NULL
      AND `source_on_hand_quantity` IS NULL AND `source_in_transit_quantity` IS NULL
      AND `target_balance_id` IS NULL AND `target_aggregate_version` IS NULL
      AND `target_on_hand_quantity` IS NULL AND `target_in_transit_quantity` IS NULL
      AND `cumulative_dispatched_quantity` IS NULL AND `cumulative_received_quantity` IS NULL
      AND `outstanding_quantity` IS NULL)
    OR
    (`status` = 10
      AND `ledger_transaction_id` IS NOT NULL
      AND `source_balance_id` IS NOT NULL AND `source_aggregate_version` IS NOT NULL
      AND `source_on_hand_quantity` IS NOT NULL AND `source_in_transit_quantity` IS NOT NULL
      AND `target_balance_id` IS NOT NULL AND `target_aggregate_version` IS NOT NULL
      AND `target_on_hand_quantity` IS NOT NULL AND `target_in_transit_quantity` IS NOT NULL
      AND `cumulative_dispatched_quantity` IS NOT NULL AND `cumulative_received_quantity` IS NOT NULL
      AND `outstanding_quantity` IS NOT NULL
      AND `cumulative_received_quantity` <= `cumulative_dispatched_quantity`
      AND `outstanding_quantity` = `cumulative_dispatched_quantity` - `cumulative_received_quantity`)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_stock_transfer_v3` (
  `movement_group_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `source_warehouse_id` varchar(36) NOT NULL,
  `source_location_id` varchar(36) NOT NULL,
  `target_warehouse_id` varchar(36) NOT NULL,
  `target_location_id` varchar(36) NOT NULL,
  `lot_id` varchar(36) DEFAULT NULL,
  `stock_status` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `dispatched_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `received_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 0,
  `created_operation_id` bigint NOT NULL,
  `last_operation_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`tenant_id`,`movement_group_id`),
  KEY `idx_cm_inv_st_source` (`tenant_id`,`source_warehouse_id`,`source_location_id`,`canonical_sku_id`),
  KEY `idx_cm_inv_st_target` (`tenant_id`,`target_warehouse_id`,`target_location_id`,`canonical_sku_id`),
  CONSTRAINT `fk_cm_inv_st_source_wh` FOREIGN KEY (`tenant_id`,`source_warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_inv_st_source_loc` FOREIGN KEY (`tenant_id`,`source_location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `fk_cm_inv_st_target_wh` FOREIGN KEY (`tenant_id`,`target_warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_inv_st_target_loc` FOREIGN KEY (`tenant_id`,`target_location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `fk_cm_inv_st_lot` FOREIGN KEY (`tenant_id`,`lot_id`)
    REFERENCES `cloudmold_inventory_lot` (`tenant_id`,`lot_id`),
  CONSTRAINT `fk_cm_inv_st_created_op` FOREIGN KEY (`tenant_id`,`created_operation_id`)
    REFERENCES `cloudmold_inventory_stock_transfer_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_st_last_op` FOREIGN KEY (`tenant_id`,`last_operation_id`)
    REFERENCES `cloudmold_inventory_stock_transfer_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `ck_cm_inv_st_owner_type` CHECK (`owner_type` IN ('MERCHANT','PLATFORM','MEMBER')),
  CONSTRAINT `ck_cm_inv_st_status` CHECK (`stock_status` IN ('SELLABLE','NON_SELLABLE')),
  CONSTRAINT `ck_cm_inv_st_quality` CHECK (`quality_status` IN ('PENDING_QC','QUALIFIED','DAMAGED','REJECTED')),
  CONSTRAINT `ck_cm_inv_st_sellable_quality` CHECK (`stock_status` <> 'SELLABLE' OR `quality_status` = 'QUALIFIED'),
  CONSTRAINT `ck_cm_inv_st_distinct_nodes` CHECK (
    `source_warehouse_id` <> `target_warehouse_id` OR `source_location_id` <> `target_location_id`
  ),
  CONSTRAINT `ck_cm_inv_st_quantities` CHECK (
    `dispatched_quantity` >= 0 AND `received_quantity` >= 0
    AND `received_quantity` <= `dispatched_quantity`
  ),
  CONSTRAINT `ck_cm_inv_st_version` CHECK (`version` >= 0),
  CONSTRAINT `ck_cm_inv_st_uom` CHECK (
    CHAR_LENGTH(TRIM(`base_uom_code`)) > 0
    AND BINARY `base_uom_code` = BINARY UPPER(TRIM(`base_uom_code`))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_inventory_ledger_transaction_v3`
  DROP INDEX `uk_cm_inv_v3_movement_group`,
  ADD KEY `idx_cm_inv_v3_movement_group` (`tenant_id`,`movement_group_id`,`ledger_transaction_id`),
  MODIFY COLUMN `operation_id` bigint DEFAULT NULL,
  ADD COLUMN `stock_transfer_operation_id` bigint DEFAULT NULL AFTER `operation_id`,
  ADD UNIQUE KEY `uk_cm_inv_v3_tx_stock_transfer_op` (`tenant_id`,`stock_transfer_operation_id`),
  ADD CONSTRAINT `fk_cm_inv_v3_tx_stock_transfer_op`
    FOREIGN KEY (`tenant_id`,`stock_transfer_operation_id`)
    REFERENCES `cloudmold_inventory_stock_transfer_operation_v3` (`tenant_id`,`operation_id`),
  ADD CONSTRAINT `ck_cm_inv_v3_tx_operation_owner` CHECK (
    (`operation_id` IS NOT NULL AND `stock_transfer_operation_id` IS NULL)
    OR (`operation_id` IS NULL AND `stock_transfer_operation_id` IS NOT NULL)
  );

ALTER TABLE `cloudmold_inventory_ledger_transaction_v3`
  DROP CONSTRAINT `ck_cm_inv_v3_tx_command`,
  ADD CONSTRAINT `ck_cm_inv_v3_tx_command` CHECK (`command_type` IN (
    'RECEIVE','RESERVE','SHIP','RETURN','RELEASE','MIGRATION_OPENING',
    'INTRANSIT_ADD','INTRANSIT_SETTLE','QUALITY_RELEASE','RELOCATE',
    'STOCK_TRANSFER_DISPATCH','STOCK_TRANSFER_RECEIVE'
  ));

ALTER TABLE `cloudmold_inventory_stock_transfer_operation_v3`
  ADD CONSTRAINT `fk_cm_inv_st_op_ledger_tx` FOREIGN KEY (`tenant_id`,`ledger_transaction_id`)
    REFERENCES `cloudmold_inventory_ledger_transaction_v3` (`tenant_id`,`ledger_transaction_id`);
