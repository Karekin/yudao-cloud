-- CloudMold red-zone migration. Do not merge these tables into upstream-owned module DDL.

CREATE TABLE IF NOT EXISTS `cloudmold_event_outbox` (
  `event_id` varchar(36) NOT NULL COMMENT 'Stable UUID; retries and replays keep the same id',
  `event_type` varchar(128) NOT NULL,
  `schema_version` int NOT NULL,
  `tenant_id` bigint NOT NULL,
  `aggregate_type` varchar(64) NOT NULL,
  `aggregate_id` varchar(128) NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `event_sequence` smallint NOT NULL DEFAULT 1,
  `occurred_at` datetime(6) NOT NULL COMMENT 'UTC business time',
  `recorded_at` datetime(6) NOT NULL COMMENT 'UTC persistence time',
  `trace_id` varchar(64) DEFAULT NULL,
  `correlation_id` varchar(36) NOT NULL,
  `causation_id` varchar(36) DEFAULT NULL,
  `idempotency_key` varchar(200) NOT NULL,
  `payload` json NOT NULL,
  `headers` json DEFAULT NULL,
  `payload_hash` char(64) NOT NULL,
  `destination` varchar(128) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PENDING, 10 CLAIMED, 20 PUBLISHED, 30 DEAD',
  `available_at` datetime(6) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `max_attempts` int NOT NULL DEFAULT 20,
  `lease_owner` varchar(128) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `last_error_code` varchar(64) DEFAULT NULL,
  `last_error_summary` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_outbox_aggregate_version` (`tenant_id`, `aggregate_type`, `aggregate_id`, `aggregate_version`, `event_sequence`),
  UNIQUE KEY `uk_outbox_idempotency` (`tenant_id`, `event_type`, `idempotency_key`),
  KEY `idx_outbox_relay` (`status`, `available_at`, `lease_until`),
  KEY `idx_outbox_aggregate_order` (`tenant_id`, `aggregate_type`, `aggregate_id`, `aggregate_version`, `event_sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable domain event with mutable delivery metadata';

CREATE TABLE IF NOT EXISTS `cloudmold_event_inbox` (
  `consumer_id` varchar(128) NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `schema_version` int NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `processed_at` datetime(6) NOT NULL,
  `result_hash` char(64) DEFAULT NULL,
  PRIMARY KEY (`consumer_id`, `event_id`),
  KEY `idx_inbox_event` (`event_id`),
  KEY `idx_inbox_tenant_processed` (`tenant_id`, `processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable consumer idempotency; insert with business side effect in one transaction';

CREATE TABLE IF NOT EXISTS `cloudmold_event_replay_audit` (
  `replay_id` varchar(36) NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `consumer_id` varchar(128) NOT NULL,
  `consumer_generation` varchar(64) NOT NULL,
  `requester` varchar(128) NOT NULL,
  `reason` varchar(512) NOT NULL,
  `mode` tinyint NOT NULL COMMENT '10 transport retry, 20 projection rebuild',
  `status` tinyint NOT NULL COMMENT '0 requested, 10 running, 20 succeeded, 30 failed',
  `requested_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `error_summary` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`replay_id`),
  KEY `idx_replay_event` (`event_id`, `requested_at`),
  KEY `idx_replay_status` (`status`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audited event replay requests; never delete Inbox to force replay';

CREATE TABLE IF NOT EXISTS `cloudmold_catalog_source_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `source_system` varchar(32) NOT NULL COMMENT 'MALL, ERP or WMS',
  `source_entity` varchar(32) NOT NULL COMMENT 'STYLE, SPU or SKU',
  `source_id` varchar(128) NOT NULL,
  `canonical_type` varchar(32) NOT NULL COMMENT 'STYLE, SPU or SKU',
  `canonical_id` varchar(128) NOT NULL,
  `projection_version` bigint NOT NULL DEFAULT 0,
  `sync_state` tinyint NOT NULL DEFAULT 0 COMMENT '0 pending, 10 synced, 20 drifted, 30 failed',
  `last_synced_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catalog_source` (`tenant_id`, `source_system`, `source_entity`, `source_id`),
  UNIQUE KEY `uk_catalog_projection` (`tenant_id`, `canonical_type`, `canonical_id`, `source_system`, `source_entity`),
  KEY `idx_catalog_sync_state` (`sync_state`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Anti-corruption mapping; legacy ids never equal canonical ids implicitly';
