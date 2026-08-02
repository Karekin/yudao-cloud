SELECT expected.id, expected.name, expected.permission, expected.parent_id
FROM (
  SELECT 9100000000522 AS id,'三账对账' AS name,
         'cloudmold:finance:procure-to-pay:reconciliation:query' AS permission,
         9100000000170 AS parent_id
  UNION ALL
  SELECT 9100000000485,'三账对账查询',
         'cloudmold:finance:procure-to-pay:reconciliation:query',9100000000522
  UNION ALL
  SELECT 9100000000486,'三账对账执行',
         'cloudmold:finance:procure-to-pay:reconciliation:command',9100000000522
) expected
LEFT JOIN system_menu actual
  ON actual.id = expected.id
 AND actual.name = expected.name
 AND actual.parent_id = expected.parent_id
 AND actual.permission = expected.permission
 AND actual.status = 0
 AND actual.deleted = b'0'
WHERE actual.id IS NULL;
