-- Expected result: zero rows.
SELECT id, name, permission, parent_id, type, status, deleted
FROM system_menu
WHERE id IN (
  9100000000200, 9100000000201, 9100000000202,
  9100000000203, 9100000000204, 9100000000205,
  9100000000206, 9100000000207, 9100000000208
)
  AND (
    deleted <> b'0'
    OR status <> 0
    OR type <> 3
    OR permission NOT IN (
      'cloudmold:catalog:sku:define',
      'cloudmold:catalog:lifecycle',
      'cloudmold:inventory:command',
      'cloudmold:listing:command',
      'cloudmold:order:command',
      'cloudmold:fulfillment:command',
      'cloudmold:aftersale:command',
      'cloudmold:merchant:command',
      'cloudmold:warehouse:command'
    )
  )
UNION ALL
SELECT 0, 'missing command permission', '', 0, 0, 0, b'0'
WHERE (
  SELECT COUNT(*)
  FROM system_menu
  WHERE id BETWEEN 9100000000200 AND 9100000000208
    AND deleted = b'0'
) <> 9;
