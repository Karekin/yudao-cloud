-- Root release migration sequence: V20260718_62.
-- Add governed cancellation responsibility to canonical order cancellation flows.
-- Merchant cancellation metrics must come from explicit controlled codes, never free-text reasons.

ALTER TABLE `cloudmold_order_header`
  ADD COLUMN `cancellation_responsibility_party` varchar(32) DEFAULT NULL AFTER `pre_cancellation_status`,
  ADD COLUMN `cancellation_responsibility_code` varchar(64) DEFAULT NULL AFTER `cancellation_responsibility_party`,
  ADD CONSTRAINT `ck_order_cancellation_responsibility_party` CHECK (
    `cancellation_responsibility_party` IS NULL
    OR `cancellation_responsibility_party` IN ('MERCHANT','BUYER','PLATFORM','CARRIER')
  ),
  ADD CONSTRAINT `ck_order_cancellation_responsibility_code` CHECK (
    (`cancellation_responsibility_party` IS NULL AND `cancellation_responsibility_code` IS NULL)
    OR (`cancellation_responsibility_party`='MERCHANT' AND `cancellation_responsibility_code` IN (
      'MERCHANT_STOCKOUT','MERCHANT_SELLER_REJECTED','MERCHANT_MANUAL_CANCEL'
    ))
    OR (`cancellation_responsibility_party`='BUYER' AND `cancellation_responsibility_code` IN (
      'BUYER_CHANGED_MIND','BUYER_ADDRESS_ERROR','BUYER_PAYMENT_ABANDONED'
    ))
    OR (`cancellation_responsibility_party`='PLATFORM' AND `cancellation_responsibility_code` IN (
      'PLATFORM_RISK_BLOCK','PLATFORM_COMPLIANCE_BLOCK','PLATFORM_PRICE_ERROR'
    ))
    OR (`cancellation_responsibility_party`='CARRIER' AND `cancellation_responsibility_code` IN (
      'CARRIER_UNSERVICEABLE','CARRIER_CAPACITY_BLOCK','CARRIER_DAMAGE_RISK'
    ))
  );

ALTER TABLE `cloudmold_order_status_history`
  ADD COLUMN `cancellation_responsibility_party` varchar(32) DEFAULT NULL AFTER `reason`,
  ADD COLUMN `cancellation_responsibility_code` varchar(64) DEFAULT NULL AFTER `cancellation_responsibility_party`,
  ADD CONSTRAINT `ck_order_status_history_cancellation_party` CHECK (
    `cancellation_responsibility_party` IS NULL
    OR `cancellation_responsibility_party` IN ('MERCHANT','BUYER','PLATFORM','CARRIER')
  ),
  ADD CONSTRAINT `ck_order_status_history_cancellation_code` CHECK (
    (`cancellation_responsibility_party` IS NULL AND `cancellation_responsibility_code` IS NULL)
    OR (`cancellation_responsibility_party`='MERCHANT' AND `cancellation_responsibility_code` IN (
      'MERCHANT_STOCKOUT','MERCHANT_SELLER_REJECTED','MERCHANT_MANUAL_CANCEL'
    ))
    OR (`cancellation_responsibility_party`='BUYER' AND `cancellation_responsibility_code` IN (
      'BUYER_CHANGED_MIND','BUYER_ADDRESS_ERROR','BUYER_PAYMENT_ABANDONED'
    ))
    OR (`cancellation_responsibility_party`='PLATFORM' AND `cancellation_responsibility_code` IN (
      'PLATFORM_RISK_BLOCK','PLATFORM_COMPLIANCE_BLOCK','PLATFORM_PRICE_ERROR'
    ))
    OR (`cancellation_responsibility_party`='CARRIER' AND `cancellation_responsibility_code` IN (
      'CARRIER_UNSERVICEABLE','CARRIER_CAPACITY_BLOCK','CARRIER_DAMAGE_RISK'
    ))
  );

ALTER TABLE `cloudmold_order_cancellation_saga`
  ADD COLUMN `responsibility_party` varchar(32) NOT NULL DEFAULT 'BUYER' AFTER `reason`,
  ADD COLUMN `responsibility_code` varchar(64) NOT NULL DEFAULT 'BUYER_CHANGED_MIND' AFTER `responsibility_party`,
  ADD CONSTRAINT `ck_cancel_saga_responsibility_party` CHECK (
    `responsibility_party` IN ('MERCHANT','BUYER','PLATFORM','CARRIER')
  ),
  ADD CONSTRAINT `ck_cancel_saga_responsibility_code` CHECK (
    (`responsibility_party`='MERCHANT' AND `responsibility_code` IN (
      'MERCHANT_STOCKOUT','MERCHANT_SELLER_REJECTED','MERCHANT_MANUAL_CANCEL'
    ))
    OR (`responsibility_party`='BUYER' AND `responsibility_code` IN (
      'BUYER_CHANGED_MIND','BUYER_ADDRESS_ERROR','BUYER_PAYMENT_ABANDONED'
    ))
    OR (`responsibility_party`='PLATFORM' AND `responsibility_code` IN (
      'PLATFORM_RISK_BLOCK','PLATFORM_COMPLIANCE_BLOCK','PLATFORM_PRICE_ERROR'
    ))
    OR (`responsibility_party`='CARRIER' AND `responsibility_code` IN (
      'CARRIER_UNSERVICEABLE','CARRIER_CAPACITY_BLOCK','CARRIER_DAMAGE_RISK'
    ))
  );

ALTER TABLE `cloudmold_order_cancellation_saga_history`
  ADD COLUMN `responsibility_party` varchar(32) NOT NULL DEFAULT 'BUYER' AFTER `released_reservation_count`,
  ADD COLUMN `responsibility_code` varchar(64) NOT NULL DEFAULT 'BUYER_CHANGED_MIND' AFTER `responsibility_party`,
  ADD CONSTRAINT `ck_cancel_saga_history_responsibility_party` CHECK (
    `responsibility_party` IN ('MERCHANT','BUYER','PLATFORM','CARRIER')
  ),
  ADD CONSTRAINT `ck_cancel_saga_history_responsibility_code` CHECK (
    (`responsibility_party`='MERCHANT' AND `responsibility_code` IN (
      'MERCHANT_STOCKOUT','MERCHANT_SELLER_REJECTED','MERCHANT_MANUAL_CANCEL'
    ))
    OR (`responsibility_party`='BUYER' AND `responsibility_code` IN (
      'BUYER_CHANGED_MIND','BUYER_ADDRESS_ERROR','BUYER_PAYMENT_ABANDONED'
    ))
    OR (`responsibility_party`='PLATFORM' AND `responsibility_code` IN (
      'PLATFORM_RISK_BLOCK','PLATFORM_COMPLIANCE_BLOCK','PLATFORM_PRICE_ERROR'
    ))
    OR (`responsibility_party`='CARRIER' AND `responsibility_code` IN (
      'CARRIER_UNSERVICEABLE','CARRIER_CAPACITY_BLOCK','CARRIER_DAMAGE_RISK'
    ))
  );
