SELECT run_id
  FROM cloudmold_finance_procure_inventory_reconciliation_run
 WHERE line_count <> matched_count + different_count + missing_count + uncomparable_count;

SELECT line_id
  FROM cloudmold_finance_procure_inventory_reconciliation_line
 WHERE difference_count < 0;

SELECT l.line_id, l.difference_count, COUNT(d.difference_id) AS actual_difference_count
  FROM cloudmold_finance_procure_inventory_reconciliation_line l
  LEFT JOIN cloudmold_finance_procure_inventory_reconciliation_difference d
    ON d.tenant_id=l.tenant_id AND d.line_id=l.line_id
 GROUP BY l.line_id, l.difference_count
HAVING l.difference_count <> COUNT(d.difference_id);

SELECT d.difference_id
  FROM cloudmold_finance_procure_inventory_reconciliation_difference d
  JOIN cloudmold_finance_procure_inventory_reconciliation_line l
    ON l.tenant_id=d.tenant_id AND l.line_id=d.line_id
 WHERE l.match_status='MATCHED';

SELECT line_id
  FROM cloudmold_finance_procure_inventory_reconciliation_line
 WHERE (match_status='MATCHED' AND (responsibility_domain<>'NONE' OR primary_difference_code IS NOT NULL))
    OR (match_status<>'MATCHED' AND (responsibility_domain='NONE' OR primary_difference_code IS NULL));

SELECT tenant_id, run_id, line_key, COUNT(*) AS duplicate_count
  FROM cloudmold_finance_procure_inventory_reconciliation_line
 GROUP BY tenant_id, run_id, line_key
HAVING COUNT(*) > 1;
