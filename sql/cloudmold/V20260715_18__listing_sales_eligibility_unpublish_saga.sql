-- Listing-owned durable fanout for Merchant suspension and Shop pause.
-- Merchant state, its Outbox event, and this frozen Listing workset are committed in one local transaction.

CREATE TABLE IF NOT EXISTS `cloudmold_listing_unpublish_saga_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `saga_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_listing_unpublish_operation_idempotency` (`tenant_id`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Listing unpublish Saga idempotency and immutable first result';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_unpublish_saga` (
  `saga_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `run_id` varchar(128) NOT NULL,
  `source_event_id` char(36) NOT NULL,
  `source_entity_type` varchar(16) NOT NULL,
  `source_aggregate_version` bigint unsigned NOT NULL,
  `merchant_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `shop_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `active_step` varchar(32) NOT NULL,
  `expected_listing_count` int unsigned NOT NULL,
  `unpublished_listing_count` int unsigned NOT NULL DEFAULT 0,
  `skipped_listing_count` int unsigned NOT NULL DEFAULT 0,
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `max_attempts` int unsigned NOT NULL DEFAULT 8,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `version` bigint unsigned NOT NULL,
  `reason` varchar(512) NOT NULL,
  `correlation_id` char(36) NOT NULL,
  `causation_id` char(36) NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`saga_id`),
  UNIQUE KEY `uk_listing_unpublish_saga_tenant_id` (`tenant_id`,`saga_id`),
  UNIQUE KEY `uk_listing_unpublish_saga_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_listing_unpublish_saga_source_event` (`tenant_id`,`source_event_id`),
  KEY `idx_listing_unpublish_saga_due` (`status`,`next_retry_at`,`lease_until`),
  KEY `idx_listing_unpublish_saga_scope` (`tenant_id`,`merchant_id`,`shop_id`,`created_at`),
  KEY `idx_listing_unpublish_saga_run` (`tenant_id`,`run_id`),
  CONSTRAINT `fk_listing_unpublish_saga_merchant` FOREIGN KEY (`tenant_id`,`merchant_id`)
    REFERENCES `cloudmold_merchant_account` (`tenant_id`,`merchant_id`),
  CONSTRAINT `fk_listing_unpublish_saga_shop` FOREIGN KEY (`tenant_id`,`shop_id`)
    REFERENCES `cloudmold_merchant_shop` (`tenant_id`,`shop_id`),
  CONSTRAINT `ck_listing_unpublish_source_shape` CHECK (
    (`source_entity_type`='MERCHANT' AND `shop_id` IS NULL)
    OR (`source_entity_type`='SHOP' AND `shop_id` IS NOT NULL)),
  CONSTRAINT `ck_listing_unpublish_saga_status` CHECK (`status` IN (
    'REQUESTED','UNPUBLISHING','RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED')),
  CONSTRAINT `ck_listing_unpublish_saga_step` CHECK (`active_step` IN ('UNPUBLISH_LISTINGS','NONE')),
  CONSTRAINT `ck_listing_unpublish_saga_counts` CHECK (
    `unpublished_listing_count` + `skipped_listing_count` <= `expected_listing_count`
    AND `attempt_count` <= `max_attempts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Durable Listing-owned sales eligibility enforcement process manager';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_unpublish_saga_item` (
  `saga_item_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `saga_id` varchar(36) NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `listing_version_at_request` bigint unsigned NOT NULL,
  `unpublish_idempotency_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `listing_operation_id` bigint DEFAULT NULL,
  `final_listing_status` varchar(32) DEFAULT NULL,
  `final_listing_version` bigint unsigned DEFAULT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`saga_item_id`),
  UNIQUE KEY `uk_listing_unpublish_item_listing` (`tenant_id`,`saga_id`,`listing_id`),
  UNIQUE KEY `uk_listing_unpublish_item_idempotency` (`tenant_id`,`unpublish_idempotency_key`),
  CONSTRAINT `fk_listing_unpublish_item_saga` FOREIGN KEY (`tenant_id`,`saga_id`)
    REFERENCES `cloudmold_listing_unpublish_saga` (`tenant_id`,`saga_id`),
  CONSTRAINT `fk_listing_unpublish_item_listing` FOREIGN KEY (`tenant_id`,`listing_id`)
    REFERENCES `cloudmold_listing_header` (`tenant_id`,`listing_id`),
  CONSTRAINT `ck_listing_unpublish_item_status` CHECK (`status` IN (
    'PENDING','RUNNING','RETRY_SCHEDULED','MANUAL_REVIEW','UNPUBLISHED','SKIPPED')),
  CONSTRAINT `ck_listing_unpublish_item_terminal` CHECK (
    (`status` IN ('UNPUBLISHED','SKIPPED') AND `final_listing_status` IS NOT NULL
      AND `final_listing_version` IS NOT NULL AND `completed_at` IS NOT NULL)
    OR (`status` NOT IN ('UNPUBLISHED','SKIPPED') AND `completed_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Frozen exact Listing set and idempotent UNPUBLISH effects';

CREATE TABLE IF NOT EXISTS `cloudmold_listing_unpublish_saga_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `saga_id` varchar(36) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `previous_status` varchar(32) DEFAULT NULL,
  `current_status` varchar(32) NOT NULL,
  `active_step` varchar(32) NOT NULL,
  `attempt_count` int unsigned NOT NULL,
  `expected_listing_count` int unsigned NOT NULL,
  `unpublished_listing_count` int unsigned NOT NULL,
  `skipped_listing_count` int unsigned NOT NULL,
  `error_code` varchar(128) DEFAULT NULL,
  `error_message` varchar(512) DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_listing_unpublish_history_version` (`tenant_id`,`saga_id`,`aggregate_version`),
  CONSTRAINT `fk_listing_unpublish_history_saga` FOREIGN KEY (`tenant_id`,`saga_id`)
    REFERENCES `cloudmold_listing_unpublish_saga` (`tenant_id`,`saga_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable Listing unpublish Saga transition and recovery history';
