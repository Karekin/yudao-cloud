-- A governed coupon return is completed through Promotion before its immutable AfterSale
-- reversal fact is recorded. Keep the snapshot tenant-safe and fail closed at the database edge.

ALTER TABLE `cloudmold_promotion_coupon_entitlement`
  ADD UNIQUE KEY `uk_cm_promotion_entitlement_tenant_id` (`tenant_id`,`entitlement_id`);

ALTER TABLE `cloudmold_after_sale_benefit_reversal`
  MODIFY COLUMN `entitlement_id` varchar(64)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  DROP CHECK `ck_cm_after_sale_benefit_reversal_entitlement`,
  ADD CONSTRAINT `ck_cm_after_sale_benefit_reversal_entitlement` CHECK (
    (`entitlement_id` IS NULL AND `entitlement_effect_status` = 'NOT_REQUIRED')
    OR (`entitlement_id` IS NOT NULL AND `entitlement_effect_status` = 'RETURNED')
  ),
  ADD CONSTRAINT `fk_cm_after_sale_benefit_reversal_entitlement`
    FOREIGN KEY (`tenant_id`,`entitlement_id`)
    REFERENCES `cloudmold_promotion_coupon_entitlement` (`tenant_id`,`entitlement_id`);
