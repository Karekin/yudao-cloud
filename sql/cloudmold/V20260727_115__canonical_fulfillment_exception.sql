CREATE TABLE IF NOT EXISTS `cloudmold_fulfillment_exception_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `exception_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_fulfillment_exception_operation_idem` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Fulfillment exception command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_fulfillment_exception` (
  `exception_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `exception_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `fulfillment_id` varchar(36) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `exception_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `action_code` varchar(32) DEFAULT NULL,
  `action_description` varchar(1000) DEFAULT NULL,
  `plan_evidence_ref` varchar(255) DEFAULT NULL,
  `approval_ref` varchar(255) DEFAULT NULL,
  `resolution_evidence_ref` varchar(255) DEFAULT NULL,
  `reason` varchar(2000) NOT NULL,
  `resolution_summary` varchar(2000) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`exception_id`),
  UNIQUE KEY `uk_fulfillment_exception_tenant_id` (`tenant_id`,`exception_id`),
  UNIQUE KEY `uk_fulfillment_exception_no` (`tenant_id`,`exception_no`),
  KEY `idx_fulfillment_exception_order` (`tenant_id`,`order_id`,`created_at`),
  KEY `idx_fulfillment_exception_active` (`tenant_id`,`fulfillment_id`,`status`),
  CONSTRAINT `fk_fulfillment_exception_order` FOREIGN KEY (`tenant_id`,`fulfillment_id`)
    REFERENCES `cloudmold_fulfillment_order` (`tenant_id`,`fulfillment_id`),
  CONSTRAINT `ck_fulfillment_exception_type` CHECK (
    `exception_type` IN ('DELAY','LOST','DAMAGED','ADDRESS_CHANGE')
  ),
  CONSTRAINT `ck_fulfillment_exception_status` CHECK (
    `status` IN ('OPEN','PLANNED','WAITING_APPROVAL','EXECUTING','RESOLVED','CLOSED')
  ),
  CONSTRAINT `ck_fulfillment_exception_action` CHECK (
    `action_code` IS NULL OR `action_code` IN (
      'CONTACT_CARRIER','TRACK_AND_WAIT','REISSUE_SHIPMENT','FILE_CLAIM',
      'REROUTE_ADDRESS','RETURN_TO_SENDER','MANUAL_REVIEW'
    )
  ),
  CONSTRAINT `ck_fulfillment_exception_version` CHECK (`version` > 0),
  CONSTRAINT `ck_fulfillment_exception_plan_evidence` CHECK (
    `status` = 'OPEN' OR
    (`action_code` IS NOT NULL AND `action_description` IS NOT NULL
      AND `plan_evidence_ref` IS NOT NULL)
  ),
  CONSTRAINT `ck_fulfillment_exception_approval_evidence` CHECK (
    `status` IN ('OPEN','PLANNED') OR `approval_ref` IS NOT NULL
  ),
  CONSTRAINT `ck_fulfillment_exception_resolution_evidence` CHECK (
    `status` NOT IN ('RESOLVED','CLOSED') OR
    (`resolution_evidence_ref` IS NOT NULL AND `resolution_summary` IS NOT NULL
      AND `resolved_at` IS NOT NULL)
  ),
  CONSTRAINT `ck_fulfillment_exception_closed_at` CHECK (
    `status` <> 'CLOSED' OR `closed_at` IS NOT NULL
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Canonical fulfillment exception case, approval gate, action and evidence authority';
