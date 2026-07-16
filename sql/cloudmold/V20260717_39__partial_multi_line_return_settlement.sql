-- Canonical partial and multi-line return settlement.
-- Additive red-zone migration. Legacy Trade/Pay/AfterSale tables remain compatibility sources only.

ALTER TABLE `cloudmold_payment`
  DROP CHECK `ck_payment_status`,
  ADD CONSTRAINT `ck_payment_status` CHECK (`status` IN ('CAPTURED','PARTIALLY_REFUNDED','REFUNDED'));

CREATE TABLE IF NOT EXISTS `cloudmold_order_return_settlement` (
  `order_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `total_item_count` int NOT NULL,
  `returned_item_count` int NOT NULL,
  `returned_quantity` decimal(24,6) NOT NULL,
  `refunded_net_amount_minor` bigint NOT NULL,
  `reversed_benefit_amount_minor` bigint NOT NULL,
  `version` bigint unsigned NOT NULL,
  `last_after_sale_id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_cm_order_return_settlement_tenant` (`tenant_id`,`order_id`),
  CONSTRAINT `fk_cm_order_return_settlement_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `ck_cm_order_return_settlement_status` CHECK (`status` IN ('PARTIAL','FULL')),
  CONSTRAINT `ck_cm_order_return_settlement_counts` CHECK (
    `total_item_count` > 0 AND `returned_item_count` >= 0
    AND `returned_item_count` <= `total_item_count`),
  CONSTRAINT `ck_cm_order_return_settlement_values` CHECK (
    `returned_quantity` > 0 AND `refunded_net_amount_minor` >= 0
    AND `reversed_benefit_amount_minor` >= 0 AND `version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Order-owned cumulative return settlement, orthogonal to commercial order status';

CREATE TABLE IF NOT EXISTS `cloudmold_order_item_return_settlement` (
  `order_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `ordered_quantity` decimal(24,6) NOT NULL,
  `returned_quantity` decimal(24,6) NOT NULL,
  `returned_gross_amount_minor` bigint NOT NULL,
  `reversed_benefit_amount_minor` bigint NOT NULL,
  `refunded_net_amount_minor` bigint NOT NULL,
  `version` bigint unsigned NOT NULL,
  `last_after_sale_id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`order_item_id`),
  UNIQUE KEY `uk_cm_order_item_return_settlement_tenant` (`tenant_id`,`order_item_id`),
  KEY `idx_cm_order_item_return_settlement_order` (`tenant_id`,`order_id`),
  CONSTRAINT `fk_cm_order_item_return_settlement_item` FOREIGN KEY (`tenant_id`,`order_item_id`)
    REFERENCES `cloudmold_order_item` (`tenant_id`,`order_item_id`),
  CONSTRAINT `fk_cm_order_item_return_settlement_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `ck_cm_order_item_return_settlement_qty` CHECK (
    `ordered_quantity` > 0 AND `returned_quantity` > 0
    AND `returned_quantity` <= `ordered_quantity`),
  CONSTRAINT `ck_cm_order_item_return_settlement_money` CHECK (
    `returned_gross_amount_minor` >= 0 AND `reversed_benefit_amount_minor` >= 0
    AND `refunded_net_amount_minor` >= 0
    AND `returned_gross_amount_minor` = `reversed_benefit_amount_minor` + `refunded_net_amount_minor`
    AND `version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Cumulative return settlement at immutable Order item grain';

CREATE TABLE IF NOT EXISTS `cloudmold_order_return_effect` (
  `settlement_effect_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `after_sale_item_id` varchar(36) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `gross_amount_minor` bigint NOT NULL,
  `benefit_amount_minor` bigint NOT NULL,
  `net_amount_minor` bigint NOT NULL,
  `inventory_operation_id` bigint NOT NULL,
  `inventory_ledger_transaction_id` bigint NOT NULL,
  `payment_refund_transaction_id` bigint NOT NULL,
  `benefit_reversal_batch_id` varchar(36) DEFAULT NULL,
  `order_settlement_version` bigint unsigned NOT NULL,
  `item_settlement_version` bigint unsigned NOT NULL,
  `full_return` bit(1) NOT NULL,
  `correlation_id` varchar(36) NOT NULL,
  `causation_id` varchar(36) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`settlement_effect_id`),
  UNIQUE KEY `uk_cm_order_return_effect_tenant` (`tenant_id`,`settlement_effect_id`),
  UNIQUE KEY `uk_cm_order_return_effect_after_sale` (`tenant_id`,`after_sale_id`),
  KEY `idx_cm_order_return_effect_order` (`tenant_id`,`order_id`,`order_item_id`),
  CONSTRAINT `fk_cm_order_return_effect_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `fk_cm_order_return_effect_item` FOREIGN KEY (`tenant_id`,`order_item_id`)
    REFERENCES `cloudmold_order_item` (`tenant_id`,`order_item_id`),
  CONSTRAINT `ck_cm_order_return_effect_qty` CHECK (`quantity` > 0),
  CONSTRAINT `ck_cm_order_return_effect_money` CHECK (
    `gross_amount_minor` >= 0 AND `benefit_amount_minor` >= 0 AND `net_amount_minor` > 0
    AND `gross_amount_minor` = `benefit_amount_minor` + `net_amount_minor`),
  CONSTRAINT `ck_cm_order_return_effect_benefit` CHECK (
    (`benefit_amount_minor` = 0 AND `benefit_reversal_batch_id` IS NULL)
    OR (`benefit_amount_minor` > 0 AND `benefit_reversal_batch_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable participant evidence for one AfterSale return settlement';

ALTER TABLE `cloudmold_after_sale_resolution_saga`
  ADD COLUMN `order_settlement_effect_id` varchar(36) DEFAULT NULL AFTER `order_return_operation_id`,
  ADD COLUMN `order_settlement_version` bigint unsigned DEFAULT NULL AFTER `order_settlement_effect_id`,
  ADD COLUMN `order_return_full` bit(1) DEFAULT NULL AFTER `order_settlement_version`,
  DROP CHECK `ck_cm_after_sale_resolution_status`,
  ADD CONSTRAINT `ck_cm_after_sale_resolution_status` CHECK (`status` IN (
    'REQUESTED','RETURNING_INVENTORY','INVENTORY_RETURNED',
    'REVERSING_BENEFITS','BENEFITS_REVERSED',
    'REFUNDING_PAYMENT','PAYMENT_REFUNDED','SETTLING_ORDER','ORDER_SETTLED',
    'CONFIRMING_ORDER_REFUND','ORDER_REFUNDED','RETURNING_ORDER','ORDER_RETURNED',
    'RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED'));

ALTER TABLE `cloudmold_after_sale_benefit_reversal`
  DROP CHECK `ck_cm_after_sale_benefit_reversal_entitlement`,
  ADD CONSTRAINT `ck_cm_after_sale_benefit_reversal_entitlement` CHECK (
    (`entitlement_id` IS NULL AND `entitlement_effect_status` = 'NOT_REQUIRED')
    OR (`entitlement_id` IS NOT NULL
      AND `entitlement_effect_status` IN ('RETAINED_PARTIAL','RETURNED')));
