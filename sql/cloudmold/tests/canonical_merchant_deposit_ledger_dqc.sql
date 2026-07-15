-- Every result column must be zero.
SELECT 'merchant_deposit_account_conservation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_merchant_deposit_account
WHERE held_amount_minor<>paid_amount_minor-deducted_amount_minor
   OR frozen_amount_minor>held_amount_minor;

SELECT 'merchant_deposit_ledger_arithmetic' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_merchant_deposit_ledger_entry
WHERE held_after_minor<>held_before_minor+held_delta_minor
   OR frozen_after_minor<>frozen_before_minor+frozen_delta_minor
   OR frozen_after_minor>held_after_minor;

SELECT 'merchant_deposit_ledger_version_gap' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT tenant_id,account_id,COUNT(*) AS entry_count,MIN(account_version) AS min_version,
         MAX(account_version) AS max_version
  FROM cloudmold_merchant_deposit_ledger_entry
  GROUP BY tenant_id,account_id
) ledger
JOIN cloudmold_merchant_deposit_account account
  ON account.tenant_id=ledger.tenant_id AND account.account_id=ledger.account_id
WHERE ledger.min_version<>1 OR ledger.max_version<>account.version OR ledger.entry_count<>account.version;

SELECT 'merchant_deposit_account_not_latest_ledger' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_merchant_deposit_account account
JOIN cloudmold_merchant_deposit_ledger_entry ledger
  ON ledger.tenant_id=account.tenant_id AND ledger.account_id=account.account_id
 AND ledger.account_version=account.version
WHERE account.held_amount_minor<>ledger.held_after_minor
   OR account.frozen_amount_minor<>ledger.frozen_after_minor
   OR account.required_amount_minor<>ledger.required_after_minor
   OR account.coverage_status<>ledger.current_coverage_status
   OR account.enforcement_status<>ledger.current_enforcement_status;

SELECT 'merchant_deposit_coverage_threshold' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_merchant_deposit_account
WHERE coverage_status<>CASE
  WHEN CAST(held_amount_minor AS DECIMAL(38,0))*5>=CAST(required_amount_minor AS DECIMAL(38,0))*3
    THEN 'SUFFICIENT'
  WHEN CAST(held_amount_minor AS DECIMAL(38,0))*5>=CAST(required_amount_minor AS DECIMAL(38,0))
    THEN 'BID_RESTRICTED'
  ELSE 'SALES_BLOCKED' END;

SELECT 'merchant_deposit_enforced_sales_block_not_suspended' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_merchant_deposit_account deposit
JOIN cloudmold_merchant_account merchant
  ON merchant.tenant_id=deposit.tenant_id AND merchant.merchant_id=deposit.merchant_id
WHERE deposit.enforcement_status='ENFORCED' AND deposit.coverage_status='SALES_BLOCKED'
  AND merchant.status='ACTIVE';

SELECT 'merchant_deposit_missing_outbox_event' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_merchant_deposit_ledger_entry ledger
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=ledger.tenant_id AND event.event_type='merchant.deposit.ledger_posted'
 AND event.aggregate_id COLLATE utf8mb4_unicode_ci=ledger.account_id
 AND event.aggregate_version=ledger.account_version
WHERE event.event_id IS NULL;
