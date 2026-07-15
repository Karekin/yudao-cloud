-- Inventory v1 -> v3 migration assessment. This migration records evidence; it never opens v3 stock.

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_operation` (
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
  UNIQUE KEY `uk_cm_inv_migration_operation_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_inv_migration_operation_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_inv_migration_operation_type` CHECK (`command_type` IN ('ASSESS_V1')),
  CONSTRAINT `ck_cm_inv_migration_operation_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_inv_migration_operation_result` CHECK (
    (`status`=0 AND `migration_run_id` IS NULL AND `result_json` IS NULL)
    OR (`status`=10 AND `migration_run_id` IS NOT NULL AND `result_json` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_run` (
  `migration_run_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_scope` varchar(32) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `candidate_count` int NOT NULL,
  `eligible_count` int NOT NULL,
  `blocked_count` int NOT NULL,
  `rejected_count` int NOT NULL,
  `status` varchar(24) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`migration_run_id`),
  UNIQUE KEY `uk_cm_inv_migration_run_tenant_id` (`tenant_id`,`migration_run_id`),
  KEY `idx_cm_inv_migration_run_status` (`tenant_id`,`status`,`assessed_at`),
  CONSTRAINT `ck_cm_inv_migration_run_scope` CHECK (`source_scope`='INVENTORY_V1'),
  CONSTRAINT `ck_cm_inv_migration_run_status` CHECK (`status`='ASSESSED'),
  CONSTRAINT `ck_cm_inv_migration_run_version` CHECK (`version`=1),
  CONSTRAINT `ck_cm_inv_migration_run_counts` CHECK (
    `candidate_count`>0 AND `eligible_count`>=0 AND `blocked_count`>=0 AND `rejected_count`>=0
    AND `candidate_count`=`eligible_count`+`blocked_count`+`rejected_count`
  ),
  CONSTRAINT `ck_cm_inv_migration_run_evidence` CHECK (
    CHAR_LENGTH(TRIM(`policy_version`))>0 AND CHAR_LENGTH(TRIM(`evidence_ref`))>0
    AND `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_candidate` (
  `candidate_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `legacy_balance_id` varchar(36) NOT NULL,
  `legacy_balance_version` bigint NOT NULL,
  `legacy_snapshot_hash` char(64) NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `source_classification` varchar(32) NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `legacy_owner_id` varchar(128) NOT NULL,
  `legacy_canonical_sku_id` varchar(128) NOT NULL,
  `legacy_warehouse_id` varchar(128) NOT NULL,
  `stock_status` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `source_on_hand_quantity` decimal(24,6) NOT NULL,
  `source_reserved_quantity` decimal(24,6) NOT NULL,
  `source_in_transit_quantity` decimal(24,6) NOT NULL,
  `initial_business_type` varchar(32) DEFAULT NULL,
  `initial_source_event_id` varchar(128) DEFAULT NULL,
  `active_reservation_count` int NOT NULL,
  `active_reservation_quantity` decimal(24,6) NOT NULL,
  `lot_tracking_policy` varchar(16) NOT NULL,
  `decision_status` varchar(16) NOT NULL,
  `reason_codes` json NOT NULL,
  `verification_ref` varchar(256) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`candidate_id`),
  UNIQUE KEY `uk_cm_inv_migration_candidate_run_balance` (`tenant_id`,`migration_run_id`,`legacy_balance_id`),
  KEY `idx_cm_inv_migration_candidate_decision` (`tenant_id`,`migration_run_id`,`decision_status`),
  CONSTRAINT `fk_cm_inv_migration_candidate_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_inventory_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_inv_migration_candidate_status` CHECK (`decision_status` IN ('ELIGIBLE','BLOCKED','REJECTED')),
  CONSTRAINT `ck_cm_inv_migration_candidate_version` CHECK (`version`=1 AND `legacy_balance_version`>=1),
  CONSTRAINT `ck_cm_inv_migration_candidate_quantities` CHECK (
    `source_on_hand_quantity`>=0 AND `source_reserved_quantity`>=0 AND `source_in_transit_quantity`>=0
    AND `active_reservation_count`>=0 AND `active_reservation_quantity`>=0
  ),
  CONSTRAINT `ck_cm_inv_migration_candidate_source` CHECK (
    `source_system`='CLOUDMOLD_INVENTORY_V1' AND `source_type`='BALANCE'
    AND BINARY `source_id`=BINARY `legacy_balance_id`
    AND `source_classification` IN ('CONTROLLED_FIXTURE','CONTROLLED_SCENARIO','CONCURRENCY_PROBE','UNCLASSIFIED')
    AND `lot_tracking_policy`='UNRESOLVED'
  ),
  CONSTRAINT `ck_cm_inv_migration_candidate_hash` CHECK (`legacy_snapshot_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `ck_cm_inv_migration_candidate_reasons` CHECK (
    JSON_TYPE(`reason_codes`)='ARRAY'
    AND ((`decision_status`='ELIGIBLE' AND JSON_LENGTH(`reason_codes`)=0)
      OR (`decision_status`<>'ELIGIBLE' AND JSON_LENGTH(`reason_codes`)>0))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
