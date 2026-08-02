ALTER TABLE `cloudmold_procurement_receipt_inspection`
  MODIFY COLUMN `purchase_order_id` varchar(128) NOT NULL,
  DROP CONSTRAINT `ck_pri_external_uuid`,
  ADD CONSTRAINT `ck_pri_external_uuid` CHECK (
    `receipt_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    AND `supplier_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    AND `owner_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');

ALTER TABLE `cloudmold_procurement_receipt_inspection_line`
  MODIFY COLUMN `purchase_order_id` varchar(128) NOT NULL,
  MODIFY COLUMN `item_id` varchar(128) NOT NULL,
  MODIFY COLUMN `schedule_id` varchar(128) NOT NULL,
  DROP CONSTRAINT `ck_pri_line_external_uuid`,
  DROP CONSTRAINT `ck_pri_line_valuation`,
  ADD CONSTRAINT `ck_pri_line_external_uuid` CHECK (
    `receipt_line_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    AND `canonical_sku_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    AND `supplier_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    AND `owner_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
  ADD CONSTRAINT `ck_pri_line_valuation` CHECK (
    `valuation_policy` REGEXP '^[A-Z0-9][A-Z0-9_.-]{1,63}$'
    AND `valuation_policy_version` REGEXP '^[A-Z0-9][A-Z0-9._-]{0,63}$'
    AND `valuation_policy_hash` REGEXP '^[0-9a-f]{64}$'
    AND `unit_cost_amount_minor` >= 0 AND `currency_code` REGEXP '^[A-Z]{3}$');
