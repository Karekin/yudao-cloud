ALTER TABLE `cloudmold_finance_posting_rule`
  DROP CONSTRAINT `ck_finance_posting_rule_source`,
  ADD CONSTRAINT `ck_finance_posting_rule_source` CHECK (`source_type` IN (
    'QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT','SUPPLIER_RETURN',
    'STOCK_COUNT_ADJUSTMENT','INVENTORY_SCRAP'));

ALTER TABLE `cloudmold_finance_journal_entry`
  DROP CONSTRAINT `ck_finance_journal_source`,
  ADD CONSTRAINT `ck_finance_journal_source` CHECK (`source_type` IN (
    'SETTLEMENT_BATCH','QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT',
    'SUPPLIER_RETURN','JOURNAL_REVERSAL','STOCK_COUNT_ADJUSTMENT','INVENTORY_SCRAP'));
