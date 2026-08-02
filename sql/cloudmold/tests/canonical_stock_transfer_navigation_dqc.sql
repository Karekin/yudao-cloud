-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 9100000000450 AS missing_menu_id
WHERE NOT EXISTS (
    SELECT 1
    FROM system_menu
    WHERE id = 9100000000450
      AND name = '库存调拨'
      AND permission = 'cloudmold:warehouse:query'
      AND type = 2
      AND sort = 4
      AND parent_id = 9100000000140
      AND path = 'stock-transfers'
      AND component = 'cloudmold/stock-transfer/index'
      AND component_name = 'CloudMoldStockTransfer'
      AND status = 0
      AND visible = b'1'
      AND deleted = b'0'
);

SELECT 9100000000451 AS missing_permission_id
WHERE NOT EXISTS (
    SELECT 1
    FROM system_menu
    WHERE id = 9100000000451
      AND parent_id = 9100000000450
      AND permission = 'cloudmold:warehouse:query'
      AND type = 3
      AND status = 0
      AND deleted = b'0'
);

SELECT id, name, component
FROM system_menu
WHERE id IN (9100000000450, 9100000000451)
  AND (component LIKE 'erp/%' OR component LIKE 'wms/%');
