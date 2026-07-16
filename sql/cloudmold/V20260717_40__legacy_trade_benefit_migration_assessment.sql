-- Legacy Yudao Trade benefit migration assessment.
-- This migration records immutable evidence only. It never creates canonical Orders,
-- applications, allocations, funding rows, source mappings, or production import authority.

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_migration_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(128) DEFAULT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `migration_run_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_order_benefit_mig_op_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_order_benefit_mig_op_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_order_benefit_mig_op_type` CHECK (`command_type`='ASSESS_LEGACY_TRADE_V1'),
  CONSTRAINT `ck_cm_order_benefit_mig_op_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_order_benefit_mig_op_result` CHECK (
    (`status`=0 AND `migration_run_id` IS NULL AND `result_json` IS NULL)
    OR (`status`=10 AND `migration_run_id` IS NOT NULL AND `result_json` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_migration_run` (
  `migration_run_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_scope` varchar(48) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `source_watermark` datetime(6) NOT NULL,
  `source_order_count` int NOT NULL,
  `non_deleted_order_count` int NOT NULL,
  `deleted_excluded_count` int NOT NULL,
  `no_benefit_order_count` int NOT NULL,
  `benefit_evidence_pending_order_count` int NOT NULL,
  `quarantined_order_count` int NOT NULL,
  `benefit_component_count` int NOT NULL,
  `source_benefit_amount_minor` bigint NOT NULL,
  `component_amount_minor` bigint NOT NULL,
  `unresolved_identity_count` int NOT NULL,
  `unresolved_funding_count` int NOT NULL,
  `import_allowed_component_count` int NOT NULL,
  `production_migration_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(48) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`migration_run_id`),
  UNIQUE KEY `uk_cm_order_benefit_mig_run_tenant` (`tenant_id`,`migration_run_id`),
  KEY `idx_cm_order_benefit_mig_run_status` (`tenant_id`,`status`,`assessed_at`),
  CONSTRAINT `ck_cm_order_benefit_mig_run_scope` CHECK (`source_scope`='LOCAL_YUDAO_TRADE_CURRENT'),
  CONSTRAINT `ck_cm_order_benefit_mig_run_status` CHECK (`status`='BLOCKED_REQUIRES_GOVERNED_EVIDENCE'),
  CONSTRAINT `ck_cm_order_benefit_mig_run_version` CHECK (`version`=1),
  CONSTRAINT `ck_cm_order_benefit_mig_run_denominator` CHECK (
    `source_order_count`>0
    AND `source_order_count`=`non_deleted_order_count`+`deleted_excluded_count`
    AND `non_deleted_order_count`=`no_benefit_order_count`
      +`benefit_evidence_pending_order_count`+`quarantined_order_count`
  ),
  CONSTRAINT `ck_cm_order_benefit_mig_run_components` CHECK (
    `benefit_component_count`>=0 AND `unresolved_identity_count`=`benefit_component_count`
    AND `unresolved_funding_count`=`benefit_component_count`
    AND `source_benefit_amount_minor`=`component_amount_minor`
    AND `import_allowed_component_count`=0 AND `production_migration_enabled`=0
  ),
  CONSTRAINT `ck_cm_order_benefit_mig_run_evidence` CHECK (
    CHAR_LENGTH(TRIM(`policy_version`))>0 AND CHAR_LENGTH(TRIM(`evidence_ref`))>0
    AND `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_migration_candidate` (
  `candidate_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `legacy_order_no` varchar(64) NOT NULL,
  `legacy_snapshot_hash` char(64) NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `is_deleted` tinyint(1) NOT NULL,
  `header_quantity` int NOT NULL,
  `item_row_count` int NOT NULL,
  `item_quantity` int NOT NULL,
  `header_gross_amount_minor` bigint NOT NULL,
  `header_generic_discount_amount_minor` bigint NOT NULL,
  `header_coupon_amount_minor` bigint NOT NULL,
  `header_point_amount_minor` bigint NOT NULL,
  `header_vip_amount_minor` bigint NOT NULL,
  `header_delivery_amount_minor` bigint NOT NULL,
  `header_adjust_amount_minor` bigint NOT NULL,
  `header_pay_amount_minor` bigint NOT NULL,
  `item_gross_amount_minor` bigint NOT NULL,
  `item_generic_discount_amount_minor` bigint NOT NULL,
  `item_coupon_amount_minor` bigint NOT NULL,
  `item_point_amount_minor` bigint NOT NULL,
  `item_vip_amount_minor` bigint NOT NULL,
  `item_delivery_amount_minor` bigint NOT NULL,
  `item_adjust_amount_minor` bigint NOT NULL,
  `item_pay_amount_minor` bigint NOT NULL,
  `invalid_item_money_count` int NOT NULL,
  `negative_money` tinyint(1) NOT NULL,
  `header_money_mismatch` tinyint(1) NOT NULL,
  `header_item_mismatch` tinyint(1) NOT NULL,
  `assessment_status` varchar(40) NOT NULL,
  `reason_codes` json NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL DEFAULT 0,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`candidate_id`),
  UNIQUE KEY `uk_cm_order_benefit_mig_candidate_source` (`tenant_id`,`migration_run_id`,`legacy_order_id`),
  UNIQUE KEY `uk_cm_order_benefit_mig_candidate_lineage` (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  KEY `idx_cm_order_benefit_mig_candidate_status` (`tenant_id`,`migration_run_id`,`assessment_status`),
  CONSTRAINT `fk_cm_order_benefit_mig_candidate_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_order_benefit_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_order_benefit_mig_candidate_status` CHECK (`assessment_status` IN (
    'DELETED_EXCLUDED','NO_BENEFIT','BENEFIT_REQUIRES_IDENTITY_AND_FUNDING',
    'QUARANTINED_MONEY','QUARANTINED_HEADER_ITEM')),
  CONSTRAINT `ck_cm_order_benefit_mig_candidate_version` CHECK (`version`=1),
  CONSTRAINT `ck_cm_order_benefit_mig_candidate_hash` CHECK (`legacy_snapshot_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `ck_cm_order_benefit_mig_candidate_import` CHECK (`canonical_import_allowed`=0),
  CONSTRAINT `ck_cm_order_benefit_mig_candidate_reasons` CHECK (
    JSON_TYPE(`reason_codes`)='ARRAY'
    AND ((`assessment_status`='NO_BENEFIT' AND JSON_LENGTH(`reason_codes`)=0)
      OR (`assessment_status`<>'NO_BENEFIT' AND JSON_LENGTH(`reason_codes`)>0))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_migration_component` (
  `component_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `component_type` varchar(24) NOT NULL,
  `component_amount_minor` bigint NOT NULL,
  `source_reference` varchar(128) DEFAULT NULL,
  `identity_resolution_status` varchar(40) NOT NULL,
  `funding_resolution_status` varchar(40) NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL DEFAULT 0,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`component_id`),
  UNIQUE KEY `uk_cm_order_benefit_mig_component_type` (`tenant_id`,`migration_run_id`,`legacy_order_id`,`component_type`),
  CONSTRAINT `fk_cm_order_benefit_mig_component_candidate` FOREIGN KEY
    (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`)
    REFERENCES `cloudmold_order_benefit_migration_candidate`
      (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_benefit_mig_component_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_order_benefit_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_order_benefit_mig_component_type` CHECK (`component_type` IN ('GENERIC_DISCOUNT','COUPON','POINT','VIP')),
  CONSTRAINT `ck_cm_order_benefit_mig_component_amount` CHECK (`component_amount_minor`<>0),
  CONSTRAINT `ck_cm_order_benefit_mig_component_identity` CHECK (`identity_resolution_status` IN (
    'MISSING_SOURCE_REFERENCE','AMBIGUOUS_SOURCE_REFERENCE','SOURCE_REFERENCE_WITHOUT_VERSION',
    'MISSING_ENTITLEMENT_VERSION','MISSING_BENEFIT_VERSION')),
  CONSTRAINT `ck_cm_order_benefit_mig_component_funding` CHECK (`funding_resolution_status`='MISSING_NAMED_FUNDER_BREAKDOWN'),
  CONSTRAINT `ck_cm_order_benefit_mig_component_import` CHECK (`canonical_import_allowed`=0 AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
