-- CloudMold compatibility migration derived from the user-supplied Yudao module SQL snapshots.
-- Source snapshots:
--   member-2026-05-30
--   mall-2026-04-18
--   pay-2026-04-18
--   wms-2026-05-15
-- Upstream module tables remain compatibility projections. Canonical Catalog/Inventory/Order authority is unchanged.
-- Do not replace this migration with the destructive DROP/CREATE statements from the source snapshots.

ALTER TABLE `member_user`
  ADD COLUMN `email` varchar(50) DEFAULT NULL COMMENT '邮箱' AFTER `mobile`;

ALTER TABLE `pay_order`
  ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '用户编号' AFTER `channel_code`,
  ADD COLUMN `user_type` tinyint DEFAULT NULL COMMENT '用户类型' AFTER `user_id`,
  MODIFY COLUMN `success_time` datetime DEFAULT NULL COMMENT '支付成功时间';

ALTER TABLE `pay_refund`
  ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '用户编号' AFTER `order_no`,
  ADD COLUMN `user_type` tinyint DEFAULT NULL COMMENT '用户类型' AFTER `user_id`;

ALTER TABLE `pay_transfer`
  ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '用户编号' AFTER `channel_code`,
  ADD COLUMN `user_type` tinyint DEFAULT NULL COMMENT '用户类型' AFTER `user_id`;

ALTER TABLE `product_comment`
  MODIFY COLUMN `visible` bit(1) DEFAULT b'0' COMMENT '是否可见';

ALTER TABLE `product_sku`
  MODIFY COLUMN `updater` varchar(255) DEFAULT NULL COMMENT '更新者';

-- Restore reference indexes for already-enabled Member, Pay, Mall, Promotion, and Trade tables.
ALTER TABLE `member_sign_in_record` ADD KEY `idx_user_id_create_time` (`user_id`,`create_time`);
ALTER TABLE `member_user` ADD UNIQUE KEY `idx_mobile` (`mobile`,`tenant_id`);
ALTER TABLE `pay_app` ADD KEY `idx_app_key` (`app_key`);
ALTER TABLE `pay_channel` ADD KEY `idx_app_id_code` (`app_id`,`code`);
ALTER TABLE `pay_notify_log` ADD KEY `idx_task_id` (`task_id`);
ALTER TABLE `pay_notify_task` ADD KEY `idx_app_id_status_create_time` (`app_id`,`status`,`create_time`);
ALTER TABLE `pay_notify_task` ADD KEY `idx_status_next_notify_time` (`status`,`next_notify_time`);
ALTER TABLE `pay_order` ADD KEY `idx_app_id_merchant_order_id` (`app_id`,`merchant_order_id`);
ALTER TABLE `pay_order` ADD KEY `idx_no` (`no`);
ALTER TABLE `pay_order` ADD KEY `idx_status_expire_time` (`status`,`expire_time`);
ALTER TABLE `pay_order_extension` ADD KEY `idx_no` (`no`);
ALTER TABLE `pay_order_extension` ADD KEY `idx_order_id_status` (`order_id`,`status`);
ALTER TABLE `pay_order_extension` ADD KEY `idx_status_create_time` (`status`,`create_time`);
ALTER TABLE `pay_refund` ADD KEY `idx_app_id_merchant_refund_id` (`app_id`,`merchant_refund_id`);
ALTER TABLE `pay_refund` ADD KEY `idx_app_id_order_id_status` (`app_id`,`order_id`,`status`);
ALTER TABLE `pay_refund` ADD KEY `idx_no` (`no`);
ALTER TABLE `pay_refund` ADD KEY `idx_status` (`status`);
ALTER TABLE `pay_transfer` ADD KEY `idx_app_id_merchant_transfer_id` (`app_id`,`merchant_transfer_id`);
ALTER TABLE `pay_transfer` ADD KEY `idx_no` (`no`);
ALTER TABLE `pay_transfer` ADD KEY `idx_status` (`status`);
ALTER TABLE `pay_wallet` ADD KEY `idx_user_id_user_type` (`user_id`,`user_type`);
ALTER TABLE `pay_wallet_recharge` ADD KEY `idx_wallet_id_pay_status_id` (`wallet_id`,`pay_status`,`id`);
ALTER TABLE `pay_wallet_transaction` ADD KEY `idx_biz_id_biz_type` (`biz_id`,`biz_type`);
ALTER TABLE `pay_wallet_transaction` ADD KEY `idx_no` (`no`);
ALTER TABLE `pay_wallet_transaction` ADD KEY `idx_wallet_id_create_time` (`wallet_id`,`create_time`);
ALTER TABLE `pay_wallet_transaction` ADD KEY `idx_wallet_id_id` (`wallet_id`,`id`);
ALTER TABLE `product_comment` ADD KEY `idx_spu_id` (`spu_id`);
ALTER TABLE `product_property_value` ADD KEY `idx_property_id` (`property_id`);
ALTER TABLE `product_sku` ADD KEY `idx_spu_id` (`spu_id`);
ALTER TABLE `product_spu` ADD KEY `idx_category_id` (`category_id`);
ALTER TABLE `promotion_article` ADD KEY `idx_category_id` (`category_id`);
ALTER TABLE `promotion_bargain_activity` ADD KEY `idx_spu_id` (`spu_id`);
ALTER TABLE `promotion_bargain_help` ADD KEY `idx_record_id_user_id` (`record_id`,`user_id`);
ALTER TABLE `promotion_bargain_record` ADD KEY `idx_user_id_activity_id` (`user_id`,`activity_id`);
ALTER TABLE `promotion_combination_activity` ADD KEY `idx_spu_id` (`spu_id`);
ALTER TABLE `promotion_combination_product` ADD KEY `idx_activity_id` (`activity_id`);
ALTER TABLE `promotion_combination_record` ADD KEY `idx_head_id` (`head_id`);
ALTER TABLE `promotion_combination_record` ADD KEY `idx_user_id` (`user_id`);
ALTER TABLE `promotion_coupon` ADD KEY `idx_user_id` (`user_id`);
ALTER TABLE `promotion_coupon_template` ADD KEY `idx_take_type_status` (`take_type`,`status`);
ALTER TABLE `promotion_discount_product` ADD KEY `idx_sku_id` (`sku_id`);
ALTER TABLE `promotion_kefu_conversation` ADD KEY `idx_user_id` (`user_id`);
ALTER TABLE `promotion_kefu_message` ADD KEY `idx_conversation_id_create_time` (`conversation_id`,`create_time`);
ALTER TABLE `promotion_kefu_message` ADD KEY `idx_conversation_id_read_status` (`conversation_id`,`read_status`);
ALTER TABLE `promotion_point_product` ADD KEY `idx_activity_id` (`activity_id`);
ALTER TABLE `promotion_seckill_activity` ADD KEY `idx_spu_id` (`spu_id`);
ALTER TABLE `promotion_seckill_product` ADD KEY `idx_activity_id` (`activity_id`);
ALTER TABLE `trade_after_sale` ADD KEY `idx_user_id` (`user_id`);
ALTER TABLE `trade_after_sale_log` ADD KEY `idx_after_sale_id` (`after_sale_id`);
ALTER TABLE `trade_brokerage_user` ADD KEY `idx_bind_user_id` (`bind_user_id`);
ALTER TABLE `trade_cart` ADD KEY `idx_user_id_sku_id` (`user_id`,`sku_id`);
ALTER TABLE `trade_delivery_express_template_charge` ADD KEY `idx_template_id` (`template_id`);
ALTER TABLE `trade_delivery_express_template_free` ADD KEY `idx_template_id` (`template_id`);
ALTER TABLE `trade_order` ADD KEY `idx_pick_up_verify_code` (`pick_up_verify_code`);
ALTER TABLE `trade_order` ADD KEY `idx_status_create_time` (`status`,`create_time`);
ALTER TABLE `trade_order` ADD KEY `idx_user_id` (`user_id`);
ALTER TABLE `trade_order_item` ADD KEY `idx_order_id` (`order_id`);
ALTER TABLE `trade_order_log` ADD KEY `idx_order_id` (`order_id`);

