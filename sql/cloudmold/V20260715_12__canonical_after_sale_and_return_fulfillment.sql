-- CloudMold canonical after-sale/refund authority and reverse Fulfillment first slice.
-- Additive red-zone migration; does not mutate upstream Trade/Pay/WMS tables.

CREATE TABLE IF NOT EXISTS `cloudmold_return_fulfillment` (
  `return_fulfillment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `return_fulfillment_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `owner_id` varchar(128) NOT NULL,
  `warehouse_id` varchar(128) NOT NULL,
  `uom_code` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `correlation_id` varchar(36) NOT NULL,
  `causation_id` varchar(36) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`return_fulfillment_id`),
  UNIQUE KEY `uk_cm_return_fulfillment_tenant_id` (`tenant_id`,`return_fulfillment_id`),
  UNIQUE KEY `uk_cm_return_fulfillment_no` (`tenant_id`,`return_fulfillment_no`),
  UNIQUE KEY `uk_cm_return_fulfillment_after_sale` (`tenant_id`,`after_sale_id`),
  KEY `idx_cm_return_fulfillment_order` (`tenant_id`,`order_id`),
  KEY `idx_cm_return_fulfillment_run` (`tenant_id`,`run_id`),
  CONSTRAINT `ck_cm_return_fulfillment_status` CHECK (`status` IN ('CREATED','HANDED_OVER','IN_TRANSIT','RECEIVED','INSPECTION_ACCEPTED')),
  CONSTRAINT `ck_cm_return_fulfillment_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_return_fulfillment_uom` CHECK (`uom_code` = 'PCS')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_fulfillment_item` (
  `return_fulfillment_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `after_sale_item_id` varchar(36) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`return_fulfillment_item_id`),
  UNIQUE KEY `uk_cm_return_fulfillment_item_tenant_id` (`tenant_id`,`return_fulfillment_item_id`),
  UNIQUE KEY `uk_cm_return_fulfillment_item_as` (`tenant_id`,`after_sale_item_id`),
  KEY `idx_cm_return_fulfillment_item_parent` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `fk_cm_return_fulfillment_item_parent` FOREIGN KEY (`tenant_id`,`return_fulfillment_id`)
    REFERENCES `cloudmold_return_fulfillment` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `ck_cm_return_fulfillment_item_qty` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_fulfillment_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `return_fulfillment_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_return_fulfillment_operation` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_fulfillment_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `operation_id` bigint NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cm_return_fulfillment_history` (`tenant_id`,`return_fulfillment_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_shipment` (
  `return_shipment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `carrier_code` varchar(64) NOT NULL,
  `waybill_no` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `handed_over_at` datetime(6) NOT NULL,
  `in_transit_at` datetime(6) DEFAULT NULL,
  `received_at` datetime(6) DEFAULT NULL,
  `receiver_id` varchar(128) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`return_shipment_id`),
  UNIQUE KEY `uk_cm_return_shipment_tenant_id` (`tenant_id`,`return_shipment_id`),
  UNIQUE KEY `uk_cm_return_shipment_parent` (`tenant_id`,`return_fulfillment_id`),
  UNIQUE KEY `uk_cm_return_waybill` (`tenant_id`,`carrier_code`,`waybill_no`),
  CONSTRAINT `fk_cm_return_shipment_parent` FOREIGN KEY (`tenant_id`,`return_fulfillment_id`)
    REFERENCES `cloudmold_return_fulfillment` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `ck_cm_return_shipment_status` CHECK (`status` IN ('HANDED_OVER','IN_TRANSIT','RECEIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_shipment_item` (
  `return_shipment_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `return_shipment_id` varchar(36) NOT NULL,
  `return_fulfillment_item_id` varchar(36) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`return_shipment_item_id`),
  UNIQUE KEY `uk_cm_return_shipment_item_tenant_id` (`tenant_id`,`return_shipment_item_id`),
  UNIQUE KEY `uk_cm_return_shipment_item_link` (`tenant_id`,`return_shipment_id`,`return_fulfillment_item_id`),
  CONSTRAINT `fk_cm_return_shipment_item_shipment` FOREIGN KEY (`tenant_id`,`return_shipment_id`)
    REFERENCES `cloudmold_return_shipment` (`tenant_id`,`return_shipment_id`),
  CONSTRAINT `fk_cm_return_shipment_item_fulfillment` FOREIGN KEY (`tenant_id`,`return_fulfillment_item_id`)
    REFERENCES `cloudmold_return_fulfillment_item` (`tenant_id`,`return_fulfillment_item_id`),
  CONSTRAINT `ck_cm_return_shipment_item_qty` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_tracking_event` (
  `tracking_event_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `return_shipment_id` varchar(36) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `operator_id` varchar(128) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`tracking_event_id`),
  UNIQUE KEY `uk_cm_return_tracking_type` (`tenant_id`,`return_shipment_id`,`event_type`),
  KEY `idx_cm_return_tracking_parent` (`tenant_id`,`return_fulfillment_id`,`occurred_at`),
  CONSTRAINT `fk_cm_return_tracking_fulfillment` FOREIGN KEY (`tenant_id`,`return_fulfillment_id`)
    REFERENCES `cloudmold_return_fulfillment` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `fk_cm_return_tracking_shipment` FOREIGN KEY (`tenant_id`,`return_shipment_id`)
    REFERENCES `cloudmold_return_shipment` (`tenant_id`,`return_shipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_return_inspection` (
  `inspection_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `return_shipment_id` varchar(36) NOT NULL,
  `return_fulfillment_item_id` varchar(36) NOT NULL,
  `return_shipment_item_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(128) NOT NULL,
  `received_quantity` decimal(24,6) NOT NULL,
  `accepted_quantity` decimal(24,6) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `inspector_id` varchar(128) NOT NULL,
  `decided_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`inspection_id`),
  UNIQUE KEY `uk_cm_return_inspection_tenant_id` (`tenant_id`,`inspection_id`),
  UNIQUE KEY `uk_cm_return_inspection_parent` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `fk_cm_return_inspection_fulfillment` FOREIGN KEY (`tenant_id`,`return_fulfillment_id`)
    REFERENCES `cloudmold_return_fulfillment` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `fk_cm_return_inspection_shipment` FOREIGN KEY (`tenant_id`,`return_shipment_id`)
    REFERENCES `cloudmold_return_shipment` (`tenant_id`,`return_shipment_id`),
  CONSTRAINT `fk_cm_return_inspection_fulfillment_item` FOREIGN KEY (`tenant_id`,`return_fulfillment_item_id`)
    REFERENCES `cloudmold_return_fulfillment_item` (`tenant_id`,`return_fulfillment_item_id`),
  CONSTRAINT `fk_cm_return_inspection_shipment_item` FOREIGN KEY (`tenant_id`,`return_shipment_item_id`)
    REFERENCES `cloudmold_return_shipment_item` (`tenant_id`,`return_shipment_item_id`),
  CONSTRAINT `ck_cm_return_inspection_qty` CHECK (`received_quantity` > 0 AND `accepted_quantity` = `received_quantity`),
  CONSTRAINT `ck_cm_return_inspection_quality` CHECK (`quality_status` = 'QUALIFIED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_case` (
  `after_sale_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `after_sale_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `buyer_id` varchar(128) NOT NULL,
  `order_version_at_request` bigint NOT NULL,
  `payment_id` varchar(36) NOT NULL,
  `payment_version_at_request` bigint NOT NULL,
  `forward_fulfillment_id` varchar(36) NOT NULL,
  `forward_shipment_id` varchar(36) NOT NULL,
  `owner_id` varchar(128) NOT NULL,
  `warehouse_id` varchar(128) NOT NULL,
  `uom_code` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `refund_status` varchar(32) NOT NULL,
  `approved_amount_minor` bigint DEFAULT NULL,
  `currency_code` char(3) NOT NULL,
  `return_fulfillment_id` varchar(36) DEFAULT NULL,
  `return_shipment_id` varchar(36) DEFAULT NULL,
  `inspection_id` varchar(36) DEFAULT NULL,
  `resolution_saga_id` varchar(36) DEFAULT NULL,
  `after_sale_type` varchar(32) NOT NULL,
  `reason_code` varchar(64) NOT NULL,
  `responsibility` varchar(32) NOT NULL,
  `reason` varchar(256) NOT NULL,
  `reviewer_id` varchar(128) DEFAULT NULL,
  `version` bigint NOT NULL,
  `correlation_id` varchar(36) NOT NULL,
  `causation_id` varchar(36) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`after_sale_id`),
  UNIQUE KEY `uk_cm_after_sale_tenant_id` (`tenant_id`,`after_sale_id`),
  UNIQUE KEY `uk_cm_after_sale_no` (`tenant_id`,`after_sale_no`),
  UNIQUE KEY `uk_cm_after_sale_return_fulfillment` (`tenant_id`,`return_fulfillment_id`),
  KEY `idx_cm_after_sale_order` (`tenant_id`,`order_id`),
  KEY `idx_cm_after_sale_run` (`tenant_id`,`run_id`),
  CONSTRAINT `ck_cm_after_sale_currency` CHECK (`currency_code` = 'CNY'),
  CONSTRAINT `ck_cm_after_sale_type` CHECK (`after_sale_type` = 'RETURN_AND_REFUND'),
  CONSTRAINT `ck_cm_after_sale_responsibility` CHECK (`responsibility` = 'BUYER'),
  CONSTRAINT `ck_cm_after_sale_reason_code` CHECK (`reason_code` = 'SIZE_NOT_FIT'),
  CONSTRAINT `ck_cm_after_sale_status` CHECK (`status` IN ('REQUESTED','APPROVED','RESOLUTION_PENDING','MANUAL_REVIEW','COMPLETED')),
  CONSTRAINT `ck_cm_after_sale_refund_status` CHECK (`refund_status` IN ('NOT_REQUESTED','REQUESTED','SUCCEEDED')),
  CONSTRAINT `ck_cm_after_sale_amount` CHECK (`approved_amount_minor` IS NULL OR `approved_amount_minor` >= 0),
  CONSTRAINT `ck_cm_after_sale_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_item` (
  `after_sale_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `line_amount_minor` bigint NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `listing_offer_id` varchar(36) NOT NULL,
  `active_guard` tinyint DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`after_sale_item_id`),
  UNIQUE KEY `uk_cm_after_sale_item_tenant_id` (`tenant_id`,`after_sale_item_id`),
  UNIQUE KEY `uk_cm_after_sale_active_item` (`tenant_id`,`order_item_id`,`active_guard`),
  KEY `idx_cm_after_sale_item_parent` (`tenant_id`,`after_sale_id`),
  CONSTRAINT `fk_cm_after_sale_item_parent` FOREIGN KEY (`tenant_id`,`after_sale_id`)
    REFERENCES `cloudmold_after_sale_case` (`tenant_id`,`after_sale_id`),
  CONSTRAINT `ck_cm_after_sale_item_qty` CHECK (`quantity` > 0),
  CONSTRAINT `ck_cm_after_sale_item_active` CHECK (`active_guard` IS NULL OR `active_guard` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `after_sale_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_after_sale_operation` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `operation_id` bigint DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cm_after_sale_history` (`tenant_id`,`after_sale_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_refund_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `previous_status` varchar(32) NOT NULL,
  `current_status` varchar(32) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `payment_refund_transaction_id` bigint DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cm_after_sale_refund_history` (`tenant_id`,`after_sale_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_resolution_saga` (
  `saga_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `after_sale_item_id` varchar(36) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `order_version_at_request` bigint NOT NULL,
  `order_version` bigint NOT NULL,
  `payment_id` varchar(36) NOT NULL,
  `payment_version_at_request` bigint NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `return_shipment_id` varchar(36) NOT NULL,
  `inspection_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `owner_id` varchar(128) NOT NULL,
  `warehouse_id` varchar(128) NOT NULL,
  `uom_code` varchar(32) NOT NULL,
  `approved_amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `reason` varchar(256) NOT NULL,
  `status` varchar(32) NOT NULL,
  `active_step` varchar(32) NOT NULL,
  `attempt_count` int NOT NULL,
  `max_attempts` int NOT NULL,
  `version` bigint NOT NULL,
  `inventory_operation_id` bigint DEFAULT NULL,
  `inventory_ledger_transaction_id` bigint DEFAULT NULL,
  `payment_refund_transaction_id` bigint DEFAULT NULL,
  `order_refund_operation_id` bigint DEFAULT NULL,
  `order_return_operation_id` bigint DEFAULT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `correlation_id` varchar(36) NOT NULL,
  `causation_id` varchar(36) DEFAULT NULL,
  `inventory_occurred_at` datetime(6) NOT NULL,
  `payment_occurred_at` datetime(6) NOT NULL,
  `order_refund_occurred_at` datetime(6) NOT NULL,
  `order_return_occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`saga_id`),
  UNIQUE KEY `uk_cm_after_sale_resolution_tenant_id` (`tenant_id`,`saga_id`),
  UNIQUE KEY `uk_cm_after_sale_resolution` (`tenant_id`,`after_sale_id`),
  KEY `idx_cm_after_sale_resolution_due` (`status`,`next_retry_at`,`lease_until`,`created_at`),
  CONSTRAINT `fk_cm_after_sale_resolution_case` FOREIGN KEY (`tenant_id`,`after_sale_id`)
    REFERENCES `cloudmold_after_sale_case` (`tenant_id`,`after_sale_id`),
  CONSTRAINT `fk_cm_after_sale_resolution_item` FOREIGN KEY (`tenant_id`,`after_sale_item_id`)
    REFERENCES `cloudmold_after_sale_item` (`tenant_id`,`after_sale_item_id`),
  CONSTRAINT `ck_cm_after_sale_resolution_status` CHECK (`status` IN ('REQUESTED','RETURNING_INVENTORY','INVENTORY_RETURNED','REFUNDING_PAYMENT','PAYMENT_REFUNDED','CONFIRMING_ORDER_REFUND','ORDER_REFUNDED','RETURNING_ORDER','ORDER_RETURNED','RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED')),
  CONSTRAINT `ck_cm_after_sale_resolution_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_after_sale_resolution_amount` CHECK (`approved_amount_minor` >= 0 AND `currency_code` = 'CNY'),
  CONSTRAINT `ck_cm_after_sale_resolution_attempt` CHECK (`attempt_count` >= 0 AND `max_attempts` = 8)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_resolution_saga_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `saga_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `active_step` varchar(32) NOT NULL,
  `attempt_count` int NOT NULL,
  `error_code` varchar(128) DEFAULT NULL,
  `error_message` varchar(512) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cm_after_sale_resolution_history` (`tenant_id`,`saga_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_return_fulfillment`
  ADD CONSTRAINT `fk_cm_return_fulfillment_after_sale`
  FOREIGN KEY (`tenant_id`,`after_sale_id`)
  REFERENCES `cloudmold_after_sale_case` (`tenant_id`,`after_sale_id`);

ALTER TABLE `cloudmold_return_fulfillment_item`
  ADD CONSTRAINT `fk_cm_return_fulfillment_item_after_sale`
  FOREIGN KEY (`tenant_id`,`after_sale_item_id`)
  REFERENCES `cloudmold_after_sale_item` (`tenant_id`,`after_sale_item_id`);
