-- Publish the complete CloudMold supply-chain information architecture for the
-- capabilities that already have canonical models and APIs. Missing warehouse
-- documents (supplier return, count and scrap) are deliberately not represented
-- by empty routes; they receive menu nodes only with their future aggregates.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v158_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v158_menu_contract (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  type TINYINT NOT NULL,
  sort INT NOT NULL,
  parent_id BIGINT NOT NULL,
  path VARCHAR(200) NOT NULL,
  icon VARCHAR(100) NOT NULL,
  component VARCHAR(255) NULL,
  component_name VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cloudmold_v158_menu_contract
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name)
VALUES
  (9100000000490,'供销计划','',1,2,9100000000140,'planning','lucide:chart-no-axes-combined','',NULL),
  (9100000000491,'采购执行','',1,3,9100000000140,'procurement-execution','lucide:clipboard-check','',NULL),
  (9100000000492,'仓储执行','',1,4,9100000000140,'warehouse-execution','lucide:warehouse','',NULL),
  (9100000000493,'库存控制','',1,5,9100000000140,'inventory-control','lucide:shield-check','',NULL),

  (9100000000494,'销量与需求计划','cloudmold:supply-planning:query',2,1,9100000000490,'demand-plans','lucide:chart-line','cloudmold/supply-chain/index','CloudMoldDemandPlans'),
  (9100000000495,'供应计划与情景','cloudmold:supply-planning:query',2,2,9100000000490,'supply-plans','lucide:git-branch','cloudmold/supply-chain/index','CloudMoldSupplyPlans'),
  (9100000000496,'补货计划','cloudmold:supply-planning:query',2,3,9100000000490,'replenishments','lucide:package-plus','cloudmold/supply-chain/index','CloudMoldReplenishments'),

  (9100000000497,'采购申请','cloudmold:procurement:requisition:query',2,2,9100000000491,'purchase-requisitions','lucide:file-plus-2','cloudmold/procurement/index','CloudMoldPurchaseRequisitions'),
  (9100000000498,'寻源与定标','cloudmold:procurement:sourcing:query',2,3,9100000000491,'sourcing','lucide:handshake','cloudmold/procurement/index','CloudMoldProcurementSourcing'),
  (9100000000499,'采购订单','cloudmold:procurement:order:query',2,4,9100000000491,'purchase-orders','lucide:clipboard-list','cloudmold/procurement/index','CloudMoldPurchaseOrders'),

  (9100000000503,'库存余额','cloudmold:inventory:query',2,2,9100000000493,'balances','lucide:boxes','cloudmold/inventory/index','CloudMoldInventoryBalances'),
  (9100000000504,'预占与分配','cloudmold:inventory:query',2,3,9100000000493,'reservations','lucide:lock-keyhole','cloudmold/inventory/index','CloudMoldInventoryReservations'),
  (9100000000505,'库存流水','cloudmold:inventory:query',2,4,9100000000493,'ledger','lucide:notebook-tabs','cloudmold/inventory/index','CloudMoldInventoryLedger'),
  (9100000000506,'库存健康','cloudmold:supply-planning:query',2,5,9100000000493,'inventory-health','lucide:heart-pulse','cloudmold/supply-chain/index','CloudMoldInventoryHealth');

DROP TEMPORARY TABLE IF EXISTS cloudmold_v158_guard;
CREATE TEMPORARY TABLE cloudmold_v158_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v158_guard CHECK (guard_value = 'OK')
);

INSERT INTO cloudmold_v158_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu actual
JOIN cloudmold_v158_menu_contract expected ON expected.id = actual.id
WHERE NOT (
  actual.name = expected.name
  AND actual.permission = expected.permission
  AND actual.type = expected.type
  AND actual.sort = expected.sort
  AND actual.parent_id = expected.parent_id
  AND actual.path = expected.path
  AND actual.icon = expected.icon
  AND actual.component <=> expected.component
  AND actual.component_name <=> expected.component_name
  AND actual.status = 0
  AND actual.visible = b'1'
  AND actual.keep_alive = b'1'
  AND actual.always_show = b'1'
  AND actual.creator = 'CloudMold'
  AND actual.deleted = b'0'
);

INSERT INTO cloudmold_v158_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 4 THEN 'OK' ELSE 'FAIL' END
FROM system_menu parent
WHERE parent.id IN (9100000000140,9100000000010,9100000000060,9100000000460)
  AND parent.status = 0
  AND parent.deleted = b'0';

INSERT INTO cloudmold_v158_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu actual
JOIN cloudmold_v158_menu_contract expected
  ON actual.parent_id = expected.parent_id
 AND actual.path = expected.path
 AND actual.id <> expected.id
WHERE actual.deleted = b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT expected.id,expected.name,expected.permission,expected.type,expected.sort,
       expected.parent_id,expected.path,expected.icon,expected.component,expected.component_name,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-supply-chain-domain-navigation',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v158_menu_contract expected
LEFT JOIN system_menu actual ON actual.id = expected.id
WHERE actual.id IS NULL;

-- Move the already authoritative pages beneath their final bounded-context menus.
UPDATE system_menu SET parent_id=9100000000491,name='采购全景',sort=1,
  updater='CloudMold:canonical-supply-chain-domain-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000460 AND deleted=b'0';
