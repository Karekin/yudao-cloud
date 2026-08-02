SELECT 'finance_period_state_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_accounting_period
WHERE period_end < period_start
   OR (status = 'OPEN' AND (closed_by_principal_id IS NOT NULL OR close_evidence_sha256 IS NOT NULL OR closed_at IS NOT NULL))
   OR (status = 'CLOSED' AND (closed_by_principal_id IS NULL
       OR close_evidence_sha256 NOT REGEXP '^[0-9a-f]{64}$'
       OR closed_at IS NULL));

SELECT 'finance_statement_arithmetic_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_channel_statement
WHERE net_settlement_amount_minor <> gross_amount_minor - refund_amount_minor - fee_amount_minor
   OR difference_amount_minor <> net_settlement_amount_minor - expected_business_net_amount_minor;

SELECT 'finance_difference_resolution_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_reconciliation_difference
WHERE difference_amount_minor = 0
   OR (status = 'OPEN' AND (adjustment_amount_minor IS NOT NULL
       OR resolution_type IS NOT NULL
       OR resolution_evidence_sha256 IS NOT NULL
       OR resolved_by_principal_id IS NOT NULL
       OR resolved_at IS NOT NULL))
   OR (status = 'RESOLVED' AND (adjustment_amount_minor <> difference_amount_minor
       OR resolution_type NOT IN ('CHANNEL_ADJUSTMENT','BUSINESS_ADJUSTMENT','MANUAL_EVIDENCE')
       OR resolution_evidence_sha256 NOT REGEXP '^[0-9a-f]{64}$'
       OR resolved_by_principal_id IS NULL
       OR resolved_at IS NULL));

SELECT 'finance_settlement_evidence_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_settlement_batch
WHERE (status = 'PREPARED' AND (settled_amount_minor IS NOT NULL
       OR bank_reference IS NOT NULL
       OR settlement_evidence_sha256 IS NOT NULL
       OR settled_by_principal_id IS NOT NULL
       OR settled_at IS NOT NULL))
   OR (status = 'SETTLED' AND (settled_amount_minor <> expected_amount_minor
       OR bank_reference IS NULL
       OR settlement_evidence_sha256 NOT REGEXP '^[0-9a-f]{64}$'
       OR settled_by_principal_id IS NULL
       OR settled_at IS NULL));

SELECT 'finance_journal_balance_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_journal_entry
WHERE debit_total_minor <= 0
   OR debit_total_minor <> credit_total_minor
   OR source_type <> 'SETTLEMENT_BATCH'
   OR evidence_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR (status = 'PREPARED' AND (posted_by_principal_id IS NOT NULL OR posted_at IS NOT NULL))
   OR (status = 'POSTED' AND (posted_by_principal_id IS NULL OR posted_at IS NULL));

SELECT 'finance_period_reference_violation' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT statements.tenant_id, statements.period_id
    FROM cloudmold_finance_channel_statement statements
    LEFT JOIN cloudmold_finance_accounting_period periods
      ON periods.tenant_id = statements.tenant_id AND periods.period_id = statements.period_id
    WHERE periods.period_id IS NULL
    UNION ALL
    SELECT settlements.tenant_id, settlements.period_id
    FROM cloudmold_finance_settlement_batch settlements
    LEFT JOIN cloudmold_finance_accounting_period periods
      ON periods.tenant_id = settlements.tenant_id AND periods.period_id = settlements.period_id
    WHERE periods.period_id IS NULL
    UNION ALL
    SELECT journals.tenant_id, journals.period_id
    FROM cloudmold_finance_journal_entry journals
    LEFT JOIN cloudmold_finance_accounting_period periods
      ON periods.tenant_id = journals.tenant_id AND periods.period_id = journals.period_id
    WHERE periods.period_id IS NULL
) orphan_period_reference;
