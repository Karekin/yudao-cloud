-- Expected result: zero rows.
SELECT id, name, permission, parent_id, type, status, deleted
FROM system_menu
WHERE id IN (9100000000209, 9100000000210)
  AND (
    deleted <> b'0'
    OR status <> 0
    OR type <> 3
    OR permission NOT IN (
      'cloudmold:catalog:metadata:update',
      'cloudmold:catalog:barcode:rotate'
    )
  )
UNION ALL
SELECT 0, 'missing catalog write command permission', '', 0, 0, 0, b'0'
WHERE (
  SELECT COUNT(*)
  FROM system_menu
  WHERE id IN (9100000000209, 9100000000210)
    AND deleted = b'0'
) <> 2;
