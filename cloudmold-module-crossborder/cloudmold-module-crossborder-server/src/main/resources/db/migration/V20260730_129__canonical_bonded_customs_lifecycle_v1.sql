CREATE TABLE IF NOT EXISTS `cloudmold_bonded_customs_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `aggregate_type` varchar(64) DEFAULT NULL,
  `aggregate_id` varchar(128) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_bc_operation_tenant_idem` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `ck_cm_bc_operation_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_bc_operation_hash` CHECK (`request_hash` REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_bonded_customs_case` (
  `case_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `case_no` varchar(32) NOT NULL,
  `mode` varchar(32) NOT NULL,
  `canonical_order_id` varchar(128) NOT NULL,
  `status` varchar(48) NOT NULL,
  `triple_match_status` varchar(32) NOT NULL,
  `customs_status` varchar(32) NOT NULL,
  `bonded_release_status` varchar(32) NOT NULL,
  `delivery_status` varchar(32) NOT NULL,
  `order_ref` varchar(128) NOT NULL,
  `payment_ref` varchar(128) NOT NULL,
  `logistics_ref` varchar(128) NOT NULL,
  `order_amount_minor` bigint DEFAULT NULL,
  `payment_amount_minor` bigint DEFAULT NULL,
  `logistics_amount_minor` bigint DEFAULT NULL,
  `currency` char(3) DEFAULT NULL,
  `buyer_identity_hash` varchar(64) DEFAULT NULL,
  `receiver_identity_hash` varchar(64) DEFAULT NULL,
  `declarant_identity_hash` varchar(64) DEFAULT NULL,
  `order_snapshot_ref` varchar(255) DEFAULT NULL,
  `payment_snapshot_ref` varchar(255) DEFAULT NULL,
  `logistics_snapshot_ref` varchar(255) DEFAULT NULL,
  `assessment_id` varchar(128) DEFAULT NULL,
  `hs_code` varchar(12) DEFAULT NULL,
  `positive_list_code` varchar(64) DEFAULT NULL,
  `goods_name` varchar(255) DEFAULT NULL,
  `goods_evidence_ref` varchar(255) DEFAULT NULL,
  `dutiable_amount_minor` bigint DEFAULT NULL,
  `consumption_tax_minor` bigint DEFAULT NULL,
  `value_added_tax_minor` bigint DEFAULT NULL,
  `total_tax_minor` bigint DEFAULT NULL,
  `tax_currency` char(3) DEFAULT NULL,
  `tax_evidence_ref` varchar(255) DEFAULT NULL,
  `approval_ref` varchar(128) DEFAULT NULL,
  `declaration_ref` varchar(128) DEFAULT NULL,
  `customs_acceptance_ref` varchar(128) DEFAULT NULL,
  `bonded_release_ref` varchar(128) DEFAULT NULL,
  `delivery_confirmation_ref` varchar(128) DEFAULT NULL,
  `close_reason` varchar(255) DEFAULT NULL,
  `created_by_principal_id` varchar(128) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `closed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`case_id`),
  UNIQUE KEY `uk_cm_bc_case_tenant_case` (`tenant_id`,`case_id`),
  UNIQUE KEY `uk_cm_bc_case_tenant_no` (`tenant_id`,`case_no`),
  KEY `idx_cm_bc_case_order` (`tenant_id`,`canonical_order_id`),
  CONSTRAINT `ck_cm_bc_case_mode` CHECK (`mode` = 'BONDED_RETAIL_IMPORT'),
  CONSTRAINT `ck_cm_bc_case_status` CHECK (`status` IN (
    'DRAFT','ELIGIBILITY_ASSESSED','GOODS_CLASSIFIED','TRIPLE_MATCHED','TAX_CALCULATED',
    'DECLARATION_APPROVED','DECLARATION_SUBMITTED','CUSTOMS_ACCEPTED','BONDED_RELEASED','DELIVERED','CLOSED')),
  CONSTRAINT `ck_cm_bc_case_triple_match_status` CHECK (`triple_match_status` IN ('PENDING','TRIPLE_MATCHED')),
  CONSTRAINT `ck_cm_bc_case_customs_status` CHECK (`customs_status` IN ('PENDING','DECLARATION_SUBMITTED','CUSTOMS_ACCEPTED')),
  CONSTRAINT `ck_cm_bc_case_bonded_release_status` CHECK (`bonded_release_status` IN ('PENDING','BONDED_RELEASED')),
  CONSTRAINT `ck_cm_bc_case_delivery_status` CHECK (`delivery_status` IN ('PENDING','DELIVERED')),
  CONSTRAINT `ck_cm_bc_case_version` CHECK (`version` > 0),
  CONSTRAINT `ck_cm_bc_case_amounts` CHECK (
    (`order_amount_minor` IS NULL OR `order_amount_minor` > 0)
    AND (`payment_amount_minor` IS NULL OR `payment_amount_minor` > 0)
    AND (`logistics_amount_minor` IS NULL OR `logistics_amount_minor` > 0)
    AND (`dutiable_amount_minor` IS NULL OR `dutiable_amount_minor` > 0)
    AND (`consumption_tax_minor` IS NULL OR `consumption_tax_minor` >= 0)
    AND (`value_added_tax_minor` IS NULL OR `value_added_tax_minor` >= 0)
    AND (`total_tax_minor` IS NULL OR `total_tax_minor` >= 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_bonded_customs_eligibility_assessment` (
  `assessment_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `case_id` varchar(128) NOT NULL,
  `facts_json` json NOT NULL,
  `options_json` json NOT NULL,
  `recommendation` varchar(255) NOT NULL,
  `risks_json` json NOT NULL,
  `confidence` decimal(5,4) NOT NULL,
  `missing_facts_json` json NOT NULL,
  `evidence_ref` varchar(255) NOT NULL,
  `assessed_by_principal_id` varchar(128) NOT NULL,
  `assessed_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`assessment_id`),
  KEY `idx_cm_bc_assessment_case` (`tenant_id`,`case_id`,`assessed_at`),
  CONSTRAINT `fk_cm_bc_assessment_case`
    FOREIGN KEY (`tenant_id`,`case_id`)
    REFERENCES `cloudmold_bonded_customs_case` (`tenant_id`,`case_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_cm_bc_assessment_confidence` CHECK (`confidence` >= 0 AND `confidence` <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_bonded_customs_status_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `case_id` varchar(128) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `operation` varchar(64) NOT NULL,
  `previous_status` varchar(48) DEFAULT NULL,
  `current_status` varchar(48) NOT NULL,
  `actor_principal_id` varchar(128) NOT NULL,
  `occurred_at` datetime NOT NULL,
  `detail_json` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_cm_bc_history_version` (`tenant_id`,`case_id`,`aggregate_version`),
  KEY `idx_cm_bc_history_case` (`tenant_id`,`case_id`,`history_id`),
  CONSTRAINT `fk_cm_bc_history_case`
    FOREIGN KEY (`tenant_id`,`case_id`)
    REFERENCES `cloudmold_bonded_customs_case` (`tenant_id`,`case_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_cm_bc_history_version` CHECK (`aggregate_version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
