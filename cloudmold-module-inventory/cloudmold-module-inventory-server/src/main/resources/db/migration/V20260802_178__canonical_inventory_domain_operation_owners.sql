-- Give stock-count adjustment and scrap disposition their own immutable-ledger
-- operation ownership. Domain operation IDs must never impersonate a general
-- inventory operation merely because both use bigint identifiers.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_inventory_stock_count_adjustment_operation`
  ADD UNIQUE KEY `uk_cm_inv_count_adjustment_op_tenant_id` (`tenant_id`,`operation_id`);

ALTER TABLE `cloudmold_inventory_scrap_disposition_operation_v1`
  ADD UNIQUE KEY `uk_cm_inv_scrap_disposition_op_tenant_id` (`tenant_id`,`operation_id`);

ALTER TABLE `cloudmold_inventory_ledger_transaction_v3`
  DROP CONSTRAINT `ck_cm_inv_v3_tx_operation_owner`,
  ADD COLUMN `stock_count_adjustment_operation_id` bigint DEFAULT NULL
    AFTER `procurement_receipt_operation_id`,
  ADD COLUMN `scrap_disposition_operation_id` bigint DEFAULT NULL
    AFTER `stock_count_adjustment_operation_id`,
  ADD UNIQUE KEY `uk_cm_inv_v3_tx_count_adjustment_op`
    (`tenant_id`,`stock_count_adjustment_operation_id`),
  ADD UNIQUE KEY `uk_cm_inv_v3_tx_scrap_disposition_op`
    (`tenant_id`,`scrap_disposition_operation_id`),
  ADD CONSTRAINT `fk_cm_inv_v3_tx_count_adjustment_op`
    FOREIGN KEY (`tenant_id`,`stock_count_adjustment_operation_id`)
    REFERENCES `cloudmold_inventory_stock_count_adjustment_operation` (`tenant_id`,`operation_id`),
  ADD CONSTRAINT `fk_cm_inv_v3_tx_scrap_disposition_op`
    FOREIGN KEY (`tenant_id`,`scrap_disposition_operation_id`)
    REFERENCES `cloudmold_inventory_scrap_disposition_operation_v1` (`tenant_id`,`operation_id`),
  ADD CONSTRAINT `ck_cm_inv_v3_tx_operation_owner` CHECK (
    (`operation_id` IS NOT NULL)
      + (`stock_transfer_operation_id` IS NOT NULL)
      + (`procurement_receipt_operation_id` IS NOT NULL)
      + (`stock_count_adjustment_operation_id` IS NOT NULL)
      + (`scrap_disposition_operation_id` IS NOT NULL) = 1
  );
