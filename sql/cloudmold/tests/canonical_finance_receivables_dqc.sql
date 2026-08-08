SELECT 'finance_receivable_plan_balance' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_finance_receivable_plan
WHERE planned_amount_minor <= 0 OR allocated_amount_minor < 0 OR allocated_amount_minor > planned_amount_minor
HAVING COUNT(*) > 0;

SELECT 'finance_receipt_balance' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_finance_receipt
WHERE receipt_amount_minor <= 0 OR allocated_amount_minor < 0 OR allocated_amount_minor > receipt_amount_minor
HAVING COUNT(*) > 0;

SELECT 'finance_receivable_currency_mismatch' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_finance_receipt_allocation a
JOIN cloudmold_finance_receipt r ON r.tenant_id=a.tenant_id AND r.receipt_id=a.receipt_id
JOIN cloudmold_finance_receivable_plan p ON p.tenant_id=a.tenant_id AND p.receivable_plan_id=a.receivable_plan_id
WHERE a.currency_code<>r.currency_code OR a.currency_code<>p.currency_code
   OR a.customer_id<>r.customer_id OR a.customer_id<>p.customer_id
   OR a.sales_contract_id<>r.sales_contract_id OR a.sales_contract_id<>p.sales_contract_id
HAVING COUNT(*) > 0;
