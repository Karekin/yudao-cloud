-- Canonical Lot lifecycle and qualified source-identity command control.
-- This migration is additive: it does not infer, backfill, or migrate any legacy inventory balance.

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_lot_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(36) DEFAULT NULL,
  `command_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 PROCESSING, 10 SUCCEEDED',
  `lot_id` varchar(36) DEFAULT NULL,
  `mapping_id` varchar(36) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_inv_lot_operation_tenant_id` (`tenant_id`,`operation_id`),
  UNIQUE KEY `uk_cm_inv_lot_operation_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_inv_lot_operation_source_event` (`tenant_id`,`source_event_id`),
  KEY `idx_cm_inv_lot_operation_lot` (`tenant_id`,`lot_id`,`operation_id`),
  KEY `idx_cm_inv_lot_operation_mapping` (`tenant_id`,`mapping_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_lot_operation_lot` FOREIGN KEY (`tenant_id`,`lot_id`)
    REFERENCES `cloudmold_inventory_lot` (`tenant_id`,`lot_id`),
  CONSTRAINT `fk_cm_inv_lot_operation_mapping` FOREIGN KEY (`tenant_id`,`mapping_id`)
    REFERENCES `cloudmold_inventory_lot_source_mapping` (`tenant_id`,`mapping_id`),
  CONSTRAINT `ck_cm_inv_lot_operation_command` CHECK (`command_type` IN (
    'REGISTER','RECALL','CLOSE','LINK_SOURCE','END_SOURCE')),
  CONSTRAINT `ck_cm_inv_lot_operation_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_inv_lot_operation_result` CHECK (
    (`status`=0 AND `lot_id` IS NULL AND `mapping_id` IS NULL AND `result_json` IS NULL)
    OR (`status`=10 AND `lot_id` IS NOT NULL AND `result_json` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable first-result and idempotency control for canonical Lot commands';

ALTER TABLE `cloudmold_inventory_lot_source_mapping`
  ADD CONSTRAINT `ck_cm_inv_lot_map_status_interval` CHECK (
    (`status`='ACTIVE' AND `valid_to` IS NULL)
    OR (`status`='ENDED' AND `valid_to` IS NOT NULL));
