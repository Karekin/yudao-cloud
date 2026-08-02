ALTER TABLE `cloudmold_inventory_procurement_receipt_operation_v3`
  DROP INDEX `uk_cm_inv_pr_op_effect`,
  ADD COLUMN `quality_effect_guard` varchar(160) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (
      CASE
        WHEN `operation_type` = 'RETURN_TO_SUPPLIER' THEN NULL
        ELSE CONCAT(`receipt_line_id`, ':', `decision_version`, ':', `disposition`, ':', `operation_type`)
      END
    ) STORED AFTER `operation_type`,
  ADD UNIQUE KEY `uk_cm_inv_pr_op_quality_effect` (`tenant_id`, `quality_effect_guard`);
