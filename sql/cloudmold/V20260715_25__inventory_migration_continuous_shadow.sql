-- V25 continuous, read-only shadow comparison for an admitted production pilot.
-- VERIFIED means the evidence window is terminal; verification_result carries the conclusion.
-- This migration adds no qualification, opening, bridge, materialized v3 balance, execution, or cutover path.

ALTER TABLE `cloudmold_inventory_migration_operation`
  DROP CHECK `ck_cm_inv_migration_operation_type`,
  ADD CONSTRAINT `ck_cm_inv_migration_operation_type`
    CHECK (`command_type` IN (
      'ASSESS_V1','QUALIFY_V1','MIGRATE_V1',
      'FREEZE_PILOT_V1','APPROVE_PILOT_V1','ADMIT_PILOT_V1',
      'START_SHADOW_V1','RECORD_SHADOW_ROUND_V1','FINALIZE_SHADOW_V1'
    ));

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_shadow_window` (
  `window_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `batch_id` varchar(36) NOT NULL,
  `migration_run_id` varchar(36) NOT NULL,
  `environment` varchar(16) NOT NULL,
  `environment_fingerprint` varchar(128) NOT NULL,
  `manifest_hash` char(64) NOT NULL,
  `expected_item_set_hash` char(64) NOT NULL,
  `admission_checkpoint_id` varchar(36) NOT NULL,
  `admission_checkpoint_hash` char(64) NOT NULL,
  `admission_event_id` varchar(36) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `admission_batch_version` bigint NOT NULL,
  `policy_version` varchar(32) NOT NULL,
  `policy_hash` char(64) NOT NULL,
  `target_projection_kind` varchar(64) NOT NULL,
  `target_projection_version` int NOT NULL,
  `target_materialized` bit(1) NOT NULL,
  `expected_item_count` int NOT NULL,
  `required_round_count` int NOT NULL,
  `minimum_duration_seconds` int NOT NULL,
  `max_round_interval_seconds` int NOT NULL,
  `max_watermark_lag_seconds` int NOT NULL,
  `collector_id` bigint NOT NULL,
  `verifier_id` bigint DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `verification_result` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `observed_round_count` int NOT NULL,
  `total_match_count` int NOT NULL,
  `total_different_count` int NOT NULL,
  `total_uncomparable_count` int NOT NULL,
  `last_source_watermark_kind` varchar(32) DEFAULT NULL,
  `last_source_watermark_value` text DEFAULT NULL,
  `last_source_watermark_hash` char(64) DEFAULT NULL,
  `last_source_watermark_captured_at` datetime(6) DEFAULT NULL,
  `last_target_watermark_kind` varchar(32) DEFAULT NULL,
  `last_target_watermark_value` text DEFAULT NULL,
  `last_target_watermark_hash` char(64) DEFAULT NULL,
  `last_target_watermark_applied_at` datetime(6) DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `last_observed_at` datetime(6) DEFAULT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`window_id`),
  UNIQUE KEY `uk_cm_inv_shadow_window_tenant` (`tenant_id`,`window_id`),
  KEY `idx_cm_inv_shadow_window_batch` (`tenant_id`,`batch_id`,`started_at`),
  CONSTRAINT `fk_cm_inv_shadow_window_batch` FOREIGN KEY (`tenant_id`,`batch_id`)
    REFERENCES `cloudmold_inventory_migration_pilot_batch` (`tenant_id`,`batch_id`),
  CONSTRAINT `fk_cm_inv_shadow_window_run` FOREIGN KEY (`tenant_id`,`migration_run_id`)
    REFERENCES `cloudmold_inventory_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `fk_cm_inv_shadow_window_checkpoint` FOREIGN KEY (`admission_checkpoint_id`)
    REFERENCES `cloudmold_inventory_migration_pilot_checkpoint` (`checkpoint_id`),
  CONSTRAINT `fk_cm_inv_shadow_window_admission_event` FOREIGN KEY (`admission_event_id`)
    REFERENCES `cloudmold_event_outbox` (`event_id`),
  CONSTRAINT `ck_cm_inv_shadow_window_policy` CHECK (
    `environment`='PRODUCTION' AND CHAR_LENGTH(TRIM(`environment_fingerprint`))>0
    AND `manifest_hash` REGEXP '^[0-9a-f]{64}$'
    AND `expected_item_set_hash` REGEXP '^[0-9a-f]{64}$'
    AND `admission_checkpoint_hash` REGEXP '^[0-9a-f]{64}$'
    AND `admission_batch_version`=4
    AND `policy_hash` REGEXP '^[0-9a-f]{64}$'
    AND `target_projection_kind`='CANONICAL_INVENTORY_V3_SHADOW_PROJECTION'
    AND `target_projection_version`=1 AND `target_materialized`=b'0'
    AND `expected_item_count` BETWEEN 1 AND 10
    AND `required_round_count` BETWEEN 2 AND 1000
    AND `minimum_duration_seconds` BETWEEN 1 AND 604800
    AND `max_round_interval_seconds` BETWEEN 60 AND 86400
    AND `max_watermark_lag_seconds` BETWEEN 0 AND 300
  ),
  CONSTRAINT `ck_cm_inv_shadow_window_denominator` CHECK (
    `observed_round_count`>=0 AND `total_match_count`>=0
    AND `total_different_count`>=0 AND `total_uncomparable_count`>=0
    AND `total_match_count`+`total_different_count`+`total_uncomparable_count`
      =`observed_round_count`*`expected_item_count`
  ),
  CONSTRAINT `ck_cm_inv_shadow_window_watermark_shape` CHECK (
    (`observed_round_count`=0 AND `last_source_watermark_kind` IS NULL
      AND `last_source_watermark_value` IS NULL AND `last_source_watermark_hash` IS NULL
      AND `last_source_watermark_captured_at` IS NULL AND `last_target_watermark_kind` IS NULL
      AND `last_target_watermark_value` IS NULL AND `last_target_watermark_hash` IS NULL
      AND `last_target_watermark_applied_at` IS NULL AND `last_observed_at` IS NULL)
    OR (`observed_round_count`>0 AND `last_source_watermark_kind`='MYSQL_GTID_SET'
      AND CHAR_LENGTH(TRIM(`last_source_watermark_value`))>0
      AND `last_source_watermark_hash` REGEXP '^[0-9a-f]{64}$'
      AND `last_source_watermark_captured_at` IS NOT NULL
      AND `last_target_watermark_kind`='MYSQL_GTID_SET'
      AND CHAR_LENGTH(TRIM(`last_target_watermark_value`))>0
      AND `last_target_watermark_hash` REGEXP '^[0-9a-f]{64}$'
      AND `last_target_watermark_applied_at` IS NOT NULL AND `last_observed_at` IS NOT NULL)
  ),
  CONSTRAINT `ck_cm_inv_shadow_window_state` CHECK (
    (`status`='OPEN' AND `verification_result`='PENDING' AND `verifier_id` IS NULL
      AND `finalized_at` IS NULL AND `version`=1 AND `aggregate_version`=1
      AND `observed_round_count`=0)
    OR (`status`='OBSERVING' AND `verification_result`='PENDING' AND `verifier_id` IS NULL
      AND `finalized_at` IS NULL AND `version`=`observed_round_count`+1
      AND `aggregate_version`=2 AND `observed_round_count`>=1)
    OR (`status`='VERIFIED' AND `verification_result` IN ('MATCH','DIFFERENT','UNCOMPARABLE')
      AND `verifier_id` IS NOT NULL AND `finalized_at` IS NOT NULL
      AND `version`=`observed_round_count`+2 AND `aggregate_version`=3
      AND `observed_round_count`>=`required_round_count`
      AND ((`verification_result`='MATCH' AND `total_different_count`=0
              AND `total_uncomparable_count`=0)
        OR (`verification_result`='DIFFERENT' AND `total_different_count`>0
              AND `total_uncomparable_count`=0)
        OR (`verification_result`='UNCOMPARABLE' AND `total_uncomparable_count`>0)))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_shadow_round` (
  `round_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `window_id` varchar(36) NOT NULL,
  `round_number` int NOT NULL,
  `observed_at` datetime(6) NOT NULL,
  `previous_source_watermark_value` text NOT NULL,
  `previous_source_watermark_hash` char(64) NOT NULL,
  `source_watermark_kind` varchar(32) NOT NULL,
  `source_watermark_value` text NOT NULL,
  `source_watermark_hash` char(64) NOT NULL,
  `source_watermark_captured_at` datetime(6) NOT NULL,
  `source_monotonic` bit(1) NOT NULL,
  `previous_target_watermark_value` text NOT NULL,
  `previous_target_watermark_hash` char(64) NOT NULL,
  `target_watermark_kind` varchar(32) NOT NULL,
  `target_watermark_value` text NOT NULL,
  `target_watermark_hash` char(64) NOT NULL,
  `target_watermark_applied_at` datetime(6) NOT NULL,
  `target_monotonic` bit(1) NOT NULL,
  `target_contains_source` bit(1) NOT NULL,
  `watermark_validator` varchar(64) NOT NULL,
  `watermark_validation_evidence_ref` varchar(256) NOT NULL,
  `watermark_valid` bit(1) NOT NULL,
  `watermark_lag_seconds` int NOT NULL,
  `round_gap_seconds` int NOT NULL,
  `denominator_hash` char(64) NOT NULL,
  `expected_item_count` int NOT NULL,
  `match_count` int NOT NULL,
  `different_count` int NOT NULL,
  `uncomparable_count` int NOT NULL,
  `collector_id` bigint NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `status` varchar(16) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`round_id`),
  UNIQUE KEY `uk_cm_inv_shadow_round_tenant` (`tenant_id`,`round_id`),
  UNIQUE KEY `uk_cm_inv_shadow_round_number` (`tenant_id`,`window_id`,`round_number`),
  CONSTRAINT `fk_cm_inv_shadow_round_window` FOREIGN KEY (`tenant_id`,`window_id`)
    REFERENCES `cloudmold_inventory_migration_shadow_window` (`tenant_id`,`window_id`),
  CONSTRAINT `ck_cm_inv_shadow_round_shape` CHECK (
    `round_number`>=1 AND `source_watermark_kind`='MYSQL_GTID_SET'
    AND `target_watermark_kind`='MYSQL_GTID_SET'
    AND CHAR_LENGTH(TRIM(`previous_source_watermark_value`))>0
    AND `previous_source_watermark_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`source_watermark_value`))>0
    AND `source_watermark_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`previous_target_watermark_value`))>0
    AND `previous_target_watermark_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`target_watermark_value`))>0
    AND `target_watermark_hash` REGEXP '^[0-9a-f]{64}$'
    AND `watermark_validator`='MYSQL_GTID_SET_CONTAINS_V1'
    AND `watermark_lag_seconds`>=0 AND `round_gap_seconds`>=0
    AND `denominator_hash` REGEXP '^[0-9a-f]{64}$'
    AND `expected_item_count` BETWEEN 1 AND 10
    AND `match_count`>=0 AND `different_count`>=0 AND `uncomparable_count`>=0
    AND `match_count`+`different_count`+`uncomparable_count`=`expected_item_count`
    AND `watermark_valid`=(`source_monotonic` AND `target_monotonic` AND `target_contains_source`)
    AND `target_watermark_applied_at`>=`source_watermark_captured_at`
    AND `status`='COMPLETED' AND `aggregate_version`=1
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_migration_shadow_comparison` (
  `comparison_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `window_id` varchar(36) NOT NULL,
  `round_id` varchar(36) NOT NULL,
  `round_number` int NOT NULL,
  `pilot_item_id` varchar(36) NOT NULL,
  `manifest_ordinal` int NOT NULL,
  `item_scope_hash` char(64) NOT NULL,
  `canonical_grain_hash` char(64) NOT NULL,
  `source_watermark_hash` char(64) NOT NULL,
  `target_watermark_hash` char(64) NOT NULL,
  `source_id` varchar(36) NOT NULL,
  `source_version` bigint NOT NULL,
  `source_updated_at` datetime(6) NOT NULL,
  `source_snapshot_hash` char(64) NOT NULL,
  `source_on_hand_quantity` decimal(24,6) NOT NULL,
  `source_reserved_quantity` decimal(24,6) NOT NULL,
  `source_in_transit_quantity` decimal(24,6) NOT NULL,
  `active_reservation_count` int NOT NULL,
  `active_reservation_quantity` decimal(24,6) NOT NULL,
  `source_evidence_ref` varchar(256) NOT NULL,
  `target_available` bit(1) NOT NULL,
  `target_record_version` bigint DEFAULT NULL,
  `target_canonical_grain_hash` char(64) DEFAULT NULL,
  `target_on_hand_quantity` decimal(24,6) DEFAULT NULL,
  `target_reserved_quantity` decimal(24,6) DEFAULT NULL,
  `target_in_transit_quantity` decimal(24,6) DEFAULT NULL,
  `target_projection_hash` char(64) DEFAULT NULL,
  `target_evidence_ref` varchar(256) NOT NULL,
  `comparable` bit(1) NOT NULL,
  `comparison_result` varchar(16) NOT NULL,
  `difference_fields` json NOT NULL,
  `reason_codes` json NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`comparison_id`),
  UNIQUE KEY `uk_cm_inv_shadow_comparison_item` (`tenant_id`,`round_id`,`pilot_item_id`),
  UNIQUE KEY `uk_cm_inv_shadow_comparison_ordinal` (`tenant_id`,`round_id`,`manifest_ordinal`),
  KEY `idx_cm_inv_shadow_comparison_window` (`tenant_id`,`window_id`,`round_number`),
  CONSTRAINT `fk_cm_inv_shadow_comparison_round` FOREIGN KEY (`tenant_id`,`round_id`)
    REFERENCES `cloudmold_inventory_migration_shadow_round` (`tenant_id`,`round_id`),
  CONSTRAINT `fk_cm_inv_shadow_comparison_window` FOREIGN KEY (`tenant_id`,`window_id`)
    REFERENCES `cloudmold_inventory_migration_shadow_window` (`tenant_id`,`window_id`),
  CONSTRAINT `fk_cm_inv_shadow_comparison_item` FOREIGN KEY (`tenant_id`,`pilot_item_id`)
    REFERENCES `cloudmold_inventory_migration_pilot_item` (`tenant_id`,`item_id`),
  CONSTRAINT `ck_cm_inv_shadow_comparison_source` CHECK (
    `source_version`>=1 AND `source_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND `source_on_hand_quantity`>=0 AND `source_reserved_quantity`>=0
    AND `source_in_transit_quantity`>=0 AND `source_reserved_quantity`<=`source_on_hand_quantity`
    AND `active_reservation_count`>=0 AND `active_reservation_quantity`>=0
    AND `item_scope_hash` REGEXP '^[0-9a-f]{64}$'
    AND `canonical_grain_hash` REGEXP '^[0-9a-f]{64}$'
    AND `source_watermark_hash` REGEXP '^[0-9a-f]{64}$'
    AND `target_watermark_hash` REGEXP '^[0-9a-f]{64}$'
    AND JSON_TYPE(`difference_fields`)='ARRAY' AND JSON_TYPE(`reason_codes`)='ARRAY'
    AND `aggregate_version`=1
  ),
  CONSTRAINT `ck_cm_inv_shadow_comparison_result` CHECK (
    (`comparison_result`='MATCH' AND `target_available`=b'1' AND `comparable`=b'1'
      AND `target_record_version`>=1
      AND `target_canonical_grain_hash`=`canonical_grain_hash`
      AND `target_on_hand_quantity`=`source_on_hand_quantity`
      AND `target_reserved_quantity`=`source_reserved_quantity`
      AND `target_in_transit_quantity`=`source_in_transit_quantity`
      AND `target_projection_hash` REGEXP '^[0-9a-f]{64}$'
      AND JSON_LENGTH(`difference_fields`)=0 AND JSON_LENGTH(`reason_codes`)=0)
    OR (`comparison_result`='DIFFERENT' AND `target_available`=b'1' AND `comparable`=b'1'
      AND `target_record_version`>=1
      AND `target_canonical_grain_hash` REGEXP '^[0-9a-f]{64}$'
      AND `target_on_hand_quantity` IS NOT NULL AND `target_reserved_quantity` IS NOT NULL
      AND `target_in_transit_quantity` IS NOT NULL
      AND `target_projection_hash` REGEXP '^[0-9a-f]{64}$'
      AND JSON_LENGTH(`difference_fields`)>0 AND JSON_LENGTH(`reason_codes`)>0
      AND (`target_canonical_grain_hash`<>`canonical_grain_hash`
        OR `target_on_hand_quantity`<>`source_on_hand_quantity`
        OR `target_reserved_quantity`<>`source_reserved_quantity`
        OR `target_in_transit_quantity`<>`source_in_transit_quantity`))
    OR (`comparison_result`='UNCOMPARABLE' AND `comparable`=b'0'
      AND JSON_LENGTH(`difference_fields`)=0 AND JSON_LENGTH(`reason_codes`)>0
      AND (`target_available`=b'1' OR (`target_available`=b'0'
        AND `target_record_version` IS NULL AND `target_canonical_grain_hash` IS NULL AND `target_on_hand_quantity` IS NULL
        AND `target_reserved_quantity` IS NULL AND `target_in_transit_quantity` IS NULL
        AND `target_projection_hash` IS NULL)))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
