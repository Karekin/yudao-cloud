UPDATE `cloudmold_procurement_order`
SET `header_net_amount_minor` = COALESCE(`header_net_amount_minor`, `total_amount_minor`),
    `header_tax_amount_minor` = COALESCE(`header_tax_amount_minor`, 0),
    `header_gross_amount_minor` = COALESCE(`header_gross_amount_minor`, `total_amount_minor`),
    `tax_calculation_policy_code` = COALESCE(`tax_calculation_policy_code`, 'STANDARD_V1'),
    `rounding_policy_code` = COALESCE(`rounding_policy_code`, 'HALF_UP')
WHERE `header_net_amount_minor` IS NULL
   OR `header_tax_amount_minor` IS NULL
   OR `header_gross_amount_minor` IS NULL
   OR `tax_calculation_policy_code` IS NULL
   OR `rounding_policy_code` IS NULL;

UPDATE `cloudmold_procurement_order` o
JOIN `cloudmold_supplier_profile` s
  ON s.`tenant_id` = o.`tenant_id`
 AND s.`supplier_id` = o.`supplier_ref`
SET o.`supplier_id` = s.`supplier_id`
WHERE o.`supplier_id` IS NULL
  AND o.`supplier_ref` IS NOT NULL
  AND o.`supplier_ref` NOT LIKE 'ERP\\_SUPPLIER:%';

DROP TEMPORARY TABLE IF EXISTS `cloudmold_procurement_v137_reference_guard`;
CREATE TEMPORARY TABLE `cloudmold_procurement_v137_reference_guard` (
  `guard_value` varchar(2) NOT NULL,
  CONSTRAINT `ck_procurement_v137_reference_guard` CHECK (`guard_value` = 'OK')
);

