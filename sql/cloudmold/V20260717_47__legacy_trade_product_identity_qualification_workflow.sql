-- Dual-review workflow for historical product identity qualification.
-- No actor can request and approve, or satisfy both approval roles.

CREATE TABLE IF NOT EXISTS `cloudmold_order_product_identity_qualification_request` (
  `request_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `action_type` varchar(16) NOT NULL,
  `target_qualification_id` varchar(36) DEFAULT NULL,
  `source_migration_run_id` varchar(36) NOT NULL,
  `item_evidence_id` varchar(36) NOT NULL,
  `legacy_order_item_id` bigint NOT NULL,
  `historical_spu_id` bigint NOT NULL,
  `historical_sku_id` bigint NOT NULL,
  `source_item_evidence_hash` char(64) NOT NULL,
  `historical_product_snapshot_hash` char(64) NOT NULL,
  `source_evidence_uri` varchar(512) NOT NULL,
  `qualification_ref` varchar(256) NOT NULL,
  `scope_hash` char(64) NOT NULL,
  `requester_id` bigint NOT NULL,
  `approval_count` int NOT NULL,
  `status` varchar(32) NOT NULL,
  `qualification_id` varchar(36) DEFAULT NULL,
  `open_guard` tinyint GENERATED ALWAYS AS (
    CASE WHEN `status` IN ('PENDING','PARTIALLY_APPROVED') THEN 1 ELSE NULL END
  ) STORED,
  `version` bigint NOT NULL,
  `requested_at` datetime(6) NOT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uk_cm_order_product_identity_request_tenant` (`tenant_id`,`request_id`),
  UNIQUE KEY `uk_cm_order_product_identity_request_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_order_product_identity_request_open`
    (`tenant_id`,`source_migration_run_id`,`item_evidence_id`,`action_type`,`open_guard`),
  CONSTRAINT `fk_cm_order_product_identity_request_source`
    FOREIGN KEY (`tenant_id`,`item_evidence_id`)
    REFERENCES `cloudmold_order_benefit_migration_item` (`tenant_id`,`item_evidence_id`),
  CONSTRAINT `fk_cm_order_product_identity_request_target`
    FOREIGN KEY (`tenant_id`,`target_qualification_id`)
    REFERENCES `cloudmold_order_product_identity_qualification` (`tenant_id`,`qualification_id`),
  CONSTRAINT `fk_cm_order_product_identity_request_result`
    FOREIGN KEY (`tenant_id`,`qualification_id`)
    REFERENCES `cloudmold_order_product_identity_qualification` (`tenant_id`,`qualification_id`),
  CONSTRAINT `ck_cm_order_product_identity_request_action` CHECK (
    (`action_type`='QUALIFY' AND `target_qualification_id` IS NULL)
    OR (`action_type`='REVOKE' AND `target_qualification_id` IS NOT NULL)),
  CONSTRAINT `ck_cm_order_product_identity_request_identity` CHECK (
    `legacy_order_item_id`>0 AND `historical_spu_id`>0 AND `historical_sku_id`>0),
  CONSTRAINT `ck_cm_order_product_identity_request_hashes` CHECK (
    `request_hash` REGEXP '^[0-9a-f]{64}$'
    AND `source_item_evidence_hash` REGEXP '^[0-9a-f]{64}$'
    AND `historical_product_snapshot_hash` REGEXP '^[0-9a-f]{64}$'
    AND `scope_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `ck_cm_order_product_identity_request_evidence` CHECK (
    CHAR_LENGTH(TRIM(`source_evidence_uri`))>0
    AND CHAR_LENGTH(TRIM(`qualification_ref`))>0 AND `requester_id`>0),
  CONSTRAINT `ck_cm_order_product_identity_request_state` CHECK (
    (`status`='PENDING' AND `approval_count`=0 AND `version`=1
      AND `qualification_id` IS NULL AND `applied_at` IS NULL)
    OR (`status`='PARTIALLY_APPROVED' AND `approval_count`=1 AND `version`=2
      AND `qualification_id` IS NULL AND `applied_at` IS NULL)
    OR (`status`='APPLIED' AND `approval_count`=2 AND `version`=3
      AND `qualification_id` IS NOT NULL AND `applied_at` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Dual-review request for historical product qualification or revocation';

CREATE TABLE IF NOT EXISTS `cloudmold_order_product_identity_qualification_approval` (
  `approval_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `request_id` varchar(36) NOT NULL,
  `approval_role` varchar(32) NOT NULL,
  `approver_id` bigint NOT NULL,
  `scope_hash` char(64) NOT NULL,
  `expected_request_version` bigint NOT NULL,
  `evidence_ref` varchar(256) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `approved_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`approval_id`),
  UNIQUE KEY `uk_cm_order_product_identity_approval_role` (`tenant_id`,`request_id`,`approval_role`),
  UNIQUE KEY `uk_cm_order_product_identity_approval_actor` (`tenant_id`,`request_id`,`approver_id`),
  UNIQUE KEY `uk_cm_order_product_identity_approval_idempotency` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `fk_cm_order_product_identity_approval_request`
    FOREIGN KEY (`tenant_id`,`request_id`)
    REFERENCES `cloudmold_order_product_identity_qualification_request` (`tenant_id`,`request_id`),
  CONSTRAINT `ck_cm_order_product_identity_approval_role`
    CHECK (`approval_role` IN ('DATA_OWNER','CHANGE_MANAGER')),
  CONSTRAINT `ck_cm_order_product_identity_approval_shape` CHECK (
    `approver_id`>0 AND `scope_hash` REGEXP '^[0-9a-f]{64}$'
    AND `request_hash` REGEXP '^[0-9a-f]{64}$'
    AND `expected_request_version` IN (1,2)
    AND CHAR_LENGTH(TRIM(`evidence_ref`))>0
    AND `status`='APPROVED' AND `version`=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable role approval bound to one historical product identity request scope';

ALTER TABLE `cloudmold_order_product_identity_qualification`
  DROP INDEX `uk_cm_order_product_identity_qual_version`,
  ADD COLUMN `request_id` varchar(36) NOT NULL AFTER `qualification_ref`,
  ADD COLUMN `approval_set_hash` char(64) NOT NULL AFTER `request_id`,
  ADD COLUMN `revocation_request_id` varchar(36) DEFAULT NULL AFTER `approval_set_hash`,
  ADD COLUMN `revocation_approval_set_hash` char(64) DEFAULT NULL AFTER `revocation_request_id`,
  ADD COLUMN `revoked_at` datetime(6) DEFAULT NULL AFTER `revocation_approval_set_hash`,
  ADD CONSTRAINT `fk_cm_order_product_identity_qualification_request`
    FOREIGN KEY (`tenant_id`,`request_id`)
    REFERENCES `cloudmold_order_product_identity_qualification_request` (`tenant_id`,`request_id`),
  ADD CONSTRAINT `fk_cm_order_product_identity_qualification_revocation_request`
    FOREIGN KEY (`tenant_id`,`revocation_request_id`)
    REFERENCES `cloudmold_order_product_identity_qualification_request` (`tenant_id`,`request_id`),
  ADD UNIQUE KEY `uk_cm_order_product_identity_qualification_request` (`tenant_id`,`request_id`),
  ADD UNIQUE KEY `uk_cm_order_product_identity_revocation_request` (`tenant_id`,`revocation_request_id`),
  ADD CONSTRAINT `ck_cm_order_product_identity_qualification_workflow` CHECK (
    `approval_set_hash` REGEXP '^[0-9a-f]{64}$'
    AND ((`status`='QUALIFIED' AND `revocation_request_id` IS NULL
      AND `revocation_approval_set_hash` IS NULL AND `revoked_at` IS NULL)
    OR (`status`='REVOKED' AND `revocation_request_id` IS NOT NULL
      AND `revocation_approval_set_hash` REGEXP '^[0-9a-f]{64}$'
      AND `revoked_at` IS NOT NULL)));
