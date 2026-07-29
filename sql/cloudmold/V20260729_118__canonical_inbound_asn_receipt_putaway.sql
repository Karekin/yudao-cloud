-- 入库权威聚合 first slice：ASN（发货通知）/收货/上架。
-- 补齐补货闭环下游缺失的物理作业 SoR，使 PO确认→ASN→收货→QC放行→上架 能产出真实业务结果。
-- 幂等信封 cloudmold_inbound_operation 仿 cloudmold_warehouse_operation；业务表带 version + status CHECK。
-- 仅新增表，不改既有结构。

CREATE TABLE IF NOT EXISTS `cloudmold_inbound_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(36) DEFAULT NULL,
  `operation_type` varchar(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `aggregate_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_inbound_operation` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_inbound_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_inbound_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_asn` (
  `asn_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `asn_no` varchar(64) NOT NULL,
  `source_business_type` varchar(32) NOT NULL,
  `source_business_ref` varchar(128) NOT NULL,
  `supplier_ref` varchar(64) DEFAULT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`asn_id`),
  UNIQUE KEY `uk_cm_asn_no` (`tenant_id`,`asn_no`),
  UNIQUE KEY `uk_cm_asn_source_ref` (`tenant_id`,`source_business_type`,`source_business_ref`),
  CONSTRAINT `ck_cm_asn_status` CHECK (`status` IN ('DRAFT','ANNOUNCED','IN_TRANSIT','RECEIVED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_asn_line` (
  `asn_line_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `asn_id` varchar(36) NOT NULL,
  `line_no` int NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `expected_quantity` decimal(24,6) NOT NULL,
  `unit_cost_amount_minor` bigint DEFAULT NULL,
  `currency_code` char(3) DEFAULT NULL,
  `staging_location_id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`asn_line_id`),
  UNIQUE KEY `uk_cm_asn_line_no` (`tenant_id`,`asn_id`,`line_no`),
  KEY `idx_cm_asn_line_asn` (`tenant_id`,`asn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_receipt` (
  `receipt_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `receipt_no` varchar(64) NOT NULL,
  `asn_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`receipt_id`),
  UNIQUE KEY `uk_cm_receipt_no` (`tenant_id`,`receipt_no`),
  UNIQUE KEY `uk_cm_receipt_asn` (`tenant_id`,`asn_id`),
  CONSTRAINT `ck_cm_receipt_status` CHECK (`status` IN ('DRAFT','RECEIVING','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_receipt_line` (
  `receipt_line_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `receipt_id` varchar(36) NOT NULL,
  `asn_line_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `expected_quantity` decimal(24,6) NOT NULL,
  `received_quantity` decimal(24,6) NOT NULL,
  `short_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `over_quantity` decimal(24,6) NOT NULL DEFAULT 0,
  `unit_cost_amount_minor` bigint DEFAULT NULL,
  `currency_code` char(3) DEFAULT NULL,
  `staging_location_id` varchar(36) NOT NULL,
  `qc_required` tinyint NOT NULL DEFAULT 0,
  `quality_task_id` varchar(36) DEFAULT NULL,
  `inventory_idempotency_key` varchar(128) DEFAULT NULL,
  `inventory_operation_id` bigint DEFAULT NULL,
  `inventory_ledger_tx_id` bigint DEFAULT NULL,
  `inventory_balance_id` varchar(36) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`receipt_line_id`),
  UNIQUE KEY `uk_cm_receipt_line_inv_key` (`tenant_id`,`inventory_idempotency_key`),
  KEY `idx_cm_receipt_line_receipt` (`tenant_id`,`receipt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_putaway` (
  `putaway_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `receipt_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `target_location_id` varchar(36) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`putaway_id`),
  UNIQUE KEY `uk_cm_putaway_receipt` (`tenant_id`,`receipt_id`),
  CONSTRAINT `ck_cm_putaway_status` CHECK (`status` IN ('PENDING','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
