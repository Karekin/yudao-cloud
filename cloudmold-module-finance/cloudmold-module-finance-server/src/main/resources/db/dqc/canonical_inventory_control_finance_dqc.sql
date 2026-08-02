SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 'inventory_control_posting_duplicate_source' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, source_type, source_reference_id
    FROM cloudmold_finance_inventory_control_posting
    GROUP BY tenant_id, source_type, source_reference_id
    HAVING COUNT(*) > 1
) t;

SELECT 'inventory_control_posting_missing_journal' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_inventory_control_posting p
LEFT JOIN cloudmold_finance_journal_entry j
  ON j.tenant_id=p.tenant_id AND j.journal_entry_id=p.journal_entry_id
WHERE j.journal_entry_id IS NULL;

SELECT 'inventory_control_posting_allocation_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_inventory_control_posting p
LEFT JOIN (
    SELECT tenant_id, inventory_control_posting_id,
           SUM(allocated_cost_amount_minor) AS total_cost
    FROM cloudmold_finance_inventory_control_posting_allocation
    GROUP BY tenant_id, inventory_control_posting_id
) a
  ON a.tenant_id=p.tenant_id AND a.inventory_control_posting_id=p.inventory_control_posting_id
WHERE COALESCE(a.total_cost, -1) <> p.total_amount_minor;

SELECT 'inventory_control_reversed_without_reversal_journal' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_inventory_control_posting
WHERE status='REVERSED' AND reversal_journal_entry_id IS NULL;

SELECT 'stock_count_gain_basis_approved_without_checker' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_stock_count_gain_basis
WHERE status='APPROVED'
  AND (approved_by_principal_id IS NULL OR approved_at IS NULL OR approved_by_principal_id=submitted_by_principal_id);

SELECT 'stock_count_gain_basis_cost_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_finance_stock_count_gain_basis
WHERE total_cost_amount_minor <> ROUND(gain_quantity * unit_cost_amount_minor, 0);
