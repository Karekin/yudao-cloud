-- Governed target-mapping admission for legacy Trade Orders.
-- Mapping rows are explicit qualifications. This migration never derives or creates a mapping from a legacy id.

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_migration_source_mapping` (
  `mapping_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(16) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `target_type` varchar(16) NOT NULL,
  `target_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `spu_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `sku_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `qualified_by` varchar(128) NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_cm_catalog_source_mapping_tenant` (`tenant_id`,`mapping_id`),
  UNIQUE KEY `uk_cm_catalog_source_mapping_effective`
    (`tenant_id`,`source_system`,`source_type`,`source_id`,`version`),
  UNIQUE KEY `uk_cm_catalog_source_mapping_active`
    (`tenant_id`,`source_system`,`source_type`,`source_id`,`active_guard`),
  KEY `idx_cm_catalog_source_mapping_target` (`tenant_id`,`target_type`,`target_id`,`status`),
  CONSTRAINT `fk_cm_catalog_source_mapping_spu` FOREIGN KEY (`tenant_id`,`spu_id`)
    REFERENCES `cloudmold_catalog_spu` (`tenant_id`,`spu_id`),
  CONSTRAINT `fk_cm_catalog_source_mapping_sku` FOREIGN KEY (`tenant_id`,`sku_id`)
    REFERENCES `cloudmold_catalog_sku` (`tenant_id`,`sku_id`),
  CONSTRAINT `ck_cm_catalog_source_mapping_type` CHECK (
    (`source_type`='SPU' AND `target_type`='SPU' AND BINARY `target_id`=BINARY `spu_id` AND `sku_id` IS NULL)
    OR (`source_type`='SKU' AND `target_type`='SKU' AND BINARY `target_id`=BINARY `sku_id` AND `spu_id` IS NULL)
  ),
  CONSTRAINT `ck_cm_catalog_source_mapping_identity` CHECK (
    CHAR_LENGTH(TRIM(`source_system`))>0 AND CHAR_LENGTH(TRIM(`source_id`))>0
    AND BINARY `source_system`=BINARY UPPER(TRIM(`source_system`))
    AND BINARY `source_id`=BINARY TRIM(`source_id`)
    AND BINARY `source_id`<>BINARY `target_id`
  ),
  CONSTRAINT `ck_cm_catalog_source_mapping_evidence` CHECK (
    `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`qualification_ref`))>0 AND CHAR_LENGTH(TRIM(`qualified_by`))>0
  ),
  CONSTRAINT `ck_cm_catalog_source_mapping_status` CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_catalog_source_mapping_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicit qualified mapping from source product identity to existing canonical Catalog identity';

CREATE TABLE IF NOT EXISTS `cloudmold_order_migration_mapping_plan` (
  `mapping_plan_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(16) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `parent_source_order_id` varchar(128) DEFAULT NULL,
  `planned_target_id` varchar(36) NOT NULL,
  `planned_target_order_id` varchar(36) NOT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `mapping_policy_version` varchar(64) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `qualified_by` varchar(128) NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`mapping_plan_id`),
  UNIQUE KEY `uk_cm_order_mapping_plan_tenant` (`tenant_id`,`mapping_plan_id`),
  UNIQUE KEY `uk_cm_order_mapping_plan_effective`
    (`tenant_id`,`source_system`,`source_type`,`source_id`,`version`),
  UNIQUE KEY `uk_cm_order_mapping_plan_source_active`
    (`tenant_id`,`source_system`,`source_type`,`source_id`,`active_guard`),
  UNIQUE KEY `uk_cm_order_mapping_plan_target_active`
    (`tenant_id`,`source_type`,`planned_target_id`,`active_guard`),
  KEY `idx_cm_order_mapping_plan_parent`
    (`tenant_id`,`source_system`,`parent_source_order_id`,`status`),
  CONSTRAINT `ck_cm_order_mapping_plan_type` CHECK (
    (`source_type`='ORDER' AND `parent_source_order_id` IS NULL
      AND BINARY `planned_target_id`=BINARY `planned_target_order_id`)
    OR (`source_type`='ORDER_ITEM' AND CHAR_LENGTH(TRIM(`parent_source_order_id`))>0
      AND BINARY `planned_target_id`<>BINARY `planned_target_order_id`)
  ),
  CONSTRAINT `ck_cm_order_mapping_plan_identity` CHECK (
    BINARY `source_system`=BINARY UPPER(TRIM(`source_system`))
    AND CHAR_LENGTH(TRIM(`source_system`))>0 AND CHAR_LENGTH(TRIM(`source_id`))>0
    AND BINARY `source_id`=BINARY TRIM(`source_id`)
    AND `planned_target_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    AND `planned_target_order_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
  ),
  CONSTRAINT `ck_cm_order_mapping_plan_evidence` CHECK (
    `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`mapping_policy_version`))>0
    AND CHAR_LENGTH(TRIM(`qualification_ref`))>0 AND CHAR_LENGTH(TRIM(`qualified_by`))>0
  ),
  CONSTRAINT `ck_cm_order_mapping_plan_status` CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_order_mapping_plan_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicitly reviewed pre-import Order and OrderItem target-id plan; never auto-derived';

CREATE TABLE IF NOT EXISTS `cloudmold_order_legacy_status_mapping_policy` (
  `status_mapping_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_status` int NOT NULL,
  `canonical_status` varchar(32) NOT NULL,
  `policy_version` varchar(64) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `qualified_by` varchar(128) NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`status_mapping_id`),
  UNIQUE KEY `uk_cm_order_status_mapping_tenant` (`tenant_id`,`status_mapping_id`),
  UNIQUE KEY `uk_cm_order_status_mapping_effective`
    (`tenant_id`,`source_system`,`source_status`,`version`),
  UNIQUE KEY `uk_cm_order_status_mapping_active`
    (`tenant_id`,`source_system`,`source_status`,`active_guard`),
  CONSTRAINT `ck_cm_order_status_mapping_identity` CHECK (
    BINARY `source_system`=BINARY UPPER(TRIM(`source_system`)) AND CHAR_LENGTH(TRIM(`source_system`))>0
    AND CHAR_LENGTH(TRIM(`policy_version`))>0 AND CHAR_LENGTH(TRIM(`qualification_ref`))>0
    AND CHAR_LENGTH(TRIM(`qualified_by`))>0
  ),
  CONSTRAINT `ck_cm_order_status_mapping_target` CHECK (`canonical_status` IN (
    'PLACED','INVENTORY_RESERVED','PAYMENT_CONFIRMED','SHIPPED','COMPLETED','CANCELLED','REFUNDED','RETURNED')),
  CONSTRAINT `ck_cm_order_status_mapping_status` CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_order_status_mapping_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicit legacy lifecycle semantics mapped to canonical Order lifecycle';

CREATE TABLE IF NOT EXISTS `cloudmold_order_target_readiness_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(128) DEFAULT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL,
  `target_readiness_run_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_order_target_ready_op_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_order_target_ready_op_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_order_target_ready_op_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_order_target_ready_op_result` CHECK (
    (`status`=0 AND `target_readiness_run_id` IS NULL AND `result_json` IS NULL)
    OR (`status`=10 AND `target_readiness_run_id` IS NOT NULL AND `result_json` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_target_readiness_run` (
  `target_readiness_run_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `policy_version` varchar(64) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `target_mapping_evidence_hash` char(64) NOT NULL,
  `source_order_count` int NOT NULL,
  `active_order_count` int NOT NULL,
  `excluded_order_count` int NOT NULL,
  `source_item_count` int NOT NULL,
  `active_item_count` int NOT NULL,
  `excluded_item_count` int NOT NULL,
  `buyer_resolved_order_count` int NOT NULL,
  `order_mapping_qualified_count` int NOT NULL,
  `lifecycle_mapping_qualified_count` int NOT NULL,
  `fully_mapped_item_count` int NOT NULL,
  `mapping_admitted_order_count` int NOT NULL,
  `mapping_blocked_order_count` int NOT NULL,
  `canonical_import_allowed_order_count` int NOT NULL,
  `production_migration_enabled` tinyint(1) NOT NULL,
  `status` varchar(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`target_readiness_run_id`),
  UNIQUE KEY `uk_cm_order_target_ready_run_tenant` (`tenant_id`,`target_readiness_run_id`),
  UNIQUE KEY `uk_cm_order_target_ready_source_run`
    (`tenant_id`,`source_migration_run_id`,`target_readiness_run_id`),
  CONSTRAINT `fk_cm_order_target_ready_source_run` FOREIGN KEY (`tenant_id`,`source_migration_run_id`)
    REFERENCES `cloudmold_order_benefit_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_order_target_ready_denominator` CHECK (
    `source_order_count`>0 AND `source_order_count`=`active_order_count`+`excluded_order_count`
    AND `source_item_count`>0 AND `source_item_count`=`active_item_count`+`excluded_item_count`
    AND `active_order_count`=`mapping_admitted_order_count`+`mapping_blocked_order_count`),
  CONSTRAINT `ck_cm_order_target_ready_counts` CHECK (
    `buyer_resolved_order_count` BETWEEN 0 AND `active_order_count`
    AND `order_mapping_qualified_count` BETWEEN 0 AND `active_order_count`
    AND `lifecycle_mapping_qualified_count` BETWEEN 0 AND `active_order_count`
    AND `fully_mapped_item_count` BETWEEN 0 AND `active_item_count`
    AND `canonical_import_allowed_order_count`=0 AND `production_migration_enabled`=0),
  CONSTRAINT `ck_cm_order_target_ready_evidence` CHECK (
    `target_mapping_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`policy_version`))>0 AND CHAR_LENGTH(TRIM(`evidence_ref`))>0),
  CONSTRAINT `ck_cm_order_target_ready_status` CHECK (`status`='BLOCKED_REQUIRES_EXPLICIT_TARGET_MAPPINGS'),
  CONSTRAINT `ck_cm_order_target_ready_version` CHECK (`version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_target_readiness_order` (
  `order_readiness_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `target_readiness_run_id` varchar(36) NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `legacy_snapshot_hash` char(64) NOT NULL,
  `buyer_identity_status` varchar(16) NOT NULL,
  `buyer_source_identity_id` varchar(36) DEFAULT NULL,
  `buyer_principal_id` varchar(36) DEFAULT NULL,
  `buyer_identity_version` bigint DEFAULT NULL,
  `order_mapping_plan_id` varchar(36) DEFAULT NULL,
  `planned_order_id` varchar(36) DEFAULT NULL,
  `order_mapping_version` bigint DEFAULT NULL,
  `order_mapping_status` varchar(16) NOT NULL,
  `status_mapping_id` varchar(36) DEFAULT NULL,
  `canonical_order_status` varchar(32) DEFAULT NULL,
  `lifecycle_mapping_version` bigint DEFAULT NULL,
  `lifecycle_mapping_status` varchar(16) NOT NULL,
  `money_reconciliation_status` varchar(16) NOT NULL,
  `active_item_count` int NOT NULL,
  `fully_mapped_item_count` int NOT NULL,
  `mapping_readiness_status` varchar(16) NOT NULL,
  `blocker_codes` json NOT NULL,
  `mapping_admission_allowed` tinyint(1) NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL,
  `evidence_hash` char(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`order_readiness_id`),
  UNIQUE KEY `uk_cm_order_target_ready_order_source`
    (`tenant_id`,`target_readiness_run_id`,`legacy_order_id`),
  UNIQUE KEY `uk_cm_order_target_ready_order_lineage`
    (`tenant_id`,`target_readiness_run_id`,`order_readiness_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_target_ready_order_run` FOREIGN KEY (`tenant_id`,`target_readiness_run_id`)
    REFERENCES `cloudmold_order_target_readiness_run` (`tenant_id`,`target_readiness_run_id`),
  CONSTRAINT `fk_cm_order_target_ready_order_candidate`
    FOREIGN KEY (`tenant_id`,`source_migration_run_id`,`candidate_id`,`legacy_order_id`)
    REFERENCES `cloudmold_order_benefit_migration_candidate`
      (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_target_ready_order_plan` FOREIGN KEY (`tenant_id`,`order_mapping_plan_id`)
    REFERENCES `cloudmold_order_migration_mapping_plan` (`tenant_id`,`mapping_plan_id`),
  CONSTRAINT `fk_cm_order_target_ready_status_policy` FOREIGN KEY (`tenant_id`,`status_mapping_id`)
    REFERENCES `cloudmold_order_legacy_status_mapping_policy` (`tenant_id`,`status_mapping_id`),
  CONSTRAINT `ck_cm_order_target_ready_buyer` CHECK (
    (`buyer_identity_status`='RESOLVED' AND `buyer_source_identity_id` IS NOT NULL
      AND `buyer_principal_id` IS NOT NULL AND `buyer_identity_version`>0)
    OR (`buyer_identity_status` IN ('MISSING','AMBIGUOUS') AND `buyer_source_identity_id` IS NULL
      AND `buyer_principal_id` IS NULL AND `buyer_identity_version` IS NULL)),
  CONSTRAINT `ck_cm_order_target_ready_order_mapping` CHECK (
    (`order_mapping_status`='QUALIFIED' AND `order_mapping_plan_id` IS NOT NULL
      AND `planned_order_id` IS NOT NULL AND `order_mapping_version`>0)
    OR (`order_mapping_status` IN ('MISSING','AMBIGUOUS') AND `order_mapping_plan_id` IS NULL
      AND `planned_order_id` IS NULL AND `order_mapping_version` IS NULL)),
  CONSTRAINT `ck_cm_order_target_ready_lifecycle` CHECK (
    (`lifecycle_mapping_status`='QUALIFIED' AND `status_mapping_id` IS NOT NULL
      AND `canonical_order_status` IS NOT NULL AND `lifecycle_mapping_version`>0)
    OR (`lifecycle_mapping_status` IN ('MISSING','AMBIGUOUS') AND `status_mapping_id` IS NULL
      AND `canonical_order_status` IS NULL AND `lifecycle_mapping_version` IS NULL)),
  CONSTRAINT `ck_cm_order_target_ready_order_readiness` CHECK (
    `money_reconciliation_status` IN ('EXACT','INVALID','EXCLUDED')
    AND `mapping_readiness_status` IN ('READY','BLOCKED','EXCLUDED')
    AND `active_item_count`>=0 AND `fully_mapped_item_count` BETWEEN 0 AND `active_item_count`
    AND JSON_TYPE(`blocker_codes`)='ARRAY'
    AND ((`mapping_readiness_status`='READY' AND JSON_LENGTH(`blocker_codes`)=0
      AND `mapping_admission_allowed`=1)
      OR (`mapping_readiness_status`<>'READY' AND `mapping_admission_allowed`=0))
    AND `canonical_import_allowed`=0 AND `evidence_hash` REGEXP '^[0-9a-f]{64}$' AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_order_benefit_migration_item`
  ADD UNIQUE KEY `uk_cm_order_benefit_mig_item_tenant_id` (`tenant_id`,`item_evidence_id`);

CREATE TABLE IF NOT EXISTS `cloudmold_order_target_readiness_item` (
  `item_readiness_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `target_readiness_run_id` varchar(36) NOT NULL,
  `order_readiness_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `legacy_order_item_id` bigint NOT NULL,
  `item_evidence_id` varchar(36) NOT NULL,
  `legacy_item_snapshot_hash` char(64) NOT NULL,
  `spu_mapping_id` varchar(36) DEFAULT NULL,
  `canonical_spu_id` varchar(36) DEFAULT NULL,
  `spu_mapping_version` bigint DEFAULT NULL,
  `spu_mapping_status` varchar(16) NOT NULL,
  `sku_mapping_id` varchar(36) DEFAULT NULL,
  `canonical_sku_id` varchar(36) DEFAULT NULL,
  `sku_mapping_version` bigint DEFAULT NULL,
  `sku_mapping_status` varchar(16) NOT NULL,
  `order_item_mapping_plan_id` varchar(36) DEFAULT NULL,
  `planned_order_item_id` varchar(36) DEFAULT NULL,
  `planned_order_id` varchar(36) DEFAULT NULL,
  `order_item_mapping_version` bigint DEFAULT NULL,
  `order_item_mapping_status` varchar(16) NOT NULL,
  `money_reconciliation_status` varchar(16) NOT NULL,
  `mapping_readiness_status` varchar(16) NOT NULL,
  `blocker_codes` json NOT NULL,
  `mapping_admission_allowed` tinyint(1) NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL,
  `evidence_hash` char(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`item_readiness_id`),
  UNIQUE KEY `uk_cm_order_target_ready_item_source`
    (`tenant_id`,`target_readiness_run_id`,`legacy_order_item_id`),
  CONSTRAINT `fk_cm_order_target_ready_item_order`
    FOREIGN KEY (`tenant_id`,`target_readiness_run_id`,`order_readiness_id`,`legacy_order_id`)
    REFERENCES `cloudmold_order_target_readiness_order`
      (`tenant_id`,`target_readiness_run_id`,`order_readiness_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_target_ready_item_source` FOREIGN KEY (`tenant_id`,`item_evidence_id`)
    REFERENCES `cloudmold_order_benefit_migration_item` (`tenant_id`,`item_evidence_id`),
  CONSTRAINT `fk_cm_order_target_ready_item_spu` FOREIGN KEY (`tenant_id`,`spu_mapping_id`)
    REFERENCES `cloudmold_catalog_migration_source_mapping` (`tenant_id`,`mapping_id`),
  CONSTRAINT `fk_cm_order_target_ready_item_sku` FOREIGN KEY (`tenant_id`,`sku_mapping_id`)
    REFERENCES `cloudmold_catalog_migration_source_mapping` (`tenant_id`,`mapping_id`),
  CONSTRAINT `fk_cm_order_target_ready_item_plan` FOREIGN KEY (`tenant_id`,`order_item_mapping_plan_id`)
    REFERENCES `cloudmold_order_migration_mapping_plan` (`tenant_id`,`mapping_plan_id`),
  CONSTRAINT `ck_cm_order_target_ready_item_spu` CHECK (
    (`spu_mapping_status`='QUALIFIED' AND `spu_mapping_id` IS NOT NULL
      AND `canonical_spu_id` IS NOT NULL AND `spu_mapping_version`>0)
    OR (`spu_mapping_status` IN ('MISSING','AMBIGUOUS') AND `spu_mapping_id` IS NULL
      AND `canonical_spu_id` IS NULL AND `spu_mapping_version` IS NULL)),
  CONSTRAINT `ck_cm_order_target_ready_item_sku` CHECK (
    (`sku_mapping_status`='QUALIFIED' AND `sku_mapping_id` IS NOT NULL
      AND `canonical_sku_id` IS NOT NULL AND `sku_mapping_version`>0)
    OR (`sku_mapping_status` IN ('MISSING','AMBIGUOUS') AND `sku_mapping_id` IS NULL
      AND `canonical_sku_id` IS NULL AND `sku_mapping_version` IS NULL)),
  CONSTRAINT `ck_cm_order_target_ready_item_plan` CHECK (
    (`order_item_mapping_status`='QUALIFIED' AND `order_item_mapping_plan_id` IS NOT NULL
      AND `planned_order_item_id` IS NOT NULL AND `planned_order_id` IS NOT NULL
      AND `order_item_mapping_version`>0)
    OR (`order_item_mapping_status` IN ('MISSING','AMBIGUOUS') AND `order_item_mapping_plan_id` IS NULL
      AND `planned_order_item_id` IS NULL AND `planned_order_id` IS NULL
      AND `order_item_mapping_version` IS NULL)),
  CONSTRAINT `ck_cm_order_target_ready_item_readiness` CHECK (
    `money_reconciliation_status` IN ('EXACT','INVALID','EXCLUDED')
    AND `mapping_readiness_status` IN ('READY','BLOCKED','EXCLUDED')
    AND JSON_TYPE(`blocker_codes`)='ARRAY'
    AND ((`mapping_readiness_status`='READY' AND JSON_LENGTH(`blocker_codes`)=0
      AND `mapping_admission_allowed`=1)
      OR (`mapping_readiness_status`<>'READY' AND `mapping_admission_allowed`=0))
    AND `canonical_import_allowed`=0 AND `evidence_hash` REGEXP '^[0-9a-f]{64}$' AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
