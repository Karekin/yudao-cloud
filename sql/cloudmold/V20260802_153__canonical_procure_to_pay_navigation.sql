-- Canonical CloudMold procure-to-pay administration navigation.
-- This migration is intentionally fail-closed: an occupied canonical ID, route, or
-- component must already match the exact contract. ERP/WMS aliases are not accepted.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v153_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v153_menu_contract (
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

INSERT INTO cloudmold_v153_menu_contract
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name)
VALUES
  (9100000000460,'采购工作台','',2,5,9100000000140,'procurement','lucide:clipboard-list','cloudmold/procurement/index','CloudMoldProcurement'),
  (9100000000461,'采购入库','cloudmold:warehouse:query',2,6,9100000000140,'procurement-inbound','lucide:package-check','cloudmold/procurement-inbound/index','CloudMoldProcurementInbound'),
  (9100000000462,'来货质检','cloudmold:quality:procurement-receipt-inspection:query',2,2,9100000000150,'procurement-quality','lucide:scan-line','cloudmold/procurement-quality/index','CloudMoldProcurementQuality'),
  (9100000000463,'采购到付款','cloudmold:finance:procure-to-pay:query',2,3,9100000000170,'procure-to-pay','lucide:receipt-text','cloudmold/finance/index','CloudMoldProcureToPay'),

  (9100000000464,'采购申请查询','cloudmold:procurement:requisition:query',3,1,9100000000460,'','',NULL,NULL),
  (9100000000465,'寻源事件查询','cloudmold:procurement:sourcing:query',3,2,9100000000460,'','',NULL,NULL),
  (9100000000466,'供应商报价查询','cloudmold:procurement:quotation:query',3,3,9100000000460,'','',NULL,NULL),
  (9100000000467,'定标查询','cloudmold:procurement:award:query',3,4,9100000000460,'','',NULL,NULL),
  (9100000000468,'采购订单查询','cloudmold:procurement:order:query',3,5,9100000000460,'','',NULL,NULL),
  (9100000000469,'采购订单操作','cloudmold:procurement:order:write',3,6,9100000000460,'','',NULL,NULL),
  (9100000000470,'采购订单发布','cloudmold:procurement:order:release',3,7,9100000000460,'','',NULL,NULL),
  (9100000000471,'寻源事件操作','cloudmold:procurement:sourcing:write',3,8,9100000000460,'','',NULL,NULL),
  (9100000000472,'供应商报价操作','cloudmold:procurement:quotation:write',3,9,9100000000460,'','',NULL,NULL),
  (9100000000473,'寻源评审操作','cloudmold:procurement:evaluation:write',3,10,9100000000460,'','',NULL,NULL),
  (9100000000474,'定标草案操作','cloudmold:procurement:award:write',3,11,9100000000460,'','',NULL,NULL),
  (9100000000475,'定标提交','cloudmold:procurement:award:submit',3,12,9100000000460,'','',NULL,NULL),
  (9100000000476,'定标审批','cloudmold:procurement:award:approve',3,13,9100000000460,'','',NULL,NULL),

  (9100000000477,'采购入库查询','cloudmold:warehouse:query',3,1,9100000000461,'','',NULL,NULL),
  (9100000000478,'采购入库操作','cloudmold:warehouse:command',3,2,9100000000461,'','',NULL,NULL),
  (9100000000479,'来货质检查询','cloudmold:quality:procurement-receipt-inspection:query',3,1,9100000000462,'','',NULL,NULL),
  (9100000000480,'来货质检操作','cloudmold:quality:procurement-receipt-inspection:command',3,2,9100000000462,'','',NULL,NULL),
  (9100000000481,'采购到付款查询','cloudmold:finance:procure-to-pay:query',3,1,9100000000463,'','',NULL,NULL),
  (9100000000482,'采购到付款操作','cloudmold:finance:procure-to-pay:command',3,2,9100000000463,'','',NULL,NULL),
  (9100000000483,'采购到付款治理','cloudmold:finance:procure-to-pay:govern',3,3,9100000000463,'','',NULL,NULL);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v153_contract_guard;
CREATE TEMPORARY TABLE cloudmold_v153_contract_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v153_contract_guard CHECK (guard_value = 'OK')
);

INSERT INTO cloudmold_v153_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM (
  SELECT existing.id
  FROM system_menu existing
  JOIN cloudmold_v153_menu_contract expected ON expected.id = existing.id
  WHERE NOT (
    existing.name = expected.name
    AND existing.permission = expected.permission
    AND existing.type = expected.type
    AND existing.sort = expected.sort
    AND existing.parent_id = expected.parent_id
    AND existing.path = expected.path
    AND existing.icon = expected.icon
    AND existing.component <=> expected.component
    AND existing.component_name <=> expected.component_name
    AND existing.status = 0
    AND existing.visible = b'1'
    AND existing.keep_alive = b'1'
    AND existing.always_show = b'1'
    AND existing.creator = 'CloudMold'
    AND existing.deleted = b'0'
  )

  UNION ALL

  SELECT existing.id
  FROM system_menu existing
  JOIN cloudmold_v153_menu_contract expected
    ON expected.type = 2
   AND existing.parent_id = expected.parent_id
   AND existing.path = expected.path
   AND existing.id <> expected.id
  WHERE existing.deleted = b'0'

  UNION ALL

  SELECT existing.id
  FROM system_menu existing
  JOIN cloudmold_v153_menu_contract expected
    ON expected.type = 2
   AND existing.component = expected.component
   AND existing.id <> expected.id
  WHERE existing.deleted = b'0'

  UNION ALL

  SELECT parent.id
  FROM (
    SELECT 9100000000140 AS id
    UNION ALL SELECT 9100000000150
    UNION ALL SELECT 9100000000170
  ) required
  LEFT JOIN system_menu parent ON parent.id = required.id
  WHERE parent.id IS NULL
     OR parent.type <> 1
     OR parent.status <> 0
     OR parent.visible <> b'1'
     OR parent.deleted <> b'0'
) conflicts;

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT expected.id,expected.name,expected.permission,expected.type,expected.sort,
       expected.parent_id,expected.path,expected.icon,expected.component,expected.component_name,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-procure-to-pay-navigation',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v153_menu_contract expected
LEFT JOIN system_menu existing ON existing.id = expected.id
WHERE existing.id IS NULL
ORDER BY expected.id;

DROP TEMPORARY TABLE cloudmold_v153_contract_guard;
DROP TEMPORARY TABLE cloudmold_v153_menu_contract;
