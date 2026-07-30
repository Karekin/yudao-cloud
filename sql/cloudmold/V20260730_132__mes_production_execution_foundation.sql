-- Complete the additive MES schema required by the governed production
-- execution lifecycle. V64 intentionally stopped at work orders and tasks;
-- this migration adds the master data, routing, feedback, WIP receipt, and
-- inventory-ledger tables needed to prove a real production close loop.

CREATE TABLE IF NOT EXISTS `mes_md_workshop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `area` decimal(12,2) DEFAULT NULL,
  `charge_user_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_md_workshop_code` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 车间';

CREATE TABLE IF NOT EXISTS `mes_pro_process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `attention` varchar(1000) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_pro_process_code` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工序';

CREATE TABLE IF NOT EXISTS `mes_md_workstation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `workshop_id` bigint NOT NULL,
  `process_id` bigint NOT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `area_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_md_workstation_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_mes_md_workstation_workshop` (`tenant_id`,`workshop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工作站';

CREATE TABLE IF NOT EXISTS `mes_pro_route` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_pro_route_code` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线';

CREATE TABLE IF NOT EXISTS `mes_pro_route_process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_id` bigint NOT NULL,
  `process_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT 1,
  `next_process_id` bigint DEFAULT NULL,
  `link_type` tinyint NOT NULL DEFAULT 0,
  `prepare_time` int NOT NULL DEFAULT 0,
  `wait_time` int NOT NULL DEFAULT 0,
  `color_code` char(7) DEFAULT '#00AEF3',
  `key_flag` bit(1) NOT NULL DEFAULT b'0',
  `check_flag` bit(1) NOT NULL DEFAULT b'0',
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_route_process` (`tenant_id`,`route_id`,`process_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线工序';

CREATE TABLE IF NOT EXISTS `mes_pro_route_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `quantity` int DEFAULT NULL,
  `production_time` decimal(12,2) DEFAULT NULL,
  `time_unit_type` varchar(32) DEFAULT NULL,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_route_product` (`tenant_id`,`item_id`,`deleted`),
  KEY `idx_mes_route_product_route` (`tenant_id`,`route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线产品';

CREATE TABLE IF NOT EXISTS `mes_pro_route_product_bom` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_id` bigint NOT NULL,
  `process_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `quantity` decimal(14,4) NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_route_product_bom` (`tenant_id`,`route_id`,`process_id`,`product_id`,`item_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线产品 BOM';

CREATE TABLE IF NOT EXISTS `mes_pro_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `type` tinyint NOT NULL,
  `channel` varchar(16) DEFAULT 'PC',
  `feedback_time` datetime NOT NULL,
  `workstation_id` bigint NOT NULL,
  `route_id` bigint NOT NULL,
  `process_id` bigint NOT NULL,
  `work_order_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `expire_date` datetime DEFAULT NULL,
  `lot_number` varchar(64) DEFAULT NULL,
  `scheduled_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `feedback_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `qualified_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `unqualified_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `uncheck_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `labor_scrap_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `material_scrap_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `other_scrap_quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `feedback_user_id` bigint NOT NULL,
  `approve_user_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_pro_feedback_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_mes_pro_feedback_task` (`tenant_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产报工';

CREATE TABLE IF NOT EXISTS `mes_wm_warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `area` decimal(12,2) DEFAULT NULL,
  `charge_user_id` bigint DEFAULT NULL,
  `frozen` bit(1) NOT NULL DEFAULT b'0',
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_wm_warehouse_code` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 仓库';

CREATE TABLE IF NOT EXISTS `mes_wm_warehouse_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `area` decimal(12,2) DEFAULT NULL,
  `frozen` bit(1) NOT NULL DEFAULT b'0',
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_wm_location_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_mes_wm_location_warehouse` (`tenant_id`,`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库区';

CREATE TABLE IF NOT EXISTS `mes_wm_warehouse_area` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `location_id` bigint NOT NULL,
  `area` decimal(12,2) DEFAULT NULL,
  `max_load` decimal(14,2) DEFAULT NULL,
  `position_x` int DEFAULT NULL,
  `position_y` int DEFAULT NULL,
  `position_z` int DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `frozen` bit(1) NOT NULL DEFAULT b'0',
  `allow_item_mixing` bit(1) NOT NULL DEFAULT b'1',
  `allow_batch_mixing` bit(1) NOT NULL DEFAULT b'1',
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_wm_area_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_mes_wm_area_location` (`tenant_id`,`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库位';

CREATE TABLE IF NOT EXISTS `mes_wm_product_produce` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_order_id` bigint NOT NULL,
  `feedback_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `workstation_id` bigint NOT NULL,
  `process_id` bigint NOT NULL,
  `produce_date` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_product_produce_feedback` (`tenant_id`,`feedback_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产入库单';

CREATE TABLE IF NOT EXISTS `mes_wm_product_produce_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `produce_id` bigint NOT NULL,
  `feedback_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `batch_id` bigint DEFAULT NULL,
  `batch_code` varchar(64) DEFAULT NULL,
  `expire_date` datetime DEFAULT NULL,
  `lot_number` varchar(64) DEFAULT NULL,
  `quality_status` tinyint NOT NULL,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_mes_product_produce_line` (`tenant_id`,`produce_id`),
  KEY `idx_mes_product_produce_feedback` (`tenant_id`,`feedback_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产入库单行';

CREATE TABLE IF NOT EXISTS `mes_wm_product_produce_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `produce_id` bigint NOT NULL,
  `line_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `batch_id` bigint DEFAULT NULL,
  `batch_code` varchar(64) DEFAULT NULL,
  `warehouse_id` bigint NOT NULL,
  `location_id` bigint NOT NULL,
  `area_id` bigint NOT NULL,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_mes_product_produce_detail` (`tenant_id`,`produce_id`,`line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产入库明细';

CREATE TABLE IF NOT EXISTS `mes_wm_material_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_type_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `batch_code` varchar(64) DEFAULT NULL,
  `warehouse_id` bigint NOT NULL,
  `location_id` bigint NOT NULL,
  `area_id` bigint NOT NULL,
  `vendor_id` bigint DEFAULT NULL,
  `quantity` decimal(14,2) NOT NULL DEFAULT 0,
  `receipt_time` datetime NOT NULL,
  `frozen` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_mes_material_stock_position` (`tenant_id`,`warehouse_id`,`location_id`,`area_id`),
  KEY `idx_mes_material_stock_item` (`tenant_id`,`item_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存台账';

CREATE TABLE IF NOT EXISTS `mes_wm_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` tinyint NOT NULL,
  `biz_type` int NOT NULL,
  `biz_id` bigint NOT NULL,
  `biz_code` varchar(64) DEFAULT '',
  `biz_line_id` bigint DEFAULT NULL,
  `material_stock_id` bigint NOT NULL,
  `related_transaction_id` bigint DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `quantity` decimal(14,2) NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `batch_code` varchar(64) DEFAULT NULL,
  `warehouse_id` bigint NOT NULL,
  `location_id` bigint NOT NULL,
  `area_id` bigint NOT NULL,
  `transaction_time` datetime NOT NULL,
  `erp_time` datetime DEFAULT NULL,
  `receipt_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_mes_transaction_business` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_mes_transaction_stock` (`tenant_id`,`material_stock_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存事务流水';

CREATE TABLE IF NOT EXISTS `mes_md_auto_code_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `max_length` int DEFAULT NULL,
  `padded` bit(1) NOT NULL DEFAULT b'0',
  `padded_char` varchar(1) DEFAULT '0',
  `padded_method` tinyint DEFAULT 1,
  `status` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_auto_code_rule` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 编码规则';

CREATE TABLE IF NOT EXISTS `mes_md_auto_code_part` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT 1,
  `type` tinyint NOT NULL,
  `length` int NOT NULL,
  `date_format` varchar(32) DEFAULT NULL,
  `fix_character` varchar(64) DEFAULT NULL,
  `serial_start_no` int DEFAULT NULL,
  `serial_step` int DEFAULT NULL,
  `cycle_flag` bit(1) NOT NULL DEFAULT b'0',
  `cycle_method` tinyint DEFAULT NULL,
  `remark` varchar(500) DEFAULT '',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_auto_code_part` (`tenant_id`,`rule_id`,`sort`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 编码规则组成';

CREATE TABLE IF NOT EXISTS `mes_md_auto_code_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_id` bigint NOT NULL,
  `result` varchar(128) NOT NULL,
  `serial_no` bigint DEFAULT NULL,
  `input_char` varchar(128) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mes_auto_code_result` (`tenant_id`,`result`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 编码生成记录';

-- A virtual WIP position is a runtime prerequisite, not demo inventory. Seed
-- one unfrozen hierarchy for each effective tenant so production output can be
-- posted through the normal inventory transaction service.
INSERT INTO `mes_wm_warehouse`
    (`code`,`name`,`address`,`area`,`charge_user_id`,`frozen`,`remark`,
     `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'WIP_VIRTUAL_WAREHOUSE','在制品虚拟线边库','SYSTEM/WIP',0,NULL,b'0',
       'MES 生产报工的系统线边库','CloudMold:mes-production-foundation',
       UTC_TIMESTAMP(), 'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),b'0',t.id
FROM `system_tenant` t
WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `mes_wm_warehouse` w
    WHERE w.tenant_id=t.id AND w.code='WIP_VIRTUAL_WAREHOUSE' AND w.deleted=b'0'
  );

INSERT INTO `mes_wm_warehouse_location`
    (`code`,`name`,`warehouse_id`,`area`,`frozen`,`remark`,
     `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'WIP_VIRTUAL_LOCATION','在制品虚拟库区',w.id,0,b'0',
       'MES 生产报工的系统线边库区','CloudMold:mes-production-foundation',
       UTC_TIMESTAMP(), 'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),b'0',w.tenant_id
FROM `mes_wm_warehouse` w
WHERE w.code='WIP_VIRTUAL_WAREHOUSE' AND w.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `mes_wm_warehouse_location` l
    WHERE l.tenant_id=w.tenant_id AND l.code='WIP_VIRTUAL_LOCATION' AND l.deleted=b'0'
  );

INSERT INTO `mes_wm_warehouse_area`
    (`code`,`name`,`location_id`,`area`,`max_load`,`position_x`,`position_y`,`position_z`,
     `status`,`frozen`,`allow_item_mixing`,`allow_batch_mixing`,`remark`,
     `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'WIP_VIRTUAL_AREA','在制品虚拟库位',l.id,0,NULL,0,0,0,
       0,b'0',b'1',b'1','MES 生产报工的系统线边库位',
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),b'0',l.tenant_id
FROM `mes_wm_warehouse_location` l
WHERE l.code='WIP_VIRTUAL_LOCATION' AND l.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `mes_wm_warehouse_area` a
    WHERE a.tenant_id=l.tenant_id AND a.code='WIP_VIRTUAL_AREA' AND a.deleted=b'0'
  );

-- Task codes are generated by the MES domain service. Seed the minimum
-- tenant-local rule rather than bypassing the domain with caller-built codes.
INSERT INTO `mes_md_auto_code_rule`
    (`code`,`name`,`description`,`max_length`,`padded`,`padded_char`,`padded_method`,
     `status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'PRO_TASK_CODE','生产任务编码','PT + 8 位租户内流水号',10,b'0','0',1,
       0,'CloudMold 生产主管闭环所需系统规则',
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),b'0',t.id
FROM `system_tenant` t
WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `mes_md_auto_code_rule` r
    WHERE r.tenant_id=t.id AND r.code='PRO_TASK_CODE' AND r.deleted=b'0'
  );

INSERT INTO `mes_md_auto_code_part`
    (`rule_id`,`sort`,`type`,`length`,`fix_character`,`cycle_flag`,
     `remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,1,3,2,'PT',b'0','生产任务固定前缀',
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),b'0',r.tenant_id
FROM `mes_md_auto_code_rule` r
WHERE r.code='PRO_TASK_CODE' AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `mes_md_auto_code_part` p
    WHERE p.tenant_id=r.tenant_id AND p.rule_id=r.id AND p.sort=1 AND p.deleted=b'0'
  );

INSERT INTO `mes_md_auto_code_part`
    (`rule_id`,`sort`,`type`,`length`,`serial_start_no`,`serial_step`,`cycle_flag`,
     `remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,2,4,8,1,1,b'0','生产任务租户内流水号',
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),
       'CloudMold:mes-production-foundation',UTC_TIMESTAMP(),b'0',r.tenant_id
FROM `mes_md_auto_code_rule` r
WHERE r.code='PRO_TASK_CODE' AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `mes_md_auto_code_part` p
    WHERE p.tenant_id=r.tenant_id AND p.rule_id=r.id AND p.sort=2 AND p.deleted=b'0'
  );
