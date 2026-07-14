-- Extend the existing Order Cancellation Saga for paid-but-unshipped orders.
-- V09 remains the immutable unpaid-cancellation baseline.

ALTER TABLE `cloudmold_order_header`
  DROP CHECK `ck_order_cancellation_fence`,
  ADD CONSTRAINT `ck_order_cancellation_fence` CHECK (
    (`status` <> 'CANCELLATION_PENDING' AND `cancellation_saga_id` IS NULL
      AND `pre_cancellation_status` IS NULL)
    OR (`status` = 'CANCELLATION_PENDING' AND `cancellation_saga_id` IS NOT NULL
      AND `pre_cancellation_status` IN ('INVENTORY_RESERVED','PAYMENT_CONFIRMED'))
    OR (`status` = 'CANCELLED' AND (`cancellation_saga_id` IS NULL
      OR (`cancellation_saga_id` IS NOT NULL
        AND `pre_cancellation_status` IN ('INVENTORY_RESERVED','PAYMENT_CONFIRMED'))))
  );

ALTER TABLE `cloudmold_order_cancellation_saga`
  ADD COLUMN `cancellation_mode` varchar(32) NOT NULL DEFAULT 'UNPAID_RESERVED' AFTER `saga_id`,
  ADD COLUMN `payment_id` varchar(36) DEFAULT NULL AFTER `released_reservation_count`,
  ADD COLUMN `payment_version_at_request` bigint unsigned DEFAULT NULL AFTER `payment_id`,
  ADD COLUMN `payment_refund_transaction_id` bigint DEFAULT NULL AFTER `payment_version_at_request`,
  ADD COLUMN `payment_status` varchar(32) DEFAULT NULL AFTER `payment_refund_transaction_id`,
  ADD COLUMN `expected_fulfillment_count` int unsigned NOT NULL DEFAULT 0 AFTER `payment_status`,
  ADD COLUMN `cancelled_fulfillment_count` int unsigned NOT NULL DEFAULT 0 AFTER `expected_fulfillment_count`,
  ADD COLUMN `payment_refund_occurred_at` datetime(6) DEFAULT NULL AFTER `finalize_occurred_at`,
  DROP CHECK `ck_cancel_saga_source_status`,
  DROP CHECK `ck_cancel_saga_status`,
  DROP CHECK `ck_cancel_saga_step`,
  ADD CONSTRAINT `ck_cancel_saga_mode` CHECK (`cancellation_mode` IN ('UNPAID_RESERVED','PAID_UNSHIPPED')),
  ADD CONSTRAINT `ck_cancel_saga_source_status` CHECK (
    (`cancellation_mode`='UNPAID_RESERVED' AND `order_status_at_request`='INVENTORY_RESERVED')
    OR (`cancellation_mode`='PAID_UNSHIPPED' AND `order_status_at_request`='PAYMENT_CONFIRMED')),
  ADD CONSTRAINT `ck_cancel_saga_status` CHECK (`status` IN (
    'REQUESTED','CANCELLING_FULFILLMENT','FULFILLMENT_CANCELLED','REFUNDING_PAYMENT','PAYMENT_REFUNDED',
    'RELEASING_RESERVATIONS','RESERVATIONS_RELEASED','CANCELLING_ORDER',
    'RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED')),
  ADD CONSTRAINT `ck_cancel_saga_step` CHECK (`active_step` IN (
    'CANCEL_FULFILLMENT','REFUND_PAYMENT','RELEASE_RESERVATIONS','CANCEL_ORDER','NONE')),
  ADD CONSTRAINT `ck_cancel_saga_paid_shape` CHECK (
    (`cancellation_mode`='UNPAID_RESERVED' AND `payment_id` IS NULL
      AND `payment_version_at_request` IS NULL AND `expected_fulfillment_count`=0)
    OR (`cancellation_mode`='PAID_UNSHIPPED' AND `payment_id` IS NOT NULL
      AND `payment_version_at_request` > 0 AND `expected_fulfillment_count`=1)),
  ADD CONSTRAINT `ck_cancel_saga_fulfillment_counts` CHECK (
    `cancelled_fulfillment_count` <= `expected_fulfillment_count`);

ALTER TABLE `cloudmold_fulfillment_order`
  ADD COLUMN `cancellation_saga_id` varchar(36) DEFAULT NULL AFTER `status`,
  ADD COLUMN `pre_cancellation_status` varchar(32) DEFAULT NULL AFTER `cancellation_saga_id`,
  ADD UNIQUE KEY `uk_fulfillment_cancellation_saga` (`tenant_id`,`cancellation_saga_id`),
  DROP CHECK `ck_fulfillment_status`,
  ADD CONSTRAINT `ck_fulfillment_status` CHECK (`status` IN (
    'CREATED','CANCELLATION_PENDING','CANCELLED','SHIPPED','IN_TRANSIT','DELIVERED')),
  ADD CONSTRAINT `ck_fulfillment_cancellation_fence` CHECK (
    (`status` NOT IN ('CANCELLATION_PENDING','CANCELLED') AND `cancellation_saga_id` IS NULL
      AND `pre_cancellation_status` IS NULL)
    OR (`status`='CANCELLATION_PENDING' AND `cancellation_saga_id` IS NOT NULL
      AND `pre_cancellation_status`='CREATED')
    OR (`status`='CANCELLED' AND `cancellation_saga_id` IS NOT NULL
      AND `pre_cancellation_status`='CREATED'));

CREATE TABLE IF NOT EXISTS `cloudmold_order_cancellation_saga_fulfillment` (
  `saga_fulfillment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `saga_id` varchar(36) NOT NULL,
  `fulfillment_id` varchar(36) NOT NULL,
  `fulfillment_version_at_request` bigint unsigned NOT NULL,
  `status_at_request` varchar(32) NOT NULL,
  `request_idempotency_key` varchar(128) NOT NULL,
  `finalize_idempotency_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `fulfillment_operation_id` bigint DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`saga_fulfillment_id`),
  UNIQUE KEY `uk_cancel_saga_fulfillment` (`tenant_id`,`saga_id`,`fulfillment_id`),
  UNIQUE KEY `uk_cancel_saga_fulfillment_request` (`tenant_id`,`request_idempotency_key`),
  UNIQUE KEY `uk_cancel_saga_fulfillment_finalize` (`tenant_id`,`finalize_idempotency_key`),
  CONSTRAINT `fk_cancel_saga_fulfillment_saga` FOREIGN KEY (`tenant_id`,`saga_id`)
    REFERENCES `cloudmold_order_cancellation_saga` (`tenant_id`,`saga_id`),
  CONSTRAINT `fk_cancel_saga_fulfillment_order` FOREIGN KEY (`tenant_id`,`fulfillment_id`)
    REFERENCES `cloudmold_fulfillment_order` (`tenant_id`,`fulfillment_id`),
  CONSTRAINT `ck_cancel_saga_fulfillment_source` CHECK (`status_at_request`='CREATED'),
  CONSTRAINT `ck_cancel_saga_fulfillment_status` CHECK (`status` IN (
    'FENCED','RUNNING','RETRY_SCHEDULED','CANCELLED','MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Exact paid cancellation Fulfillment checkpoint';
