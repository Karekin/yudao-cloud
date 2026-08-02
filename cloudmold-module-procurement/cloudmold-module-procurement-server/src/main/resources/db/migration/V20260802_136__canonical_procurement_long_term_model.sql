ALTER TABLE `cloudmold_procurement_order`
  ADD COLUMN `supplier_id` varchar(128) NULL AFTER `source_business_ref`,
  ADD COLUMN `header_net_amount_minor` bigint NULL AFTER `lead_time_days`,
  ADD COLUMN `header_tax_amount_minor` bigint NULL AFTER `header_net_amount_minor`,
  ADD COLUMN `header_gross_amount_minor` bigint NULL AFTER `header_tax_amount_minor`,
  ADD COLUMN `tax_calculation_policy_code` varchar(32) NULL AFTER `header_gross_amount_minor`,
  ADD COLUMN `rounding_policy_code` varchar(32) NULL AFTER `tax_calculation_policy_code`;
