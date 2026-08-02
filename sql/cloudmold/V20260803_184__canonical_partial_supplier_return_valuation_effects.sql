ALTER TABLE `cloudmold_finance_inventory_valuation_effect`
  ADD COLUMN `supplier_return_line_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    AFTER `effect_type`;

UPDATE `cloudmold_finance_inventory_valuation_effect` effect
JOIN `cloudmold_finance_supplier_debit_adjustment` adjustment
  ON adjustment.tenant_id = effect.tenant_id
 AND adjustment.journal_entry_id = effect.journal_entry_id
JOIN `cloudmold_finance_supplier_debit_adjustment_line` adjustment_line
  ON adjustment_line.tenant_id = adjustment.tenant_id
 AND adjustment_line.supplier_debit_adjustment_id = adjustment.supplier_debit_adjustment_id
 AND adjustment_line.valuation_layer_id = effect.valuation_layer_id
SET effect.supplier_return_line_id = adjustment_line.supplier_return_line_id
WHERE effect.effect_type = 'SUPPLIER_RETURN';

ALTER TABLE `cloudmold_finance_inventory_valuation_effect`
  DROP INDEX `uk_finance_valuation_effect_source`,
  ADD COLUMN `source_effect_guard` varchar(180) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (
      CASE
        WHEN `effect_type` = 'SUPPLIER_RETURN' THEN NULL
        ELSE CONCAT(`inventory_movement_id`, ':', `effect_type`)
      END
    ) STORED AFTER `inventory_movement_version`,
  ADD UNIQUE KEY `uk_finance_valuation_effect_source_guard` (`tenant_id`, `source_effect_guard`),
  ADD UNIQUE KEY `uk_finance_supplier_return_effect_line`
    (`tenant_id`, `supplier_return_line_id`, `effect_type`),
  ADD CONSTRAINT `ck_finance_valuation_effect_supplier_return_line`
    CHECK ((`effect_type` = 'SUPPLIER_RETURN' AND `supplier_return_line_id` IS NOT NULL)
        OR (`effect_type` <> 'SUPPLIER_RETURN' AND `supplier_return_line_id` IS NULL));

