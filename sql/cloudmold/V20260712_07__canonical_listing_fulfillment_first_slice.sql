-- Canonical Listing red-zone schema. Y-Shopping Product onboarding and legacy Mall Product stay adapters.
-- Fulfillment intentionally starts in V20260712_08: do not mix its independent aggregate into this migration.

CREATE TABLE IF NOT EXISTS `cloudmold_listing_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `listing_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_listing_operation_idempotency` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Canonical Listing command idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_header` (
  `listing_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `listing_no` varchar(32) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `merchant_id` varchar(128) NOT NULL,
  `channel_code` varchar(32) NOT NULL,
  `shop_id` varchar(128) NOT NULL,
  `canonical_spu_id` varchar(36) NOT NULL,
  `revision` int unsigned NOT NULL,
  `title` varchar(255) NOT NULL,
  `primary_image_url` varchar(1024) DEFAULT NULL,
  `category_ref` varchar(128) NOT NULL,
  `brand_ref` varchar(128) NOT NULL,
  `source_system` varchar(64) NOT NULL,
  `publisher_ref` varchar(128) NOT NULL,
  `currency_code` char(3) NOT NULL,
  `publish_start_at` datetime(6) DEFAULT NULL,
  `publish_end_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `completion_passed` bit(1) NOT NULL DEFAULT b'0',
  `business_approved` bit(1) NOT NULL DEFAULT b'0',
  `risk_approved` bit(1) NOT NULL DEFAULT b'0',
  `version` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`listing_id`),
  UNIQUE KEY `uk_listing_tenant_id` (`tenant_id`,`listing_id`),
  UNIQUE KEY `uk_listing_no` (`tenant_id`,`listing_no`),
  UNIQUE KEY `uk_listing_channel_shop_spu` (`tenant_id`,`channel_code`,`shop_id`,`canonical_spu_id`),
  KEY `idx_listing_run` (`tenant_id`,`run_id`),
  KEY `idx_listing_publish_gate` (`tenant_id`,`status`,`publish_start_at`,`publish_end_at`),
  CONSTRAINT `fk_listing_catalog_spu` FOREIGN KEY (`tenant_id`,`canonical_spu_id`)
    REFERENCES `cloudmold_catalog_spu` (`tenant_id`,`spu_id`),
  CONSTRAINT `ck_listing_revision` CHECK (`revision` > 0),
  CONSTRAINT `ck_listing_currency` CHECK (`currency_code` = 'CNY'),
  CONSTRAINT `ck_listing_window` CHECK (`publish_end_at` IS NULL OR `publish_start_at` IS NULL
    OR `publish_end_at` > `publish_start_at`),
  CONSTRAINT `ck_listing_status` CHECK (`status` IN (
    'DRAFT','SUBMITTED','COMPLETION_PASSED','BUSINESS_APPROVED','RISK_APPROVED',
    'REJECTED','PUBLISHED','UNPUBLISHED','SUSPENDED','ARCHIVED')),
  CONSTRAINT `ck_listing_publish_approvals` CHECK (`status` <> 'PUBLISHED'
    OR (`completion_passed` = b'1' AND `business_approved` = b'1' AND `risk_approved` = b'1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One canonical channel/shop/SPU Listing header in the first slice';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_offer` (
  `listing_offer_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `revision` int unsigned NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `price_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `external_offer_id` varchar(128) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`listing_offer_id`),
  UNIQUE KEY `uk_listing_offer_tenant_id` (`tenant_id`,`listing_offer_id`),
  UNIQUE KEY `uk_listing_offer_sku_revision` (`tenant_id`,`listing_id`,`revision`,`canonical_sku_id`),
  UNIQUE KEY `uk_listing_external_offer` (`tenant_id`,`listing_id`,`revision`,`external_offer_id`),
  CONSTRAINT `fk_listing_offer_header` FOREIGN KEY (`tenant_id`,`listing_id`)
    REFERENCES `cloudmold_listing_header` (`tenant_id`,`listing_id`),
  CONSTRAINT `fk_listing_offer_catalog_sku` FOREIGN KEY (`tenant_id`,`canonical_sku_id`)
    REFERENCES `cloudmold_catalog_sku` (`tenant_id`,`sku_id`),
  CONSTRAINT `ck_listing_offer_revision` CHECK (`revision` > 0),
  CONSTRAINT `ck_listing_offer_price` CHECK (`price_minor` >= 0),
  CONSTRAINT `ck_listing_offer_currency` CHECK (`currency_code` = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable per-revision canonical SKU offer; stock is not a Listing concern';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_status_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `revision` int unsigned NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `operation_id` bigint NOT NULL,
  `reason` varchar(256) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_listing_history_version` (`tenant_id`,`listing_id`,`aggregate_version`),
  UNIQUE KEY `uk_listing_history_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_listing_history_header` FOREIGN KEY (`tenant_id`,`listing_id`)
    REFERENCES `cloudmold_listing_header` (`tenant_id`,`listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable canonical Listing status history';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_review_decision` (
  `decision_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `revision` int unsigned NOT NULL,
  `stage` varchar(16) NOT NULL,
  `attempt_no` int unsigned NOT NULL,
  `decision` varchar(16) NOT NULL,
  `reason` varchar(256) DEFAULT NULL,
  `operation_id` bigint NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`decision_id`),
  UNIQUE KEY `uk_listing_review_attempt` (`tenant_id`,`listing_id`,`revision`,`stage`,`attempt_no`),
  UNIQUE KEY `uk_listing_review_operation` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_listing_review_header` FOREIGN KEY (`tenant_id`,`listing_id`)
    REFERENCES `cloudmold_listing_header` (`tenant_id`,`listing_id`),
  CONSTRAINT `ck_listing_review_stage` CHECK (`stage` IN ('COMPLETION','BUSINESS','RISK')),
  CONSTRAINT `ck_listing_review_decision` CHECK (`decision` IN ('PENDING','PASSED','REJECTED')),
  CONSTRAINT `ck_listing_review_reason` CHECK (`decision` <> 'REJECTED' OR `reason` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable Listing review decision per revision and stage';
