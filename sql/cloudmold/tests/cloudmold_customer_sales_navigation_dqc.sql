SELECT 'crm_menu_missing_or_wrong_component' AS violation, COUNT(*) AS violating_rows
FROM (
  SELECT 9100000000800 id,'cloudmold/crm/workbench/index' component
  UNION ALL SELECT 9100000000801,'cloudmold/crm/clue/index'
  UNION ALL SELECT 9100000000802,'cloudmold/crm/customer/index'
  UNION ALL SELECT 9100000000803,'cloudmold/crm/contact/index'
  UNION ALL SELECT 9100000000804,'cloudmold/crm/customer/pool/index'
  UNION ALL SELECT 9100000000805,'cloudmold/crm/followup/index'
  UNION ALL SELECT 9100000000806,'cloudmold/crm/business/index'
  UNION ALL SELECT 9100000000807,'cloudmold/crm/sales-contract/index'
) expected LEFT JOIN system_menu actual ON actual.id=expected.id AND actual.deleted=b'0'
WHERE actual.id IS NULL OR actual.parent_id<>9100000000180 OR actual.component<>expected.component
HAVING COUNT(*) > 0;

SELECT 'crm_legacy_menu_exposed' AS violation, COUNT(*) AS violating_rows
FROM system_menu
WHERE deleted=b'0' AND creator='CloudMold' AND parent_id=9100000000180
  AND (component LIKE 'crm/%' OR component LIKE 'cloudmold/crm/product%'
       OR component LIKE 'cloudmold/crm/receivable%')
HAVING COUNT(*) > 0;

SELECT 'crm_permission_missing' AS violation, COUNT(*) AS violating_rows
FROM (
  SELECT 'cloudmold:crm:query' permission
  UNION ALL SELECT 'cloudmold:crm:command'
  UNION ALL SELECT 'cloudmold:crm:sales-contract:query'
  UNION ALL SELECT 'cloudmold:crm:sales-contract:command'
  UNION ALL SELECT 'cloudmold:finance:receivables:query'
  UNION ALL SELECT 'cloudmold:finance:receivables:command'
) expected
LEFT JOIN system_menu actual ON actual.permission=expected.permission AND actual.deleted=b'0'
WHERE actual.id IS NULL
HAVING COUNT(*) > 0;

SELECT 'crm_customer_sales_role_missing' AS violation, COUNT(*) AS violating_rows
FROM system_role tenant_admin
LEFT JOIN system_role sales_role
  ON sales_role.tenant_id=tenant_admin.tenant_id
 AND sales_role.code='customer_sales_operator'
 AND sales_role.status=0
 AND sales_role.deleted=b'0'
WHERE tenant_admin.code='tenant_admin' AND tenant_admin.status=0 AND tenant_admin.deleted=b'0'
  AND sales_role.id IS NULL
HAVING COUNT(*) > 0;

SELECT 'crm_tenant_admin_or_sales_role_menu_missing' AS violation, COUNT(*) AS violating_rows
FROM system_role r
CROSS JOIN (
  SELECT 9100000000180 menu_id UNION ALL SELECT 9100000000800 UNION ALL SELECT 9100000000801
  UNION ALL SELECT 9100000000802 UNION ALL SELECT 9100000000803 UNION ALL SELECT 9100000000804
  UNION ALL SELECT 9100000000805 UNION ALL SELECT 9100000000806 UNION ALL SELECT 9100000000807
  UNION ALL SELECT 9100000000850 UNION ALL SELECT 9100000000851 UNION ALL SELECT 9100000000852
  UNION ALL SELECT 9100000000853 UNION ALL SELECT 9100000000854 UNION ALL SELECT 9100000000855
) expected
LEFT JOIN system_role_menu rm
  ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.menu_id=expected.menu_id AND rm.deleted=b'0'
WHERE r.code IN ('tenant_admin','customer_sales_operator') AND r.status=0 AND r.deleted=b'0'
  AND rm.menu_id IS NULL
HAVING COUNT(*) > 0;
