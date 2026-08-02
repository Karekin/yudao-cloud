-- Canonical CloudMold award-to-purchase-order release permission.
-- Fail closed when the canonical parent or menu identifier is occupied by another contract.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v155_contract_guard;
CREATE TEMPORARY TABLE cloudmold_v155_contract_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v155_contract_guard CHECK (guard_value = 'OK')
);

INSERT INTO cloudmold_v155_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM (
  SELECT parent.id
  FROM system_menu parent
  WHERE parent.id = 9100000000460
    AND NOT (
      parent.name = '采购工作台'
      AND parent.type = 2
      AND parent.path = 'procurement'
      AND parent.component = 'cloudmold/procurement/index'
      AND parent.status = 0
      AND parent.visible = b'1'
      AND parent.deleted = b'0'
    )

  UNION ALL

  SELECT existing.id
  FROM system_menu existing
  WHERE existing.id = 9100000000484
    AND NOT (
      existing.name = '定标生成采购订单'
      AND existing.permission = 'cloudmold:procurement:award:release'
      AND existing.type = 3
      AND existing.sort = 14
      AND existing.parent_id = 9100000000460
      AND existing.path = ''
      AND existing.icon = ''
      AND existing.component IS NULL
      AND existing.component_name IS NULL
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
  WHERE existing.permission = 'cloudmold:procurement:award:release'
    AND existing.id <> 9100000000484
    AND existing.deleted = b'0'
) conflicts;

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 9100000000484,'定标生成采购订单','cloudmold:procurement:award:release',3,14,
       9100000000460,'','',NULL,NULL,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-award-purchase-order-release',UTC_TIMESTAMP(6),b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE id = 9100000000484
);

DROP TEMPORARY TABLE cloudmold_v155_contract_guard;
