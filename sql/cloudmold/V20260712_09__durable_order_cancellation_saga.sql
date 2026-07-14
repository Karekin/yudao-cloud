-- Durable pre-payment Order cancellation Saga. The Order fence prevents payment/fulfillment races.

ALTER TABLE `cloudmold_order_header`
  ADD COLUMN `cancellation_saga_id` varchar(36) DEFAULT NULL AFTER `refund_id`,
  ADD COLUMN `pre_cancellation_status` varchar(32) DEFAULT NULL AFTER `cancellation_saga_id`,
  ADD UNIQUE KEY `uk_order_cancellation_saga` (`tenant_id`,`cancellation_saga_id`),
  DROP CHECK `ck_order_status`,
  ADD CONSTRAINT `ck_order_status` CHECK (`status` IN (
    'PLACED','INVENTORY_RESERVED','PAYMENT_CONFIRMED','SHIPPED','COMPLETED',
    'CANCELLATION_PENDING','CANCELLED','REFUNDED','RETURNED')),
  ADD CONSTRAINT `ck_order_cancellation_fence` CHECK (
    (`status` <> 'CANCELLATION_PENDING' AND `cancellation_saga_id` IS NULL
      AND `pre_cancellation_status` IS NULL)
    OR (`status` = 'CANCELLATION_PENDING' AND `cancellation_saga_id` IS NOT NULL
      AND `pre_cancellation_status` = 'INVENTORY_RESERVED')
    OR (`status` = 'CANCELLED' AND (`cancellation_saga_id` IS NULL
      OR (`cancellation_saga_id` IS NOT NULL
        AND `pre_cancellation_status` = 'INVENTORY_RESERVED')))
  );

ALTER TABLE `cloudmold_inventory_reservation`
  ADD UNIQUE KEY `uk_inventory_reservation_tenant_id` (`tenant_id`,`reservation_id`);

CREATE TABLE IF NOT EXISTS `cloudmold_order_cancellation_saga_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `saga_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cancel_saga_operation_idempotency` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cancellation Saga command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_order_cancellation_saga` (
  `saga_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `order_status_at_request` varchar(32) NOT NULL,
  `order_version_at_request` bigint unsigned NOT NULL,
  `status` varchar(32) NOT NULL,
  `active_step` varchar(32) NOT NULL,
  `expected_reservation_count` int unsigned NOT NULL,
  `released_reservation_count` int unsigned NOT NULL DEFAULT 0,
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `max_attempts` int unsigned NOT NULL DEFAULT 8,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  `reason` varchar(256) NOT NULL,
  `correlation_id` char(36) NOT NULL,
  `causation_id` char(36) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `finalize_occurred_at` datetime(6) NOT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`saga_id`),
  UNIQUE KEY `uk_cancel_saga_tenant_id` (`tenant_id`,`saga_id`),
  UNIQUE KEY `uk_cancel_saga_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cancel_saga_order` (`tenant_id`,`order_id`),
  KEY `idx_cancel_saga_due` (`status`,`next_retry_at`,`lease_until`),
  KEY `idx_cancel_saga_run` (`tenant_id`,`run_id`),
  CONSTRAINT `fk_cancel_saga_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `ck_cancel_saga_source_status` CHECK (`order_status_at_request` = 'INVENTORY_RESERVED'),
  CONSTRAINT `ck_cancel_saga_status` CHECK (`status` IN (
    'REQUESTED','RELEASING_RESERVATIONS','RESERVATIONS_RELEASED','CANCELLING_ORDER',
    'RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED')),
  CONSTRAINT `ck_cancel_saga_step` CHECK (`active_step` IN (
    'RELEASE_RESERVATIONS','CANCEL_ORDER','NONE')),
  CONSTRAINT `ck_cancel_saga_counts` CHECK (
    `released_reservation_count` <= `expected_reservation_count`
    AND `attempt_count` <= `max_attempts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable Order-owned cancellation process manager';

CREATE TABLE IF NOT EXISTS `cloudmold_order_cancellation_saga_item` (
  `saga_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `saga_id` varchar(36) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `reservation_id` varchar(36) DEFAULT NULL,
  `owner_id` varchar(128) DEFAULT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `warehouse_id` varchar(128) DEFAULT NULL,
  `stock_status` varchar(32) DEFAULT NULL,
  `quality_status` varchar(32) DEFAULT NULL,
  `uom_code` varchar(32) DEFAULT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `release_idempotency_key` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `inventory_operation_id` bigint DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `released_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`saga_item_id`),
  UNIQUE KEY `uk_cancel_saga_item_order_item` (`tenant_id`,`saga_id`,`order_item_id`),
  UNIQUE KEY `uk_cancel_saga_item_reservation` (`tenant_id`,`saga_id`,`reservation_id`),
  UNIQUE KEY `uk_cancel_saga_item_idempotency` (`tenant_id`,`release_idempotency_key`),
  CONSTRAINT `fk_cancel_saga_item_saga` FOREIGN KEY (`tenant_id`,`saga_id`)
    REFERENCES `cloudmold_order_cancellation_saga` (`tenant_id`,`saga_id`),
  CONSTRAINT `fk_cancel_saga_item_order_item` FOREIGN KEY (`tenant_id`,`order_item_id`)
    REFERENCES `cloudmold_order_item` (`tenant_id`,`order_item_id`),
  CONSTRAINT `fk_cancel_saga_item_reservation` FOREIGN KEY (`tenant_id`,`reservation_id`)
    REFERENCES `cloudmold_inventory_reservation` (`tenant_id`,`reservation_id`),
  CONSTRAINT `ck_cancel_saga_item_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_cancel_saga_item_status` CHECK (`status` IN (
    'NOT_REQUIRED','PENDING','RUNNING','RETRY_SCHEDULED','RELEASED','MANUAL_REVIEW')),
  CONSTRAINT `ck_cancel_saga_item_reservation_shape` CHECK (
    (`status` = 'NOT_REQUIRED' AND `reservation_id` IS NULL AND `release_idempotency_key` IS NULL)
    OR (`status` <> 'NOT_REQUIRED' AND `reservation_id` IS NOT NULL
      AND `owner_id` IS NOT NULL AND `warehouse_id` IS NOT NULL
      AND `stock_status` IS NOT NULL AND `quality_status` IS NOT NULL
      AND `uom_code` IS NOT NULL AND `release_idempotency_key` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Exact reservation release steps and immutable Inventory command snapshot';

CREATE TABLE IF NOT EXISTS `cloudmold_order_cancellation_saga_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `saga_id` varchar(36) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `active_step` varchar(32) NOT NULL,
  `attempt_count` int unsigned NOT NULL,
  `expected_reservation_count` int unsigned NOT NULL,
  `released_reservation_count` int unsigned NOT NULL,
  `error_code` varchar(128) DEFAULT NULL,
  `error_message` varchar(512) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cancel_saga_history_version` (`tenant_id`,`saga_id`,`aggregate_version`),
  CONSTRAINT `fk_cancel_saga_history_saga` FOREIGN KEY (`tenant_id`,`saga_id`)
    REFERENCES `cloudmold_order_cancellation_saga` (`tenant_id`,`saga_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable cancellation Saga transition and recovery history';