INSERT INTO `cloudmold_procurement_v137_reference_guard` (`guard_value`)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE NULL END
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_supplier_profile` supplier
  ON supplier.`tenant_id` = o.`tenant_id`
 AND supplier.`supplier_id` = o.`supplier_id`
 AND supplier.`status` = 'ACTIVE'
 AND supplier.`admission_status` = 'ADMITTED'
LEFT JOIN `cloudmold_catalog_sku` sku
  ON sku.`tenant_id` = o.`tenant_id`
 AND BINARY sku.`sku_id` = BINARY o.`canonical_sku_id`
 AND sku.`status` = 10
LEFT JOIN `cloudmold_catalog_spu` spu
  ON spu.`tenant_id` = sku.`tenant_id`
 AND spu.`spu_id` = sku.`spu_id`
 AND spu.`status` = 30
LEFT JOIN `cloudmold_catalog_style` style
  ON style.`tenant_id` = spu.`tenant_id`
 AND style.`style_id` = spu.`style_id`
 AND style.`status` = 10
LEFT JOIN `cloudmold_catalog_color` color
  ON color.`tenant_id` = sku.`tenant_id`
 AND color.`color_id` = sku.`color_id`
 AND color.`status` = 10
LEFT JOIN `cloudmold_catalog_size` size_value
  ON size_value.`tenant_id` = sku.`tenant_id`
 AND size_value.`size_id` = sku.`size_id`
 AND size_value.`status` = 10
LEFT JOIN `cloudmold_catalog_size_group` size_group
  ON size_group.`tenant_id` = size_value.`tenant_id`
 AND size_group.`size_group_id` = size_value.`size_group_id`
 AND size_group.`status` = 10
LEFT JOIN `cloudmold_catalog_barcode` barcode
  ON barcode.`tenant_id` = sku.`tenant_id`
 AND barcode.`sku_id` = sku.`sku_id`
 AND barcode.`is_primary` = b'1'
 AND barcode.`status` = 10
 AND barcode.`valid_from` <= UTC_TIMESTAMP(6)
 AND (barcode.`valid_to` IS NULL OR barcode.`valid_to` > UTC_TIMESTAMP(6))
LEFT JOIN `cloudmold_warehouse` warehouse
  ON warehouse.`tenant_id` = o.`tenant_id`
 AND BINARY warehouse.`warehouse_id` = BINARY o.`canonical_warehouse_id`
 AND warehouse.`status` = 'ACTIVE'
WHERE supplier.`supplier_id` IS NULL
   OR sku.`sku_id` IS NULL
   OR spu.`spu_id` IS NULL
   OR style.`style_id` IS NULL
   OR color.`color_id` IS NULL
   OR size_value.`size_id` IS NULL
   OR size_group.`size_group_id` IS NULL
   OR barcode.`barcode_id` IS NULL
   OR warehouse.`warehouse_id` IS NULL;

DROP TEMPORARY TABLE `cloudmold_procurement_v137_reference_guard`;

CREATE TABLE IF NOT EXISTS `cloudmold_procurement_order_item` (
  `item_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(128) NOT NULL,
  `line_number` int NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `ordered_quantity` decimal(24, 6) NOT NULL,
  `uom_code` varchar(16) NOT NULL,
  `tax_code` varchar(64) NOT NULL,
  `tax_rate_bps` int NOT NULL,
  `unit_net_price_minor` decimal(24, 6) NOT NULL,
  `line_net_amount_minor` bigint NOT NULL,
  `line_tax_amount_minor` bigint NOT NULL,
  `line_gross_amount_minor` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_procurement_order_item_tenant_id` (`tenant_id`, `item_id`),
  UNIQUE KEY `uk_procurement_order_item_line` (`tenant_id`, `order_id`, `line_number`),
  KEY `idx_procurement_order_item_order` (`tenant_id`, `order_id`),
  CONSTRAINT `fk_procurement_order_item_order`
    FOREIGN KEY (`tenant_id`, `order_id`)
    REFERENCES `cloudmold_procurement_order` (`tenant_id`, `order_id`),
  CONSTRAINT `ck_procurement_order_item_line_number` CHECK (`line_number` > 0),
  CONSTRAINT `ck_procurement_order_item_quantity` CHECK (`ordered_quantity` > 0),
  CONSTRAINT `ck_procurement_order_item_tax_rate` CHECK (`tax_rate_bps` >= 0 AND `tax_rate_bps` <= 10000),
  CONSTRAINT `ck_procurement_order_item_unit_net_price` CHECK (`unit_net_price_minor` >= 0),
  CONSTRAINT `ck_procurement_order_item_line_net` CHECK (`line_net_amount_minor` >= 0),
  CONSTRAINT `ck_procurement_order_item_line_tax` CHECK (`line_tax_amount_minor` >= 0),
  CONSTRAINT `ck_procurement_order_item_line_gross` CHECK (`line_gross_amount_minor` >= 0),
  CONSTRAINT `ck_procurement_order_item_line_total`
    CHECK (`line_gross_amount_minor` = `line_net_amount_minor` + `line_tax_amount_minor`),
  CONSTRAINT `ck_procurement_order_item_uom` CHECK (`uom_code` REGEXP '^[A-Z][A-Z0-9_]{0,15}$'),
  CONSTRAINT `ck_procurement_order_item_tax_code` CHECK (`tax_code` REGEXP '^[A-Z][A-Z0-9_]{0,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical procurement purchase-order items';

INSERT INTO `cloudmold_procurement_order_item`
  (`item_id`,`tenant_id`,`order_id`,`line_number`,`canonical_sku_id`,`ordered_quantity`,`uom_code`,
   `tax_code`,`tax_rate_bps`,`unit_net_price_minor`,`line_net_amount_minor`,`line_tax_amount_minor`,
   `line_gross_amount_minor`,`created_at`,`updated_at`)
SELECT CONCAT('MIG:', SHA2(CONCAT(o.`tenant_id`, '|PO_ITEM|', o.`order_id`, '|10'), 256)),
       o.`tenant_id`,
       o.`order_id`,
       10,
       o.`canonical_sku_id`,
       o.`ordered_quantity`,
       o.`uom_code`,
       'UNSPECIFIED',
       0,
       CAST(o.`unit_cost_minor` AS DECIMAL(24, 6)),
       o.`total_amount_minor`,
       0,
       o.`total_amount_minor`,
       o.`created_at`,
       o.`updated_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_item` i
  ON i.`tenant_id` = o.`tenant_id`
 AND i.`order_id` = o.`order_id`
 AND i.`line_number` = 10
WHERE i.`item_id` IS NULL;

CREATE TABLE IF NOT EXISTS `cloudmold_procurement_order_delivery_schedule` (
  `schedule_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(128) NOT NULL,
  `item_id` varchar(128) NOT NULL,
  `schedule_number` int NOT NULL,
  `required_delivery_date` date NOT NULL,
  `canonical_warehouse_id` varchar(128) NOT NULL,
  `scheduled_quantity` decimal(24, 6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`schedule_id`),
  UNIQUE KEY `uk_procurement_schedule_tenant_id` (`tenant_id`, `schedule_id`),
  UNIQUE KEY `uk_procurement_schedule_line` (`tenant_id`, `item_id`, `schedule_number`),
  KEY `idx_procurement_schedule_order` (`tenant_id`, `order_id`, `item_id`),
  CONSTRAINT `fk_procurement_schedule_order`
    FOREIGN KEY (`tenant_id`, `order_id`)
    REFERENCES `cloudmold_procurement_order` (`tenant_id`, `order_id`),
  CONSTRAINT `fk_procurement_schedule_item`
    FOREIGN KEY (`tenant_id`, `item_id`)
    REFERENCES `cloudmold_procurement_order_item` (`tenant_id`, `item_id`),
  CONSTRAINT `ck_procurement_schedule_number` CHECK (`schedule_number` > 0),
  CONSTRAINT `ck_procurement_schedule_quantity` CHECK (`scheduled_quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical procurement purchase-order delivery schedules';

INSERT INTO `cloudmold_procurement_order_delivery_schedule`
  (`schedule_id`,`tenant_id`,`order_id`,`item_id`,`schedule_number`,`required_delivery_date`,
   `canonical_warehouse_id`,`scheduled_quantity`,`created_at`,`updated_at`)
SELECT CONCAT('MIG:', SHA2(CONCAT(o.`tenant_id`, '|PO_SCHEDULE|', o.`order_id`, '|10|1'), 256)),
       o.`tenant_id`,
       o.`order_id`,
       CONCAT('MIG:', SHA2(CONCAT(o.`tenant_id`, '|PO_ITEM|', o.`order_id`, '|10'), 256)),
       1,
       o.`required_delivery_date`,
       o.`canonical_warehouse_id`,
       o.`ordered_quantity`,
       o.`created_at`,
       o.`updated_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_delivery_schedule` s
  ON s.`tenant_id` = o.`tenant_id`
 AND s.`order_id` = o.`order_id`
 AND s.`item_id` = CONCAT('MIG:', SHA2(CONCAT(o.`tenant_id`, '|PO_ITEM|', o.`order_id`, '|10'), 256))
 AND s.`schedule_number` = 1
WHERE s.`schedule_id` IS NULL;

CREATE TABLE IF NOT EXISTS `cloudmold_procurement_order_status_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` varchar(128) NOT NULL,
  `operation_id` bigint NULL,
  `aggregate_version` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `actor_principal_id` varchar(128) NULL,
  `reason_code` varchar(64) NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_procurement_status_history_version` (`tenant_id`, `order_id`, `aggregate_version`),
  KEY `idx_procurement_status_history_order` (`tenant_id`, `order_id`, `occurred_at`),
  CONSTRAINT `fk_procurement_status_history_order`
    FOREIGN KEY (`tenant_id`, `order_id`)
    REFERENCES `cloudmold_procurement_order` (`tenant_id`, `order_id`),
  CONSTRAINT `ck_procurement_status_history_version` CHECK (`aggregate_version` > 0),
  CONSTRAINT `ck_procurement_status_history_status` CHECK (`status` IN (
    'CREATED','DISPATCHED','SUPPLIER_CONFIRMED','CANCELLED','CLOSED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only procurement purchase-order status transitions';

INSERT INTO `cloudmold_procurement_order_status_history`
  (`tenant_id`,`order_id`,`operation_id`,`aggregate_version`,`status`,`actor_principal_id`,`reason_code`,`occurred_at`,`created_at`)
SELECT o.`tenant_id`,
       o.`order_id`,
       NULL,
       1,
       'CREATED',
       o.`created_by_principal_id`,
       CASE WHEN o.`status` = 'CREATED' THEN o.`reason_code` ELSE NULL END,
       o.`created_at`,
       o.`created_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_status_history` h
  ON h.`tenant_id` = o.`tenant_id`
 AND h.`order_id` = o.`order_id`
 AND h.`aggregate_version` = 1
WHERE h.`history_id` IS NULL;

INSERT INTO `cloudmold_procurement_order_status_history`
  (`tenant_id`,`order_id`,`operation_id`,`aggregate_version`,`status`,`actor_principal_id`,`reason_code`,`occurred_at`,`created_at`)
SELECT o.`tenant_id`,
       o.`order_id`,
       NULL,
       2,
       'DISPATCHED',
       o.`dispatched_by_principal_id`,
       CASE WHEN o.`status` = 'DISPATCHED' THEN o.`reason_code` ELSE NULL END,
       o.`dispatched_at`,
       o.`dispatched_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_status_history` h
  ON h.`tenant_id` = o.`tenant_id`
 AND h.`order_id` = o.`order_id`
 AND h.`aggregate_version` = 2
WHERE o.`dispatched_at` IS NOT NULL
  AND h.`history_id` IS NULL;

INSERT INTO `cloudmold_procurement_order_status_history`
  (`tenant_id`,`order_id`,`operation_id`,`aggregate_version`,`status`,`actor_principal_id`,`reason_code`,`occurred_at`,`created_at`)
SELECT o.`tenant_id`,
       o.`order_id`,
       NULL,
       3,
       'SUPPLIER_CONFIRMED',
       o.`supplier_confirmed_by_principal_id`,
       CASE WHEN o.`status` = 'SUPPLIER_CONFIRMED' THEN o.`reason_code` ELSE NULL END,
       o.`supplier_confirmed_at`,
       o.`supplier_confirmed_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_status_history` h
  ON h.`tenant_id` = o.`tenant_id`
 AND h.`order_id` = o.`order_id`
 AND h.`aggregate_version` = 3
WHERE o.`supplier_confirmed_at` IS NOT NULL
  AND h.`history_id` IS NULL;

INSERT INTO `cloudmold_procurement_order_status_history`
  (`tenant_id`,`order_id`,`operation_id`,`aggregate_version`,`status`,`actor_principal_id`,`reason_code`,`occurred_at`,`created_at`)
SELECT o.`tenant_id`,
       o.`order_id`,
       NULL,
       o.`version`,
       'CANCELLED',
       o.`cancelled_by_principal_id`,
       o.`reason_code`,
       o.`cancelled_at`,
       o.`cancelled_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_status_history` h
  ON h.`tenant_id` = o.`tenant_id`
 AND h.`order_id` = o.`order_id`
 AND h.`aggregate_version` = o.`version`
WHERE o.`status` = 'CANCELLED'
  AND h.`history_id` IS NULL;

INSERT INTO `cloudmold_procurement_order_status_history`
  (`tenant_id`,`order_id`,`operation_id`,`aggregate_version`,`status`,`actor_principal_id`,`reason_code`,`occurred_at`,`created_at`)
SELECT o.`tenant_id`,
       o.`order_id`,
       NULL,
       o.`version`,
       'CLOSED',
       o.`closed_by_principal_id`,
       o.`reason_code`,
       o.`closed_at`,
       o.`closed_at`
FROM `cloudmold_procurement_order` o
LEFT JOIN `cloudmold_procurement_order_status_history` h
  ON h.`tenant_id` = o.`tenant_id`
 AND h.`order_id` = o.`order_id`
 AND h.`aggregate_version` = o.`version`
WHERE o.`status` = 'CLOSED'
  AND h.`history_id` IS NULL;
