-- Canonical Fulfillment red-zone schema. Order is commercial truth; Inventory is stock truth.

ALTER TABLE `cloudmold_order_header`
  ADD COLUMN `fulfillment_id` varchar(36) DEFAULT NULL AFTER `payment_id`,
  ADD COLUMN `shipment_id` varchar(36) DEFAULT NULL AFTER `fulfillment_id`,
  ADD UNIQUE KEY `uk_order_fulfillment` (`tenant_id`,`fulfillment_id`),
  ADD UNIQUE KEY `uk_order_shipment` (`tenant_id`,`shipment_id`);

ALTER TABLE `cloudmold_listing_offer`
  ADD UNIQUE KEY `uk_listing_offer_header_ref` (`tenant_id`,`listing_id`,`listing_offer_id`);

ALTER TABLE `cloudmold_order_item`
  ADD COLUMN `listing_id` varchar(36) DEFAULT NULL AFTER `reservation_id`,
  ADD COLUMN `listing_offer_id` varchar(36) DEFAULT NULL AFTER `listing_id`,
  ADD COLUMN `listing_revision` int unsigned DEFAULT NULL AFTER `listing_offer_id`,
  ADD COLUMN `listing_version` bigint unsigned DEFAULT NULL AFTER `listing_revision`,
  ADD COLUMN `channel_code` varchar(32) DEFAULT NULL AFTER `listing_version`,
  ADD COLUMN `shop_id` varchar(128) DEFAULT NULL AFTER `channel_code`,
  ADD KEY `idx_order_item_listing` (`tenant_id`,`listing_id`,`listing_offer_id`),
  ADD CONSTRAINT `fk_order_item_listing_offer` FOREIGN KEY (`tenant_id`,`listing_id`,`listing_offer_id`)
    REFERENCES `cloudmold_listing_offer` (`tenant_id`,`listing_id`,`listing_offer_id`),
  ADD CONSTRAINT `ck_order_item_listing_snapshot` CHECK (
    (`listing_id` IS NULL AND `listing_offer_id` IS NULL AND `listing_revision` IS NULL
      AND `listing_version` IS NULL AND `channel_code` IS NULL AND `shop_id` IS NULL)
    OR (`listing_id` IS NOT NULL AND `listing_offer_id` IS NOT NULL AND `listing_revision` > 0
      AND `listing_version` > 0 AND `channel_code` IS NOT NULL AND `shop_id` IS NOT NULL)
  );

