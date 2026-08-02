-- Expected result: every violation_count is zero.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 'supply_chain_document_menu_contract' AS check_name,COUNT(*) AS violation_count
FROM (
  SELECT 9100000000510 id,9100000000491 parent_id,'supplier-returns' path,
         'cloudmold:warehouse:supplier-return:query' permission
  UNION ALL SELECT 9100000000513,9100000000492,'stock-counts','cloudmold:warehouse:query'
  UNION ALL SELECT 9100000000516,9100000000493,'safety-stock-policies','cloudmold:supply-planning:policy:query'
  UNION ALL SELECT 9100000000519,9100000000493,'inventory-health-snapshots','cloudmold:supply-planning:health-snapshot:query'
) expected
LEFT JOIN system_menu actual
  ON actual.id=expected.id AND actual.parent_id=expected.parent_id
 AND actual.path=expected.path AND actual.permission=expected.permission
 AND actual.status=0 AND actual.visible=b'1' AND actual.deleted=b'0'
WHERE actual.id IS NULL;

SELECT 'supply_chain_document_button_contract' AS check_name,COUNT(*) AS violation_count
FROM (
  SELECT 9100000000511 id,9100000000510 parent_id,'cloudmold:warehouse:supplier-return:query' permission
  UNION ALL SELECT 9100000000512,9100000000510,'cloudmold:warehouse:supplier-return:command'
  UNION ALL SELECT 9100000000514,9100000000513,'cloudmold:warehouse:query'
  UNION ALL SELECT 9100000000515,9100000000513,'cloudmold:warehouse:command'
  UNION ALL SELECT 9100000000517,9100000000516,'cloudmold:supply-planning:policy:query'
  UNION ALL SELECT 9100000000518,9100000000516,'cloudmold:supply-planning:policy:command'
  UNION ALL SELECT 9100000000520,9100000000519,'cloudmold:supply-planning:health-snapshot:query'
  UNION ALL SELECT 9100000000521,9100000000519,'cloudmold:supply-planning:health-snapshot:command'
) expected
LEFT JOIN system_menu actual
  ON actual.id=expected.id AND actual.parent_id=expected.parent_id
 AND actual.type=3 AND actual.permission=expected.permission AND actual.deleted=b'0'
WHERE actual.id IS NULL;

SELECT 'supply_chain_document_duplicate_route' AS check_name,COUNT(*) AS violation_count
FROM system_menu candidate
JOIN system_menu duplicate
  ON duplicate.parent_id=candidate.parent_id AND duplicate.path=candidate.path
 AND duplicate.id<>candidate.id AND duplicate.deleted=b'0'
WHERE candidate.id IN (9100000000510,9100000000513,9100000000516,9100000000519)
  AND candidate.deleted=b'0';

SELECT 'supply_chain_document_tenant_admin_grant' AS check_name,COUNT(*) AS violation_count
FROM system_role role
JOIN system_tenant tenant ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN system_tenant_package package ON package.id=tenant.package_id AND package.deleted=b'0' AND package.status=0
CROSS JOIN (
  SELECT 9100000000510 id UNION ALL SELECT 9100000000511 UNION ALL SELECT 9100000000512
  UNION ALL SELECT 9100000000513 UNION ALL SELECT 9100000000514 UNION ALL SELECT 9100000000515
  UNION ALL SELECT 9100000000516 UNION ALL SELECT 9100000000517 UNION ALL SELECT 9100000000518
  UNION ALL SELECT 9100000000519 UNION ALL SELECT 9100000000520 UNION ALL SELECT 9100000000521
) expected
LEFT JOIN system_role_menu grant_row
  ON grant_row.role_id=role.id AND grant_row.tenant_id=role.tenant_id
 AND grant_row.menu_id=expected.id AND grant_row.deleted=b'0'
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(9100000000140 AS JSON))
  AND grant_row.role_id IS NULL;
