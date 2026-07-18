ALTER TABLE cloudmold_commerce_behavior_event
    ADD COLUMN `listing_id` CHAR(36) NULL AFTER `sku_id`,
    ADD COLUMN `listing_offer_id` CHAR(36) NULL AFTER `listing_id`,
    ADD COLUMN `merchant_id` CHAR(36) NULL AFTER `listing_offer_id`,
    ADD COLUMN `shop_id` CHAR(36) NULL AFTER `merchant_id`,
    ADD COLUMN `channel_code` VARCHAR(32) NULL AFTER `shop_id`,
    ADD KEY `idx_commerce_behavior_shop_time` (`tenant_id`, `shop_id`, `occurred_at`),
    ADD KEY `idx_commerce_behavior_merchant_time` (`tenant_id`, `merchant_id`, `occurred_at`);

ALTER TABLE cloudmold_commerce_session_payment_attribution
    ADD COLUMN `merchant_id` CHAR(36) NULL AFTER `payment_id`,
    ADD KEY `idx_commerce_payment_attribution_merchant_time` (`tenant_id`, `merchant_id`, `occurred_at`);
