CREATE TABLE IF NOT EXISTS `cloudmold_purchase_requisition` (
    `requisition_id` VARCHAR(128) NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `requisition_code` VARCHAR(64) NOT NULL,
    `source_business_type` VARCHAR(64) NOT NULL,
    `source_business_ref` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `requested_by_principal_id` VARCHAR(128) NOT NULL,
    `approved_by_principal_id` VARCHAR(128) NOT NULL,
    `reason_code` VARCHAR(64) NOT NULL,
    `remark` VARCHAR(255) NULL,
    `version` BIGINT NOT NULL,
    `requested_at` DATETIME(6) NOT NULL,
    `approved_at` DATETIME(6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`requisition_id`),
    UNIQUE KEY `uk_purchase_requisition_tenant_id` (`tenant_id`, `requisition_id`),
    UNIQUE KEY `uk_purchase_requisition_code` (`tenant_id`, `requisition_code`),
    UNIQUE KEY `uk_purchase_requisition_source` (`tenant_id`, `source_business_type`, `source_business_ref`),
    KEY `idx_purchase_requisition_status` (`tenant_id`, `status`, `updated_at`),
    CONSTRAINT `ck_purchase_requisition_status`
        CHECK (`status` IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED','CLOSED')),
    CONSTRAINT `ck_purchase_requisition_version` CHECK (`version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical supplier-neutral purchase requisition header';

CREATE TABLE IF NOT EXISTS `cloudmold_purchase_requisition_line` (
    `line_id` VARCHAR(128) NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `requisition_id` VARCHAR(128) NOT NULL,
    `line_number` INT NOT NULL,
    `canonical_sku_id` VARCHAR(128) NOT NULL,
    `requested_quantity` DECIMAL(24,6) NOT NULL,
    `uom_code` VARCHAR(16) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`line_id`),
    UNIQUE KEY `uk_purchase_requisition_line_tenant_id` (`tenant_id`, `line_id`),
    UNIQUE KEY `uk_purchase_requisition_line_number` (`tenant_id`, `requisition_id`, `line_number`),
    KEY `idx_purchase_requisition_line_sku` (`tenant_id`, `canonical_sku_id`),
    CONSTRAINT `fk_purchase_requisition_line_header`
        FOREIGN KEY (`tenant_id`, `requisition_id`)
        REFERENCES `cloudmold_purchase_requisition` (`tenant_id`, `requisition_id`),
    CONSTRAINT `ck_purchase_requisition_line_number` CHECK (`line_number` > 0),
    CONSTRAINT `ck_purchase_requisition_line_quantity` CHECK (`requested_quantity` > 0),
    CONSTRAINT `ck_purchase_requisition_line_uom`
        CHECK (`uom_code` REGEXP '^[A-Z][A-Z0-9_]{0,15}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical purchase requisition lines';

CREATE TABLE IF NOT EXISTS `cloudmold_purchase_requisition_delivery_schedule` (
    `schedule_id` VARCHAR(128) NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `requisition_id` VARCHAR(128) NOT NULL,
    `line_id` VARCHAR(128) NOT NULL,
    `schedule_number` INT NOT NULL,
    `canonical_warehouse_id` VARCHAR(128) NOT NULL,
    `required_delivery_date` DATE NOT NULL,
    `scheduled_quantity` DECIMAL(24,6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`schedule_id`),
    UNIQUE KEY `uk_purchase_requisition_schedule_tenant_id` (`tenant_id`, `schedule_id`),
    UNIQUE KEY `uk_purchase_requisition_schedule_number`
        (`tenant_id`, `line_id`, `schedule_number`),
    KEY `idx_purchase_requisition_schedule_header` (`tenant_id`, `requisition_id`),
    KEY `idx_purchase_requisition_schedule_warehouse_date`
        (`tenant_id`, `canonical_warehouse_id`, `required_delivery_date`),
    CONSTRAINT `fk_purchase_requisition_schedule_header`
        FOREIGN KEY (`tenant_id`, `requisition_id`)
        REFERENCES `cloudmold_purchase_requisition` (`tenant_id`, `requisition_id`),
    CONSTRAINT `fk_purchase_requisition_schedule_line`
        FOREIGN KEY (`tenant_id`, `line_id`)
        REFERENCES `cloudmold_purchase_requisition_line` (`tenant_id`, `line_id`),
    CONSTRAINT `ck_purchase_requisition_schedule_number` CHECK (`schedule_number` > 0),
    CONSTRAINT `ck_purchase_requisition_schedule_quantity` CHECK (`scheduled_quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical purchase requisition delivery schedules';

CREATE TABLE IF NOT EXISTS `cloudmold_purchase_requisition_status_history` (
    `history_id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL,
    `requisition_id` VARCHAR(128) NOT NULL,
    `operation_id` BIGINT NOT NULL,
    `aggregate_version` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `actor_principal_id` VARCHAR(128) NOT NULL,
    `reason_code` VARCHAR(64) NOT NULL,
    `occurred_at` DATETIME(6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`history_id`),
    UNIQUE KEY `uk_purchase_requisition_history_version`
        (`tenant_id`, `requisition_id`, `aggregate_version`),
    KEY `idx_purchase_requisition_history_operation` (`operation_id`),
    CONSTRAINT `fk_purchase_requisition_history_header`
        FOREIGN KEY (`tenant_id`, `requisition_id`)
        REFERENCES `cloudmold_purchase_requisition` (`tenant_id`, `requisition_id`),
    CONSTRAINT `fk_purchase_requisition_history_operation`
        FOREIGN KEY (`operation_id`) REFERENCES `cloudmold_procurement_operation` (`operation_id`),
    CONSTRAINT `ck_purchase_requisition_history_version` CHECK (`aggregate_version` > 0),
    CONSTRAINT `ck_purchase_requisition_history_status`
        CHECK (`status` IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only purchase requisition lifecycle history';
