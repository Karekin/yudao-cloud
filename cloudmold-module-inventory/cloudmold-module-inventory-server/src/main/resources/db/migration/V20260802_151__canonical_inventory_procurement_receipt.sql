-- Canonical procurement receipt, quality disposition, and supplier-return inventory effects.

ALTER TABLE `cloudmold_inventory_balance_v3`
  DROP CONSTRAINT `ck_cm_inv_v3_stock_status`,
  DROP CONSTRAINT `ck_cm_inv_v3_quality_status`,
  DROP CONSTRAINT `ck_cm_inv_v3_sellable_quality`;

UPDATE `cloudmold_inventory_balance_v3`
SET `stock_status` = 'QA_HOLD',
    `updated_at` = UTC_TIMESTAMP(6)
WHERE `stock_status` = 'NON_SELLABLE'
  AND `quality_status` = 'PENDING_QC';

ALTER TABLE `cloudmold_inventory_balance_v3`
  ADD CONSTRAINT `ck_cm_inv_v3_stock_status` CHECK (`stock_status` IN ('SELLABLE','NON_SELLABLE','QA_HOLD')),
  ADD CONSTRAINT `ck_cm_inv_v3_quality_status` CHECK (`quality_status` IN ('PENDING_QC','QUALIFIED','DAMAGED','REJECTED','QUARANTINED')),
  ADD CONSTRAINT `ck_cm_inv_v3_sellable_quality` CHECK (
    (`stock_status` = 'SELLABLE' AND `quality_status` = 'QUALIFIED')
    OR (`stock_status` = 'QA_HOLD' AND `quality_status` = 'PENDING_QC')
    OR (`stock_status` = 'NON_SELLABLE' AND `quality_status` IN ('DAMAGED','REJECTED','QUARANTINED'))
  );

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_procurement_receipt_operation_v3` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_event_id` varchar(36) DEFAULT NULL,
  `receipt_id` varchar(36) NOT NULL,
  `receipt_line_id` varchar(36) NOT NULL,
  `decision_version` bigint NOT NULL,
  `disposition` varchar(16) NOT NULL,
  `operation_type` varchar(32) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL,
  `ledger_transaction_id` bigint DEFAULT NULL,
  `source_balance_id` varchar(36) DEFAULT NULL,
  `source_aggregate_version` bigint DEFAULT NULL,
  `target_balance_id` varchar(36) DEFAULT NULL,
  `target_aggregate_version` bigint DEFAULT NULL,
  `received_quantity` decimal(24,6) DEFAULT NULL,
  `pending_quantity` decimal(24,6) DEFAULT NULL,
  `accepted_quantity` decimal(24,6) DEFAULT NULL,
  `rejected_quantity` decimal(24,6) DEFAULT NULL,
  `quarantined_quantity` decimal(24,6) DEFAULT NULL,
  `returned_quantity` decimal(24,6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_cm_inv_pr_op_tenant_id` (`tenant_id`,`operation_id`),
  UNIQUE KEY `uk_cm_inv_pr_op_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_cm_inv_pr_op_source_event` (`tenant_id`,`source_event_id`),
  UNIQUE KEY `uk_cm_inv_pr_op_effect` (`tenant_id`,`receipt_line_id`,`decision_version`,`disposition`,`operation_type`),
  CONSTRAINT `fk_cm_inv_pr_op_source_balance` FOREIGN KEY (`tenant_id`,`source_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `fk_cm_inv_pr_op_target_balance` FOREIGN KEY (`tenant_id`,`target_balance_id`)
    REFERENCES `cloudmold_inventory_balance_v3` (`tenant_id`,`balance_id`),
  CONSTRAINT `ck_cm_inv_pr_op_type` CHECK (`operation_type` IN ('RECEIVE_PENDING_QUALITY','ACCEPT_QUALITY','REJECT_QUALITY','QUARANTINE_QUALITY','RETURN_TO_SUPPLIER')),
  CONSTRAINT `ck_cm_inv_pr_op_disposition` CHECK (`disposition` IN ('PENDING','ACCEPTED','REJECTED','QUARANTINED')),
  CONSTRAINT `ck_cm_inv_pr_op_decision` CHECK ((`operation_type` = 'RECEIVE_PENDING_QUALITY' AND `decision_version` = 0 AND `disposition` = 'PENDING') OR (`operation_type` <> 'RECEIVE_PENDING_QUALITY' AND `decision_version` >= 1)),
  CONSTRAINT `ck_cm_inv_pr_op_status` CHECK (`status` IN (0,10)),
  CONSTRAINT `ck_cm_inv_pr_op_result` CHECK (
    (`status`=0 AND `ledger_transaction_id` IS NULL AND `source_balance_id` IS NULL AND `target_balance_id` IS NULL
      AND `received_quantity` IS NULL AND `pending_quantity` IS NULL AND `accepted_quantity` IS NULL
      AND `rejected_quantity` IS NULL AND `quarantined_quantity` IS NULL AND `returned_quantity` IS NULL)
    OR
    (`status`=10 AND `ledger_transaction_id` IS NOT NULL AND `target_balance_id` IS NOT NULL
      AND `target_aggregate_version` IS NOT NULL AND `received_quantity` IS NOT NULL
      AND `pending_quantity` IS NOT NULL AND `accepted_quantity` IS NOT NULL
      AND `rejected_quantity` IS NOT NULL AND `quarantined_quantity` IS NOT NULL
      AND `returned_quantity` IS NOT NULL)
  ),
  CONSTRAINT `ck_cm_inv_pr_op_quantities` CHECK (
    (`received_quantity` IS NULL OR (`received_quantity` >= 0 AND `pending_quantity` >= 0
      AND `accepted_quantity` >= 0 AND `rejected_quantity` >= 0 AND `quarantined_quantity` >= 0
      AND `returned_quantity` >= 0 AND `received_quantity` = `pending_quantity` + `accepted_quantity`
        + `rejected_quantity` + `quarantined_quantity` + `returned_quantity`))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cloudmold_inventory_procurement_receipt_v3` (
  `receipt_line_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `receipt_id` varchar(36) NOT NULL,
  `purchase_order_id` varchar(36) NOT NULL,
  `purchase_order_item_id` varchar(36) NOT NULL,
  `purchase_order_schedule_id` varchar(36) NOT NULL,
  `supplier_id` varchar(36) NOT NULL,
  `owner_type` varchar(32) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `canonical_sku_id` varchar(36) NOT NULL,
  `warehouse_id` varchar(36) NOT NULL,
  `location_id` varchar(36) NOT NULL,
  `lot_id` varchar(36) DEFAULT NULL,
  `base_uom_code` varchar(32) NOT NULL,
  `valuation_policy` varchar(64) NOT NULL,
  `valuation_policy_version` varchar(64) NOT NULL,
  `valuation_policy_hash` char(64) NOT NULL,
  `unit_cost_amount_minor` bigint NOT NULL,
  `currency_code` char(3) NOT NULL,
  `received_quantity` decimal(24,6) NOT NULL,
  `pending_quantity` decimal(24,6) NOT NULL,
  `accepted_quantity` decimal(24,6) NOT NULL,
  `rejected_quantity` decimal(24,6) NOT NULL,
  `quarantined_quantity` decimal(24,6) NOT NULL,
  `returned_quantity` decimal(24,6) NOT NULL,
  `version` bigint NOT NULL,
  `created_operation_id` bigint NOT NULL,
  `last_operation_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`tenant_id`,`receipt_line_id`),
  UNIQUE KEY `uk_cm_inv_pr_receipt_line` (`tenant_id`,`receipt_id`,`receipt_line_id`),
  KEY `idx_cm_inv_pr_po_item` (`tenant_id`,`purchase_order_id`,`purchase_order_item_id`,`purchase_order_schedule_id`),
  CONSTRAINT `fk_cm_inv_pr_warehouse` FOREIGN KEY (`tenant_id`,`warehouse_id`) REFERENCES `cloudmold_warehouse` (`tenant_id`,`warehouse_id`),
  CONSTRAINT `fk_cm_inv_pr_location` FOREIGN KEY (`tenant_id`,`location_id`) REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`location_id`),
  CONSTRAINT `fk_cm_inv_pr_lot` FOREIGN KEY (`tenant_id`,`lot_id`) REFERENCES `cloudmold_inventory_lot` (`tenant_id`,`lot_id`),
  CONSTRAINT `fk_cm_inv_pr_created_op` FOREIGN KEY (`tenant_id`,`created_operation_id`) REFERENCES `cloudmold_inventory_procurement_receipt_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `fk_cm_inv_pr_last_op` FOREIGN KEY (`tenant_id`,`last_operation_id`) REFERENCES `cloudmold_inventory_procurement_receipt_operation_v3` (`tenant_id`,`operation_id`),
  CONSTRAINT `ck_cm_inv_pr_owner` CHECK (`owner_type` IN ('MERCHANT','PLATFORM','MEMBER')),
  CONSTRAINT `ck_cm_inv_pr_cost` CHECK (`unit_cost_amount_minor` >= 0
    AND `valuation_policy` REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
    AND `valuation_policy_version` REGEXP '^[A-Z0-9][A-Z0-9._-]{0,63}$'
    AND `valuation_policy_hash` REGEXP '^[0-9a-f]{64}$'
    AND BINARY `currency_code`=BINARY UPPER(`currency_code`)),
  CONSTRAINT `ck_cm_inv_pr_quantities` CHECK (`received_quantity` >= 0 AND `pending_quantity` >= 0 AND `accepted_quantity` >= 0 AND `rejected_quantity` >= 0 AND `quarantined_quantity` >= 0 AND `returned_quantity` >= 0 AND `received_quantity` = `pending_quantity` + `accepted_quantity` + `rejected_quantity` + `quarantined_quantity` + `returned_quantity`),
  CONSTRAINT `ck_cm_inv_pr_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_inventory_ledger_transaction_v3`
  DROP CONSTRAINT `ck_cm_inv_v3_tx_operation_owner`,
  ADD COLUMN `procurement_receipt_operation_id` bigint DEFAULT NULL AFTER `stock_transfer_operation_id`,
  ADD UNIQUE KEY `uk_cm_inv_v3_tx_proc_receipt_op` (`tenant_id`,`procurement_receipt_operation_id`),
  ADD CONSTRAINT `fk_cm_inv_v3_tx_proc_receipt_op` FOREIGN KEY (`tenant_id`,`procurement_receipt_operation_id`)
    REFERENCES `cloudmold_inventory_procurement_receipt_operation_v3` (`tenant_id`,`operation_id`),
  ADD CONSTRAINT `ck_cm_inv_v3_tx_operation_owner` CHECK (
    (`operation_id` IS NOT NULL) + (`stock_transfer_operation_id` IS NOT NULL)
      + (`procurement_receipt_operation_id` IS NOT NULL) = 1
  ),
  DROP CONSTRAINT `ck_cm_inv_v3_tx_command`,
  ADD CONSTRAINT `ck_cm_inv_v3_tx_command` CHECK (`command_type` IN (
    'RECEIVE','RESERVE','SHIP','RETURN','RELEASE','MIGRATION_OPENING','INTRANSIT_ADD','INTRANSIT_SETTLE',
    'QUALITY_RELEASE','RELOCATE','STOCK_TRANSFER_DISPATCH','STOCK_TRANSFER_RECEIVE',
    'RECEIVE_PENDING_QUALITY','ACCEPT_QUALITY','REJECT_QUALITY','QUARANTINE_QUALITY','RETURN_TO_SUPPLIER'
  ));

ALTER TABLE `cloudmold_inventory_procurement_receipt_operation_v3`
  ADD CONSTRAINT `fk_cm_inv_pr_op_ledger_tx` FOREIGN KEY (`tenant_id`,`ledger_transaction_id`)
    REFERENCES `cloudmold_inventory_ledger_transaction_v3` (`tenant_id`,`ledger_transaction_id`);
