-- Inventory v3 parallel shadow ledger with canonical owner, SKU, warehouse, location, and optional lot dimensions.
-- This migration is additive: legacy inventory v1/v2 tables and behavior remain unchanged.

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_lot` (
  `lot_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `lot_code` varchar(128) NOT NULL,
  `manufactured_on` date DEFAULT NULL,
  `expires_on` date DEFAULT NULL,
  `received_at` datetime(6) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`lot_id`),
  UNIQUE KEY `uk_cm_inv_lot_tenant_id` (`tenant_id`,`lot_id`),
  UNIQUE KEY `uk_cm_inv_lot_business` (`tenant_id`,`owner_type`,`owner_id`,`canonical_sku_id`,`lot_code`),
  KEY `idx_cm_inv_lot_expiry` (`tenant_id`,`canonical_sku_id`,`expires_on`,`status`),
  CONSTRAINT `ck_cm_inv_lot_owner_type` CHECK (`owner_type` IN ('MERCHANT','PLATFORM','MEMBER')),
  CONSTRAINT `ck_cm_inv_lot_status` CHECK (`status` IN ('ACTIVE','CLOSED','RECALLED')),
  CONSTRAINT `ck_cm_inv_lot_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_inv_lot_dates` CHECK (`expires_on` IS NULL OR `manufactured_on` IS NULL OR `expires_on` >= `manufactured_on`),
  CONSTRAINT `ck_cm_inv_lot_identity` CHECK (CHAR_LENGTH(TRIM(`lot_code`)) > 0 AND BINARY `lot_code` = BINARY TRIM(`lot_code`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_lot_source_mapping` (
  `mapping_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `lot_id` varchar(36) NOT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `verification_ref` varchar(256) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_cm_inv_lot_map_tenant_id` (`tenant_id`,`mapping_id`),
  UNIQUE KEY `uk_cm_inv_lot_map_effective` (`tenant_id`,`source_system`,`source_type`,`source_id`,`valid_from`),
  UNIQUE KEY `uk_cm_inv_lot_map_active` (`tenant_id`,`source_system`,`source_type`,`source_id`,`active_guard`),
  KEY `idx_cm_inv_lot_map_target` (`tenant_id`,`lot_id`,`status`),
  CONSTRAINT `fk_cm_inv_lot_map_lot` FOREIGN KEY (`tenant_id`,`lot_id`)
    REFERENCES `cloudmold_inventory_lot` (`tenant_id`,`lot_id`),
  CONSTRAINT `ck_cm_inv_lot_map_status` CHECK (`status` IN ('ACTIVE','ENDED')),
  CONSTRAINT `ck_cm_inv_lot_map_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_inv_lot_map_validity` CHECK (`valid_to` IS NULL OR `valid_to` > `valid_from`),
  CONSTRAINT `ck_cm_inv_lot_map_source` CHECK (
    CHAR_LENGTH(TRIM(`source_system`)) > 0
    AND CHAR_LENGTH(TRIM(`source_type`)) > 0
    AND CHAR_LENGTH(TRIM(`source_id`)) > 0
    AND CHAR_LENGTH(TRIM(`verification_ref`)) > 0
    AND BINARY `source_system` = BINARY UPPER(TRIM(`source_system`))
    AND BINARY `source_type` = BINARY UPPER(TRIM(`source_type`))
    AND BINARY `source_id` = BINARY TRIM(`source_id`)
    AND `lot_id` <> `source_id`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_balance_v3` (
  `balance_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `location_id` varchar(36) NOT NULL,
  `lot_id` varchar(36) DEFAULT NULL,
  `lot_dimension_key` varchar(36) GENERATED ALWAYS AS (COALESCE(`lot_id`,'')) STORED,
  `stock_status` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `on_hand_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `reserved_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `in_transit_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`balance_id`),
  UNIQUE KEY `uk_cm_inv_v3_balance_tenant_id` (`tenant_id`,`balance_id`),
  UNIQUE KEY `uk_cm_inv_v3_dimension` (`tenant_id`,`owner_type`,`owner_id`,`canonical_sku_id`,`warehouse_id`,`location_id`,`lot_dimension_key`,`stock_status`,`quality_status`),
  KEY `idx_cm_inv_v3_sku_location` (`tenant_id`,`canonical_sku_id`,`warehouse_id`,`location_id`),
  CONSTRAINT `fk_cm_inv_v3_warehouse` FOREIGN KEY (`tenant_id`,`warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_inv_v3_location` FOREIGN KEY (`tenant_id`,`location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `fk_cm_inv_v3_lot` FOREIGN KEY (`tenant_id`,`lot_id`)
    REFERENCES `cloudmold_inventory_lot` (`tenant_id`,`lot_id`),
  CONSTRAINT `ck_cm_inv_v3_owner_type` CHECK (`owner_type` IN ('MERCHANT','PLATFORM','MEMBER')),
  CONSTRAINT `ck_cm_inv_v3_stock_status` CHECK (`stock_status` IN ('SELLABLE','NON_SELLABLE')),
  CONSTRAINT `ck_cm_inv_v3_quality_status` CHECK (`quality_status` IN ('PENDING_QC','QUALIFIED','DAMAGED','REJECTED')),
  CONSTRAINT `ck_cm_inv_v3_sellable_quality` CHECK (`stock_status` <> 'SELLABLE' OR `quality_status` = 'QUALIFIED'),
  CONSTRAINT `ck_cm_inv_v3_quantities` CHECK (`on_hand_quantity` >= 0 AND `reserved_quantity` >= 0 AND `in_transit_quantity` >= 0),
  CONSTRAINT `ck_cm_inv_v3_reserve_limit` CHECK (`stock_status` <> 'SELLABLE' OR `reserved_quantity` <= `on_hand_quantity`),
  CONSTRAINT `ck_cm_inv_v3_version` CHECK (`version` >= 0),
  CONSTRAINT `ck_cm_inv_v3_uom` CHECK (CHAR_LENGTH(TRIM(`base_uom_code`)) > 0 AND BINARY `base_uom_code` = BINARY UPPER(TRIM(`base_uom_code`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_operation_v3` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(36) DEFAULT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `ledger_transaction_id` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_inv_v3_operation_tenant_id` (`tenant_id`,`operation_id`),
  UNIQUE KEY `uk_cm_inv_v3_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_inv_v3_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_inv_v3_command_type` CHECK (`command_type` IN ('RECEIVE','RESERVE','SHIP','RETURN','RELEASE')),
  CONSTRAINT `ck_cm_inv_v3_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_reservation_v3` (
  `reservation_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `business_id` varchar(128) NOT NULL,
  `business_item_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `status` tinyint NOT NULL,
  `version` bigint NOT NULL,
  `created_operation_id` bigint NOT NULL,
  `closed_operation_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`reservation_id`),
  UNIQUE KEY `uk_cm_inv_v3_res_tenant_id` (`tenant_id`,`reservation_id`),
  UNIQUE KEY `uk_cm_inv_v3_res_business` (`tenant_id`,`business_type`,`business_id`,`business_item_id`),
  CONSTRAINT `fk_cm_inv_v3_res_created_op` FOREIGN KEY (`tenant_id`,`created_operation_id`)
    REFERENCES `cloudmold_inventory_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_v3_res_closed_op` FOREIGN KEY (`tenant_id`,`closed_operation_id`)
    REFERENCES `cloudmold_inventory_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `ck_cm_inv_v3_res_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_cm_inv_v3_res_status` CHECK (`status` IN (10,20,30)),
  CONSTRAINT `ck_cm_inv_v3_res_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_inv_v3_res_close` CHECK ((`status` = 10 AND `closed_operation_id` IS NULL) OR (`status` IN (20,30) AND `closed_operation_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_reservation_allocation_v3` (
  `allocation_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `reservation_id` varchar(36) NOT NULL,
  `balance_id` varchar(36) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `status` tinyint NOT NULL,
  `version` bigint NOT NULL,
  `created_operation_id` bigint NOT NULL,
  `closed_operation_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`allocation_id`),
  UNIQUE KEY `uk_cm_inv_v3_alloc_tenant_id` (`tenant_id`,`allocation_id`),
  UNIQUE KEY `uk_cm_inv_v3_alloc_dimension` (`tenant_id`,`reservation_id`,`balance_id`),
  KEY `idx_cm_inv_v3_alloc_balance` (`tenant_id`,`balance_id`,`status`),
  CONSTRAINT `fk_cm_inv_v3_alloc_res` FOREIGN KEY (`tenant_id`,`reservation_id`)
    REFERENCES `cloudmold_inventory_reservation_v3` (`tenant_id`,`reservation_id`),
  CONSTRAINT `fk_cm_inv_v3_alloc_balance` FOREIGN KEY (`tenant_id`,`balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `fk_cm_inv_v3_alloc_created_op` FOREIGN KEY (`tenant_id`,`created_operation_id`)
    REFERENCES `cloudmold_inventory_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_v3_alloc_closed_op` FOREIGN KEY (`tenant_id`,`closed_operation_id`)
    REFERENCES `cloudmold_inventory_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `ck_cm_inv_v3_alloc_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_cm_inv_v3_alloc_status` CHECK (`status` IN (10,20,30)),
  CONSTRAINT `ck_cm_inv_v3_alloc_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_inv_v3_alloc_close` CHECK ((`status` = 10 AND `closed_operation_id` IS NULL) OR (`status` IN (20,30) AND `closed_operation_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_ledger_transaction_v3` (
  `ledger_transaction_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `operation_id` bigint NOT NULL,
  `movement_group_id` varchar(36) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `business_id` varchar(128) NOT NULL,
  `business_item_id` varchar(128) NOT NULL,
  `business_no` varchar(128) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`ledger_transaction_id`),
  UNIQUE KEY `uk_cm_inv_v3_tx_tenant_id` (`tenant_id`,`ledger_transaction_id`),
  UNIQUE KEY `uk_cm_inv_v3_tx_operation` (`tenant_id`,`operation_id`),
  UNIQUE KEY `uk_cm_inv_v3_movement_group` (`tenant_id`,`movement_group_id`),
  CONSTRAINT `fk_cm_inv_v3_tx_operation` FOREIGN KEY (`tenant_id`,`operation_id`)
    REFERENCES `cloudmold_inventory_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `ck_cm_inv_v3_tx_command` CHECK (`command_type` IN ('RECEIVE','RESERVE','SHIP','RETURN','RELEASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_ledger_entry_v3` (
  `ledger_entry_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `ledger_transaction_id` bigint NOT NULL,
  `movement_group_id` varchar(36) NOT NULL,
  `entry_role` varchar(16) NOT NULL,
  `counterparty_balance_id` varchar(36) DEFAULT NULL,
  `balance_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `before_on_hand_quantity` decimal(24,6) NOT NULL,
  `delta_on_hand_quantity` decimal(24,6) NOT NULL,
  `after_on_hand_quantity` decimal(24,6) NOT NULL,
  `before_reserved_quantity` decimal(24,6) NOT NULL,
  `delta_reserved_quantity` decimal(24,6) NOT NULL,
  `after_reserved_quantity` decimal(24,6) NOT NULL,
  `before_in_transit_quantity` decimal(24,6) NOT NULL,
  `delta_in_transit_quantity` decimal(24,6) NOT NULL,
  `after_in_transit_quantity` decimal(24,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`ledger_entry_id`),
  UNIQUE KEY `uk_cm_inv_v3_entry_tenant_id` (`tenant_id`,`ledger_entry_id`),
  UNIQUE KEY `uk_cm_inv_v3_entry_version` (`tenant_id`,`balance_id`,`aggregate_version`),
  UNIQUE KEY `uk_cm_inv_v3_entry_tx_balance` (`tenant_id`,`ledger_transaction_id`,`balance_id`),
  KEY `idx_cm_inv_v3_entry_group` (`tenant_id`,`movement_group_id`),
  CONSTRAINT `fk_cm_inv_v3_entry_tx` FOREIGN KEY (`tenant_id`,`ledger_transaction_id`)
    REFERENCES `cloudmold_inventory_ledger_transaction_v3` (`tenant_id`,`ledger_transaction_id`),
  CONSTRAINT `fk_cm_inv_v3_entry_balance` FOREIGN KEY (`tenant_id`,`balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `fk_cm_inv_v3_entry_counterparty` FOREIGN KEY (`tenant_id`,`counterparty_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `ck_cm_inv_v3_entry_role` CHECK (`entry_role` IN ('SINGLE','OUT','IN')),
  CONSTRAINT `ck_cm_inv_v3_entry_version` CHECK (`aggregate_version` >= 1),
  CONSTRAINT `ck_cm_inv_v3_entry_on_hand` CHECK (`after_on_hand_quantity` = `before_on_hand_quantity` + `delta_on_hand_quantity`),
  CONSTRAINT `ck_cm_inv_v3_entry_reserved` CHECK (`after_reserved_quantity` = `before_reserved_quantity` + `delta_reserved_quantity`),
  CONSTRAINT `ck_cm_inv_v3_entry_transit` CHECK (`after_in_transit_quantity` = `before_in_transit_quantity` + `delta_in_transit_quantity`),
  CONSTRAINT `ck_cm_inv_v3_entry_nonnegative` CHECK (`after_on_hand_quantity` >= 0 AND `after_reserved_quantity` >= 0 AND `after_in_transit_quantity` >= 0),
  CONSTRAINT `ck_cm_inv_v3_entry_counterparty_shape` CHECK ((`entry_role` = 'SINGLE' AND `counterparty_balance_id` IS NULL) OR (`entry_role` IN ('OUT','IN') AND `counterparty_balance_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_bridge` (
  `bridge_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `legacy_balance_id` varchar(36) NOT NULL,
  `target_balance_id` varchar(36) DEFAULT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `resolution_status` varchar(16) NOT NULL,
  `verification_ref` varchar(256) NOT NULL,
  `source_on_hand_quantity` decimal(24,6) NOT NULL,
  `source_reserved_quantity` decimal(24,6) NOT NULL,
  `source_in_transit_quantity` decimal(24,6) NOT NULL,
  `target_on_hand_quantity` decimal(24,6) DEFAULT NULL,
  `target_reserved_quantity` decimal(24,6) DEFAULT NULL,
  `target_in_transit_quantity` decimal(24,6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`bridge_id`),
  UNIQUE KEY `uk_cm_inv_bridge_tenant_id` (`tenant_id`,`bridge_id`),
  UNIQUE KEY `uk_cm_inv_bridge_source` (`tenant_id`,`source_system`,`source_type`,`source_id`),
  UNIQUE KEY `uk_cm_inv_bridge_legacy` (`tenant_id`,`legacy_balance_id`),
  KEY `idx_cm_inv_bridge_target` (`tenant_id`,`target_balance_id`,`resolution_status`),
  CONSTRAINT `fk_cm_inv_bridge_target` FOREIGN KEY (`tenant_id`,`target_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `ck_cm_inv_bridge_status` CHECK (`resolution_status` IN ('UNRESOLVED','RESOLVED','REJECTED')),
  CONSTRAINT `ck_cm_inv_bridge_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_inv_bridge_quantities` CHECK (`source_on_hand_quantity` >= 0 AND `source_reserved_quantity` >= 0 AND `source_in_transit_quantity` >= 0),
  CONSTRAINT `ck_cm_inv_bridge_shape` CHECK (
    (`resolution_status` = 'RESOLVED' AND `target_balance_id` IS NOT NULL AND `target_on_hand_quantity` IS NOT NULL AND `target_reserved_quantity` IS NOT NULL AND `target_in_transit_quantity` IS NOT NULL)
    OR (`resolution_status` IN ('UNRESOLVED','REJECTED') AND `target_balance_id` IS NULL AND `target_on_hand_quantity` IS NULL AND `target_reserved_quantity` IS NULL AND `target_in_transit_quantity` IS NULL)
  ),
  CONSTRAINT `ck_cm_inv_bridge_source_identity` CHECK (
    CHAR_LENGTH(TRIM(`source_system`)) > 0
    AND CHAR_LENGTH(TRIM(`source_type`)) > 0
    AND CHAR_LENGTH(TRIM(`source_id`)) > 0
    AND CHAR_LENGTH(TRIM(`verification_ref`)) > 0
    AND BINARY `source_system` = BINARY UPPER(TRIM(`source_system`))
    AND BINARY `source_type` = BINARY UPPER(TRIM(`source_type`))
    AND BINARY `source_id` = BINARY TRIM(`source_id`)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
