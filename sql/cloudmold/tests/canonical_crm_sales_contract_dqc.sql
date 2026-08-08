SELECT 'crm_sales_contract_amount' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_sales_contract c
LEFT JOIN (
  SELECT tenant_id,sales_contract_id,SUM(line_amount_minor) item_total
  FROM cloudmold_sales_contract_item GROUP BY tenant_id,sales_contract_id
) i ON i.tenant_id=c.tenant_id AND i.sales_contract_id=c.sales_contract_id
WHERE c.total_amount_minor<=0 OR c.total_amount_minor<>COALESCE(i.item_total,0)
   OR (c.expires_on IS NOT NULL AND c.expires_on<c.effective_date)
HAVING COUNT(*) > 0;

SELECT 'crm_sales_contract_bpm_binding' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_sales_contract
WHERE (status='PENDING_APPROVAL' AND approval_process_instance_id IS NULL)
   OR (status='DRAFT' AND approval_process_instance_id IS NOT NULL)
HAVING COUNT(*) > 0;

SELECT 'crm_sales_contract_catalog_authority' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_sales_contract_item
WHERE canonical_sku_id IS NULL OR canonical_sku_id=''
HAVING COUNT(*) > 0;
