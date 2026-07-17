-- Governed benefit identity, funding, and quarantine readiness for immutable legacy Trade evidence.
-- Current promotion rows are observations only. They never qualify a historical benefit version.
-- Qualification registries require explicit evidence and are read by an immutable readiness run.

ALTER TABLE `cloudmold_order_benefit_migration_component`
  ADD UNIQUE KEY `uk_cm_order_benefit_mig_component_tenant_id`
    (`tenant_id`,`migration_run_id`,`component_id`);

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_identity_qualification` (
  `qualification_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `component_id` varchar(36) NOT NULL,
  `source_component_evidence_hash` char(64) NOT NULL,
  `historical_benefit_type` varchar(32) NOT NULL,
  `historical_benefit_id` varchar(128) NOT NULL,
  `historical_version_key` varchar(128) NOT NULL,
  `historical_snapshot_hash` char(64) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `qualified_by` varchar(128) NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`qualification_id`),
  UNIQUE KEY `uk_cm_order_benefit_identity_qual_tenant` (`tenant_id`,`qualification_id`),
  UNIQUE KEY `uk_cm_order_benefit_identity_qual_version`
    (`tenant_id`,`source_migration_run_id`,`component_id`,`version`),
  UNIQUE KEY `uk_cm_order_benefit_identity_qual_active`
    (`tenant_id`,`source_migration_run_id`,`component_id`,`active_guard`),
  CONSTRAINT `fk_cm_order_benefit_identity_qual_component` FOREIGN KEY
    (`tenant_id`,`source_migration_run_id`,`component_id`)
    REFERENCES `cloudmold_order_benefit_migration_component`
      (`tenant_id`,`migration_run_id`,`component_id`),
  CONSTRAINT `ck_cm_order_benefit_identity_qual_identity` CHECK (
    `source_system`='LOCAL_YUDAO_TRADE'
    AND `historical_benefit_type` IN ('GENERIC_DISCOUNT','COUPON','POINT','VIP')
    AND CHAR_LENGTH(TRIM(`historical_benefit_id`))>0
    AND CHAR_LENGTH(TRIM(`historical_version_key`))>0),
  CONSTRAINT `ck_cm_order_benefit_identity_qual_evidence` CHECK (
    `source_component_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND `historical_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`qualification_ref`))>0 AND CHAR_LENGTH(TRIM(`qualified_by`))>0),
  CONSTRAINT `ck_cm_order_benefit_identity_qual_status` CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_order_benefit_identity_qual_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicit historical benefit-version qualification for one immutable legacy component';

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_funding_qualification` (
  `funding_qualification_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `component_id` varchar(36) NOT NULL,
  `source_component_evidence_hash` char(64) NOT NULL,
  `funding_key` varchar(128) NOT NULL,
  `funder_type` varchar(16) NOT NULL,
  `funder_id` varchar(128) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `qualified_by` varchar(128) NOT NULL,
  `qualified_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`funding_qualification_id`),
  UNIQUE KEY `uk_cm_order_benefit_funding_qual_tenant` (`tenant_id`,`funding_qualification_id`),
  UNIQUE KEY `uk_cm_order_benefit_funding_qual_version`
    (`tenant_id`,`source_migration_run_id`,`component_id`,`funding_key`,`version`),
  UNIQUE KEY `uk_cm_order_benefit_funding_qual_active`
    (`tenant_id`,`source_migration_run_id`,`component_id`,`funding_key`,`active_guard`),
  CONSTRAINT `fk_cm_order_benefit_funding_qual_component` FOREIGN KEY
    (`tenant_id`,`source_migration_run_id`,`component_id`)
    REFERENCES `cloudmold_order_benefit_migration_component`
      (`tenant_id`,`migration_run_id`,`component_id`),
  CONSTRAINT `ck_cm_order_benefit_funding_qual_identity` CHECK (
    CHAR_LENGTH(TRIM(`funding_key`))>0 AND `funder_type` IN ('PLATFORM','MERCHANT','PARTNER')
    AND CHAR_LENGTH(TRIM(`funder_id`))>0),
  CONSTRAINT `ck_cm_order_benefit_funding_qual_money` CHECK (
    `amount_minor`>0 AND `currency_code`='CNY'),
  CONSTRAINT `ck_cm_order_benefit_funding_qual_evidence` CHECK (
    `source_component_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`qualification_ref`))>0 AND CHAR_LENGTH(TRIM(`qualified_by`))>0),
  CONSTRAINT `ck_cm_order_benefit_funding_qual_status` CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_order_benefit_funding_qual_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicit named economic funding shares for one immutable legacy benefit component';

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_quarantine_decision` (
  `decision_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `source_candidate_evidence_hash` char(64) NOT NULL,
  `decision_type` varchar(40) NOT NULL,
  `decision_reason` varchar(512) NOT NULL,
  `decision_ref` varchar(256) NOT NULL,
  `decided_by` varchar(128) NOT NULL,
  `decided_at` datetime(6) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status`='QUALIFIED' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`decision_id`),
  UNIQUE KEY `uk_cm_order_benefit_quarantine_dec_tenant` (`tenant_id`,`decision_id`),
  UNIQUE KEY `uk_cm_order_benefit_quarantine_dec_version`
    (`tenant_id`,`source_migration_run_id`,`candidate_id`,`version`),
  UNIQUE KEY `uk_cm_order_benefit_quarantine_dec_active`
    (`tenant_id`,`source_migration_run_id`,`candidate_id`,`active_guard`),
  CONSTRAINT `fk_cm_order_benefit_quarantine_dec_candidate` FOREIGN KEY
    (`tenant_id`,`source_migration_run_id`,`candidate_id`,`legacy_order_id`)
    REFERENCES `cloudmold_order_benefit_migration_candidate`
      (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  CONSTRAINT `ck_cm_order_benefit_quarantine_dec_type` CHECK (
    `decision_type`='EXCLUDE_CONFIRMED_SOURCE_DEFECT'),
  CONSTRAINT `ck_cm_order_benefit_quarantine_dec_evidence` CHECK (
    `source_candidate_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND CHAR_LENGTH(TRIM(`decision_reason`))>0 AND CHAR_LENGTH(TRIM(`decision_ref`))>0
    AND CHAR_LENGTH(TRIM(`decided_by`))>0),
  CONSTRAINT `ck_cm_order_benefit_quarantine_dec_status` CHECK (`status` IN ('QUALIFIED','REVOKED')),
  CONSTRAINT `ck_cm_order_benefit_quarantine_dec_version` CHECK (`version`>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Explicit reviewed exclusion of an immutable legacy source defect; corrections require a new source run';

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_governance_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(128) DEFAULT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL,
  `governance_run_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_order_benefit_gov_op_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_order_benefit_gov_op_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_order_benefit_gov_op_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_order_benefit_gov_op_result` CHECK (
    (`status`=0 AND `governance_run_id` IS NULL AND `result_json` IS NULL)
    OR (`status`=10 AND `governance_run_id` IS NOT NULL AND `result_json` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_governance_run` (
  `governance_run_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `policy_version` varchar(64) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `governance_evidence_hash` char(64) NOT NULL,
  `source_component_count` int NOT NULL,
  `source_reference_present_count` int NOT NULL,
  `current_reference_observed_count` int NOT NULL,
  `historical_identity_qualified_count` int NOT NULL,
  `identity_blocked_count` int NOT NULL,
  `funding_qualified_count` int NOT NULL,
  `funding_blocked_count` int NOT NULL,
  `source_quarantine_count` int NOT NULL,
  `quarantine_decided_count` int NOT NULL,
  `quarantine_open_count` int NOT NULL,
  `governance_admitted_component_count` int NOT NULL,
  `production_migration_enabled` tinyint(1) NOT NULL,
  `status` varchar(96) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`governance_run_id`),
  UNIQUE KEY `uk_cm_order_benefit_gov_run_tenant` (`tenant_id`,`governance_run_id`),
  UNIQUE KEY `uk_cm_order_benefit_gov_source_run`
    (`tenant_id`,`source_migration_run_id`,`governance_run_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_source_run` FOREIGN KEY (`tenant_id`,`source_migration_run_id`)
    REFERENCES `cloudmold_order_benefit_migration_run` (`tenant_id`,`migration_run_id`),
  CONSTRAINT `ck_cm_order_benefit_gov_run_denominator` CHECK (
    `source_component_count`>0
    AND `source_reference_present_count` BETWEEN 0 AND `source_component_count`
    AND `current_reference_observed_count` BETWEEN 0 AND `source_reference_present_count`
    AND `source_component_count`=`historical_identity_qualified_count`+`identity_blocked_count`
    AND `source_component_count`=`funding_qualified_count`+`funding_blocked_count`
    AND `source_quarantine_count`=`quarantine_decided_count`+`quarantine_open_count`),
  CONSTRAINT `ck_cm_order_benefit_gov_run_admission` CHECK (
    `governance_admitted_component_count` BETWEEN 0 AND `source_component_count`
    AND `production_migration_enabled`=0),
  CONSTRAINT `ck_cm_order_benefit_gov_run_evidence` CHECK (
    `governance_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND `policy_version`='legacy-trade-benefit-governance-v1'
    AND CHAR_LENGTH(TRIM(`evidence_ref`))>0),
  CONSTRAINT `ck_cm_order_benefit_gov_run_status` CHECK (
    `status` IN ('READY_FOR_COMBINED_ADMISSION',
      'BLOCKED_REQUIRES_HISTORICAL_BENEFIT_FUNDING_AND_QUARANTINE_DECISIONS')),
  CONSTRAINT `ck_cm_order_benefit_gov_run_version` CHECK (`version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_governance_component` (
  `component_governance_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `governance_run_id` varchar(36) NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `component_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `component_type` varchar(24) NOT NULL,
  `component_amount_minor` bigint NOT NULL,
  `source_reference` varchar(128) DEFAULT NULL,
  `source_component_evidence_hash` char(64) NOT NULL,
  `current_reference_status` varchar(64) NOT NULL,
  `observed_source_table` varchar(64) DEFAULT NULL,
  `observed_source_id` bigint DEFAULT NULL,
  `observed_source_created_at` datetime(6) DEFAULT NULL,
  `observed_source_updated_at` datetime(6) DEFAULT NULL,
  `observed_source_status` varchar(32) DEFAULT NULL,
  `observed_source_deleted` tinyint(1) DEFAULT NULL,
  `observed_source_spu_id` bigint DEFAULT NULL,
  `current_reference_snapshot_hash` char(64) DEFAULT NULL,
  `identity_qualification_id` varchar(36) DEFAULT NULL,
  `historical_identity_status` varchar(48) NOT NULL,
  `funding_share_count` int NOT NULL,
  `funding_amount_minor` bigint NOT NULL,
  `funding_resolution_status` varchar(48) NOT NULL,
  `governance_status` varchar(16) NOT NULL,
  `blocker_codes` json NOT NULL,
  `governance_admission_allowed` tinyint(1) NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL,
  `evidence_hash` char(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`component_governance_id`),
  UNIQUE KEY `uk_cm_order_benefit_gov_component_source`
    (`tenant_id`,`governance_run_id`,`component_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_component_run` FOREIGN KEY (`tenant_id`,`governance_run_id`)
    REFERENCES `cloudmold_order_benefit_governance_run` (`tenant_id`,`governance_run_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_component_source` FOREIGN KEY
    (`tenant_id`,`source_migration_run_id`,`component_id`)
    REFERENCES `cloudmold_order_benefit_migration_component`
      (`tenant_id`,`migration_run_id`,`component_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_component_identity` FOREIGN KEY
    (`tenant_id`,`identity_qualification_id`)
    REFERENCES `cloudmold_order_benefit_identity_qualification` (`tenant_id`,`qualification_id`),
  CONSTRAINT `ck_cm_order_benefit_gov_component_current` CHECK (
    (`current_reference_status`='CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION'
      AND `source_reference` IS NOT NULL AND `observed_source_table` IS NOT NULL
      AND `observed_source_id` IS NOT NULL AND `observed_source_created_at` IS NOT NULL
      AND `observed_source_updated_at` IS NOT NULL AND `observed_source_deleted` IS NOT NULL
      AND `current_reference_snapshot_hash` REGEXP '^[0-9a-f]{64}$')
    OR (`current_reference_status` IN ('MISSING_SOURCE_REFERENCE','SOURCE_REFERENCE_NOT_FOUND',
          'NON_VERSIONED_ENTITLEMENT_QUANTITY_ONLY')
      AND `observed_source_table` IS NULL AND `observed_source_id` IS NULL
      AND `observed_source_created_at` IS NULL AND `observed_source_updated_at` IS NULL
      AND `observed_source_status` IS NULL AND `observed_source_deleted` IS NULL
      AND `observed_source_spu_id` IS NULL AND `current_reference_snapshot_hash` IS NULL)),
  CONSTRAINT `ck_cm_order_benefit_gov_component_identity` CHECK (
    (`historical_identity_status`='QUALIFIED' AND `identity_qualification_id` IS NOT NULL)
    OR (`historical_identity_status` IN ('BLOCKED_MISSING_HISTORICAL_VERSION',
          'BLOCKED_MISSING_SOURCE_REFERENCE','BLOCKED_SOURCE_REFERENCE_NOT_FOUND')
      AND `identity_qualification_id` IS NULL)),
  CONSTRAINT `ck_cm_order_benefit_gov_component_funding` CHECK (
    `funding_share_count`>=0 AND `funding_amount_minor`>=0
    AND `funding_resolution_status` IN ('QUALIFIED','BLOCKED_MISSING_NAMED_FUNDER_BREAKDOWN',
      'BLOCKED_FUNDING_AMOUNT_MISMATCH','BLOCKED_SOURCE_COMPONENT_NOT_POSITIVE')),
  CONSTRAINT `ck_cm_order_benefit_gov_component_readiness` CHECK (
    JSON_TYPE(`blocker_codes`)='ARRAY'
    AND ((`governance_status`='READY' AND JSON_LENGTH(`blocker_codes`)=0
      AND `governance_admission_allowed`=1)
      OR (`governance_status`='BLOCKED' AND JSON_LENGTH(`blocker_codes`)>0
      AND `governance_admission_allowed`=0))
    AND `canonical_import_allowed`=0
    AND `source_component_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND `evidence_hash` REGEXP '^[0-9a-f]{64}$' AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_order_benefit_governance_quarantine` (
  `quarantine_governance_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `governance_run_id` varchar(36) NOT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `candidate_id` varchar(36) NOT NULL,
  `legacy_order_id` bigint NOT NULL,
  `legacy_order_no` varchar(64) NOT NULL,
  `source_candidate_evidence_hash` char(64) NOT NULL,
  `source_assessment_status` varchar(40) NOT NULL,
  `source_reason_codes` json NOT NULL,
  `decision_id` varchar(36) DEFAULT NULL,
  `decision_status` varchar(16) NOT NULL,
  `recommended_action` varchar(48) NOT NULL,
  `blocker_codes` json NOT NULL,
  `canonical_import_allowed` tinyint(1) NOT NULL,
  `evidence_hash` char(64) NOT NULL,
  `version` bigint NOT NULL,
  `assessed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`quarantine_governance_id`),
  UNIQUE KEY `uk_cm_order_benefit_gov_quarantine_source`
    (`tenant_id`,`governance_run_id`,`candidate_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_quarantine_run` FOREIGN KEY (`tenant_id`,`governance_run_id`)
    REFERENCES `cloudmold_order_benefit_governance_run` (`tenant_id`,`governance_run_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_quarantine_source` FOREIGN KEY
    (`tenant_id`,`source_migration_run_id`,`candidate_id`,`legacy_order_id`)
    REFERENCES `cloudmold_order_benefit_migration_candidate`
      (`tenant_id`,`migration_run_id`,`candidate_id`,`legacy_order_id`),
  CONSTRAINT `fk_cm_order_benefit_gov_quarantine_decision` FOREIGN KEY (`tenant_id`,`decision_id`)
    REFERENCES `cloudmold_order_benefit_quarantine_decision` (`tenant_id`,`decision_id`),
  CONSTRAINT `ck_cm_order_benefit_gov_quarantine_source_status` CHECK (
    `source_assessment_status` IN ('QUARANTINED_MONEY','QUARANTINED_HEADER_ITEM')
    AND JSON_TYPE(`source_reason_codes`)='ARRAY' AND JSON_LENGTH(`source_reason_codes`)>0),
  CONSTRAINT `ck_cm_order_benefit_gov_quarantine_decision` CHECK (
    (`decision_status`='DECIDED' AND `decision_id` IS NOT NULL
      AND JSON_LENGTH(`blocker_codes`)=0)
    OR (`decision_status`='OPEN' AND `decision_id` IS NULL
      AND JSON_LENGTH(`blocker_codes`)>0)),
  CONSTRAINT `ck_cm_order_benefit_gov_quarantine_readiness` CHECK (
    `recommended_action` IN ('EXCLUDE_CONFIRMED_SOURCE_DEFECT','CORRECT_SOURCE_AND_REASSESS')
    AND JSON_TYPE(`blocker_codes`)='ARRAY' AND `canonical_import_allowed`=0
    AND `source_candidate_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND `evidence_hash` REGEXP '^[0-9a-f]{64}$' AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
