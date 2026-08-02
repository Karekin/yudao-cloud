-- CloudMold managed merchant invitation, buyer assignment, grade/benefit, probation,
-- monthly scorecard, and exit-decision additive first slice.
-- Thresholds, detailed scoring, and source evidence remain in external controlled stores via config/evidence refs.

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_managed_invitation` (
  `invitation_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `invitation_code` varchar(64) NOT NULL,
  `recruiter_principal_id` varchar(36) NOT NULL,
  `attribution_source_system` varchar(32) NOT NULL,
  `attribution_source_type` varchar(32) NOT NULL,
  `attribution_source_id` varchar(128) NOT NULL,
  `attribution_reference` varchar(128) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `used_admission_id` varchar(36) DEFAULT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`invitation_id`),
  UNIQUE KEY `uk_cm_merchant_invitation_tenant_id` (`tenant_id`,`invitation_id`),
  UNIQUE KEY `uk_cm_merchant_invitation_code` (`tenant_id`,`invitation_code`),
  KEY `idx_cm_merchant_invitation_status` (`tenant_id`,`status`),
  CONSTRAINT `fk_cm_merchant_invitation_used_admission` FOREIGN KEY (`tenant_id`,`used_admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `ck_cm_merchant_invitation_status` CHECK (`status` IN ('ISSUED','USED','REVOKED')),
  CONSTRAINT `ck_cm_merchant_invitation_usage` CHECK (
    (`status`='ISSUED' AND `used_admission_id` IS NULL AND `used_at` IS NULL)
    OR (`status`='USED' AND `used_admission_id` IS NOT NULL AND `used_at` IS NOT NULL)
    OR (`status`='REVOKED')
  ),
  CONSTRAINT `ck_cm_merchant_invitation_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_buyer_assignment` (
  `buyer_assignment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `admission_id` varchar(36) NOT NULL,
  `inspection_task_id` varchar(36) NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) NOT NULL,
  `buyer_tl_principal_id` varchar(36) NOT NULL,
  `buyer_principal_id` varchar(36) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`buyer_assignment_id`),
  UNIQUE KEY `uk_cm_merchant_buyer_assignment_tenant_id` (`tenant_id`,`buyer_assignment_id`),
  UNIQUE KEY `uk_cm_merchant_buyer_assignment_admission` (`tenant_id`,`admission_id`,`status`),
  CONSTRAINT `fk_cm_merchant_buyer_assignment_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_buyer_assignment_task` FOREIGN KEY (`tenant_id`,`inspection_task_id`)
    REFERENCES `cloudmold_merchant_factory_inspection_task` (`tenant_id`,`inspection_task_id`),
  CONSTRAINT `fk_cm_merchant_buyer_assignment_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_cm_merchant_buyer_assignment_shop` FOREIGN KEY (`tenant_id`,`shop_id`)
    REFERENCES `cloudmold_merchant_shop` (`tenant_id`,`shop_id`),
  CONSTRAINT `ck_cm_merchant_buyer_assignment_status` CHECK (`status` IN ('ACTIVE','REVOKED')),
  CONSTRAINT `ck_cm_merchant_buyer_assignment_distinct` CHECK (`buyer_tl_principal_id` <> `buyer_principal_id`),
  CONSTRAINT `ck_cm_merchant_buyer_assignment_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_grade_decision` (
  `grade_decision_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) DEFAULT NULL,
  `probation_assessment_id` varchar(36) DEFAULT NULL,
  `scorecard_id` varchar(36) DEFAULT NULL,
  `grade_code` varchar(32) NOT NULL,
  `transition_decision` varchar(16) NOT NULL,
  `decision_status` varchar(16) NOT NULL,
  `thresholds_config_ref` varchar(256) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `entitlements_json` json NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`grade_decision_id`),
  UNIQUE KEY `uk_cm_merchant_grade_decision_tenant_id` (`tenant_id`,`grade_decision_id`),
  CONSTRAINT `fk_cm_merchant_grade_decision_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `ck_cm_merchant_grade_transition` CHECK (`transition_decision` IN ('UPGRADE','MAINTAIN','DOWNGRADE')),
  CONSTRAINT `ck_cm_merchant_grade_decision_status` CHECK (`decision_status` IN ('APPROVED','REJECTED')),
  CONSTRAINT `ck_cm_merchant_grade_decision_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_probation_assessment` (
  `probation_assessment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `admission_id` varchar(36) DEFAULT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) DEFAULT NULL,
  `assessment_status` varchar(32) NOT NULL,
  `thresholds_config_ref` varchar(256) NOT NULL,
  `gates_json` json NOT NULL,
  `gate_count` int NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`probation_assessment_id`),
  UNIQUE KEY `uk_cm_merchant_probation_tenant_id` (`tenant_id`,`probation_assessment_id`),
  CONSTRAINT `fk_cm_merchant_probation_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_probation_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `ck_cm_merchant_probation_status` CHECK (`assessment_status` IN ('PASS','FAIL','NEEDS_REMEDIATION')),
  CONSTRAINT `ck_cm_merchant_probation_gate_count` CHECK (`gate_count` = 3),
  CONSTRAINT `ck_cm_merchant_probation_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_merchant_grade_decision`
  ADD CONSTRAINT `fk_cm_merchant_grade_decision_probation`
  FOREIGN KEY (`tenant_id`,`probation_assessment_id`)
  REFERENCES `cloudmold_merchant_probation_assessment` (`tenant_id`,`probation_assessment_id`);

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_monthly_scorecard` (
  `scorecard_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) DEFAULT NULL,
  `scorecard_month` char(7) NOT NULL,
  `scorecard_status` varchar(16) NOT NULL,
  `transition_recommendation` varchar(16) NOT NULL,
  `thresholds_config_ref` varchar(256) NOT NULL,
  `items_json` json NOT NULL,
  `item_count` int NOT NULL,
  `red_line_count` int NOT NULL,
  `remediation_failure_count` int NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`scorecard_id`),
  UNIQUE KEY `uk_cm_merchant_scorecard_tenant_id` (`tenant_id`,`scorecard_id`),
  UNIQUE KEY `uk_cm_merchant_scorecard_month` (`tenant_id`,`merchant_id`,`scorecard_month`),
  CONSTRAINT `fk_cm_merchant_scorecard_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `ck_cm_merchant_scorecard_status` CHECK (`scorecard_status` IN ('RECORDED','REVIEWED')),
  CONSTRAINT `ck_cm_merchant_scorecard_transition` CHECK (`transition_recommendation` IN ('UPGRADE','MAINTAIN','DOWNGRADE')),
  CONSTRAINT `ck_cm_merchant_scorecard_month` CHECK (`scorecard_month` REGEXP '^[0-9]{4}-[0-9]{2}$'),
  CONSTRAINT `ck_cm_merchant_scorecard_item_count` CHECK (`item_count` = 9),
  CONSTRAINT `ck_cm_merchant_scorecard_counts` CHECK ((`red_line_count` >= 0) AND (`remediation_failure_count` >= 0)),
  CONSTRAINT `ck_cm_merchant_scorecard_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_exit_decision` (
  `exit_decision_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) DEFAULT NULL,
  `admission_id` varchar(36) DEFAULT NULL,
  `scorecard_id` varchar(36) DEFAULT NULL,
  `reason_type` varchar(64) NOT NULL,
  `decision_status` varchar(16) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `note` varchar(512) DEFAULT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`exit_decision_id`),
  UNIQUE KEY `uk_cm_merchant_exit_tenant_id` (`tenant_id`,`exit_decision_id`),
  CONSTRAINT `fk_cm_merchant_exit_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_cm_merchant_exit_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_exit_scorecard` FOREIGN KEY (`tenant_id`,`scorecard_id`)
    REFERENCES `cloudmold_merchant_monthly_scorecard` (`tenant_id`,`scorecard_id`),
  CONSTRAINT `ck_cm_merchant_exit_reason_type` CHECK (`reason_type` IN ('RED_LINE','MULTIPLE_REMEDIATION_FAILURE')),
  CONSTRAINT `ck_cm_merchant_exit_decision_status` CHECK (`decision_status` IN ('APPROVED','REJECTED')),
  CONSTRAINT `ck_cm_merchant_exit_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
