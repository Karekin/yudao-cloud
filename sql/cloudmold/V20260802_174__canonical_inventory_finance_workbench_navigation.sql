-- Publish the canonical inventory aging and finance impact workbenches only
-- after their CloudMold-owned query/command authorities exist.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v174_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v174_menu_contract (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  type TINYINT NOT NULL,
  sort INT NOT NULL,
  parent_id BIGINT NOT NULL,
  path VARCHAR(200) NOT NULL,
  icon VARCHAR(100) NOT NULL,
  component VARCHAR(255) NULL,
  component_name VARCHAR(255) NULL,
  keep_alive BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cloudmold_v174_menu_contract
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,keep_alive)
VALUES
  (9100000000523,'库存库龄与效期','cloudmold:inventory:aging-snapshot:query',2,8,9100000000493,
   'lot-aging','lucide:hourglass','cloudmold/inventory/lot-aging/index','CloudMoldInventoryLotAging',b'1'),
  (9100000000524,'库存库龄与效期查询','cloudmold:inventory:aging-snapshot:query',3,1,9100000000523,
   '','',NULL,NULL,b'0'),
  (9100000000525,'库存库龄快照生成','cloudmold:inventory:aging-snapshot:command',3,2,9100000000523,
   '','',NULL,NULL,b'0'),
  (9100000000526,'财务影响与凭证追踪','cloudmold:finance:financial-impact:query',2,5,9100000000170,
   'voucher-trace','lucide:book-open-check','cloudmold/finance/voucher-trace/index','CloudMoldFinanceVoucherTrace',b'1'),
  (9100000000527,'财务影响查询','cloudmold:finance:financial-impact:query',3,1,9100000000526,
   '','',NULL,NULL,b'0'),
  (9100000000528,'库存控制财务操作','cloudmold:finance:inventory-control:command',3,2,9100000000526,
   '','',NULL,NULL,b'0');

DROP TEMPORARY TABLE IF EXISTS cloudmold_v174_guard;
CREATE TEMPORARY TABLE cloudmold_v174_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v174_guard CHECK (guard_value='OK')
);

INSERT INTO cloudmold_v174_guard (guard_value)
SELECT CASE WHEN COUNT(*)=2 THEN 'OK' ELSE 'FAIL' END
FROM system_menu parent
WHERE parent.id IN (9100000000493,9100000000170)
  AND parent.status=0 AND parent.deleted=b'0';

INSERT INTO cloudmold_v174_guard (guard_value)
SELECT CASE WHEN COUNT(*)=0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu actual
JOIN cloudmold_v174_menu_contract expected ON expected.id=actual.id
WHERE NOT (
  actual.name=expected.name
  AND actual.permission=expected.permission
  AND actual.type=expected.type
  AND actual.sort=expected.sort
  AND actual.parent_id=expected.parent_id
  AND actual.path=expected.path
  AND actual.icon=expected.icon
  AND actual.component <=> expected.component
  AND actual.component_name <=> expected.component_name
  AND actual.status=0
  AND actual.visible=b'1'
  AND actual.keep_alive=expected.keep_alive
  AND actual.deleted=b'0'
);

INSERT INTO cloudmold_v174_guard (guard_value)
SELECT CASE WHEN COUNT(*)=0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu actual
JOIN cloudmold_v174_menu_contract expected
  ON actual.parent_id=expected.parent_id
 AND actual.type=expected.type
 AND ((expected.type=2 AND actual.path=expected.path)
   OR (expected.type=3 AND actual.permission=expected.permission))
 AND actual.id<>expected.id
WHERE actual.deleted=b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT expected.id,expected.name,expected.permission,expected.type,expected.sort,expected.parent_id,
       expected.path,expected.icon,expected.component,expected.component_name,
       0,b'1',expected.keep_alive,b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:inventory-finance-workbench-v174',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v174_menu_contract expected
LEFT JOIN system_menu actual ON actual.id=expected.id
WHERE actual.id IS NULL;

DROP TEMPORARY TABLE cloudmold_v174_guard;
DROP TEMPORARY TABLE cloudmold_v174_menu_contract;
