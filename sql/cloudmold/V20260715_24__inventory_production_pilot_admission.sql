-- V24 production Inventory migration pilot admission control plane.
-- Admission is governance-only: this migration adds no production opening, bridge, or v3 balance driver.

ALTER TABLE `cloudmold_inventory_migration_operation`
  DROP CHECK `ck_cm_inv_migration_operation_type`,
  ADD CONSTRAINT `ck_cm_inv_migration_operation_type`
    CHECK (`command_type` IN (
      'ASSESS_V1','QUALIFY_V1','MIGRATE_V1',
      'FREEZE_PILOT_V1','APPROVE_PILOT_V1','ADMIT_PILOT_V1'
    ));

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_pilot_batch` (
  `batch_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `environment` varchar(16) NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_classification` varchar(32) NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `policy_hash` char(64) NOT NULL,
  `manifest_hash` char(64) NOT NULL,
  `expected_item_count` int NOT NULL,
  `expected_on_hand_quantity` decimal(24,6) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `source_watermark_kind` varchar(32) NOT NULL,
  `source_watermark_value` varchar(256) NOT NULL,
  `source_watermark_captured_at` datetime(6) NOT NULL,
  `target_watermark_kind` varchar(32) NOT NULL,
  `target_watermark_value` varchar(256) NOT NULL,
  `target_watermark_applied_at` datetime(6) NOT NULL,
  `max_lag_seconds` int NOT NULL,
  `execution_window_start` datetime(6) NOT NULL,
  `execution_window_end` datetime(6) NOT NULL,
  `change_ticket` varchar(128) NOT NULL,
  `purpose` varchar(512) NOT NULL,
  `requester_id` bigint NOT NULL,
  `executor_id` bigint DEFAULT NULL,
  `approval_count` int NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `frozen_at` datetime(6) NOT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `admitted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_batch_tenant` (`tenant_id`,`batch_id`),
  KEY `idx_cm_inv_migration_pilot_batch_run` (`tenant_id`,`migration_run_id`,`status`),
  CONSTRAINT `fk_cm_inv_migration_pilot_batch_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_inventory_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_inv_migration_pilot_batch_source` CHECK (
    `environment`='PRODUCTION' AND `source_system`='CLOUDMOLD_INVENTORY_V1'
    AND `source_type`='BALANCE' AND `source_classification`='PRODUCTION_HISTORY'
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_batch_policy` CHECK (
    `policy_hash` REGEXP '^[0-9a-f]{64}$' AND `manifest_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`policy_version`))>0
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_batch_scope` CHECK (
    `expected_item_count` BETWEEN 1 AND 10 AND `expected_on_hand_quantity`>0
    AND CHAR_LENGTH(TRIM(`warehouse_id`))>0
    AND BINARY `base_uom_code`=BINARY UPPER(TRIM(`base_uom_code`))
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_batch_watermark` CHECK (
    CHAR_LENGTH(TRIM(`source_watermark_kind`))>0 AND CHAR_LENGTH(TRIM(`source_watermark_value`))>0
    AND CHAR_LENGTH(TRIM(`target_watermark_kind`))>0 AND CHAR_LENGTH(TRIM(`target_watermark_value`))>0
    AND `target_watermark_applied_at`>=`source_watermark_captured_at`
    AND `max_lag_seconds` BETWEEN 0 AND 300
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_batch_window` CHECK (
    `execution_window_end`>`execution_window_start`
    AND TIMESTAMPDIFF(SECOND,`execution_window_start`,`execution_window_end`)<=900
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_batch_state` CHECK (
    (`status`='FROZEN' AND `approval_count`=0 AND `version`=1 AND `approved_at` IS NULL
      AND `executor_id` IS NULL AND `admitted_at` IS NULL)
    OR (`status`='PARTIALLY_APPROVED' AND `approval_count`=1 AND `version`=2 AND `approved_at` IS NULL
      AND `executor_id` IS NULL AND `admitted_at` IS NULL)
    OR (`status`='APPROVED' AND `approval_count`=2 AND `version`=3 AND `approved_at` IS NOT NULL
      AND `executor_id` IS NULL AND `admitted_at` IS NULL)
    OR (`status`='ADMISSION_PASSED' AND `approval_count`=2 AND `version`=4 AND `approved_at` IS NOT NULL
      AND `executor_id` IS NOT NULL AND `admitted_at` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_pilot_item` (
  `item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `batch_id` varchar(36) NOT NULL,
  `ordinal` int NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_balance_id` varchar(36) NOT NULL,
  `source_version` bigint NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `source_on_hand_quantity` decimal(24,6) NOT NULL,
  `source_reserved_quantity` decimal(24,6) NOT NULL,
  `source_in_transit_quantity` decimal(24,6) NOT NULL,
  `active_reservation_count` int NOT NULL,
  `active_reservation_quantity` decimal(24,6) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `owner_source_system` varchar(32) NOT NULL,
  `owner_source_type` varchar(32) NOT NULL,
  `owner_source_id` varchar(128) NOT NULL,
  `owner_mapping_id` varchar(36) NOT NULL,
  `owner_mapping_version` bigint NOT NULL,
  `owner_mapping_evidence_ref` varchar(256) NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `sku_mapping_id` varchar(36) NOT NULL,
  `sku_mapping_version` bigint NOT NULL,
  `sku_mapping_evidence_ref` varchar(256) NOT NULL,
  `source_uom_code` varchar(32) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `uom_conversion_ratio` decimal(24,9) NOT NULL,
  `uom_evidence_ref` varchar(256) NOT NULL,
  `warehouse_source_system` varchar(32) NOT NULL,
  `warehouse_source_type` varchar(32) NOT NULL,
  `warehouse_source_id` varchar(128) NOT NULL,
  `warehouse_source_mapping_id` varchar(36) NOT NULL,
  `warehouse_mapping_version` bigint NOT NULL,
  `warehouse_mapping_evidence_ref` varchar(256) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `location_source_system` varchar(32) NOT NULL,
  `location_source_type` varchar(32) NOT NULL,
  `location_source_id` varchar(128) NOT NULL,
  `location_source_mapping_id` varchar(36) NOT NULL,
  `location_mapping_version` bigint NOT NULL,
  `location_mapping_evidence_ref` varchar(256) NOT NULL,
  `location_id` varchar(36) NOT NULL,
  `zone_id` varchar(36) DEFAULT NULL,
  `lot_tracking_policy` varchar(16) NOT NULL,
  `lot_id` varchar(36) DEFAULT NULL,
  `lot_mapping_id` varchar(36) DEFAULT NULL,
  `lot_mapping_version` bigint DEFAULT NULL,
  `lot_evidence_ref` varchar(256) NOT NULL,
  `stock_status` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `authoritative_record_ref` varchar(256) NOT NULL,
  `quantity_evidence_ref` varchar(256) NOT NULL,
  `source_cdc_position` varchar(256) NOT NULL,
  `source_extracted_at` datetime(6) NOT NULL,
  `target_balance_absent` tinyint(1) NOT NULL,
  `bridge_absent` tinyint(1) NOT NULL,
  `item_scope_hash` char(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_item_tenant` (`tenant_id`,`item_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_item_ordinal` (`tenant_id`,`batch_id`,`ordinal`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_item_candidate` (`tenant_id`,`candidate_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_item_source` (`tenant_id`,`legacy_balance_id`),
  CONSTRAINT `fk_cm_inv_migration_pilot_item_batch` FOREIGN KEY (`tenant_id`,`batch_id`)
    REFERENCES `cloudmold_inventory_migration_pilot_batch` (`tenant_id`,`batch_id`),
  CONSTRAINT `fk_cm_inv_migration_pilot_item_candidate` FOREIGN KEY (`tenant_id`,`candidate_id`)
    REFERENCES `cloudmold_inventory_migration_candidate` (`tenant_id`,`candidate_id`),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_source` CHECK (
    `source_version`>=1 AND `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND `source_on_hand_quantity`>0 AND `source_reserved_quantity`=0 AND `source_in_transit_quantity`=0
    AND `active_reservation_count`=0 AND `active_reservation_quantity`=0
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_owner` CHECK (
    `owner_type`='MERCHANT' AND `owner_mapping_version`>=1
    AND CHAR_LENGTH(TRIM(`owner_mapping_evidence_ref`))>0
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_uom` CHECK (
    BINARY `source_uom_code`=BINARY `base_uom_code` AND `uom_conversion_ratio`=1
    AND BINARY `base_uom_code`=BINARY UPPER(TRIM(`base_uom_code`))
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_location` CHECK (
    `warehouse_mapping_version`>=1 AND `location_mapping_version`>=1
    AND CHAR_LENGTH(TRIM(`location_id`))>0
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_lot` CHECK (
    (`lot_tracking_policy`='NOT_TRACKED' AND `lot_id` IS NULL AND `lot_mapping_id` IS NULL
      AND `lot_mapping_version` IS NULL)
    OR (`lot_tracking_policy`='TRACKED' AND `lot_id` IS NOT NULL AND `lot_mapping_id` IS NOT NULL
      AND `lot_mapping_version`>=1)
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_target` CHECK (
    `target_balance_absent`=1 AND `bridge_absent`=1
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_item_state` CHECK (
    (`status`='FROZEN' AND `version`=1)
    OR (`status`='APPROVED' AND `version`=2)
    OR (`status`='ADMISSION_PASSED' AND `version`=3)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_pilot_approval` (
  `approval_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `batch_id` varchar(36) NOT NULL,
  `approval_role` varchar(32) NOT NULL,
  `approver_id` bigint NOT NULL,
  `scope_hash` char(64) NOT NULL,
  `policy_hash` char(64) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `approved_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`approval_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_approval_role` (`tenant_id`,`batch_id`,`approval_role`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_approval_actor` (`tenant_id`,`batch_id`,`approver_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_approval_key` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `fk_cm_inv_migration_pilot_approval_batch` FOREIGN KEY (`tenant_id`,`batch_id`)
    REFERENCES `cloudmold_inventory_migration_pilot_batch` (`tenant_id`,`batch_id`),
  CONSTRAINT `ck_cm_inv_migration_pilot_approval_role` CHECK (`approval_role` IN ('DATA_OWNER','CHANGE_MANAGER')),
  CONSTRAINT `ck_cm_inv_migration_pilot_approval_hash` CHECK (
    `scope_hash` REGEXP '^[0-9a-f]{64}$' AND `policy_hash` REGEXP '^[0-9a-f]{64}$'
    AND `request_hash` REGEXP '^[0-9a-f]{64}$'
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_approval_state` CHECK (
    `status`='APPROVED' AND `version`=1 AND `expires_at`>`approved_at`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_pilot_checkpoint` (
  `checkpoint_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `batch_id` varchar(36) NOT NULL,
  `checkpoint_type` varchar(32) NOT NULL,
  `actor_id` bigint NOT NULL,
  `batch_version` bigint NOT NULL,
  `scope_hash` char(64) NOT NULL,
  `policy_hash` char(64) NOT NULL,
  `item_count` int NOT NULL,
  `details_json` json NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`checkpoint_id`),
  UNIQUE KEY `uk_cm_inv_migration_pilot_checkpoint` (`tenant_id`,`batch_id`,`checkpoint_type`,`batch_version`),
  CONSTRAINT `fk_cm_inv_migration_pilot_checkpoint_batch` FOREIGN KEY (`tenant_id`,`batch_id`)
    REFERENCES `cloudmold_inventory_migration_pilot_batch` (`tenant_id`,`batch_id`),
  CONSTRAINT `ck_cm_inv_migration_pilot_checkpoint_type` CHECK (
    `checkpoint_type` IN ('FROZEN','PARTIALLY_APPROVED','APPROVED','ADMISSION_PASSED')
  ),
  CONSTRAINT `ck_cm_inv_migration_pilot_checkpoint_payload` CHECK (
    `batch_version`>=1 AND `item_count` BETWEEN 1 AND 10 AND JSON_TYPE(`details_json`)='OBJECT'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
