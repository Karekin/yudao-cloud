-- Canonical Order and Payment red-zone schema. Legacy Trade/Pay remain adapters and channel engines.

CREATE TABLE IF NOT EXISTS `cloudmold_order_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `order_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_order_operation_idempotency` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical order command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_order_header` (
  `order_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `buyer_id` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `total_quantity` decimal(24,6) NOT NULL,
  `product_amount_minor` bigint NOT NULL,
  `shipping_amount_minor` bigint NOT NULL,
  `discount_amount_minor` bigint NOT NULL,
  `payable_amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `payment_id` varchar(36) DEFAULT NULL,
  `refund_id` varchar(128) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_tenant_id` (`tenant_id`,`order_id`),
  UNIQUE KEY `uk_order_no` (`tenant_id`,`order_no`),
  KEY `idx_order_run` (`tenant_id`,`run_id`),
  CONSTRAINT `ck_order_money` CHECK (
    `product_amount_minor` >= 0 AND `shipping_amount_minor` >= 0 AND `discount_amount_minor` >= 0
    AND `payable_amount_minor` = `product_amount_minor` + `shipping_amount_minor` - `discount_amount_minor`
    AND `payable_amount_minor` >= 0
  ),
  CONSTRAINT `ck_order_currency` CHECK (`currency_code` = 'CNY'),
  CONSTRAINT `ck_order_status` CHECK (`status` IN (
    'PLACED','INVENTORY_RESERVED','PAYMENT_CONFIRMED','SHIPPED','COMPLETED','CANCELLED','REFUNDED','RETURNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical consumer order authority';

CREATE TABLE IF NOT EXISTS `cloudmold_order_item` (
  `order_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `unit_price_minor` bigint NOT NULL,
  `line_amount_minor` bigint NOT NULL,
  `reservation_id` varchar(36) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`order_item_id`),
  UNIQUE KEY `uk_order_item_tenant_id` (`tenant_id`,`order_item_id`),
  UNIQUE KEY `uk_order_item_sku` (`tenant_id`,`order_id`,`canonical_sku_id`),
  UNIQUE KEY `uk_order_item_reservation` (`tenant_id`,`reservation_id`),
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `fk_order_item_catalog_sku` FOREIGN KEY (`tenant_id`,`canonical_sku_id`)
    REFERENCES `cloudmold_catalog_sku` (`tenant_id`,`sku_id`),
  CONSTRAINT `ck_order_item_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_order_item_money` CHECK (
    `unit_price_minor` >= 0 AND `line_amount_minor` >= 0
    AND `line_amount_minor` = `unit_price_minor` * `quantity`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable canonical order item trade snapshot';

CREATE TABLE IF NOT EXISTS `cloudmold_order_status_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `operation_id` bigint NOT NULL,
  `reason` varchar(256) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_order_history_version` (`tenant_id`,`order_id`,`aggregate_version`),
  UNIQUE KEY `uk_order_history_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_order_history_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable canonical order state history';

CREATE TABLE IF NOT EXISTS `cloudmold_payment_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `payment_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_payment_operation_idempotency` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical payment command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_payment` (
  `payment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `payment_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `payable_amount_minor` bigint NOT NULL,
  `captured_amount_minor` bigint NOT NULL,
  `refunded_amount_minor` bigint NOT NULL DEFAULT 0,
  `currency_code` char(3) NOT NULL,
  `provider_code` varchar(32) NOT NULL,
  `provider_transaction_id` varchar(128) NOT NULL,
  `test_mode` bit(1) NOT NULL,
  `version` bigint unsigned NOT NULL,
  `captured_at` datetime(6) DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`payment_id`),
  UNIQUE KEY `uk_payment_tenant_id` (`tenant_id`,`payment_id`),
  UNIQUE KEY `uk_payment_no` (`tenant_id`,`payment_no`),
  UNIQUE KEY `uk_payment_order` (`tenant_id`,`order_id`),
  UNIQUE KEY `uk_payment_provider_tx` (`tenant_id`,`provider_code`,`provider_transaction_id`),
  KEY `idx_payment_run` (`tenant_id`,`run_id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `ck_payment_money` CHECK (
    `payable_amount_minor` >= 0 AND `captured_amount_minor` = `payable_amount_minor`
    AND `refunded_amount_minor` >= 0 AND `refunded_amount_minor` <= `captured_amount_minor`
  ),
  CONSTRAINT `ck_payment_currency` CHECK (`currency_code` = 'CNY'),
  CONSTRAINT `ck_payment_status` CHECK (`status` IN ('CAPTURED','REFUNDED')),
  CONSTRAINT `ck_payment_test_provider` CHECK (`test_mode` = b'0' OR `provider_code` = 'INTERNAL_TEST')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical business payment fact';

CREATE TABLE IF NOT EXISTS `cloudmold_payment_transaction` (
  `transaction_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `payment_id` varchar(36) NOT NULL,
  `operation_id` bigint NOT NULL,
  `transaction_type` varchar(32) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `provider_code` varchar(32) NOT NULL,
  `provider_transaction_id` varchar(128) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`transaction_id`),
  UNIQUE KEY `uk_payment_transaction_operation` (`tenant_id`,`operation_id`),
  UNIQUE KEY `uk_payment_transaction_version` (`tenant_id`,`payment_id`,`aggregate_version`),
  UNIQUE KEY `uk_payment_transaction_provider` (`tenant_id`,`provider_code`,`provider_transaction_id`),
  CONSTRAINT `fk_payment_transaction_payment` FOREIGN KEY (`tenant_id`,`payment_id`)
    REFERENCES `cloudmold_payment` (`tenant_id`,`payment_id`),
  CONSTRAINT `ck_payment_transaction_amount` CHECK (`amount_minor` >= 0),
  CONSTRAINT `ck_payment_transaction_type` CHECK (`transaction_type` IN ('CAPTURE','REFUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable canonical payment and refund transaction';
