-- Expected result: zero rows.

SELECT expected.permission
FROM (
    SELECT 'cloudmold:supply-planning:query' AS permission, 9100000000320 AS parent_id
    UNION ALL SELECT 'cloudmold:supply-planning:command', 9100000000320
    UNION ALL SELECT 'cloudmold:quality:query', 9100000000330
    UNION ALL SELECT 'cloudmold:quality:command', 9100000000330
) expected
LEFT JOIN system_menu actual
  ON actual.permission=expected.permission
 AND actual.parent_id=expected.parent_id
 AND actual.type=3
 AND actual.status=0
 AND actual.deleted=b'0'
WHERE actual.id IS NULL;

SELECT permission,COUNT(*) duplicate_count
FROM system_menu
WHERE permission IN (
    'cloudmold:supply-planning:query',
    'cloudmold:supply-planning:command',
    'cloudmold:quality:query',
    'cloudmold:quality:command'
)
  AND deleted=b'0'
GROUP BY permission
HAVING COUNT(*)<>1;
