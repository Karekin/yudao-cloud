-- Immutable canonical Order benefit application, line allocation and economic funding truth.
-- Legacy Trade discounts remain adapter inputs; they do not own this model.

ALTER TABLE `cloudmold_order_operation`
  ADD UNIQUE KEY `uk_order_operation_tenant_id` (`tenant_id`,`operation_id`);

ALTER TABLE `cloudmold_order_item`
  ADD COLUMN `line_key` varchar(128) DEFAULT NULL AFTER `order_id`,
  ADD COLUMN `discount_amount_minor` bigint DEFAULT NULL AFTER `line_amount_minor`,
  ADD COLUMN `net_amount_minor` bigint DEFAULT NULL AFTER `discount_amount_minor`;

UPDATE `cloudmold_order_item`
SET `line_key` = `order_item_id`,
    `discount_amount_minor` = 0,
    `net_amount_minor` = `line_amount_minor`
WHERE `line_key` IS NULL OR `discount_amount_minor` IS NULL OR `net_amount_minor` IS NULL;

ALTER TABLE `cloudmold_order_item`
  MODIFY COLUMN `line_key` varchar(128) NOT NULL,
  MODIFY COLUMN `discount_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `net_amount_minor` bigint NOT NULL,
  ADD UNIQUE KEY `uk_order_item_line` (`tenant_id`,`order_id`,`line_key`),
  ADD UNIQUE KEY `uk_order_item_order_ref` (`tenant_id`,`order_id`,`order_item_id`),
  DROP CHECK `ck_order_item_money`,
  ADD CONSTRAINT `ck_order_item_money` CHECK (
    `unit_price_minor` >= 0 AND `line_amount_minor` >= 0
    AND `line_amount_minor` = `unit_price_minor` * `quantity`
    AND `discount_amount_minor` >= 0 AND `discount_amount_minor` <= `line_amount_minor`
    AND `net_amount_minor` = `line_amount_minor` - `discount_amount_minor`
  );

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_application` (
  `benefit_application_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `application_key` varchar(128) NOT NULL,
  `benefit_type` varchar(32) NOT NULL,
  `benefit_source_type` varchar(32) NOT NULL,
  `benefit_source_id` varchar(128) NOT NULL,
  `benefit_source_version` bigint unsigned NOT NULL,
  `entitlement_id` varchar(128) DEFAULT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `calculation_digest` char(64) NOT NULL,
  `operation_id` bigint NOT NULL,
  `version` bigint unsigned NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`benefit_application_id`),
  UNIQUE KEY `uk_order_benefit_app_tenant_id` (`tenant_id`,`benefit_application_id`),
  UNIQUE KEY `uk_order_benefit_app_key` (`tenant_id`,`order_id`,`application_key`),
  UNIQUE KEY `uk_order_benefit_source_version` (
    `tenant_id`,`order_id`,`benefit_source_type`,`benefit_source_id`,`benefit_source_version`),
  KEY `idx_order_benefit_entitlement` (`tenant_id`,`entitlement_id`),
  CONSTRAINT `fk_order_benefit_app_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `fk_order_benefit_app_operation` FOREIGN KEY (`tenant_id`,`operation_id`)
    REFERENCES `cloudmold_order_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `ck_order_benefit_app_type` CHECK (
    `benefit_type` IN ('COUPON','PROMOTION','ALLOWANCE','CAMPAIGN')),
  CONSTRAINT `ck_order_benefit_app_source_version` CHECK (`benefit_source_version` > 0),
  CONSTRAINT `ck_order_benefit_app_money` CHECK (`amount_minor` > 0 AND `currency_code` = 'CNY'),
  CONSTRAINT `ck_order_benefit_app_version` CHECK (`version` = 1),
  CONSTRAINT `ck_order_benefit_app_digest` CHECK (
    `calculation_digest` REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable benefit source/version application on one canonical Order';

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_allocation` (
  `benefit_allocation_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `benefit_application_id` varchar(36) NOT NULL,
  `allocation_key` varchar(128) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `line_key` varchar(128) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`benefit_allocation_id`),
  UNIQUE KEY `uk_order_benefit_alloc_tenant_id` (`tenant_id`,`benefit_allocation_id`),
  UNIQUE KEY `uk_order_benefit_alloc_app_ref` (
    `tenant_id`,`order_id`,`benefit_application_id`,`benefit_allocation_id`),
  UNIQUE KEY `uk_order_benefit_alloc_key` (`tenant_id`,`order_id`,`allocation_key`),
  UNIQUE KEY `uk_order_benefit_alloc_line` (`tenant_id`,`benefit_application_id`,`order_item_id`),
  CONSTRAINT `fk_order_benefit_alloc_app` FOREIGN KEY (`tenant_id`,`benefit_application_id`)
    REFERENCES `cloudmold_order_benefit_application` (`tenant_id`,`benefit_application_id`),
  CONSTRAINT `fk_order_benefit_alloc_item` FOREIGN KEY (`tenant_id`,`order_id`,`order_item_id`)
    REFERENCES `cloudmold_order_item` (`tenant_id`,`order_id`,`order_item_id`),
  CONSTRAINT `fk_order_benefit_alloc_line` FOREIGN KEY (`tenant_id`,`order_id`,`line_key`)
    REFERENCES `cloudmold_order_item` (`tenant_id`,`order_id`,`line_key`),
  CONSTRAINT `ck_order_benefit_alloc_money` CHECK (`amount_minor` > 0 AND `currency_code` = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable exact Order line allocation of one benefit application';

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_funding` (
  `benefit_funding_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `benefit_application_id` varchar(36) NOT NULL,
  `benefit_allocation_id` varchar(36) NOT NULL,
  `funding_key` varchar(128) NOT NULL,
  `funder_type` varchar(32) NOT NULL,
  `funder_id` varchar(128) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`benefit_funding_id`),
  UNIQUE KEY `uk_order_benefit_funding_tenant_id` (`tenant_id`,`benefit_funding_id`),
  UNIQUE KEY `uk_order_benefit_funding_key` (`tenant_id`,`order_id`,`funding_key`),
  UNIQUE KEY `uk_order_benefit_funder` (`tenant_id`,`benefit_allocation_id`,`funder_type`,`funder_id`),
  CONSTRAINT `fk_order_benefit_funding_alloc` FOREIGN KEY (
    `tenant_id`,`order_id`,`benefit_application_id`,`benefit_allocation_id`)
    REFERENCES `cloudmold_order_benefit_allocation` (
      `tenant_id`,`order_id`,`benefit_application_id`,`benefit_allocation_id`),
  CONSTRAINT `ck_order_benefit_funder_type` CHECK (`funder_type` IN ('PLATFORM','MERCHANT','PARTNER')),
  CONSTRAINT `ck_order_benefit_funding_money` CHECK (`amount_minor` > 0 AND `currency_code` = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable economic funding split for one Order benefit allocation';