UPDATE system_menu SET parent_id=9100000000492,name='仓库与库位',sort=1,
  updater='CloudMold:canonical-supply-chain-domain-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000060 AND deleted=b'0';
UPDATE system_menu SET parent_id=9100000000492,name='采购收货与上架',sort=2,
  updater='CloudMold:canonical-supply-chain-domain-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000461 AND deleted=b'0';
UPDATE system_menu SET parent_id=9100000000492,name='库存调拨',sort=3,
  updater='CloudMold:canonical-supply-chain-domain-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000450 AND deleted=b'0';
UPDATE system_menu SET parent_id=9100000000493,name='库存总览',path='inventory',sort=1,
  updater='CloudMold:canonical-supply-chain-domain-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000010 AND creator='CloudMold' AND deleted=b'0';

-- Repair the merchant page if an earlier pre-release execution of V158 moved
-- it under Inventory Control. Menu 0050 is Merchant, never Inventory.
UPDATE system_menu SET parent_id=9100000000110,name='商家管理',path='merchants',sort=1,
  updater='CloudMold:canonical-supply-chain-domain-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000050 AND creator='CloudMold' AND deleted=b'0';

DROP TEMPORARY TABLE IF EXISTS cloudmold_v158_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v158_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO cloudmold_v158_eligible_package (package_id)
SELECT id FROM system_tenant_package
WHERE deleted=b'0' AND status=0 AND JSON_VALID(menu_ids)
  AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(9100000000000 AS JSON))
  AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(9100000000140 AS JSON));

DROP TEMPORARY TABLE IF EXISTS cloudmold_v158_package_menu;
CREATE TEMPORARY TABLE cloudmold_v158_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id,menu_id)
) ENGINE=InnoDB;
INSERT IGNORE INTO cloudmold_v158_package_menu (package_id,menu_id)
SELECT eligible.package_id,existing.menu_id
FROM cloudmold_v158_eligible_package eligible
JOIN system_tenant_package package ON package.id=eligible.package_id
JOIN JSON_TABLE(CAST(package.menu_ids AS JSON),'$[*]' COLUMNS(menu_id BIGINT PATH '$')) existing;
INSERT IGNORE INTO cloudmold_v158_package_menu (package_id,menu_id)
SELECT eligible.package_id,expected.id
FROM cloudmold_v158_eligible_package eligible
CROSS JOIN cloudmold_v158_menu_contract expected;
UPDATE system_tenant_package package
JOIN (
  SELECT package_id,JSON_ARRAYAGG(menu_id) menu_ids
  FROM cloudmold_v158_package_menu GROUP BY package_id
) merged ON merged.package_id=package.id
SET package.menu_ids=CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater='CloudMold:canonical-supply-chain-domain-navigation',
    package.update_time=UTC_TIMESTAMP(6);

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT role.id,expected.id,role.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-supply-chain-domain-navigation',UTC_TIMESTAMP(6),b'0'
FROM system_role role
JOIN system_tenant tenant ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN cloudmold_v158_eligible_package eligible ON eligible.package_id=tenant.package_id
CROSS JOIN cloudmold_v158_menu_contract expected
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu current
    WHERE current.role_id=role.id AND current.menu_id=expected.id
      AND current.tenant_id=role.tenant_id AND current.deleted=b'0'
  );

-- Reparenting an already granted page must also grant its new canonical parent
-- to the same business role. This preserves the original least-privilege page
-- grant; it does not grant any newly introduced planning or execution page.
DROP TEMPORARY TABLE IF EXISTS cloudmold_v158_role_parent_grant;
CREATE TEMPORARY TABLE cloudmold_v158_role_parent_grant (
  role_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id,tenant_id,menu_id)
) ENGINE=InnoDB;
INSERT IGNORE INTO cloudmold_v158_role_parent_grant (role_id,tenant_id,menu_id)
SELECT DISTINCT existing.role_id,existing.tenant_id,mapping.parent_menu_id
FROM system_role_menu existing
JOIN (
  SELECT 9100000000460 child_menu_id,9100000000491 parent_menu_id
  UNION ALL SELECT 9100000000060,9100000000492
  UNION ALL SELECT 9100000000461,9100000000492
  UNION ALL SELECT 9100000000450,9100000000492
  UNION ALL SELECT 9100000000010,9100000000493
) mapping ON mapping.child_menu_id=existing.menu_id
JOIN system_role role
  ON role.id=existing.role_id AND role.tenant_id=existing.tenant_id
 AND role.deleted=b'0' AND role.status=0
WHERE existing.deleted=b'0';

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT required.role_id,required.menu_id,required.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-supply-chain-domain-navigation',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v158_role_parent_grant required
WHERE NOT EXISTS (
  SELECT 1 FROM system_role_menu current
  WHERE current.role_id=required.role_id AND current.menu_id=required.menu_id
    AND current.tenant_id=required.tenant_id AND current.deleted=b'0'
);

DROP TEMPORARY TABLE cloudmold_v158_role_parent_grant;
DROP TEMPORARY TABLE cloudmold_v158_package_menu;
DROP TEMPORARY TABLE cloudmold_v158_eligible_package;
DROP TEMPORARY TABLE cloudmold_v158_guard;
DROP TEMPORARY TABLE cloudmold_v158_menu_contract;
