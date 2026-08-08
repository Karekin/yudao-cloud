SELECT 'sales_contract_reviewer_role_missing' AS violation, COUNT(*) AS violating_rows
FROM system_role tenant_admin
LEFT JOIN system_role reviewer
  ON reviewer.tenant_id=tenant_admin.tenant_id
 AND reviewer.code='sales_contract_reviewer'
 AND reviewer.status=0
 AND reviewer.deleted=b'0'
WHERE tenant_admin.code='tenant_admin'
  AND tenant_admin.status=0
  AND tenant_admin.deleted=b'0'
  AND reviewer.id IS NULL
HAVING COUNT(*) > 0;

SELECT 'sales_contract_reviewer_permission_missing' AS violation, COUNT(*) AS violating_rows
FROM system_role reviewer
CROSS JOIN (
  SELECT 1185 menu_id UNION ALL SELECT 1200 UNION ALL SELECT 1207 UNION ALL SELECT 1222
  UNION ALL SELECT 9100000000180 UNION ALL SELECT 9100000000807 UNION ALL SELECT 9100000000852
) expected
LEFT JOIN system_role_menu rm
  ON rm.role_id=reviewer.id
 AND rm.tenant_id=reviewer.tenant_id
 AND rm.menu_id=expected.menu_id
 AND rm.deleted=b'0'
WHERE reviewer.code='sales_contract_reviewer'
  AND reviewer.status=0
  AND reviewer.deleted=b'0'
  AND rm.menu_id IS NULL
HAVING COUNT(*) > 0;

SELECT 'sales_contract_reviewer_has_command_permission' AS violation, COUNT(*) AS violating_rows
FROM system_role reviewer
JOIN system_role_menu rm
  ON rm.role_id=reviewer.id AND rm.tenant_id=reviewer.tenant_id AND rm.deleted=b'0'
JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0'
WHERE reviewer.code='sales_contract_reviewer'
  AND reviewer.deleted=b'0'
  AND m.permission IN (
    'cloudmold:crm:command',
    'cloudmold:crm:sales-contract:command',
    'cloudmold:finance:receivables:command'
  )
HAVING COUNT(*) > 0;

SELECT 'sales_contract_reviewer_canonical_identity_missing' AS violation, COUNT(*) AS violating_rows
FROM system_user_role ur
JOIN system_role reviewer
  ON reviewer.id=ur.role_id AND reviewer.tenant_id=ur.tenant_id
LEFT JOIN cloudmold_identity_source_identity identity_link
  ON identity_link.tenant_id=ur.tenant_id
 AND identity_link.source_system='SYSTEM'
 AND identity_link.source_type='SYSTEM_ADMIN_USER'
 AND identity_link.source_id=CAST(ur.user_id AS CHAR)
 AND identity_link.status='ACTIVE'
LEFT JOIN cloudmold_identity_principal principal
  ON principal.tenant_id=identity_link.tenant_id
 AND principal.principal_id=identity_link.principal_id
 AND principal.status='ACTIVE'
WHERE reviewer.code='sales_contract_reviewer'
  AND reviewer.status=0
  AND reviewer.deleted=b'0'
  AND ur.deleted=b'0'
  AND (identity_link.source_identity_id IS NULL OR principal.principal_id IS NULL)
HAVING COUNT(*) > 0;
