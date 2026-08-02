-- V158 supply-chain information architecture quality gates.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 'missing_or_mismatched_new_menu' AS check_name,COUNT(*) AS violation_count
FROM (
  SELECT 9100000000490 id,'供销计划' name,1 type,9100000000140 parent_id,'planning' path,'' component
  UNION ALL SELECT 9100000000491,'采购执行',1,9100000000140,'procurement-execution',''
  UNION ALL SELECT 9100000000492,'仓储执行',1,9100000000140,'warehouse-execution',''
  UNION ALL SELECT 9100000000493,'库存控制',1,9100000000140,'inventory-control',''
  UNION ALL SELECT 9100000000494,'销量与需求计划',2,9100000000490,'demand-plans','cloudmold/supply-chain/index'
  UNION ALL SELECT 9100000000495,'供应计划与情景',2,9100000000490,'supply-plans','cloudmold/supply-chain/index'
  UNION ALL SELECT 9100000000496,'补货计划',2,9100000000490,'replenishments','cloudmold/supply-chain/index'
  UNION ALL SELECT 9100000000497,'采购申请',2,9100000000491,'purchase-requisitions','cloudmold/procurement/index'
  UNION ALL SELECT 9100000000498,'寻源与定标',2,9100000000491,'sourcing','cloudmold/procurement/index'
  UNION ALL SELECT 9100000000499,'采购订单',2,9100000000491,'purchase-orders','cloudmold/procurement/index'
  UNION ALL SELECT 9100000000503,'库存余额',2,9100000000493,'balances','cloudmold/inventory/index'
  UNION ALL SELECT 9100000000504,'预占与分配',2,9100000000493,'reservations','cloudmold/inventory/index'
  UNION ALL SELECT 9100000000505,'库存流水',2,9100000000493,'ledger','cloudmold/inventory/index'
  UNION ALL SELECT 9100000000506,'库存健康',2,9100000000493,'inventory-health','cloudmold/supply-chain/index'
) expected
LEFT JOIN system_menu actual ON actual.id=expected.id
WHERE actual.id IS NULL OR actual.name<>expected.name OR actual.type<>expected.type
   OR actual.parent_id<>expected.parent_id OR actual.path<>expected.path
   OR actual.component<>expected.component OR actual.status<>0
   OR actual.visible<>b'1' OR actual.deleted<>b'0';

SELECT 'mismatched_reparented_page' AS check_name,COUNT(*) AS violation_count
FROM (
  SELECT 9100000000460 id,9100000000491 parent_id,'采购全景' name,1 sort
  UNION ALL SELECT 9100000000060,9100000000492,'仓库与库位',1
  UNION ALL SELECT 9100000000461,9100000000492,'采购收货与上架',2
  UNION ALL SELECT 9100000000450,9100000000492,'库存调拨',3
  UNION ALL SELECT 9100000000010,9100000000493,'库存总览',1
) expected
LEFT JOIN system_menu actual ON actual.id=expected.id
WHERE actual.id IS NULL OR actual.parent_id<>expected.parent_id
   OR actual.name<>expected.name OR actual.sort<>expected.sort
   OR actual.status<>0 OR actual.visible<>b'1' OR actual.deleted<>b'0';

SELECT 'eligible_package_missing_domain_menu' AS check_name,COUNT(*) AS violation_count
FROM system_tenant_package package
CROSS JOIN (
  SELECT 9100000000490 id UNION ALL SELECT 9100000000491 UNION ALL SELECT 9100000000492
  UNION ALL SELECT 9100000000493 UNION ALL SELECT 9100000000494 UNION ALL SELECT 9100000000495
  UNION ALL SELECT 9100000000496 UNION ALL SELECT 9100000000497 UNION ALL SELECT 9100000000498
  UNION ALL SELECT 9100000000499 UNION ALL SELECT 9100000000503 UNION ALL SELECT 9100000000504
  UNION ALL SELECT 9100000000505 UNION ALL SELECT 9100000000506
) expected
WHERE package.deleted=b'0' AND package.status=0 AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(9100000000000 AS JSON))
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(9100000000140 AS JSON))
  AND NOT JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.id AS JSON));

SELECT 'tenant_admin_missing_domain_menu' AS check_name,COUNT(*) AS violation_count
FROM system_role role
JOIN system_tenant tenant ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN system_tenant_package package ON package.id=tenant.package_id AND package.deleted=b'0' AND package.status=0
CROSS JOIN (
  SELECT 9100000000490 id UNION ALL SELECT 9100000000491 UNION ALL SELECT 9100000000492
  UNION ALL SELECT 9100000000493 UNION ALL SELECT 9100000000494 UNION ALL SELECT 9100000000495
  UNION ALL SELECT 9100000000496 UNION ALL SELECT 9100000000497 UNION ALL SELECT 9100000000498
  UNION ALL SELECT 9100000000499 UNION ALL SELECT 9100000000503 UNION ALL SELECT 9100000000504
  UNION ALL SELECT 9100000000505 UNION ALL SELECT 9100000000506
) expected
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(9100000000140 AS JSON))
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu grant_row
    WHERE grant_row.role_id=role.id AND grant_row.menu_id=expected.id
      AND grant_row.tenant_id=role.tenant_id AND grant_row.deleted=b'0'
  );

SELECT 'business_role_missing_reparented_parent' AS check_name,COUNT(*) AS violation_count
FROM system_role_menu child_grant
JOIN system_role role
  ON role.id=child_grant.role_id AND role.tenant_id=child_grant.tenant_id
 AND role.deleted=b'0' AND role.status=0
JOIN (
  SELECT 9100000000460 child_menu_id,9100000000491 parent_menu_id
  UNION ALL SELECT 9100000000060,9100000000492
  UNION ALL SELECT 9100000000461,9100000000492
  UNION ALL SELECT 9100000000450,9100000000492
  UNION ALL SELECT 9100000000010,9100000000493
) mapping ON mapping.child_menu_id=child_grant.menu_id
WHERE child_grant.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu parent_grant
    WHERE parent_grant.role_id=child_grant.role_id
      AND parent_grant.tenant_id=child_grant.tenant_id
      AND parent_grant.menu_id=mapping.parent_menu_id
      AND parent_grant.deleted=b'0'
  );

SELECT 'merchant_page_moved_out_of_merchant_domain' AS check_name,COUNT(*) AS violation_count
FROM system_menu merchant_page
WHERE merchant_page.id=9100000000050
  AND (merchant_page.parent_id<>9100000000110 OR merchant_page.name<>'商家管理'
       OR merchant_page.path<>'merchants' OR merchant_page.deleted<>b'0');
