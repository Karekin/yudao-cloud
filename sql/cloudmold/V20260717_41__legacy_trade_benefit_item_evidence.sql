-- Preserve the exact legacy Trade order-item denominator before any benefit identity or funding resolution.
-- V2 assessment runs remain readable. Every new V3 run must carry immutable item evidence and still cannot import.

ALTER TABLE `cloudmold_order_benefit_migration_run`
  ADD COLUMN `source_item_count` int DEFAULT NULL AFTER `component_amount_minor`,
  ADD COLUMN `active_item_count` int DEFAULT NULL AFTER `source_item_count`,
  ADD COLUMN `excluded_item_count` int DEFAULT NULL AFTER `active_item_count`,
  ADD COLUMN `item_evidence_hash` char(64) DEFAULT NULL AFTER `excluded_item_count`,
  ADD COLUMN `item_evidence_benefit_amount_minor` bigint DEFAULT NULL AFTER `item_evidence_hash`,
  ADD COLUMN `item_evidence_complete` tinyint(1) NOT NULL DEFAULT 0 AFTER `item_evidence_benefit_amount_minor`,
  ADD CONSTRAINT `ck_cm_order_benefit_mig_run_item_evidence` CHECK (
    (`policy_version` IN ('legacy-trade-benefit-v1','legacy-trade-benefit-v2')
      AND `source_item_count` IS NULL AND `active_item_count` IS NULL AND `excluded_item_count` IS NULL
      AND `item_evidence_hash` IS NULL AND `item_evidence_benefit_amount_minor` IS NULL
      AND `item_evidence_complete`=0)
    OR
    (`policy_version`='legacy-trade-benefit-v3'
      AND `source_item_count`>0 AND `active_item_count`>=0 AND `excluded_item_count`>=0
      AND `source_item_count`=`active_item_count`+`excluded_item_count`
      AND `item_evidence_hash` REGEXP '^[0-9a-f]{64}$'
      AND `item_evidence_benefit_amount_minor` IS NOT NULL
      AND `item_evidence_complete`=1)
  );

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_migration_item` (
  `item_evidence_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `legacy_order_item_id` bigint NOT NULL,
  `legacy_item_snapshot_hash` char(64) NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `is_deleted` tinyint(1) NOT NULL,
  `legacy_spu_id` bigint DEFAULT NULL,
  `legacy_sku_id` bigint DEFAULT NULL,
  `source_product_identity_status` varchar(32) NOT NULL,
  `item_quantity` int NOT NULL,
  `unit_price_minor` bigint NOT NULL,
  `gross_amount_minor` bigint NOT NULL,
  `generic_discount_amount_minor` bigint NOT NULL,
  `coupon_amount_minor` bigint NOT NULL,
  `point_amount_minor` bigint NOT NULL,
  `vip_amount_minor` bigint NOT NULL,
  `delivery_amount_minor` bigint NOT NULL,
  `adjust_amount_minor` bigint NOT NULL,
  `pay_amount_minor` bigint NOT NULL,
  `used_point_quantity` int NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL DEFAULT 0,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`item_evidence_id`),
  UNIQUE KEY `uk_cm_order_benefit_mig_item_source`
    (`tenant_id`,`migration_run_id`,`legacy_order_item_id`),
  KEY `idx_cm_order_benefit_mig_item_candidate`
    (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_benefit_mig_item_candidate` FOREIGN KEY
    (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`)
    REFERENCES `cloudmold_order_benefit_migration_candidate`
      (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_benefit_mig_item_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_order_benefit_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_order_benefit_mig_item_hash`
    CHECK (`legacy_item_snapshot_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `ck_cm_order_benefit_mig_item_identity` CHECK (
    (`source_product_identity_status`='SOURCE_IDS_PRESENT' AND `legacy_spu_id`>0 AND `legacy_sku_id`>0)
    OR (`source_product_identity_status`='MISSING_SOURCE_IDS'
      AND (`legacy_spu_id` IS NULL OR `legacy_spu_id`<=0 OR `legacy_sku_id` IS NULL OR `legacy_sku_id`<=0))
  ),
  CONSTRAINT `ck_cm_order_benefit_mig_item_import`
    CHECK (`canonical_import_allowed`=0 AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
