SELECT package.id package_id,expected.menu_id
FROM system_tenant_package package
CROSS JOIN (
  SELECT 9100000000522 menu_id UNION ALL SELECT 9100000000485 UNION ALL SELECT 9100000000486
) expected
WHERE package.deleted=b'0' AND package.status=0 AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(9100000000463 AS JSON))
  AND NOT JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.menu_id AS JSON));

SELECT role.id role_id,expected.menu_id
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN system_tenant_package package
  ON package.id=tenant.package_id AND package.deleted=b'0' AND package.status=0
CROSS JOIN (
  SELECT 9100000000522 menu_id UNION ALL SELECT 9100000000485 UNION ALL SELECT 9100000000486
) expected
LEFT JOIN system_role_menu grant_row
  ON grant_row.role_id=role.id AND grant_row.tenant_id=role.tenant_id
 AND grant_row.menu_id=expected.menu_id AND grant_row.deleted=b'0'
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(9100000000463 AS JSON))
  AND grant_row.role_id IS NULL;
