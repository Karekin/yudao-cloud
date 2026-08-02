SELECT 'supplier_return_reversal_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_return_reversal_operation
WHERE status=10 AND (aggregate_id IS NULL OR result_json IS NULL);

SELECT 'supplier_debit_adjustment_non_atomic_status' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_debit_adjustment
WHERE status<>'POSTED' OR posted_by_principal_id IS NULL OR posted_at IS NULL;

SELECT 'supplier_debit_adjustment_header_line_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_debit_adjustment h
LEFT JOIN (
    SELECT tenant_id,supplier_debit_adjustment_id,
           SUM(gross_reversal_amount_minor) gross_amount_minor,
           SUM(tax_reversal_amount_minor) tax_amount_minor,
           SUM(valuation_reversal_amount_minor) valuation_amount_minor,
           SUM(purchase_price_variance_amount_minor) variance_amount_minor
    FROM cloudmold_finance_supplier_debit_adjustment_line
    GROUP BY tenant_id,supplier_debit_adjustment_id
) l ON l.tenant_id=h.tenant_id AND l.supplier_debit_adjustment_id=h.supplier_debit_adjustment_id
WHERE l.supplier_debit_adjustment_id IS NULL
   OR l.gross_amount_minor<>h.ap_reversal_amount_minor
   OR l.tax_amount_minor<>h.tax_reversal_amount_minor
   OR l.valuation_amount_minor<>h.valuation_reversal_amount_minor
   OR l.variance_amount_minor<>h.purchase_price_variance_amount_minor;

SELECT 'supplier_debit_adjustment_ap_reversal_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_debit_adjustment h
LEFT JOIN (
    SELECT tenant_id,supplier_debit_adjustment_id,SUM(gross_reversal_amount_minor) gross_amount_minor
    FROM cloudmold_finance_supplier_return_ap_reversal
    GROUP BY tenant_id,supplier_debit_adjustment_id
) r ON r.tenant_id=h.tenant_id AND r.supplier_debit_adjustment_id=h.supplier_debit_adjustment_id
WHERE r.supplier_debit_adjustment_id IS NULL OR r.gross_amount_minor<>h.ap_reversal_amount_minor;

SELECT 'supplier_debit_adjustment_missing_posted_journal' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_debit_adjustment h
LEFT JOIN cloudmold_finance_journal_entry j
  ON j.tenant_id=h.tenant_id AND j.journal_entry_id=h.journal_entry_id
WHERE j.journal_entry_id IS NULL
   OR j.status<>'POSTED'
   OR j.source_type<>'SUPPLIER_RETURN'
   OR j.source_id<>h.supplier_debit_adjustment_id;

SELECT 'supplier_return_valuation_effect_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_debit_adjustment_line l
LEFT JOIN cloudmold_finance_inventory_valuation_effect e
  ON e.tenant_id=l.tenant_id AND e.valuation_layer_id=l.valuation_layer_id
 AND e.effect_type='SUPPLIER_RETURN'
LEFT JOIN cloudmold_finance_journal_entry j
  ON j.tenant_id=e.tenant_id AND j.journal_entry_id=e.journal_entry_id
WHERE e.valuation_effect_id IS NULL
   OR e.amount_minor<>l.valuation_reversal_amount_minor
   OR e.quantity<>l.reversal_quantity
   OR j.status<>'POSTED';

SELECT 'supplier_return_ap_application_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_supplier_return_ap_reversal r
LEFT JOIN cloudmold_finance_ap_application a
  ON a.tenant_id=r.tenant_id AND a.source_type='SUPPLIER_RETURN'
 AND a.source_id=r.supplier_debit_adjustment_id AND a.ap_open_item_id=r.ap_open_item_id
WHERE a.ap_application_id IS NULL
   OR a.application_type<>'SETTLE'
   OR a.amount_minor<>r.gross_reversal_amount_minor;
