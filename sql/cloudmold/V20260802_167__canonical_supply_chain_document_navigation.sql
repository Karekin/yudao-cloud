-- Publish only the CloudMold-owned supply-chain documents and control records
-- that have canonical schemas, APIs and management pages.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v167_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v167_menu_contract (
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

INSERT INTO cloudmold_v167_menu_contract
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name)
VALUES
  (9100000000510,'供应商退供','cloudmold:warehouse:supplier-return:query',2,6,9100000000491,
   'supplier-returns','lucide:package-minus','cloudmold/warehouse/supplier-return/index','CloudMoldSupplierReturn'),
  (9100000000511,'供应商退供查询','cloudmold:warehouse:supplier-return:query',3,1,9100000000510,
   '','',NULL,NULL),
  (9100000000512,'供应商退供操作','cloudmold:warehouse:supplier-return:command',3,2,9100000000510,
   '','',NULL,NULL),

  (9100000000513,'库存盘点','cloudmold:warehouse:query',2,4,9100000000492,
   'stock-counts','lucide:scan-line','cloudmold/warehouse/stock-count/index','CloudMoldStockCount'),
  (9100000000514,'库存盘点查询','cloudmold:warehouse:query',3,1,9100000000513,
   '','',NULL,NULL),
  (9100000000515,'库存盘点操作','cloudmold:warehouse:command',3,2,9100000000513,
   '','',NULL,NULL),

  (9100000000516,'安全库存策略','cloudmold:supply-planning:policy:query',2,6,9100000000493,
   'safety-stock-policies','lucide:shield-plus','cloudmold/inventory/safety-stock-policy/index','CloudMoldSafetyStockPolicy'),
  (9100000000517,'安全库存策略查询','cloudmold:supply-planning:policy:query',3,1,9100000000516,
   '','',NULL,NULL),
  (9100000000518,'安全库存策略操作','cloudmold:supply-planning:policy:command',3,2,9100000000516,
   '','',NULL,NULL),

  (9100000000519,'库存健康快照','cloudmold:supply-planning:health-snapshot:query',2,7,9100000000493,
   'inventory-health-snapshots','lucide:camera','cloudmold/inventory/health-snapshot/index','CloudMoldInventoryHealthSnapshot'),
  (9100000000520,'库存健康快照查询','cloudmold:supply-planning:health-snapshot:query',3,1,9100000000519,
   '','',NULL,NULL),
  (9100000000521,'库存健康快照生成','cloudmold:supply-planning:health-snapshot:command',3,2,9100000000519,
   '','',NULL,NULL);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v167_guard;
CREATE TEMPORARY TABLE cloudmold_v167_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v167_guard CHECK (guard_value='OK')
);
INSERT INTO cloudmold_v167_guard (guard_value)
SELECT CASE WHEN COUNT(*)=3 THEN 'OK' ELSE 'FAIL' END
FROM system_menu
WHERE id IN (9100000000491,9100000000492,9100000000493)
  AND deleted=b'0';
INSERT INTO cloudmold_v167_guard (guard_value)
SELECT CASE WHEN COUNT(*)=0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu actual
JOIN cloudmold_v167_menu_contract expected
  ON actual.parent_id=expected.parent_id AND actual.path=expected.path AND actual.id<>expected.id
WHERE actual.deleted=b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT expected.id,expected.name,expected.permission,expected.type,expected.sort,expected.parent_id,
       expected.path,expected.icon,expected.component,expected.component_name,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-supply-chain-document-navigation',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v167_menu_contract expected
LEFT JOIN system_menu actual ON actual.id=expected.id
WHERE actual.id IS NULL;

-- V165 owns the scrap page; this migration establishes the final warehouse
-- execution ordering after stock count is published.
UPDATE system_menu
SET sort=5,updater='CloudMold:canonical-supply-chain-document-navigation',update_time=UTC_TIMESTAMP(6)
WHERE id=9100000000507 AND deleted=b'0';

DROP TEMPORARY TABLE IF EXISTS cloudmold_v167_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v167_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO cloudmold_v167_eligible_package (package_id)
SELECT id FROM system_tenant_package
WHERE deleted=b'0' AND status=0 AND JSON_VALID(menu_ids)
  AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(9100000000140 AS JSON));

DROP TEMPORARY TABLE IF EXISTS cloudmold_v167_package_menu;
CREATE TEMPORARY TABLE cloudmold_v167_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id,menu_id)
) ENGINE=InnoDB;
INSERT IGNORE INTO cloudmold_v167_package_menu (package_id,menu_id)
SELECT eligible.package_id,existing.menu_id
FROM cloudmold_v167_eligible_package eligible
JOIN system_tenant_package package ON package.id=eligible.package_id
JOIN JSON_TABLE(CAST(package.menu_ids AS JSON),'$[*]' COLUMNS(menu_id BIGINT PATH '$')) existing;
INSERT IGNORE INTO cloudmold_v167_package_menu (package_id,menu_id)
SELECT eligible.package_id,expected.id
FROM cloudmold_v167_eligible_package eligible
CROSS JOIN cloudmold_v167_menu_contract expected;
UPDATE system_tenant_package package
JOIN (
  SELECT package_id,JSON_ARRAYAGG(menu_id) menu_ids
  FROM cloudmold_v167_package_menu GROUP BY package_id
) merged ON merged.package_id=package.id
SET package.menu_ids=CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater='CloudMold:canonical-supply-chain-document-navigation',
    package.update_time=UTC_TIMESTAMP(6);

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT role.id,expected.id,role.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-supply-chain-document-navigation',UTC_TIMESTAMP(6),b'0'
FROM system_role role
JOIN system_tenant tenant ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN cloudmold_v167_eligible_package eligible ON eligible.package_id=tenant.package_id
CROSS JOIN cloudmold_v167_menu_contract expected
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu current
    WHERE current.role_id=role.id AND current.menu_id=expected.id
      AND current.tenant_id=role.tenant_id AND current.deleted=b'0'
  );

DROP TEMPORARY TABLE cloudmold_v167_package_menu;
DROP TEMPORARY TABLE cloudmold_v167_eligible_package;
DROP TEMPORARY TABLE cloudmold_v167_guard;
DROP TEMPORARY TABLE cloudmold_v167_menu_contract;
