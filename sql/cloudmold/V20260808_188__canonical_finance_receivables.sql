CREATE TABLE IF NOT EXISTS `cloudmold_finance_receivables_operation` (
  `operation_id` BIGINT NOT NULL AUTO_INCREMENT, `tenant_id` BIGINT NOT NULL,
  `idempotency_key` VARCHAR(128) NOT NULL, `command_type` VARCHAR(64) NOT NULL, `request_hash` CHAR(64) NOT NULL,
  `attempt_token` VARCHAR(36) NOT NULL, `status` TINYINT NOT NULL DEFAULT 0,
  `aggregate_type` VARCHAR(64) NULL, `aggregate_id` VARCHAR(36) NULL, `result_json` JSON NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`operation_id`), UNIQUE KEY `uk_fin_ar_operation_tenant_key` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_finance_receivable_plan` (
  `receivable_plan_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `plan_code` VARCHAR(64) NOT NULL,
  `customer_id` VARCHAR(36) NOT NULL, `sales_contract_id` VARCHAR(36) NOT NULL, `currency_code` CHAR(3) NOT NULL,
  `planned_amount_minor` BIGINT NOT NULL, `allocated_amount_minor` BIGINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL, `created_by_principal_id` VARCHAR(128) NOT NULL,
  `last_modified_by_principal_id` VARCHAR(128) NOT NULL, `latest_reason_code` VARCHAR(128) NOT NULL,
  `due_date` DATE NOT NULL, `version` BIGINT NOT NULL, `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`receivable_plan_id`), UNIQUE KEY `uk_fin_ar_plan_tenant_code` (`tenant_id`,`plan_code`),
  KEY `idx_fin_ar_plan_customer` (`tenant_id`,`customer_id`,`currency_code`),
  KEY `idx_fin_ar_plan_contract` (`tenant_id`,`sales_contract_id`,`currency_code`),
  CONSTRAINT `chk_fin_ar_plan_amounts` CHECK (`planned_amount_minor` > 0 AND `allocated_amount_minor` BETWEEN 0 AND `planned_amount_minor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_finance_receipt` (
  `receipt_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `receipt_code` VARCHAR(64) NOT NULL,
  `customer_id` VARCHAR(36) NOT NULL, `sales_contract_id` VARCHAR(36) NOT NULL, `currency_code` CHAR(3) NOT NULL,
  `receipt_amount_minor` BIGINT NOT NULL, `allocated_amount_minor` BIGINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL, `external_reference` VARCHAR(256) NULL,
  `recorded_by_principal_id` VARCHAR(128) NOT NULL, `last_modified_by_principal_id` VARCHAR(128) NOT NULL,
  `latest_reason_code` VARCHAR(128) NOT NULL, `receipt_date` DATE NOT NULL, `version` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`receipt_id`), UNIQUE KEY `uk_fin_ar_receipt_tenant_code` (`tenant_id`,`receipt_code`),
  KEY `idx_fin_ar_receipt_customer` (`tenant_id`,`customer_id`,`currency_code`),
  KEY `idx_fin_ar_receipt_contract` (`tenant_id`,`sales_contract_id`,`currency_code`),
  CONSTRAINT `chk_fin_ar_receipt_amounts` CHECK (`receipt_amount_minor` > 0 AND `allocated_amount_minor` BETWEEN 0 AND `receipt_amount_minor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_finance_receipt_allocation` (
  `receipt_allocation_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `receipt_id` VARCHAR(36) NOT NULL,
  `receivable_plan_id` VARCHAR(36) NOT NULL, `customer_id` VARCHAR(36) NOT NULL, `sales_contract_id` VARCHAR(36) NOT NULL,
  `currency_code` CHAR(3) NOT NULL, `amount_minor` BIGINT NOT NULL, `status` VARCHAR(32) NOT NULL,
  `allocated_by_principal_id` VARCHAR(128) NOT NULL, `reason_code` VARCHAR(128) NOT NULL,
  `receipt_version` BIGINT NOT NULL, `receivable_plan_version` BIGINT NOT NULL, `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`receipt_allocation_id`), KEY `idx_fin_ar_allocation_receipt` (`tenant_id`,`receipt_id`),
  KEY `idx_fin_ar_allocation_plan` (`tenant_id`,`receivable_plan_id`),
  CONSTRAINT `fk_fin_ar_allocation_receipt` FOREIGN KEY (`receipt_id`) REFERENCES `cloudmold_finance_receipt` (`receipt_id`),
  CONSTRAINT `fk_fin_ar_allocation_plan` FOREIGN KEY (`receivable_plan_id`) REFERENCES `cloudmold_finance_receivable_plan` (`receivable_plan_id`),
  CONSTRAINT `chk_fin_ar_allocation_amount` CHECK (`amount_minor` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
