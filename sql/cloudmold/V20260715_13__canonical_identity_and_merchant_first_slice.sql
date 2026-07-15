-- CloudMold canonical Identity and Merchant first slice.
-- Additive red-zone migration; Member/System remain credential/profile authorities.

CREATE TABLE IF NOT EXISTS `cloudmold_identity_principal` (
  `principal_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `principal_type` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`principal_id`),
  UNIQUE KEY `uk_cm_identity_principal_tenant_id` (`tenant_id`,`principal_id`),
  CONSTRAINT `ck_cm_identity_principal_type` CHECK (`principal_type` IN ('PLATFORM_OPERATOR','MEMBER','MERCHANT_OPERATOR','WAREHOUSE_OPERATOR')),
  CONSTRAINT `ck_cm_identity_principal_status` CHECK (`status` IN ('ACTIVE','DISABLED')),
  CONSTRAINT `ck_cm_identity_principal_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_identity_source_identity` (
  `source_identity_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `principal_id` varchar(36) NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`source_identity_id`),
  UNIQUE KEY `uk_cm_identity_source_tenant_id` (`tenant_id`,`source_identity_id`),
  UNIQUE KEY `uk_cm_identity_source_effective` (`tenant_id`,`source_system`,`source_type`,`source_id`,`valid_from`),
  UNIQUE KEY `uk_cm_identity_source_active` (`tenant_id`,`source_system`,`source_type`,`source_id`,`active_guard`),
  KEY `idx_cm_identity_source_principal` (`tenant_id`,`principal_id`,`status`),
  CONSTRAINT `fk_cm_identity_source_principal` FOREIGN KEY (`tenant_id`,`principal_id`)
    REFERENCES `cloudmold_identity_principal` (`tenant_id`,`principal_id`),
  CONSTRAINT `ck_cm_identity_source_status` CHECK (`status` IN ('ACTIVE','REVOKED')),
  CONSTRAINT `ck_cm_identity_source_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_identity_source_validity` CHECK (`valid_to` IS NULL OR `valid_to` > `valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_identity_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `principal_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_identity_operation` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `ck_cm_identity_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_legal_entity` (
  `legal_entity_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `legal_name` varchar(256) NOT NULL,
  `registration_hash_token` varchar(256) NOT NULL,
  `business_license_token` varchar(256) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`legal_entity_id`),
  UNIQUE KEY `uk_cm_merchant_legal_tenant_id` (`tenant_id`,`legal_entity_id`),
  UNIQUE KEY `uk_cm_merchant_registration_hash` (`tenant_id`,`registration_hash_token`),
  CONSTRAINT `ck_cm_merchant_legal_status` CHECK (`status` IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','SUSPENDED','CLOSED')),
  CONSTRAINT `ck_cm_merchant_legal_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_account` (
  `merchant_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_code` varchar(32) NOT NULL,
  `legal_entity_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`merchant_id`),
  UNIQUE KEY `uk_cm_merchant_account_tenant_id` (`tenant_id`,`merchant_id`),
  UNIQUE KEY `uk_cm_merchant_account_code` (`tenant_id`,`merchant_code`),
  KEY `idx_cm_merchant_account_legal` (`tenant_id`,`legal_entity_id`),
  CONSTRAINT `fk_cm_merchant_account_legal` FOREIGN KEY (`tenant_id`,`legal_entity_id`)
    REFERENCES `cloudmold_merchant_legal_entity` (`tenant_id`,`legal_entity_id`),
  CONSTRAINT `ck_cm_merchant_account_status` CHECK (`status` IN ('PENDING_ACTIVATION','ACTIVE','RESTRICTED','SUSPENDED','EXITING','CLOSED')),
  CONSTRAINT `ck_cm_merchant_account_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_shop` (
  `shop_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `channel_code` varchar(32) NOT NULL,
  `external_shop_id` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`shop_id`),
  UNIQUE KEY `uk_cm_merchant_shop_tenant_id` (`tenant_id`,`shop_id`),
  UNIQUE KEY `uk_cm_merchant_shop_channel` (`tenant_id`,`channel_code`,`external_shop_id`),
  KEY `idx_cm_merchant_shop_merchant` (`tenant_id`,`merchant_id`,`status`),
  CONSTRAINT `fk_cm_merchant_shop_account` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `ck_cm_merchant_shop_status` CHECK (`status` IN ('DRAFT','ACTIVE','PAUSED','CLOSED')),
  CONSTRAINT `ck_cm_merchant_shop_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_merchant_shop_distinct` CHECK (`shop_id` <> `merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_onboarding_application` (
  `application_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `legal_entity_id` varchar(36) NOT NULL,
  `owner_principal_id` varchar(36) NOT NULL,
  `channel_code` varchar(32) NOT NULL,
  `external_shop_id` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `decision_reason` varchar(512) DEFAULT NULL,
  `merchant_id` varchar(36) DEFAULT NULL,
  `shop_id` varchar(36) DEFAULT NULL,
  `owner_assignment_id` varchar(36) DEFAULT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_cm_merchant_onboarding_tenant_id` (`tenant_id`,`application_id`),
  UNIQUE KEY `uk_cm_merchant_onboarding_run` (`tenant_id`,`run_id`),
  KEY `idx_cm_merchant_onboarding_legal` (`tenant_id`,`legal_entity_id`),
  KEY `idx_cm_merchant_onboarding_owner` (`tenant_id`,`owner_principal_id`),
  CONSTRAINT `fk_cm_merchant_onboarding_legal` FOREIGN KEY (`tenant_id`,`legal_entity_id`)
    REFERENCES `cloudmold_merchant_legal_entity` (`tenant_id`,`legal_entity_id`),
  CONSTRAINT `fk_cm_merchant_onboarding_owner` FOREIGN KEY (`tenant_id`,`owner_principal_id`)
    REFERENCES `cloudmold_identity_principal` (`tenant_id`,`principal_id`),
  CONSTRAINT `ck_cm_merchant_onboarding_status` CHECK (`status` IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','WITHDRAWN')),
  CONSTRAINT `ck_cm_merchant_onboarding_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_operator_assignment` (
  `assignment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) NOT NULL,
  `principal_id` varchar(36) NOT NULL,
  `role_code` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`assignment_id`),
  UNIQUE KEY `uk_cm_merchant_assignment_tenant_id` (`tenant_id`,`assignment_id`),
  UNIQUE KEY `uk_cm_merchant_assignment_effective` (`tenant_id`,`merchant_id`,`shop_id`,`principal_id`,`role_code`,`valid_from`),
  UNIQUE KEY `uk_cm_merchant_assignment_active` (`tenant_id`,`merchant_id`,`shop_id`,`principal_id`,`role_code`,`active_guard`),
  KEY `idx_cm_merchant_assignment_shop` (`tenant_id`,`shop_id`,`status`),
  KEY `idx_cm_merchant_assignment_principal` (`tenant_id`,`principal_id`,`status`),
  CONSTRAINT `fk_cm_merchant_assignment_account` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_cm_merchant_assignment_shop` FOREIGN KEY (`tenant_id`,`shop_id`)
    REFERENCES `cloudmold_merchant_shop` (`tenant_id`,`shop_id`),
  CONSTRAINT `fk_cm_merchant_assignment_principal` FOREIGN KEY (`tenant_id`,`principal_id`)
    REFERENCES `cloudmold_identity_principal` (`tenant_id`,`principal_id`),
  CONSTRAINT `ck_cm_merchant_assignment_status` CHECK (`status` IN ('ACTIVE','REVOKED')),
  CONSTRAINT `ck_cm_merchant_assignment_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_merchant_assignment_validity` CHECK (`valid_to` IS NULL OR `valid_to` > `valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `aggregate_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_merchant_operation` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `ck_cm_merchant_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_status_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `aggregate_type` varchar(32) NOT NULL,
  `aggregate_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `operation_id` bigint NOT NULL,
  `reason` varchar(512) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cm_merchant_history_version` (`tenant_id`,`aggregate_type`,`aggregate_id`,`aggregate_version`),
  KEY `idx_cm_merchant_history_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_merchant_history_operation` FOREIGN KEY (`operation_id`)
    REFERENCES `cloudmold_merchant_operation` (`operation_id`),
  CONSTRAINT `ck_cm_merchant_history_version` CHECK (`aggregate_version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
