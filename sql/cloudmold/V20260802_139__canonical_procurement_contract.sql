ALTER TABLE `cloudmold_procurement_order`
  MODIFY COLUMN `supplier_id` varchar(128) NOT NULL,
  MODIFY COLUMN `header_net_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `header_tax_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `header_gross_amount_minor` bigint NOT NULL,
  MODIFY COLUMN `tax_calculation_policy_code` varchar(32) NOT NULL,
  MODIFY COLUMN `rounding_policy_code` varchar(32) NOT NULL,
  ADD CONSTRAINT `fk_procurement_order_supplier`
    FOREIGN KEY (`tenant_id`, `supplier_id`)
    REFERENCES `cloudmold_supplier_profile` (`tenant_id`, `supplier_id`),
  DROP INDEX `uk_procurement_projection_ref`,
  DROP CHECK `ck_procurement_order_quantity`,
  DROP CHECK `ck_procurement_unit_cost`,
  DROP CHECK `ck_procurement_total_amount`,
  DROP CHECK `ck_procurement_uom`,
  DROP CHECK `ck_procurement_projection_status`,
  DROP COLUMN `supplier_ref`,
  DROP COLUMN `canonical_sku_id`,
  DROP COLUMN `canonical_warehouse_id`,
  DROP COLUMN `ordered_quantity`,
  DROP COLUMN `uom_code`,
  DROP COLUMN `unit_cost_minor`,
  DROP COLUMN `total_amount_minor`,
  DROP COLUMN `required_delivery_date`,
  DROP COLUMN `projection_source_system`,
  DROP COLUMN `projection_document_type`,
  DROP COLUMN `projection_external_document_id`,
  DROP COLUMN `projection_external_document_no`,
  DROP COLUMN `projection_document_status`,
  DROP COLUMN `projection_evidence_sha256`,
  ADD CONSTRAINT `ck_procurement_header_net` CHECK (`header_net_amount_minor` >= 0),
  ADD CONSTRAINT `ck_procurement_header_tax` CHECK (`header_tax_amount_minor` >= 0),
  ADD CONSTRAINT `ck_procurement_header_gross` CHECK (`header_gross_amount_minor` >= 0),
  ADD CONSTRAINT `ck_procurement_header_total`
    CHECK (`header_gross_amount_minor` = `header_net_amount_minor` + `header_tax_amount_minor`),
  ADD CONSTRAINT `ck_procurement_tax_policy`
    CHECK (`tax_calculation_policy_code` = 'STANDARD_V1'),
  ADD CONSTRAINT `ck_procurement_rounding_policy`
    CHECK (`rounding_policy_code` = 'HALF_UP');
