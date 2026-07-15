-- Canonical Merchant deposit authority. Money is always an integer minor unit plus ISO-4217 currency.
-- Historical merchants stay SHADOW until one reconciled account is explicitly activated for enforcement.

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_deposit_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `account_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_merchant_deposit_operation` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `ck_cm_merchant_deposit_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Idempotency and immutable first result for Merchant deposit commands';

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_deposit_account` (
  `account_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `currency` char(3) NOT NULL,
  `required_amount_minor` bigint unsigned NOT NULL,
  `held_amount_minor` bigint unsigned NOT NULL,
  `frozen_amount_minor` bigint unsigned NOT NULL,
  `paid_amount_minor` bigint unsigned NOT NULL,
  `deducted_amount_minor` bigint unsigned NOT NULL,
  `coverage_status` varchar(32) NOT NULL,
  `enforcement_status` varchar(16) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `version` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_cm_merchant_deposit_tenant_id` (`tenant_id`,`account_id`),
  UNIQUE KEY `uk_cm_merchant_deposit_currency` (`tenant_id`,`merchant_id`,`currency`),
  CONSTRAINT `fk_cm_merchant_deposit_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `ck_cm_merchant_deposit_currency` CHECK (`currency` REGEXP '^[A-Z]{3}$'),
  CONSTRAINT `ck_cm_merchant_deposit_required` CHECK (`required_amount_minor` > 0),
  CONSTRAINT `ck_cm_merchant_deposit_frozen` CHECK (`frozen_amount_minor` <= `held_amount_minor`),
  CONSTRAINT `ck_cm_merchant_deposit_conservation` CHECK (
    `held_amount_minor` = `paid_amount_minor` - `deducted_amount_minor`),
  CONSTRAINT `ck_cm_merchant_deposit_coverage` CHECK (`coverage_status` IN (
    'SUFFICIENT','BID_RESTRICTED','SALES_BLOCKED')),
  CONSTRAINT `ck_cm_merchant_deposit_enforcement` CHECK (`enforcement_status` IN ('SHADOW','ENFORCED')),
  CONSTRAINT `ck_cm_merchant_deposit_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Current canonical Merchant deposit balance and threshold policy';

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_deposit_ledger_entry` (
  `ledger_entry_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `account_id` varchar(36) NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `currency` char(3) NOT NULL,
  `account_version` bigint unsigned NOT NULL,
  `entry_type` varchar(32) NOT NULL,
  `amount_minor` bigint unsigned NOT NULL,
  `held_delta_minor` bigint NOT NULL,
  `frozen_delta_minor` bigint NOT NULL,
  `held_before_minor` bigint unsigned NOT NULL,
  `held_after_minor` bigint unsigned NOT NULL,
  `frozen_before_minor` bigint unsigned NOT NULL,
  `frozen_after_minor` bigint unsigned NOT NULL,
  `required_before_minor` bigint unsigned NOT NULL,
  `required_after_minor` bigint unsigned NOT NULL,
  `paid_after_minor` bigint unsigned NOT NULL,
  `deducted_after_minor` bigint unsigned NOT NULL,
  `previous_coverage_status` varchar(32) DEFAULT NULL,
  `current_coverage_status` varchar(32) NOT NULL,
  `previous_enforcement_status` varchar(16) DEFAULT NULL,
  `current_enforcement_status` varchar(16) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `business_reference` varchar(128) NOT NULL,
  `reason_code` varchar(64) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`ledger_entry_id`),
  UNIQUE KEY `uk_cm_merchant_deposit_ledger_version` (`tenant_id`,`account_id`,`account_version`),
  UNIQUE KEY `uk_cm_merchant_deposit_ledger_reference`
    (`tenant_id`,`merchant_id`,`currency`,`business_reference`),
  KEY `idx_cm_merchant_deposit_ledger_run` (`tenant_id`,`merchant_id`,`occurred_at`),
  CONSTRAINT `fk_cm_merchant_deposit_ledger_account` FOREIGN KEY (`tenant_id`,`account_id`)
    REFERENCES `cloudmold_merchant_deposit_account` (`tenant_id`,`account_id`),
  CONSTRAINT `ck_cm_merchant_deposit_ledger_type` CHECK (`entry_type` IN (
    'ASSESSED','PAID','FROZEN','UNFROZEN','DEDUCTED','ENFORCEMENT_ACTIVATED')),
  CONSTRAINT `ck_cm_merchant_deposit_ledger_held` CHECK (
    `held_after_minor` = `held_before_minor` + `held_delta_minor`),
  CONSTRAINT `ck_cm_merchant_deposit_ledger_frozen` CHECK (
    `frozen_after_minor` = `frozen_before_minor` + `frozen_delta_minor`
    AND `frozen_after_minor` <= `held_after_minor`),
  CONSTRAINT `ck_cm_merchant_deposit_ledger_coverage` CHECK (`current_coverage_status` IN (
    'SUFFICIENT','BID_RESTRICTED','SALES_BLOCKED')),
  CONSTRAINT `ck_cm_merchant_deposit_ledger_enforcement` CHECK (
    `current_enforcement_status` IN ('SHADOW','ENFORCED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable canonical Merchant deposit movements and policy changes';
