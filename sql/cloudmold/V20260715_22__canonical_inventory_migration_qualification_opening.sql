-- Controlled Inventory v1 -> v3 qualification and single opening driver.
-- Existing assessment rows remain immutable. Only explicitly classified CONTROLLED_CANARY rows may qualify.

ALTER TABLE `cloudmold_inventory_migration_operation`
  ADD UNIQUE KEY `uk_cm_inv_migration_operation_tenant_id` (`tenant_id`,`operation_id`),
  DROP CHECK `ck_cm_inv_migration_operation_type`,
  ADD CONSTRAINT `ck_cm_inv_migration_operation_type`
    CHECK (`command_type` IN ('ASSESS_V1','QUALIFY_V1','MIGRATE_V1'));

ALTER TABLE `cloudmold_inventory_migration_candidate`
  ADD UNIQUE KEY `uk_cm_inv_migration_candidate_tenant_id` (`tenant_id`,`candidate_id`),
  DROP CHECK `ck_cm_inv_migration_candidate_source`,
  ADD CONSTRAINT `ck_cm_inv_migration_candidate_source` CHECK (
    `source_system`='CLOUDMOLD_INVENTORY_V1' AND `source_type`='BALANCE'
    AND BINARY `source_id`=BINARY `legacy_balance_id`
    AND `source_classification` IN (
      'CONTROLLED_CANARY','CONTROLLED_FIXTURE','CONTROLLED_SCENARIO','CONCURRENCY_PROBE','UNCLASSIFIED'
    )
    AND `lot_tracking_policy`='UNRESOLVED'
  );

ALTER TABLE `cloudmold_inventory_operation_v3`
  DROP CHECK `ck_cm_inv_v3_command_type`,
  ADD CONSTRAINT `ck_cm_inv_v3_command_type`
    CHECK (`command_type` IN ('RECEIVE','RESERVE','SHIP','RETURN','RELEASE','MIGRATION_OPENING'));