-- WMS schema. The source demo rows are intentionally not imported: they use legacy sample IDs
-- and must not be confused with canonical Catalog or Inventory identities.
CREATE TABLE IF NOT EXISTS `wms_check_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '盘库单号',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '单据日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '盘库状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `total_quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '盈亏数量',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '总金额（账面）',
  `actual_price` decimal(16,2) DEFAULT NULL COMMENT '实际金额（盘点）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 盘库单';
CREATE TABLE IF NOT EXISTS `wms_check_order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '盘库单编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `inventory_id` bigint DEFAULT NULL COMMENT '库存编号',
  `inventory_detail_id` bigint DEFAULT NULL COMMENT '库存明细编号',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expiration_date` datetime DEFAULT NULL COMMENT '过期日期',
  `receipt_time` datetime DEFAULT NULL COMMENT '入库时间',
  `quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '账面数量',
  `check_quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '实盘数量',
  `price` decimal(16,2) DEFAULT NULL COMMENT '单价',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 盘库单明细';
CREATE TABLE IF NOT EXISTS `wms_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '库存数量',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_sku_id_warehouse_id` (`sku_id`,`warehouse_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 库存';
CREATE TABLE IF NOT EXISTS `wms_inventory_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号',
  `quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '库存变化数量',
  `before_quantity` decimal(20,2) DEFAULT NULL COMMENT '变化前库存数量',
  `after_quantity` decimal(20,2) DEFAULT NULL COMMENT '变化后库存数量',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expiration_date` datetime DEFAULT NULL COMMENT '过期日期',
  `price` decimal(16,2) DEFAULT NULL COMMENT '单价',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '库存变化金额',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `order_id` bigint DEFAULT NULL COMMENT '操作单编号',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作单号',
  `order_type` int DEFAULT NULL COMMENT '操作类型',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_warehouse_id_create_time` (`warehouse_id`,`create_time`) USING BTREE,
  KEY `idx_sku_id_create_time` (`sku_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 库存流水';
CREATE TABLE IF NOT EXISTS `wms_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品编号',
  `name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
  `category_id` bigint NOT NULL COMMENT '商品分类编号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `brand_id` bigint DEFAULT NULL COMMENT '商品品牌编号',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 商品';
CREATE TABLE IF NOT EXISTS `wms_item_brand` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品牌编号',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品牌名称',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 商品品牌';
CREATE TABLE IF NOT EXISTS `wms_item_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父分类编号',
  `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编号',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态（0 停用，1 正常）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 商品分类';
CREATE TABLE IF NOT EXISTS `wms_item_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规格名称',
  `item_id` bigint NOT NULL COMMENT '商品编号',
  `bar_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条码',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格编号',
  `length` decimal(10,1) DEFAULT NULL COMMENT '长，单位 cm',
  `width` decimal(10,1) DEFAULT NULL COMMENT '宽，单位 cm',
  `height` decimal(10,1) DEFAULT NULL COMMENT '高，单位 cm',
  `gross_weight` decimal(10,3) DEFAULT NULL COMMENT '毛重，单位 kg',
  `net_weight` decimal(10,3) DEFAULT NULL COMMENT '净重，单位 kg',
  `cost_price` decimal(16,2) DEFAULT NULL COMMENT '成本价（单位：元）',
  `selling_price` decimal(16,2) DEFAULT NULL COMMENT '销售价（单位：元）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 商品 SKU';
CREATE TABLE IF NOT EXISTS `wms_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '往来企业编号',
  `name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '往来企业名称',
  `type` tinyint NOT NULL COMMENT '往来企业类型',
  `level` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '级别',
  `bank_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户行',
  `bank_account` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账户',
  `address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址',
  `mobile` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `telephone` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '座机号',
  `contact` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Email',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 往来企业';
CREATE TABLE IF NOT EXISTS `wms_movement_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '移库单号',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '单据日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '移库状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `source_warehouse_id` bigint NOT NULL COMMENT '来源仓库编号',
  `source_area_id` bigint NOT NULL DEFAULT '0' COMMENT '来源库区编号',
  `target_warehouse_id` bigint NOT NULL COMMENT '目标仓库编号',
  `target_area_id` bigint NOT NULL DEFAULT '0' COMMENT '目标库区编号',
  `total_quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '总数量',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '总金额',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_source_warehouse_id` (`source_warehouse_id`) USING BTREE,
  KEY `idx_target_warehouse_id` (`target_warehouse_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 移库单';
CREATE TABLE IF NOT EXISTS `wms_movement_order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '移库单编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号',
  `source_warehouse_id` bigint NOT NULL COMMENT '来源仓库编号',
  `source_area_id` bigint NOT NULL DEFAULT '0' COMMENT '来源库区编号',
  `inventory_detail_id` bigint DEFAULT NULL COMMENT '库存明细编号',
  `target_warehouse_id` bigint NOT NULL COMMENT '目标仓库编号',
  `target_area_id` bigint NOT NULL DEFAULT '0' COMMENT '目标库区编号',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expiration_date` datetime DEFAULT NULL COMMENT '过期日期',
  `quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '移库数量',
  `price` decimal(16,2) DEFAULT NULL COMMENT '单价',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '行金额',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 移库单明细';
CREATE TABLE IF NOT EXISTS `wms_receipt_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单号',
  `type` int NOT NULL COMMENT '入库类型',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '单据日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '入库状态',
  `biz_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务订单号',
  `merchant_id` bigint DEFAULT NULL COMMENT '往来企业编号',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `total_quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '总数量',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '总金额',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 入库单';
CREATE TABLE IF NOT EXISTS `wms_receipt_order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '入库单编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expiration_date` datetime DEFAULT NULL COMMENT '过期日期',
  `quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '入库数量',
  `price` decimal(16,2) DEFAULT NULL COMMENT '单价',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '行金额',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 入库单明细';
CREATE TABLE IF NOT EXISTS `wms_shipment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单号',
  `type` int NOT NULL COMMENT '出库类型',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '单据日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '出库状态',
  `biz_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务订单号',
  `merchant_id` bigint DEFAULT NULL COMMENT '客户编号',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `total_quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '总数量',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '总金额',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 出库单';
CREATE TABLE IF NOT EXISTS `wms_shipment_order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '出库单编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `area_id` bigint NOT NULL DEFAULT '0' COMMENT '库区编号',
  `inventory_detail_id` bigint DEFAULT NULL COMMENT '库存明细编号',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expiration_date` datetime DEFAULT NULL COMMENT '过期日期',
  `quantity` decimal(20,2) NOT NULL DEFAULT '0.00' COMMENT '出库数量',
  `price` decimal(16,2) DEFAULT NULL COMMENT '单价',
  `total_price` decimal(16,2) DEFAULT NULL COMMENT '行金额',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 出库单明细';
CREATE TABLE IF NOT EXISTS `wms_warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库编号',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WMS 仓库';
