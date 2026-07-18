ALTER TABLE `cloudmold_fulfillment_order`
  ADD COLUMN `delivery_promise_version_ref` varchar(64) DEFAULT NULL AFTER `warehouse_id`,
  ADD COLUMN `promised_delivery_at` datetime(6) DEFAULT NULL AFTER `delivery_promise_version_ref`,
  ADD COLUMN `promise_frozen_at` datetime(6) DEFAULT NULL AFTER `promised_delivery_at`,
  ADD KEY `idx_fulfillment_promised_delivery` (`tenant_id`,`promised_delivery_at`),
  ADD CONSTRAINT `ck_fulfillment_delivery_promise_pair` CHECK (
    (`delivery_promise_version_ref` IS NULL AND `promised_delivery_at` IS NULL AND `promise_frozen_at` IS NULL)
    OR (`delivery_promise_version_ref` IS NOT NULL AND `promised_delivery_at` IS NOT NULL
        AND `promise_frozen_at` IS NOT NULL AND `promise_frozen_at` <= `promised_delivery_at`)
  );
