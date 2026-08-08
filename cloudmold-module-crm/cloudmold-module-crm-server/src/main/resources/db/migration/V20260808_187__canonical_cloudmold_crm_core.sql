CREATE TABLE IF NOT EXISTS `cloudmold_crm_operation` (
  `operation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `idempotency_key` VARCHAR(128) NOT NULL,
  `command_type` VARCHAR(64) NOT NULL,
  `request_hash` CHAR(64) NOT NULL,
  `attempt_token` VARCHAR(36) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `aggregate_type` VARCHAR(64) NULL,
  `aggregate_id` VARCHAR(36) NULL,
  `result_json` JSON NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_crm_operation_tenant_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_crm_operation_aggregate` (`tenant_id`,`aggregate_type`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_customer` (
  `customer_id` VARCHAR(36) NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `customer_code` VARCHAR(64) NOT NULL,
  `customer_name` VARCHAR(200) NOT NULL,
  `level_code` VARCHAR(64) NULL,
  `lifecycle_status` VARCHAR(32) NOT NULL,
  `pool_status` VARCHAR(16) NOT NULL,
  `owner_principal_id` VARCHAR(128) NULL,
  `source_code` VARCHAR(64) NULL,
  `industry_code` VARCHAR(64) NULL,
  `region_code` VARCHAR(64) NULL,
  `next_follow_up_at` DATETIME(6) NULL,
  `version` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_crm_customer_tenant_code` (`tenant_id`,`customer_code`),
  KEY `idx_crm_customer_owner` (`tenant_id`,`owner_principal_id`,`pool_status`),
  CONSTRAINT `chk_crm_customer_pool_owner` CHECK ((`pool_status`='IN_POOL' AND `owner_principal_id` IS NULL) OR (`pool_status`='OWNED' AND `owner_principal_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_lead` (
  `lead_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `lead_code` VARCHAR(64) NOT NULL,
  `lead_name` VARCHAR(200) NOT NULL, `source_code` VARCHAR(64) NULL, `status` VARCHAR(32) NOT NULL,
  `owner_principal_id` VARCHAR(128) NOT NULL, `contact_channel_ref` VARCHAR(256) NULL,
  `masked_contact` VARCHAR(128) NULL, `next_follow_up_at` DATETIME(6) NULL, `version` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`lead_id`), UNIQUE KEY `uk_crm_lead_tenant_code` (`tenant_id`,`lead_code`),
  KEY `idx_crm_lead_owner_status` (`tenant_id`,`owner_principal_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_contact` (
  `contact_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `customer_id` VARCHAR(36) NOT NULL,
  `contact_name` VARCHAR(200) NOT NULL, `role_title` VARCHAR(128) NULL, `contact_channel_ref` VARCHAR(256) NULL,
  `masked_contact` VARCHAR(128) NULL, `is_primary` BIT(1) NOT NULL DEFAULT b'0', `status` VARCHAR(32) NOT NULL,
  `version` BIGINT NOT NULL, `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`contact_id`), KEY `idx_crm_contact_customer` (`tenant_id`,`customer_id`,`status`),
  CONSTRAINT `fk_crm_contact_customer` FOREIGN KEY (`customer_id`) REFERENCES `cloudmold_crm_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_opportunity` (
  `opportunity_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `opportunity_code` VARCHAR(64) NOT NULL,
  `customer_id` VARCHAR(36) NOT NULL, `opportunity_name` VARCHAR(200) NOT NULL, `stage` VARCHAR(32) NOT NULL,
  `expected_amount_minor` BIGINT NOT NULL, `currency_code` CHAR(3) NOT NULL, `expected_close_date` DATE NULL,
  `owner_principal_id` VARCHAR(128) NOT NULL, `version` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL, `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`opportunity_id`), UNIQUE KEY `uk_crm_opportunity_tenant_code` (`tenant_id`,`opportunity_code`),
  KEY `idx_crm_opportunity_customer` (`tenant_id`,`customer_id`,`stage`),
  CONSTRAINT `fk_crm_opportunity_customer` FOREIGN KEY (`customer_id`) REFERENCES `cloudmold_crm_customer` (`customer_id`),
  CONSTRAINT `chk_crm_opportunity_amount` CHECK (`expected_amount_minor` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_follow_up` (
  `follow_up_id` VARCHAR(36) NOT NULL, `tenant_id` BIGINT NOT NULL, `subject_type` VARCHAR(32) NOT NULL,
  `subject_id` VARCHAR(36) NOT NULL, `method_code` VARCHAR(32) NOT NULL, `summary` VARCHAR(1000) NOT NULL,
  `next_follow_up_at` DATETIME(6) NULL, `actor_principal_id` VARCHAR(128) NOT NULL,
  `occurred_at` DATETIME(6) NOT NULL, `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`follow_up_id`), KEY `idx_crm_follow_up_subject` (`tenant_id`,`subject_type`,`subject_id`,`occurred_at`),
  KEY `idx_crm_follow_up_actor_due` (`tenant_id`,`actor_principal_id`,`next_follow_up_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_status_history` (
  `history_id` BIGINT NOT NULL AUTO_INCREMENT, `tenant_id` BIGINT NOT NULL,
  `aggregate_type` VARCHAR(64) NOT NULL, `aggregate_id` VARCHAR(36) NOT NULL,
  `aggregate_version` BIGINT NOT NULL, `from_status` VARCHAR(32) NULL, `to_status` VARCHAR(32) NOT NULL,
  `reason_code` VARCHAR(128) NOT NULL, `actor_principal_id` VARCHAR(128) NOT NULL,
  `occurred_at` DATETIME(6) NOT NULL, `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`history_id`), UNIQUE KEY `uk_crm_status_history_version` (`tenant_id`,`aggregate_type`,`aggregate_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_crm_customer_owner_history` (
  `history_id` BIGINT NOT NULL AUTO_INCREMENT, `tenant_id` BIGINT NOT NULL, `customer_id` VARCHAR(36) NOT NULL,
  `aggregate_version` BIGINT NOT NULL, `from_owner_principal_id` VARCHAR(128) NULL,
  `to_owner_principal_id` VARCHAR(128) NULL, `from_pool_status` VARCHAR(16) NOT NULL,
  `to_pool_status` VARCHAR(16) NOT NULL, `reason_code` VARCHAR(128) NOT NULL,
  `actor_principal_id` VARCHAR(128) NOT NULL, `occurred_at` DATETIME(6) NOT NULL, `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`history_id`), UNIQUE KEY `uk_crm_owner_history_version` (`tenant_id`,`customer_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