CREATE TABLE IF NOT EXISTS `cloudmold_fulfillment_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `fulfillment_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_fulfillment_operation_idempotency` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fulfillment command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_fulfillment_order` (
  `fulfillment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `fulfillment_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `seller_id` varchar(128) NOT NULL,
  `warehouse_id` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`fulfillment_id`),
  UNIQUE KEY `uk_fulfillment_tenant_id` (`tenant_id`,`fulfillment_id`),
  UNIQUE KEY `uk_fulfillment_no` (`tenant_id`,`fulfillment_no`),
  UNIQUE KEY `uk_fulfillment_order_seller_warehouse` (`tenant_id`,`order_id`,`seller_id`,`warehouse_id`),
  KEY `idx_fulfillment_run` (`tenant_id`,`run_id`),
  CONSTRAINT `fk_fulfillment_order` FOREIGN KEY (`tenant_id`,`order_id`)
    REFERENCES `cloudmold_order_header` (`tenant_id`,`order_id`),
  CONSTRAINT `ck_fulfillment_status` CHECK (`status` IN ('CREATED','SHIPPED','IN_TRANSIT','DELIVERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical seller and warehouse fulfillment suborder';

CREATE TABLE IF NOT EXISTS `cloudmold_fulfillment_item` (
  `fulfillment_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `fulfillment_id` varchar(36) NOT NULL,
  `order_item_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(128) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `reservation_id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`fulfillment_item_id`),
  UNIQUE KEY `uk_fulfillment_item_tenant_id` (`tenant_id`,`fulfillment_item_id`),
  UNIQUE KEY `uk_fulfillment_order_item` (`tenant_id`,`fulfillment_id`,`order_item_id`),
  CONSTRAINT `fk_fulfillment_item_order` FOREIGN KEY (`tenant_id`,`fulfillment_id`)
    REFERENCES `cloudmold_fulfillment_order` (`tenant_id`,`fulfillment_id`),
  CONSTRAINT `fk_fulfillment_order_item` FOREIGN KEY (`tenant_id`,`order_item_id`)
    REFERENCES `cloudmold_order_item` (`tenant_id`,`order_item_id`),
  CONSTRAINT `ck_fulfillment_item_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable order-item assignment to fulfillment suborder';

CREATE TABLE IF NOT EXISTS `cloudmold_shipment` (
  `shipment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `fulfillment_id` varchar(36) NOT NULL,
  `carrier_code` varchar(32) NOT NULL,
  `waybill_no` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `shipped_at` datetime(6) NOT NULL,
  `in_transit_at` datetime(6) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`shipment_id`),
  UNIQUE KEY `uk_shipment_tenant_id` (`tenant_id`,`shipment_id`),
  UNIQUE KEY `uk_shipment_fulfillment_first_slice` (`tenant_id`,`fulfillment_id`),
  UNIQUE KEY `uk_shipment_waybill` (`tenant_id`,`carrier_code`,`waybill_no`),
  CONSTRAINT `fk_shipment_fulfillment` FOREIGN KEY (`tenant_id`,`fulfillment_id`)
    REFERENCES `cloudmold_fulfillment_order` (`tenant_id`,`fulfillment_id`),
  CONSTRAINT `ck_shipment_status` CHECK (`status` IN ('SHIPPED','IN_TRANSIT','DELIVERED')),
  CONSTRAINT `ck_shipment_milestones` CHECK (
    (`in_transit_at` IS NULL OR `in_transit_at` >= `shipped_at`)
    AND (`delivered_at` IS NULL OR (`in_transit_at` IS NOT NULL AND `delivered_at` >= `in_transit_at`))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical package and immutable carrier identity';

CREATE TABLE IF NOT EXISTS `cloudmold_shipment_item` (
  `shipment_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `shipment_id` varchar(36) NOT NULL,
  `fulfillment_item_id` varchar(36) NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`shipment_item_id`),
  UNIQUE KEY `uk_shipment_item_tenant_id` (`tenant_id`,`shipment_item_id`),
  UNIQUE KEY `uk_shipment_fulfillment_item` (`tenant_id`,`shipment_id`,`fulfillment_item_id`),
  CONSTRAINT `fk_shipment_item_shipment` FOREIGN KEY (`tenant_id`,`shipment_id`)
    REFERENCES `cloudmold_shipment` (`tenant_id`,`shipment_id`),
  CONSTRAINT `fk_shipment_item_fulfillment_item` FOREIGN KEY (`tenant_id`,`fulfillment_item_id`)
    REFERENCES `cloudmold_fulfillment_item` (`tenant_id`,`fulfillment_item_id`),
  CONSTRAINT `ck_shipment_item_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Shipment allocation at fulfillment-item grain';

CREATE TABLE IF NOT EXISTS `cloudmold_tracking_event` (
  `tracking_event_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `shipment_id` varchar(36) NOT NULL,
  `provider_event_key` varchar(192) NOT NULL,
  `tracking_status` varchar(32) NOT NULL,
  `content` varchar(256) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `received_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`tracking_event_id`),
  UNIQUE KEY `uk_tracking_event_provider` (`tenant_id`,`shipment_id`,`provider_event_key`),
  CONSTRAINT `fk_tracking_event_shipment` FOREIGN KEY (`tenant_id`,`shipment_id`)
    REFERENCES `cloudmold_shipment` (`tenant_id`,`shipment_id`),
  CONSTRAINT `ck_tracking_status` CHECK (`tracking_status` IN ('SHIPPED','IN_TRANSIT','DELIVERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable normalized logistics tracking event';

CREATE TABLE IF NOT EXISTS `cloudmold_fulfillment_status_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `fulfillment_id` varchar(36) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `operation_id` bigint NOT NULL,
  `reason` varchar(256) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_fulfillment_history_version` (`tenant_id`,`fulfillment_id`,`aggregate_version`),
  UNIQUE KEY `uk_fulfillment_history_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_fulfillment_history_order` FOREIGN KEY (`tenant_id`,`fulfillment_id`)
    REFERENCES `cloudmold_fulfillment_order` (`tenant_id`,`fulfillment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable fulfillment state and logistics milestone history';
