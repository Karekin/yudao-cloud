-- Historical product identity governance for immutable legacy Trade Order Items.
-- Current product rows and majority source pairs are observations only; neither can qualify history.

CREATE TABLE IF NOT EXISTS `cloudmold_order_product_identity_qualification` (
  `qualification_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `item_evidence_id` varchar(36) NOT NULL,
  `legacy_order_item_id` bigint NOT NULL,
  `historical_spu_id` bigint NOT NULL,
  `historical_sku_id` bigint NOT NULL,
  `source_item_evidence_hash` char(64) NOT NULL,
  `historical_product_snapshot_hash` char(64) NOT NULL,
  `source_evidence_uri` varchar(512) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `qualified_by` varchar(128) NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`qualification_id`),
  UNIQUE KEY `uk_cm_order_product_identity_qual_tenant` (`tenant_id`,`qualification_id`),
  UNIQUE KEY `uk_cm_order_product_identity_qual_version`
    (`tenant_id`,`source_migration_run_id`,`item_evidence_id`,`version`),
  UNIQUE KEY `uk_cm_order_product_identity_qual_active`
    (`tenant_id`,`source_migration_run_id`,`item_evidence_id`,`active_guard`),
  CONSTRAINT `fk_cm_order_product_identity_qual_source`
    FOREIGN KEY (`tenant_id`,`item_evidence_id`)
    REFERENCES `cloudmold_order_benefit_migration_item` (`tenant_id`,`item_evidence_id`),
  CONSTRAINT `ck_cm_order_product_identity_qual_identity` CHECK (
    `legacy_order_item_id`>0 AND `historical_spu_id`>0 AND `historical_sku_id`>0),
  CONSTRAINT `ck_cm_order_product_identity_qual_evidence` CHECK (
    `source_item_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND `historical_product_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`source_evidence_uri`))>0
    AND CHAR_LENGTH(TRIM(`qualification_ref`))>0
    AND CHAR_LENGTH(TRIM(`qualified_by`))>0),
  CONSTRAINT `ck_cm_order_product_identity_qual_status`
    CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_order_product_identity_qual_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-OrderItem historical SPU/SKU identity qualification bound to immutable evidence';

CREATE TABLE IF NOT EXISTS `cloudmold_order_product_identity_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(128) DEFAULT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL,
  `identity_run_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_order_product_identity_op_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_order_product_identity_op_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_order_product_identity_op_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_order_product_identity_op_result` CHECK (
    (`status`=0 AND `identity_run_id` IS NULL AND `result_json` IS NULL)
    OR (`status`=10 AND `identity_run_id` IS NOT NULL AND `result_json` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_product_identity_run` (
  `identity_run_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `policy_version` varchar(64) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `governance_evidence_hash` char(64) NOT NULL,
  `source_item_count` int NOT NULL,
  `active_item_count` int NOT NULL,
  `excluded_item_count` int NOT NULL,
  `source_pair_unambiguous_count` int NOT NULL,
  `source_parent_conflict_item_count` int NOT NULL,
  `current_relation_observed_count` int NOT NULL,
  `historical_identity_qualified_count` int NOT NULL,
  `identity_admitted_item_count` int NOT NULL,
  `target_mapping_enabled` tinyint(1) NOT NULL,
  `status` varchar(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`identity_run_id`),
  UNIQUE KEY `uk_cm_order_product_identity_run_tenant` (`tenant_id`,`identity_run_id`),
  UNIQUE KEY `uk_cm_order_product_identity_run_source`
    (`tenant_id`,`source_migration_run_id`,`identity_run_id`),
  CONSTRAINT `fk_cm_order_product_identity_run_source`
    FOREIGN KEY (`tenant_id`,`source_migration_run_id`)
    REFERENCES `cloudmold_order_benefit_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_order_product_identity_run_denominator` CHECK (
    `source_item_count`>0 AND `source_item_count`=`active_item_count`+`excluded_item_count`
    AND `source_pair_unambiguous_count` BETWEEN 0 AND `active_item_count`
    AND `source_parent_conflict_item_count` BETWEEN 0 AND `active_item_count`
    AND `source_pair_unambiguous_count`+`source_parent_conflict_item_count`<=`active_item_count`
    AND `current_relation_observed_count` BETWEEN 0 AND `active_item_count`
    AND `historical_identity_qualified_count` BETWEEN 0 AND `active_item_count`
    AND `identity_admitted_item_count` BETWEEN 0 AND `active_item_count`),
  CONSTRAINT `ck_cm_order_product_identity_run_authority` CHECK (
    (`target_mapping_enabled`=1 AND `status`='READY_FOR_TARGET_MAPPING_ASSESSMENT'
      AND `identity_admitted_item_count`=`active_item_count`)
    OR (`target_mapping_enabled`=0
      AND `status`='BLOCKED_REQUIRES_HISTORICAL_PRODUCT_IDENTITY')),
  CONSTRAINT `ck_cm_order_product_identity_run_evidence` CHECK (
    `governance_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`policy_version`))>0 AND CHAR_LENGTH(TRIM(`evidence_ref`))>0),
  CONSTRAINT `ck_cm_order_product_identity_run_version` CHECK (`version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_product_identity_item` (
  `identity_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `identity_run_id` varchar(36) NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `item_evidence_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `legacy_order_item_id` bigint NOT NULL,
  `legacy_spu_id` bigint DEFAULT NULL,
  `legacy_sku_id` bigint DEFAULT NULL,
  `source_item_evidence_hash` char(64) NOT NULL,
  `source_parent_cardinality` int NOT NULL,
  `source_pair_status` varchar(64) NOT NULL,
  `current_reference_status` varchar(64) NOT NULL,
  `current_product_snapshot_hash` char(64) DEFAULT NULL,
  `qualification_id` varchar(36) DEFAULT NULL,
  `historical_identity_status` varchar(16) NOT NULL,
  `blocker_codes` json NOT NULL,
  `identity_admission_allowed` tinyint(1) NOT NULL,
  `target_mapping_allowed` tinyint(1) NOT NULL,
  `evidence_hash` char(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`identity_item_id`),
  UNIQUE KEY `uk_cm_order_product_identity_item_source`
    (`tenant_id`,`identity_run_id`,`item_evidence_id`),
  UNIQUE KEY `uk_cm_order_product_identity_item_lineage`
    (`tenant_id`,`identity_run_id`,`identity_item_id`,`legacy_order_item_id`),
  CONSTRAINT `fk_cm_order_product_identity_item_run`
    FOREIGN KEY (`tenant_id`,`identity_run_id`)
    REFERENCES `cloudmold_order_product_identity_run` (`tenant_id`,`identity_run_id`),
  CONSTRAINT `fk_cm_order_product_identity_item_source`
    FOREIGN KEY (`tenant_id`,`item_evidence_id`)
    REFERENCES `cloudmold_order_benefit_migration_item` (`tenant_id`,`item_evidence_id`),
  CONSTRAINT `fk_cm_order_product_identity_item_qualification`
    FOREIGN KEY (`tenant_id`,`qualification_id`)
    REFERENCES `cloudmold_order_product_identity_qualification` (`tenant_id`,`qualification_id`),
  CONSTRAINT `ck_cm_order_product_identity_item_pair` CHECK (
    `source_parent_cardinality`>=0
    AND `source_pair_status` IN ('SOURCE_IDS_MISSING','SOURCE_PAIR_UNAMBIGUOUS_NOT_HISTORICAL_VERSION',
      'SOURCE_SKU_PARENT_CONFLICT','EXCLUDED')),
  CONSTRAINT `ck_cm_order_product_identity_item_current` CHECK (
    (`current_reference_status`='CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION'
      AND `current_product_snapshot_hash` REGEXP '^[0-9a-f]{64}$')
    OR (`current_reference_status` IN ('CURRENT_RELATION_MISSING_OR_MISMATCH','NOT_OBSERVED_EXCLUDED')
      AND `current_product_snapshot_hash` IS NULL)),
  CONSTRAINT `ck_cm_order_product_identity_item_history` CHECK (
    (`historical_identity_status`='QUALIFIED' AND `qualification_id` IS NOT NULL
      AND `identity_admission_allowed`=1)
    OR (`historical_identity_status` IN ('MISSING','AMBIGUOUS','EXCLUDED')
      AND `qualification_id` IS NULL AND `identity_admission_allowed`=0)),
  CONSTRAINT `ck_cm_order_product_identity_item_authority` CHECK (
    JSON_TYPE(`blocker_codes`)='ARRAY'
    AND `target_mapping_allowed`=`identity_admission_allowed`
    AND `evidence_hash` REGEXP '^[0-9a-f]{64}$' AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_order_target_readiness_item`
  ADD COLUMN `product_identity_qualification_id` varchar(36) DEFAULT NULL
    AFTER `legacy_item_snapshot_hash`,
  ADD COLUMN `historical_product_identity_status` varchar(16) NOT NULL DEFAULT 'NOT_ASSESSED'
    AFTER `product_identity_qualification_id`,
  ADD CONSTRAINT `fk_cm_order_target_ready_item_product_identity`
    FOREIGN KEY (`tenant_id`,`product_identity_qualification_id`)
    REFERENCES `cloudmold_order_product_identity_qualification` (`tenant_id`,`qualification_id`),
  ADD CONSTRAINT `ck_cm_order_target_ready_item_product_identity` CHECK (
    (`historical_product_identity_status`='QUALIFIED' AND `product_identity_qualification_id` IS NOT NULL)
    OR (`historical_product_identity_status` IN ('NOT_ASSESSED','MISSING','AMBIGUOUS')
      AND `product_identity_qualification_id` IS NULL));
