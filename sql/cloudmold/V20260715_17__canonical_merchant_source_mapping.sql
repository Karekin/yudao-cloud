-- Qualified tenant-scoped source mapping for canonical Merchant domain targets.
-- Additive only: legacy source identifiers remain references and never become canonical IDs.

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_source_mapping` (
  `mapping_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` varchar(36) NOT NULL,
  `legal_entity_id` varchar(36) DEFAULT NULL,
  `merchant_id` varchar(36) DEFAULT NULL,
  `shop_id` varchar(36) DEFAULT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `verification_ref` varchar(256) NOT NULL,
  `migration_run_id` varchar(128) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='ACTIVE' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_cm_merchant_source_tenant_id` (`tenant_id`,`mapping_id`),
  UNIQUE KEY `uk_cm_merchant_source_effective`
    (`tenant_id`,`source_system`,`source_type`,`source_id`,`valid_from`),
  UNIQUE KEY `uk_cm_merchant_source_active`
    (`tenant_id`,`source_system`,`source_type`,`source_id`,`active_guard`),
  KEY `idx_cm_merchant_source_target` (`tenant_id`,`target_type`,`target_id`,`status`),
  KEY `idx_cm_merchant_source_legal` (`tenant_id`,`legal_entity_id`),
  KEY `idx_cm_merchant_source_merchant` (`tenant_id`,`merchant_id`),
  KEY `idx_cm_merchant_source_shop` (`tenant_id`,`shop_id`),
  CONSTRAINT `fk_cm_merchant_source_legal` FOREIGN KEY (`tenant_id`,`legal_entity_id`)
    REFERENCES `cloudmold_merchant_legal_entity` (`tenant_id`,`legal_entity_id`),
  CONSTRAINT `fk_cm_merchant_source_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_cm_merchant_source_shop` FOREIGN KEY (`tenant_id`,`shop_id`)
    REFERENCES `cloudmold_merchant_shop` (`tenant_id`,`shop_id`),
  CONSTRAINT `ck_cm_merchant_source_target_type`
    CHECK (`target_type` IN ('LEGAL_ENTITY','MERCHANT','SHOP')),
  CONSTRAINT `ck_cm_merchant_source_target_shape` CHECK (
    (`target_type`='LEGAL_ENTITY' AND BINARY `target_id`=BINARY `legal_entity_id`
      AND `merchant_id` IS NULL AND `shop_id` IS NULL)
    OR (`target_type`='MERCHANT' AND BINARY `target_id`=BINARY `merchant_id`
      AND `legal_entity_id` IS NULL AND `shop_id` IS NULL)
    OR (`target_type`='SHOP' AND BINARY `target_id`=BINARY `shop_id`
      AND `legal_entity_id` IS NULL AND `merchant_id` IS NULL)
  ),
  CONSTRAINT `ck_cm_merchant_source_identity` CHECK (
    CHAR_LENGTH(TRIM(`source_system`))>0
    AND CHAR_LENGTH(TRIM(`source_type`))>0
    AND CHAR_LENGTH(TRIM(`source_id`))>0
    AND BINARY `source_system`=BINARY UPPER(TRIM(`source_system`))
    AND BINARY `source_type`=BINARY UPPER(TRIM(`source_type`))
    AND BINARY `source_id`=BINARY TRIM(`source_id`)
  ),
  CONSTRAINT `ck_cm_merchant_source_distinct_id` CHECK (BINARY `target_id`<>BINARY `source_id`),
  CONSTRAINT `ck_cm_merchant_source_evidence` CHECK (
    CHAR_LENGTH(TRIM(`verification_ref`))>0 AND CHAR_LENGTH(TRIM(`migration_run_id`))>0
  ),
  CONSTRAINT `ck_cm_merchant_source_status` CHECK (`status` IN ('ACTIVE','REVOKED')),
  CONSTRAINT `ck_cm_merchant_source_version` CHECK (`version`>=1),
  CONSTRAINT `ck_cm_merchant_source_validity` CHECK (`valid_to` IS NULL OR `valid_to`>`valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
