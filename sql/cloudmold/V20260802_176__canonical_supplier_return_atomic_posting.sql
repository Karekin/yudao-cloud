-- Finalize supplier-return finance semantics as one atomic posted transaction.
-- This migration fails closed if an interim PREPARED adjustment was created.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v176_guard;
CREATE TEMPORARY TABLE cloudmold_v176_guard (
    guard_value VARCHAR(5) NOT NULL,
    CONSTRAINT ck_cloudmold_v176_guard CHECK (guard_value='OK')
);

INSERT INTO cloudmold_v176_guard (guard_value)
SELECT CASE WHEN COUNT(*)=0 THEN 'OK' ELSE 'FAIL' END
FROM cloudmold_finance_supplier_debit_adjustment;

ALTER TABLE cloudmold_finance_supplier_debit_adjustment
    DROP FOREIGN KEY fk_finance_supplier_debit_adjustment_journal,
    DROP CHECK ck_finance_supplier_debit_adjustment_amounts,
    CHANGE COLUMN prepared_journal_entry_id journal_entry_id VARCHAR(128) NOT NULL,
    CHANGE COLUMN prepared_by_principal_id posted_by_principal_id VARCHAR(128) NOT NULL,
    ADD COLUMN posted_at DATETIME(6) NOT NULL AFTER posted_by_principal_id;

ALTER TABLE cloudmold_finance_supplier_debit_adjustment
    ADD CONSTRAINT fk_finance_supplier_debit_adjustment_journal
        FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    ADD CONSTRAINT ck_finance_supplier_debit_adjustment_amounts CHECK (
        ap_reversal_amount_minor > 0
        AND tax_reversal_amount_minor >= 0
        AND valuation_reversal_amount_minor > 0
        AND status='POSTED'
    );

DROP TEMPORARY TABLE cloudmold_v176_guard;
