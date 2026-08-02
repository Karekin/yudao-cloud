-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 9100000000484 AS missing_or_drifted_permission_id
WHERE NOT EXISTS (
  SELECT 1
  FROM system_menu actual
  WHERE actual.id = 9100000000484
    AND actual.name = '定标生成采购订单'
    AND actual.permission = 'cloudmold:procurement:award:release'
    AND actual.type = 3
    AND actual.sort = 14
    AND actual.parent_id = 9100000000460
    AND actual.path = ''
    AND actual.icon = ''
    AND actual.component IS NULL
    AND actual.component_name IS NULL
    AND actual.status = 0
    AND actual.visible = b'1'
    AND actual.keep_alive = b'1'
    AND actual.always_show = b'1'
    AND actual.creator = 'CloudMold'
    AND actual.deleted = b'0'
);

SELECT id AS duplicate_award_release_permission_id
FROM system_menu
WHERE permission = 'cloudmold:procurement:award:release'
  AND id <> 9100000000484
  AND deleted = b'0';

SELECT id AS legacy_alias_id
FROM system_menu
WHERE id = 9100000000484
  AND deleted = b'0'
  AND (path IN ('erp','wms') OR component LIKE 'erp/%' OR component LIKE 'wms/%');