ALTER TABLE `cloudmold_inventory_ledger_transaction_v3`
  DROP CHECK `ck_cm_inv_v3_tx_command`,
  ADD CONSTRAINT `ck_cm_inv_v3_tx_command`
    CHECK (`command_type` IN ('RECEIVE','RESERVE','SHIP','RETURN','RELEASE','MIGRATION_OPENING'));

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_qualification` (
  `qualification_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `qualification_operation_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `source_version` bigint NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `source_on_hand_quantity` decimal(24,6) NOT NULL,
  `source_reserved_quantity` decimal(24,6) NOT NULL,
  `source_in_transit_quantity` decimal(24,6) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `warehouse_source_mapping_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `location_id` varchar(36) NOT NULL,
  `lot_tracking_policy` varchar(16) NOT NULL,
  `lot_id` varchar(36) DEFAULT NULL,
  `stock_status` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `resolved_blocker_codes` json NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `verification_ref` varchar(256) NOT NULL,
  `status` varchar(16) NOT NULL,
  `opening_operation_id` bigint DEFAULT NULL,
  `target_balance_id` varchar(36) DEFAULT NULL,
  `ledger_transaction_id` bigint DEFAULT NULL,
  `bridge_id` varchar(36) DEFAULT NULL,
  `version` bigint NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `migrated_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`qualification_id`),
  UNIQUE KEY `uk_cm_inv_migration_qualification_tenant_id` (`tenant_id`,`qualification_id`),
  UNIQUE KEY `uk_cm_inv_migration_qualification_candidate` (`tenant_id`,`candidate_id`),
  UNIQUE KEY `uk_cm_inv_migration_qualification_source` (`tenant_id`,`source_system`,`source_type`,`source_id`),
  UNIQUE KEY `uk_cm_inv_migration_qualification_opening` (`tenant_id`,`opening_operation_id`),
  KEY `idx_cm_inv_migration_qualification_run` (`tenant_id`,`migration_run_id`,`status`),
  CONSTRAINT `fk_cm_inv_migration_qualification_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_inventory_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_candidate` FOREIGN KEY (`tenant_id`,`candidate_id`)
    REFERENCES `cloudmold_inventory_migration_candidate` (`tenant_id`,`candidate_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_operation` FOREIGN KEY (`tenant_id`,`qualification_operation_id`)
    REFERENCES `cloudmold_inventory_migration_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_owner` FOREIGN KEY (`tenant_id`,`owner_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_sku` FOREIGN KEY (`tenant_id`,`canonical_sku_id`)
    REFERENCES `cloudmold_catalog_sku` (`tenant_id`,`sku_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_warehouse_mapping` FOREIGN KEY (`tenant_id`,`warehouse_source_mapping_id`)
    REFERENCES `cloudmold_warehouse_source_mapping` (`tenant_id`,`mapping_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_warehouse` FOREIGN KEY (`tenant_id`,`warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_location` FOREIGN KEY (`tenant_id`,`location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_lot` FOREIGN KEY (`tenant_id`,`lot_id`)
    REFERENCES `cloudmold_inventory_lot` (`tenant_id`,`lot_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_opening_operation` FOREIGN KEY (`tenant_id`,`opening_operation_id`)
    REFERENCES `cloudmold_inventory_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_target` FOREIGN KEY (`tenant_id`,`target_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_tx` FOREIGN KEY (`tenant_id`,`ledger_transaction_id`)
    REFERENCES `cloudmold_inventory_ledger_transaction_v3` (`tenant_id`,`ledger_transaction_id`),
  CONSTRAINT `fk_cm_inv_migration_qualification_bridge` FOREIGN KEY (`tenant_id`,`bridge_id`)
    REFERENCES `cloudmold_inventory_migration_bridge` (`tenant_id`,`bridge_id`),
  CONSTRAINT `ck_cm_inv_migration_qualification_source` CHECK (
    `source_system`='CLOUDMOLD_INVENTORY_V1' AND `source_type`='BALANCE'
    AND CHAR_LENGTH(TRIM(`source_id`))>0 AND `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND `source_version`>=1
  ),
  CONSTRAINT `ck_cm_inv_migration_qualification_quantities` CHECK (
    `source_on_hand_quantity`>0 AND `source_reserved_quantity`=0 AND `source_in_transit_quantity`=0
  ),
  CONSTRAINT `ck_cm_inv_migration_qualification_dimension` CHECK (
    `owner_type`='MERCHANT'
    AND `stock_status` IN ('SELLABLE','NON_SELLABLE')
    AND `quality_status` IN ('PENDING_QC','QUALIFIED','DAMAGED','REJECTED')
    AND (`stock_status`<>'SELLABLE' OR `quality_status`='QUALIFIED')
    AND CHAR_LENGTH(TRIM(`base_uom_code`))>0
    AND BINARY `base_uom_code`=BINARY UPPER(TRIM(`base_uom_code`))
  ),
  CONSTRAINT `ck_cm_inv_migration_qualification_lot_policy` CHECK (
    (`lot_tracking_policy`='NOT_TRACKED' AND `lot_id` IS NULL)
    OR (`lot_tracking_policy`='TRACKED' AND `lot_id` IS NOT NULL)
  ),
  CONSTRAINT `ck_cm_inv_migration_qualification_evidence` CHECK (
    JSON_TYPE(`resolved_blocker_codes`)='ARRAY' AND JSON_LENGTH(`resolved_blocker_codes`)>0
    AND CHAR_LENGTH(TRIM(`policy_version`))>0 AND CHAR_LENGTH(TRIM(`verification_ref`))>0
  ),
  CONSTRAINT `ck_cm_inv_migration_qualification_state` CHECK (
    (`status`='QUALIFIED' AND `opening_operation_id` IS NULL AND `target_balance_id` IS NULL
      AND `ledger_transaction_id` IS NULL AND `bridge_id` IS NULL AND `migrated_at` IS NULL AND `version`=1)
    OR (`status`='MIGRATED' AND `opening_operation_id` IS NOT NULL AND `target_balance_id` IS NOT NULL
      AND `ledger_transaction_id` IS NOT NULL AND `bridge_id` IS NOT NULL AND `migrated_at` IS NOT NULL AND `version`=2)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
