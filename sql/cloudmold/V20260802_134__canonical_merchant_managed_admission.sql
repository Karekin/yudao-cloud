-- CloudMold managed merchant admission and factory-inspection first slice.
-- Records only governed human review, evidence references, AI recommendations, and explicit readback gates.

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_managed_admission` (
  `admission_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `application_id` varchar(36) NOT NULL,
  `merchant_id` varchar(36) NOT NULL,
  `shop_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `attribution_channel_code` varchar(32) DEFAULT NULL,
  `attribution_source_system` varchar(32) DEFAULT NULL,
  `attribution_source_type` varchar(32) DEFAULT NULL,
  `attribution_source_id` varchar(128) DEFAULT NULL,
  `attribution_reference` varchar(128) DEFAULT NULL,
  `attribution_evidence_ref` varchar(256) DEFAULT NULL,
  `diagnostic_id` varchar(36) DEFAULT NULL,
  `inspection_task_id` varchar(36) DEFAULT NULL,
  `final_review_id` varchar(36) DEFAULT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`admission_id`),
  UNIQUE KEY `uk_cm_merchant_managed_admission_tenant_id` (`tenant_id`,`admission_id`),
  UNIQUE KEY `uk_cm_merchant_managed_admission_app` (`tenant_id`,`application_id`),
  KEY `idx_cm_merchant_managed_admission_merchant` (`tenant_id`,`merchant_id`,`status`),
  KEY `idx_cm_merchant_managed_admission_shop` (`tenant_id`,`shop_id`,`status`),
  CONSTRAINT `fk_cm_merchant_managed_admission_app` FOREIGN KEY (`tenant_id`,`application_id`)
    REFERENCES `cloudmold_merchant_onboarding_application` (`tenant_id`,`application_id`),
  CONSTRAINT `fk_cm_merchant_managed_admission_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_cm_merchant_managed_admission_shop` FOREIGN KEY (`tenant_id`,`shop_id`)
    REFERENCES `cloudmold_merchant_shop` (`tenant_id`,`shop_id`),
  CONSTRAINT `ck_cm_merchant_managed_admission_status` CHECK (`status` IN (
    'ATTRIBUTION_PENDING','EVIDENCE_PENDING','DIAGNOSTIC_PENDING','DIAGNOSTIC_PENDING_REVIEW',
    'DIAGNOSTIC_REJECTED','INSPECTION_PENDING','INSPECTION_IN_PROGRESS','INSPECTION_CANCELLED',
    'FINAL_REVIEW_PENDING','FINAL_APPROVED','FINAL_REJECTED'
  )),
  CONSTRAINT `ck_cm_merchant_managed_admission_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_managed_evidence_package` (
  `evidence_package_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `admission_id` varchar(36) NOT NULL,
  `package_ref` varchar(256) NOT NULL,
  `items_json` json NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`evidence_package_id`),
  UNIQUE KEY `uk_cm_merchant_evidence_tenant_id` (`tenant_id`,`evidence_package_id`),
  UNIQUE KEY `uk_cm_merchant_evidence_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_evidence_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `ck_cm_merchant_evidence_status` CHECK (`status` IN ('SUBMITTED')),
  CONSTRAINT `ck_cm_merchant_evidence_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_ai_diagnostic` (
  `diagnostic_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `admission_id` varchar(36) NOT NULL,
  `evidence_package_id` varchar(36) NOT NULL,
  `recommendation_code` varchar(64) NOT NULL,
  `recommendation_summary` varchar(512) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `status` varchar(32) NOT NULL,
  `reviewer_principal_id` varchar(36) DEFAULT NULL,
  `review_note` varchar(512) DEFAULT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`diagnostic_id`),
  UNIQUE KEY `uk_cm_merchant_ai_diag_tenant_id` (`tenant_id`,`diagnostic_id`),
  UNIQUE KEY `uk_cm_merchant_ai_diag_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_ai_diag_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_ai_diag_evidence` FOREIGN KEY (`tenant_id`,`evidence_package_id`)
    REFERENCES `cloudmold_merchant_managed_evidence_package` (`tenant_id`,`evidence_package_id`),
  CONSTRAINT `ck_cm_merchant_ai_diag_recommendation` CHECK (`recommendation_code` IN (
    'FACTORY_INSPECTION_REQUIRED','MANUAL_REVIEW_ONLY','REJECT_ADMISSION'
  )),
  CONSTRAINT `ck_cm_merchant_ai_diag_status` CHECK (`status` IN ('PENDING_REVIEW','ACCEPTED','REJECTED')),
  CONSTRAINT `ck_cm_merchant_ai_diag_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_factory_inspection_task` (
  `inspection_task_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `admission_id` varchar(36) NOT NULL,
  `diagnostic_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `actor_principal_id` varchar(36) DEFAULT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `evidence_ref` varchar(256) DEFAULT NULL,
  `note` varchar(512) DEFAULT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`inspection_task_id`),
  UNIQUE KEY `uk_cm_merchant_inspection_tenant_id` (`tenant_id`,`inspection_task_id`),
  UNIQUE KEY `uk_cm_merchant_inspection_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_inspection_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_inspection_diag` FOREIGN KEY (`tenant_id`,`diagnostic_id`)
    REFERENCES `cloudmold_merchant_ai_diagnostic` (`tenant_id`,`diagnostic_id`),
  CONSTRAINT `ck_cm_merchant_inspection_status` CHECK (`status` IN (
    'PENDING_CLAIM','PENDING_SCHEDULE','PENDING_INSPECTION','PENDING_QA_INSPECTION',
    'PENDING_REMEDIATION','PENDING_FIRST_REVIEW','PENDING_FINAL_REVIEW','COMPLETED','CANCELLED'
  )),
  CONSTRAINT `ck_cm_merchant_inspection_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_merchant_managed_final_review` (
  `final_review_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `admission_id` varchar(36) NOT NULL,
  `inspection_task_id` varchar(36) DEFAULT NULL,
  `decision` varchar(16) NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `reviewer_principal_id` varchar(36) NOT NULL,
  `review_note` varchar(512) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`final_review_id`),
  UNIQUE KEY `uk_cm_merchant_final_review_tenant_id` (`tenant_id`,`final_review_id`),
  UNIQUE KEY `uk_cm_merchant_final_review_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_final_review_admission` FOREIGN KEY (`tenant_id`,`admission_id`)
    REFERENCES `cloudmold_merchant_managed_admission` (`tenant_id`,`admission_id`),
  CONSTRAINT `fk_cm_merchant_final_review_inspection` FOREIGN KEY (`tenant_id`,`inspection_task_id`)
    REFERENCES `cloudmold_merchant_factory_inspection_task` (`tenant_id`,`inspection_task_id`),
  CONSTRAINT `ck_cm_merchant_final_review_decision` CHECK (`decision` IN ('APPROVED','REJECTED')),
  CONSTRAINT `ck_cm_merchant_final_review_status` CHECK (`status` IN ('APPROVED','REJECTED')),
  CONSTRAINT `ck_cm_merchant_final_review_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
