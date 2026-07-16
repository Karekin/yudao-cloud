-- Canonical AfterSale reversal of immutable Order Benefit allocations and funding.
-- Cash refund remains Payment-owned net money; non-cash benefit reversal is a distinct fact.

ALTER TABLE `cloudmold_after_sale_item`
  ADD COLUMN `discount_amount_minor` bigint DEFAULT NULL AFTER `line_amount_minor`,
  ADD COLUMN `net_amount_minor` bigint DEFAULT NULL AFTER `discount_amount_minor`;

UPDATE `cloudmold_after_sale_item`
SET `discount_amount_minor` = 0,
    `net_amount_minor` = `line_amount_minor`
WHERE `discount_amount_minor` IS NULL OR `net_amount_minor` IS NULL;

ALTER TABLE `cloudmold_after_sale_item`
  MODIFY COLUMN `discount_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `net_amount_minor` bigint NOT NULL,
  ADD CONSTRAINT `ck_cm_after_sale_item_money` CHECK (
    `line_amount_minor` >= 0
    AND `discount_amount_minor` >= 0
    AND `discount_amount_minor` <= `line_amount_minor`
    AND `net_amount_minor` = `line_amount_minor` - `discount_amount_minor`
  );

ALTER TABLE `cloudmold_after_sale_resolution_saga`
  ADD COLUMN `gross_amount_minor` bigint DEFAULT NULL AFTER `approved_amount_minor`,
  ADD COLUMN `benefit_amount_minor` bigint DEFAULT NULL AFTER `gross_amount_minor`,
  ADD COLUMN `net_amount_minor` bigint DEFAULT NULL AFTER `benefit_amount_minor`,
  ADD COLUMN `benefit_reversal_status` varchar(32) DEFAULT NULL AFTER `net_amount_minor`,
  ADD COLUMN `benefit_reversal_batch_id` varchar(36) DEFAULT NULL AFTER `benefit_reversal_status`,
  ADD COLUMN `benefit_reversal_amount_minor` bigint DEFAULT NULL AFTER `benefit_reversal_batch_id`,
  ADD COLUMN `benefit_reversal_occurred_at` datetime(6) DEFAULT NULL AFTER `benefit_reversal_amount_minor`;

UPDATE `cloudmold_after_sale_resolution_saga`
SET `gross_amount_minor` = `approved_amount_minor`,
    `benefit_amount_minor` = 0,
    `net_amount_minor` = `approved_amount_minor`,
    `benefit_reversal_status` = 'NOT_REQUIRED',
    `benefit_reversal_amount_minor` = 0
WHERE `gross_amount_minor` IS NULL
   OR `benefit_amount_minor` IS NULL
   OR `net_amount_minor` IS NULL
   OR `benefit_reversal_status` IS NULL
   OR `benefit_reversal_amount_minor` IS NULL;

ALTER TABLE `cloudmold_after_sale_resolution_saga`
  MODIFY COLUMN `gross_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `benefit_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `net_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `benefit_reversal_status` varchar(32) NOT NULL,
  MODIFY COLUMN `benefit_reversal_amount_minor` bigint NOT NULL,
  DROP CHECK `ck_cm_after_sale_resolution_status`,
  ADD CONSTRAINT `ck_cm_after_sale_resolution_status` CHECK (`status` IN (
    'REQUESTED','RETURNING_INVENTORY','INVENTORY_RETURNED',
    'REVERSING_BENEFITS','BENEFITS_REVERSED',
    'REFUNDING_PAYMENT','PAYMENT_REFUNDED','CONFIRMING_ORDER_REFUND','ORDER_REFUNDED',
    'RETURNING_ORDER','ORDER_RETURNED','RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED')),
  ADD CONSTRAINT `ck_cm_after_sale_resolution_money_v2` CHECK (
    `gross_amount_minor` >= 0
    AND `benefit_amount_minor` >= 0
    AND `net_amount_minor` >= 0
    AND `gross_amount_minor` = `benefit_amount_minor` + `net_amount_minor`
    AND `approved_amount_minor` = `net_amount_minor`
    AND `benefit_reversal_amount_minor` >= 0
    AND `benefit_reversal_amount_minor` <= `benefit_amount_minor`
  ),
  ADD CONSTRAINT `ck_cm_after_sale_benefit_reversal_status` CHECK (
    (`benefit_reversal_status` = 'NOT_REQUIRED'
      AND `benefit_amount_minor` = 0
      AND `benefit_reversal_amount_minor` = 0
      AND `benefit_reversal_batch_id` IS NULL
      AND `benefit_reversal_occurred_at` IS NULL)
    OR (`benefit_reversal_status` = 'PENDING'
      AND `benefit_amount_minor` > 0
      AND `benefit_reversal_amount_minor` = 0
      AND `benefit_reversal_batch_id` IS NULL
      AND `benefit_reversal_occurred_at` IS NULL)
    OR (`benefit_reversal_status` = 'RECORDED'
      AND `benefit_amount_minor` > 0
      AND `benefit_reversal_amount_minor` = `benefit_amount_minor`
      AND `benefit_reversal_batch_id` IS NOT NULL
      AND `benefit_reversal_occurred_at` IS NOT NULL)
  );

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_benefit_reversal` (
  `benefit_reversal_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `reversal_batch_id` varchar(36) NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `after_sale_item_id` varchar(36) NOT NULL,
  `order_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `order_item_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `benefit_application_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `benefit_allocation_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `benefit_type` varchar(32) NOT NULL,
  `benefit_source_type` varchar(32) NOT NULL,
  `benefit_source_id` varchar(128) NOT NULL,
  `benefit_source_version` bigint unsigned NOT NULL,
  `entitlement_id` varchar(128) DEFAULT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `entitlement_effect_status` varchar(32) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`benefit_reversal_id`),
  UNIQUE KEY `uk_cm_after_sale_benefit_reversal_tenant_id` (`tenant_id`,`benefit_reversal_id`),
  UNIQUE KEY `uk_cm_after_sale_benefit_reversal_alloc` (`tenant_id`,`after_sale_id`,`benefit_allocation_id`),
  KEY `idx_cm_after_sale_benefit_reversal_batch` (`tenant_id`,`reversal_batch_id`),
  CONSTRAINT `fk_cm_after_sale_benefit_reversal_case` FOREIGN KEY (`tenant_id`,`after_sale_id`)
    REFERENCES `cloudmold_after_sale_case` (`tenant_id`,`after_sale_id`),
  CONSTRAINT `fk_cm_after_sale_benefit_reversal_item` FOREIGN KEY (`tenant_id`,`after_sale_item_id`)
    REFERENCES `cloudmold_after_sale_item` (`tenant_id`,`after_sale_item_id`),
  CONSTRAINT `fk_cm_after_sale_benefit_reversal_alloc` FOREIGN KEY (
    `tenant_id`,`order_id`,`benefit_application_id`,`benefit_allocation_id`)
    REFERENCES `cloudmold_order_benefit_allocation` (
      `tenant_id`,`order_id`,`benefit_application_id`,`benefit_allocation_id`),
  CONSTRAINT `ck_cm_after_sale_benefit_reversal_money` CHECK (
    `amount_minor` > 0 AND `currency_code` = 'CNY'),
  CONSTRAINT `ck_cm_after_sale_benefit_reversal_entitlement` CHECK (
    `entitlement_id` IS NULL AND `entitlement_effect_status` = 'NOT_REQUIRED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable AfterSale reversal effect against one original Order Benefit allocation';

CREATE TABLE IF NOT EXISTS `cloudmold_after_sale_benefit_funding_reversal` (
  `funding_reversal_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `benefit_reversal_id` varchar(36) NOT NULL,
  `reversal_batch_id` varchar(36) NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `benefit_funding_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `funder_type` varchar(32) NOT NULL,
  `funder_id` varchar(128) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`funding_reversal_id`),
  UNIQUE KEY `uk_cm_after_sale_funding_reversal_tenant_id` (`tenant_id`,`funding_reversal_id`),
  UNIQUE KEY `uk_cm_after_sale_funding_reversal_source` (`tenant_id`,`after_sale_id`,`benefit_funding_id`),
  KEY `idx_cm_after_sale_funding_reversal_batch` (`tenant_id`,`reversal_batch_id`),
  CONSTRAINT `fk_cm_after_sale_funding_reversal_parent` FOREIGN KEY (`tenant_id`,`benefit_reversal_id`)
    REFERENCES `cloudmold_after_sale_benefit_reversal` (`tenant_id`,`benefit_reversal_id`),
  CONSTRAINT `fk_cm_after_sale_funding_reversal_source` FOREIGN KEY (`tenant_id`,`benefit_funding_id`)
    REFERENCES `cloudmold_order_benefit_funding` (`tenant_id`,`benefit_funding_id`),
  CONSTRAINT `ck_cm_after_sale_funding_reversal_type` CHECK (
    `funder_type` IN ('PLATFORM','MERCHANT','PARTNER')),
  CONSTRAINT `ck_cm_after_sale_funding_reversal_money` CHECK (
    `amount_minor` > 0 AND `currency_code` = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable exact reversal of one original Order Benefit funding share';
