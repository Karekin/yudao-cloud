SELECT expected.id,expected.name,expected.permission,expected.parent_id
FROM (
  SELECT 9100000000523 id,'库存库龄与效期' name,'cloudmold:inventory:aging-snapshot:query' permission,9100000000493 parent_id
  UNION ALL SELECT 9100000000524,'库存库龄与效期查询','cloudmold:inventory:aging-snapshot:query',9100000000523
  UNION ALL SELECT 9100000000525,'库存库龄快照生成','cloudmold:inventory:aging-snapshot:command',9100000000523
  UNION ALL SELECT 9100000000526,'财务影响与凭证追踪','cloudmold:finance:financial-impact:query',9100000000170
  UNION ALL SELECT 9100000000527,'财务影响查询','cloudmold:finance:financial-impact:query',9100000000526
  UNION ALL SELECT 9100000000528,'库存控制财务操作','cloudmold:finance:inventory-control:command',9100000000526
) expected
LEFT JOIN system_menu actual
  ON actual.id=expected.id AND actual.name=expected.name AND actual.permission=expected.permission
 AND actual.parent_id=expected.parent_id AND actual.status=0 AND actual.deleted=b'0'
WHERE actual.id IS NULL;

SELECT package.id package_id,expected.menu_id
FROM system_tenant_package package
JOIN (
  SELECT 9100000000523 menu_id,9100000000493 required_parent_id
  UNION ALL SELECT 9100000000524,9100000000493
  UNION ALL SELECT 9100000000525,9100000000493
  UNION ALL SELECT 9100000000526,9100000000170
  UNION ALL SELECT 9100000000527,9100000000170
  UNION ALL SELECT 9100000000528,9100000000170
) expected ON JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.required_parent_id AS JSON))
WHERE package.deleted=b'0' AND package.status=0 AND JSON_VALID(package.menu_ids)
  AND NOT JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.menu_id AS JSON));

SELECT role.id role_id,expected.menu_id
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN system_tenant_package package
  ON package.id=tenant.package_id AND package.deleted=b'0' AND package.status=0
JOIN (
  SELECT 9100000000523 menu_id UNION ALL SELECT 9100000000524 UNION ALL SELECT 9100000000525
  UNION ALL SELECT 9100000000526 UNION ALL SELECT 9100000000527 UNION ALL SELECT 9100000000528
) expected ON JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.menu_id AS JSON))
LEFT JOIN system_role_menu grant_row
  ON grant_row.role_id=role.id AND grant_row.tenant_id=role.tenant_id
 AND grant_row.menu_id=expected.menu_id AND grant_row.deleted=b'0'
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND grant_row.role_id IS NULL;
