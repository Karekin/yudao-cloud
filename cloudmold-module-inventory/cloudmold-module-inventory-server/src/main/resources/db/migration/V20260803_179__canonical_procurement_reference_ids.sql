ALTER TABLE `cloudmold_inventory_procurement_receipt_v3`
  MODIFY COLUMN `purchase_order_id` varchar(128) NOT NULL,
  MODIFY COLUMN `purchase_order_item_id` varchar(128) NOT NULL,
  MODIFY COLUMN `purchase_order_schedule_id` varchar(128) NOT NULL,
  DROP CONSTRAINT `ck_cm_inv_pr_cost`,
  ADD CONSTRAINT `ck_cm_inv_pr_cost` CHECK (`unit_cost_amount_minor` >= 0
    AND `valuation_policy` REGEXP '^[A-Z0-9][A-Z0-9_.-]{1,63}$'
    AND `valuation_policy_version` REGEXP '^[A-Z0-9][A-Z0-9._-]{0,63}$'
    AND `valuation_policy_hash` REGEXP '^[0-9a-f]{64}$'
    AND BINARY `currency_code`=BINARY UPPER(`currency_code`));
