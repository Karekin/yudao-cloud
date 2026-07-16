-- The resolution Saga snapshots deterministic domain timestamps before asynchronous execution.
-- A positive benefit reversal is therefore PENDING with its scheduled occurred_at already fixed.

ALTER TABLE `cloudmold_after_sale_resolution_saga`
  DROP CHECK `ck_cm_after_sale_benefit_reversal_status`,
  ADD CONSTRAINT `ck_cm_after_sale_benefit_reversal_status` CHECK (
    (`benefit_reversal_status` = 'NOT_REQUIRED'
      AND `benefit_amount_minor` = 0
      AND `benefit_reversal_amount_minor` = 0
      AND `benefit_reversal_batch_id` IS NULL
      AND `benefit_reversal_occurred_at` IS NULL)
    OR (`benefit_reversal_status` = 'PENDING'
      AND `benefit_amount_minor` > 0
      AND `benefit_reversal_amount_minor` = 0
      AND `benefit_reversal_batch_id` IS NULL
      AND `benefit_reversal_occurred_at` IS NOT NULL)
    OR (`benefit_reversal_status` = 'RECORDED'
      AND `benefit_amount_minor` > 0
      AND `benefit_reversal_amount_minor` = `benefit_amount_minor`
      AND `benefit_reversal_batch_id` IS NOT NULL
      AND `benefit_reversal_occurred_at` IS NOT NULL)
  );
