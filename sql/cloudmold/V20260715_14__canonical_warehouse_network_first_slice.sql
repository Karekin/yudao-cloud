-- CloudMold canonical Warehouse Network master-data first slice.
-- Additive red-zone migration; no ERP/WMS/MES or Inventory tables are mutated.

CREATE TABLE IF NOT EXISTS `cloudmold_warehouse` (
  `warehouse_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `warehouse_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `warehouse_type` varchar(32) NOT NULL,
  `timezone` varchar(64) NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`warehouse_id`),
  UNIQUE KEY `uk_cm_warehouse_tenant_id` (`tenant_id`,`warehouse_id`),
  UNIQUE KEY `uk_cm_warehouse_code` (`tenant_id`,`warehouse_code`),
  CONSTRAINT `ck_cm_warehouse_status` CHECK (`status` IN ('DRAFT','ACTIVE','INACTIVE')),
  CONSTRAINT `ck_cm_warehouse_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_warehouse_zone` (
  `zone_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `zone_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `zone_type` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`zone_id`),
  UNIQUE KEY `uk_cm_warehouse_zone_tenant_id` (`tenant_id`,`zone_id`),
  UNIQUE KEY `uk_cm_warehouse_zone_code` (`tenant_id`,`warehouse_id`,`zone_code`),
  CONSTRAINT `fk_cm_warehouse_zone_parent` FOREIGN KEY (`tenant_id`,`warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `ck_cm_warehouse_zone_status` CHECK (`status` IN ('DRAFT','ACTIVE','INACTIVE')),
  CONSTRAINT `ck_cm_warehouse_zone_version` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_warehouse_location` (
  `location_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `zone_id` varchar(36) NOT NULL,
  `location_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `location_type` varchar(32) NOT NULL,
  `aisle_code` varchar(32) DEFAULT NULL,
  `rack_code` varchar(32) DEFAULT NULL,
  `bay_code` varchar(32) DEFAULT NULL,
  `level_code` varchar(32) DEFAULT NULL,
  `allow_item_mixing` tinyint(1) NOT NULL,
  `allow_lot_mixing` tinyint(1) NOT NULL,
  `capacity_quantity` decimal(24,6) DEFAULT NULL,
  `capacity_uom_code` varchar(32) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`location_id`),
  UNIQUE KEY `uk_cm_warehouse_location_tenant_id` (`tenant_id`,`location_id`),
  UNIQUE KEY `uk_cm_warehouse_location_code` (`tenant_id`,`warehouse_id`,`location_code`),
  KEY `idx_cm_warehouse_location_zone` (`tenant_id`,`zone_id`,`status`),
  CONSTRAINT `fk_cm_warehouse_location_parent` FOREIGN KEY (`tenant_id`,`warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_warehouse_location_zone` FOREIGN KEY (`tenant_id`,`zone_id`)
    REFERENCES `cloudmold_warehouse_zone` (`tenant_id`,`zone_id`),
  CONSTRAINT `ck_cm_warehouse_location_status` CHECK (`status` IN ('DRAFT','ACTIVE','INACTIVE')),
  CONSTRAINT `ck_cm_warehouse_location_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_warehouse_location_capacity` CHECK (`capacity_quantity` IS NULL OR `capacity_quantity` >= 0),
  CONSTRAINT `ck_cm_warehouse_location_capacity_uom` CHECK (`capacity_quantity` IS NULL OR `capacity_uom_code` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_warehouse_source_mapping` (
  `mapping_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL,
  `canonical_type` varchar(32) NOT NULL,
  `canonical_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `zone_id` varchar(36) DEFAULT NULL,
  `location_id` varchar(36) DEFAULT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `verification_ref` varchar(256) NOT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_cm_warehouse_mapping_tenant_id` (`tenant_id`,`mapping_id`),
  UNIQUE KEY `uk_cm_warehouse_mapping_effective` (`tenant_id`,`source_system`,`source_type`,`source_id`,`valid_from`),
  UNIQUE KEY `uk_cm_warehouse_mapping_active` (`tenant_id`,`source_system`,`source_type`,`source_id`,`active_guard`),
  KEY `idx_cm_warehouse_mapping_canonical` (`tenant_id`,`canonical_type`,`canonical_id`,`status`),
  CONSTRAINT `fk_cm_warehouse_mapping_warehouse` FOREIGN KEY (`tenant_id`,`warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_warehouse_mapping_zone` FOREIGN KEY (`tenant_id`,`zone_id`)
    REFERENCES `cloudmold_warehouse_zone` (`tenant_id`,`zone_id`),
  CONSTRAINT `fk_cm_warehouse_mapping_location` FOREIGN KEY (`tenant_id`,`location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `ck_cm_warehouse_mapping_type` CHECK (`canonical_type` IN ('WAREHOUSE','ZONE','LOCATION')),
  CONSTRAINT `ck_cm_warehouse_mapping_status` CHECK (`status` IN ('ACTIVE','ENDED')),
  CONSTRAINT `ck_cm_warehouse_mapping_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_warehouse_mapping_validity` CHECK (`valid_to` IS NULL OR `valid_to` > `valid_from`),
  CONSTRAINT `ck_cm_warehouse_mapping_shape` CHECK (
    (`canonical_type`='WAREHOUSE' AND `canonical_id`=`warehouse_id` AND `zone_id` IS NULL AND `location_id` IS NULL)
    OR (`canonical_type`='ZONE' AND `canonical_id`=`zone_id` AND `zone_id` IS NOT NULL AND `location_id` IS NULL)
    OR (`canonical_type`='LOCATION' AND `canonical_id`=`location_id` AND `zone_id` IS NOT NULL AND `location_id` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_warehouse_operator_assignment` (
  `assignment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `zone_id` varchar(36) DEFAULT NULL,
  `location_id` varchar(36) DEFAULT NULL,
  `principal_id` varchar(36) NOT NULL,
  `role_code` varchar(64) NOT NULL,
  `shift_code` varchar(64) DEFAULT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `active_guard` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
  `version` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`assignment_id`),
  UNIQUE KEY `uk_cm_warehouse_assignment_tenant_id` (`tenant_id`,`assignment_id`),
  UNIQUE KEY `uk_cm_warehouse_assignment_effective` (`tenant_id`,`warehouse_id`,`principal_id`,`role_code`,`valid_from`),
  UNIQUE KEY `uk_cm_warehouse_assignment_active` (`tenant_id`,`warehouse_id`,`principal_id`,`role_code`,`active_guard`),
  CONSTRAINT `fk_cm_warehouse_assignment_warehouse` FOREIGN KEY (`tenant_id`,`warehouse_id`)
    REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_warehouse_assignment_zone` FOREIGN KEY (`tenant_id`,`zone_id`)
    REFERENCES `cloudmold_warehouse_zone` (`tenant_id`,`zone_id`),
  CONSTRAINT `fk_cm_warehouse_assignment_location` FOREIGN KEY (`tenant_id`,`location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `fk_cm_warehouse_assignment_principal` FOREIGN KEY (`tenant_id`,`principal_id`)
    REFERENCES `cloudmold_identity_principal` (`tenant_id`,`principal_id`),
  CONSTRAINT `ck_cm_warehouse_assignment_status` CHECK (`status` IN ('ACTIVE','ENDED')),
  CONSTRAINT `ck_cm_warehouse_assignment_version` CHECK (`version` >= 1),
  CONSTRAINT `ck_cm_warehouse_assignment_validity` CHECK (`valid_to` IS NULL OR `valid_to` > `valid_from`),
  CONSTRAINT `ck_cm_warehouse_assignment_shape` CHECK (`location_id` IS NULL OR `zone_id` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_warehouse_operation` (
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
  UNIQUE KEY `uk_cm_warehouse_operation` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_warehouse_source_event` (`tenant_id`,`source_event_id`),
  CONSTRAINT `ck_cm_warehouse_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
