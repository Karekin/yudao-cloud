-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Required business centers must exist once, be visible, and stay under the CloudMold root.
SELECT expected.id, expected.name
FROM (
    SELECT 9100000000100 AS id, '商品中心' AS name
    UNION ALL SELECT 9100000000110, '商家与渠道'
    UNION ALL SELECT 9100000000120, '库存与仓储'
    UNION ALL SELECT 9100000000130, '订单与履约'
    UNION ALL SELECT 9100000000140, '数据运营'
) expected
LEFT JOIN system_menu actual
  ON actual.id = expected.id
 AND actual.name = expected.name
 AND actual.parent_id = 9100000000000
 AND actual.type = 1
 AND actual.status = 0
 AND actual.visible = b'1'
 AND actual.deleted = b'0'
WHERE actual.id IS NULL;

-- Business pages must sit under the intended center with stable order and readable labels.
SELECT expected.id, expected.name
FROM (
    SELECT 9100000000001 AS id, 9100000000100 AS parent_id, 1 AS sort, '商品管理' AS name
    UNION ALL SELECT 9100000000040, 9100000000100, 2, '渠道商品'
    UNION ALL SELECT 9100000000050, 9100000000110, 1, '商家管理'
    UNION ALL SELECT 9100000000080, 9100000000110, 2, '经营主体与授权'
    UNION ALL SELECT 9100000000010, 9100000000120, 1, '库存管理'
    UNION ALL SELECT 9100000000060, 9100000000120, 2, '仓库与库位'
    UNION ALL SELECT 9100000000041, 9100000000130, 1, '订单管理'
    UNION ALL SELECT 9100000000042, 9100000000130, 2, '支付记录'
    UNION ALL SELECT 9100000000043, 9100000000130, 3, '发货履约'
    UNION ALL SELECT 9100000000044, 9100000000130, 4, '售后退款'
    UNION ALL SELECT 9100000000030, 9100000000140, 1, '数据健康'
) expected
LEFT JOIN system_menu actual
  ON actual.id = expected.id
 AND actual.parent_id = expected.parent_id
 AND actual.sort = expected.sort
 AND actual.name = expected.name
 AND actual.status = 0
 AND actual.visible = b'1'
 AND actual.deleted = b'0'
WHERE actual.id IS NULL;

-- Technical "规范" labels and direct leaf menus must not leak into the root navigation.
SELECT id, name, parent_id
FROM system_menu
WHERE deleted = b'0'
  AND visible = b'1'
  AND (
      (parent_id = 9100000000000 AND type = 2)
      OR (id BETWEEN 9100000000000 AND 9100000000999 AND name LIKE '规范%')
  );

-- Default-off Agent Control must remain unavailable in navigation.
SELECT id, name, status, visible
FROM system_menu
WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
  AND deleted = b'0'
  AND (status <> 1 OR visible <> b'0');
