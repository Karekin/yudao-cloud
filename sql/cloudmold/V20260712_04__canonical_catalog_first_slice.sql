-- CloudMold red-zone canonical apparel Catalog. Legacy Mall/ERP/WMS tables remain untouched.

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 processing, 10 succeeded',
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_catalog_operation_idempotency` (`tenant_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent canonical Catalog command';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_style` (
  `style_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `style_code` varchar(64) NOT NULL,
  `style_name` varchar(255) NOT NULL,
  `planning_category_ref` varchar(128) NOT NULL COMMENT 'Source-qualified until canonical Category exists',
  `brand_ref` varchar(128) NOT NULL COMMENT 'Source-qualified until canonical Brand exists',
  `planning_year` smallint NOT NULL,
  `season_code` varchar(16) NOT NULL,
  `wave_code` varchar(32) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 DRAFT, 10 ACTIVE, 20 INACTIVE, 90 ARCHIVED',
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`style_id`),
  UNIQUE KEY `uk_catalog_style_tenant_id` (`tenant_id`, `style_id`),
  UNIQUE KEY `uk_catalog_style_code` (`tenant_id`, `style_code`),
  CONSTRAINT `ck_catalog_style_year` CHECK (`planning_year` BETWEEN 2000 AND 2100),
  CONSTRAINT `ck_catalog_style_status` CHECK (`status` IN (0,10,20,90)),
  CONSTRAINT `ck_catalog_style_season` CHECK (`season_code` IN ('SPRING','SUMMER','AUTUMN','WINTER','ALL_SEASON'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apparel design/style authority';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_spu` (
  `spu_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `style_id` varchar(36) NOT NULL,
  `spu_code` varchar(64) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `sales_category_ref` varchar(128) NOT NULL COMMENT 'Source-qualified until canonical Category exists',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 DRAFT, 10 SUBMITTED, 20 APPROVED, 30 ACTIVE, 40 INACTIVE, 50 REJECTED, 90 ARCHIVED',
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`spu_id`),
  UNIQUE KEY `uk_catalog_spu_tenant_id` (`tenant_id`, `spu_id`),
  UNIQUE KEY `uk_catalog_spu_code` (`tenant_id`, `spu_code`),
  KEY `idx_catalog_spu_style` (`tenant_id`, `style_id`),
  CONSTRAINT `ck_catalog_spu_status` CHECK (`status` IN (0,10,20,30,40,50,90)),
  CONSTRAINT `fk_catalog_spu_style` FOREIGN KEY (`tenant_id`, `style_id`)
    REFERENCES `cloudmold_catalog_style` (`tenant_id`, `style_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical sales product unit';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_color` (
  `color_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `color_code` varchar(32) NOT NULL,
  `display_name` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`color_id`),
  UNIQUE KEY `uk_catalog_color_tenant_id` (`tenant_id`, `color_id`),
  UNIQUE KEY `uk_catalog_color_code` (`tenant_id`, `color_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tenant-standard color dictionary';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_size_group` (
  `size_group_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `size_group_code` varchar(32) NOT NULL,
  `size_group_name` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`size_group_id`),
  UNIQUE KEY `uk_catalog_size_group_tenant_id` (`tenant_id`, `size_group_id`),
  UNIQUE KEY `uk_catalog_size_group_code` (`tenant_id`, `size_group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apparel size system';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_size` (
  `size_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `size_group_id` varchar(36) NOT NULL,
  `size_code` varchar(32) NOT NULL,
  `size_name` varchar(64) NOT NULL,
  `sort_order` int NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`size_id`),
  UNIQUE KEY `uk_catalog_size_tenant_id` (`tenant_id`, `size_id`),
  UNIQUE KEY `uk_catalog_size_code` (`tenant_id`, `size_group_id`, `size_code`),
  CONSTRAINT `fk_catalog_size_group` FOREIGN KEY (`tenant_id`, `size_group_id`)
    REFERENCES `cloudmold_catalog_size_group` (`tenant_id`, `size_group_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Normalized size within a size system';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_sku` (
  `sku_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `spu_id` varchar(36) NOT NULL,
  `sku_code` varchar(64) NOT NULL,
  `color_id` varchar(36) NOT NULL,
  `size_id` varchar(36) NOT NULL,
  `variant_key` varchar(512) NOT NULL,
  `variant_key_hash` char(64) NOT NULL,
  `base_uom_code` varchar(16) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 DRAFT, 10 ACTIVE, 20 INACTIVE, 90 ARCHIVED',
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`sku_id`),
  UNIQUE KEY `uk_catalog_sku_tenant_id` (`tenant_id`, `sku_id`),
  UNIQUE KEY `uk_catalog_sku_code` (`tenant_id`, `sku_code`),
  UNIQUE KEY `uk_catalog_sku_variant` (`tenant_id`, `spu_id`, `variant_key_hash`),
  CONSTRAINT `ck_catalog_sku_status` CHECK (`status` IN (0,10,20,90)),
  CONSTRAINT `fk_catalog_sku_spu` FOREIGN KEY (`tenant_id`, `spu_id`)
    REFERENCES `cloudmold_catalog_spu` (`tenant_id`, `spu_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_catalog_sku_color` FOREIGN KEY (`tenant_id`, `color_id`)
    REFERENCES `cloudmold_catalog_color` (`tenant_id`, `color_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_catalog_sku_size` FOREIGN KEY (`tenant_id`, `size_id`)
    REFERENCES `cloudmold_catalog_size` (`tenant_id`, `size_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical inventory and sales SKU';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_barcode` (
  `barcode_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `sku_id` varchar(36) NOT NULL,
  `barcode` varchar(64) NOT NULL,
  `barcode_type` varchar(16) NOT NULL,
  `is_primary` bit(1) NOT NULL DEFAULT b'0',
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 10 COMMENT '10 ACTIVE, 20 INACTIVE, 90 RETIRED',
  `version` bigint NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`barcode_id`),
  UNIQUE KEY `uk_catalog_barcode_tenant_id` (`tenant_id`, `barcode_id`),
  UNIQUE KEY `uk_catalog_barcode_value` (`tenant_id`, `barcode`),
  KEY `idx_catalog_barcode_sku` (`tenant_id`, `sku_id`, `status`),
  CONSTRAINT `fk_catalog_barcode_sku` FOREIGN KEY (`tenant_id`, `sku_id`)
    REFERENCES `cloudmold_catalog_sku` (`tenant_id`, `sku_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One SKU may own multiple lifecycle-aware barcodes';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_legacy_projection` (
  `projection_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `target_system` varchar(16) NOT NULL COMMENT 'MALL, ERP or WMS',
  `target_entity` varchar(32) NOT NULL COMMENT 'SPU, SKU, PRODUCT, ITEM or ITEM_SKU',
  `canonical_type` varchar(16) NOT NULL,
  `canonical_id` varchar(36) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `payload` json NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 pending, 10 applied, 20 drifted, 30 failed',
  `last_error_summary` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`projection_id`),
  UNIQUE KEY `uk_catalog_legacy_projection` (`tenant_id`, `target_system`, `target_entity`, `canonical_type`, `canonical_id`),
  KEY `idx_catalog_projection_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Replayable anti-corruption projection; legacy tables are not authoritative';
