CREATE TABLE IF NOT EXISTS `cloudmold_sales_contract_operation` (
  `operation_id` BIGINT NOT NULL AUTO_INCREMENT, `tenant_id` BIGINT NOT NULL,
  `idempotency_key` VARCHAR(128) NOT NULL, `command_type` VARCHAR(64) NOT NULL,
  `request_hash` CHAR(64) NOT NULL, `attempt_token` VARCHAR(36) NOT NULL, `status` TINYINT NOT NULL DEFAULT 0,
  `aggregate_type` VARCHAR(64) NULL, `aggregate_id` VARCHAR(36) NULL, `result_json` JSON NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`operation_id`), UNIQUE KEY `uk_crm_contract_operation_key` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_sales_contract` (
  `sales_contract_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `contract_code` VARCHAR(64) NOT NULL,
  `contract_name` VARCHAR(255) NOT NULL, `customer_id` VARCHAR(36) NOT NULL,
  `seller_merchant_id` VARCHAR(128) NOT NULL, `seller_shop_id` VARCHAR(128) NOT NULL,
  `seller_legal_entity_id` VARCHAR(128) NOT NULL, `status` VARCHAR(32) NOT NULL,
  `currency_code` CHAR(3) NOT NULL, `total_amount_minor` BIGINT NOT NULL,
  `effective_date` DATE NOT NULL, `expires_on` DATE NULL, `approval_process_instance_id` VARCHAR(64) NULL,
  `created_by_principal_id` VARCHAR(128) NOT NULL, `updated_by_principal_id` VARCHAR(128) NOT NULL,
  `submitted_by_principal_id` VARCHAR(128) NULL, `version` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL, `submitted_at` DATETIME(6) NULL,
  PRIMARY KEY (`sales_contract_id`), UNIQUE KEY `uk_crm_contract_tenant_code` (`tenant_id`,`contract_code`),
  KEY `idx_crm_contract_customer_status` (`tenant_id`,`customer_id`,`status`),
  KEY `idx_crm_contract_seller` (`tenant_id`,`seller_merchant_id`,`seller_shop_id`),
  CONSTRAINT `fk_crm_contract_customer` FOREIGN KEY (`customer_id`) REFERENCES `cloudmold_crm_customer` (`customer_id`),
  CONSTRAINT `chk_crm_contract_amount` CHECK (`total_amount_minor` > 0),
  CONSTRAINT `chk_crm_contract_dates` CHECK (`expires_on` IS NULL OR `expires_on` >= `effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_sales_contract_item` (
  `sales_contract_item_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL,
  `sales_contract_id` VARCHAR(36) NOT NULL, `line_no` INT NOT NULL, `canonical_sku_id` VARCHAR(128) NOT NULL,
  `item_name` VARCHAR(255) NOT NULL, `uom_code` VARCHAR(64) NOT NULL, `quantity` DECIMAL(20,6) NOT NULL,
  `unit_price_minor` BIGINT NOT NULL, `line_amount_minor` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`sales_contract_item_id`), UNIQUE KEY `uk_crm_contract_item_line` (`tenant_id`,`sales_contract_id`,`line_no`),
  KEY `idx_crm_contract_item_sku` (`tenant_id`,`canonical_sku_id`),
  CONSTRAINT `fk_crm_contract_item_header` FOREIGN KEY (`sales_contract_id`) REFERENCES `cloudmold_sales_contract` (`sales_contract_id`),
  CONSTRAINT `chk_crm_contract_item_amount` CHECK (`quantity` > 0 AND `unit_price_minor` > 0 AND `line_amount_minor` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_sales_contract_status_history` (
  `history_id` BIGINT NOT NULL AUTO_INCREMENT, `tenant_id` BIGINT NOT NULL,
  `sales_contract_id` VARCHAR(36) NOT NULL, `operation_id` BIGINT NOT NULL, `aggregate_version` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL, `actor_principal_id` VARCHAR(128) NOT NULL, `actor_admin_user_id` BIGINT NOT NULL,
  `reason_code` VARCHAR(128) NOT NULL, `approval_process_instance_id` VARCHAR(64) NULL,
  `occurred_at` DATETIME(6) NOT NULL, `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`history_id`), UNIQUE KEY `uk_crm_contract_history_version` (`tenant_id`,`sales_contract_id`,`aggregate_version`),
  KEY `idx_crm_contract_history_process` (`tenant_id`,`approval_process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
