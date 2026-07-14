-- Canonical Inventory red-zone schema. Legacy Mall/ERP/WMS stock remains projection-only.

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_balance` (
  `balance_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `owner_id` varchar(128) NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `warehouse_id` varchar(128) NOT NULL,
  `stock_status` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `on_hand_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `reserved_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `in_transit_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `version` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`balance_id`),
  UNIQUE KEY `uk_inventory_balance_dimension`
    (`tenant_id`,`owner_id`,`canonical_sku_id`,`warehouse_id`,`stock_status`,`quality_status`),
  KEY `idx_inventory_balance_sku` (`tenant_id`,`canonical_sku_id`,`warehouse_id`),
  CONSTRAINT `ck_inventory_balance_nonnegative`
    CHECK (`on_hand_quantity` >= 0 AND `reserved_quantity` >= 0 AND `in_transit_quantity` >= 0),
  CONSTRAINT `ck_inventory_balance_available`
    CHECK (`stock_status` <> 'SELLABLE' OR `reserved_quantity` <= `on_hand_quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical inventory balance authority';

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(128) DEFAULT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `ledger_transaction_id` bigint DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_inventory_operation_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_inventory_operation_source_event` (`tenant_id`,`source_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inventory command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_reservation` (
  `reservation_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `balance_id` varchar(36) NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `business_id` varchar(128) NOT NULL,
  `business_item_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `status` tinyint NOT NULL COMMENT '10 ACTIVE, 20 COMMITTED, 30 RELEASED',
  `version` bigint unsigned NOT NULL DEFAULT 1,
  `created_operation_id` bigint NOT NULL,
  `closed_operation_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`reservation_id`),
  UNIQUE KEY `uk_inventory_reservation_business`
    (`tenant_id`,`business_type`,`business_id`,`business_item_id`),
  KEY `idx_inventory_reservation_balance_status` (`tenant_id`,`balance_id`,`status`),
  CONSTRAINT `ck_inventory_reservation_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical inventory reservation';

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_ledger_transaction` (
  `ledger_transaction_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `operation_id` bigint NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `business_id` varchar(128) NOT NULL,
  `business_item_id` varchar(128) NOT NULL,
  `business_no` varchar(128) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`ledger_transaction_id`),
  UNIQUE KEY `uk_inventory_ledger_operation` (`tenant_id`,`operation_id`),
  KEY `idx_inventory_ledger_business`
    (`tenant_id`,`business_type`,`business_id`,`business_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable inventory ledger transaction';

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_ledger_entry` (
  `ledger_entry_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `ledger_transaction_id` bigint NOT NULL,
  `balance_id` varchar(36) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `before_on_hand_quantity` decimal(24,6) NOT NULL,
  `delta_on_hand_quantity` decimal(24,6) NOT NULL,
  `after_on_hand_quantity` decimal(24,6) NOT NULL,
  `before_reserved_quantity` decimal(24,6) NOT NULL,
  `delta_reserved_quantity` decimal(24,6) NOT NULL,
  `after_reserved_quantity` decimal(24,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`ledger_entry_id`),
  UNIQUE KEY `uk_inventory_ledger_balance_version` (`tenant_id`,`balance_id`,`aggregate_version`),
  UNIQUE KEY `uk_inventory_ledger_transaction_balance` (`tenant_id`,`ledger_transaction_id`,`balance_id`),
  CONSTRAINT `ck_inventory_ledger_on_hand`
    CHECK (`before_on_hand_quantity` + `delta_on_hand_quantity` = `after_on_hand_quantity`),
  CONSTRAINT `ck_inventory_ledger_reserved`
    CHECK (`before_reserved_quantity` + `delta_reserved_quantity` = `after_reserved_quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable inventory ledger balance mutation';
