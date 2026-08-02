-- CloudMold Warehouse 原生库存调拨管理导航。
-- 只指向 cloudmold/stock-transfer，不复用 ERP/WMS 页面或权限。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000450, '库存调拨', 'cloudmold:warehouse:query', 2, 4, 9100000000140,
       'stock-transfers', 'lucide:arrow-left-right', 'cloudmold/stock-transfer/index',
       'CloudMoldStockTransfer', 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:canonical-stock-transfer-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000450);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000451, '库存调拨查询', 'cloudmold:warehouse:query', 3, 1, 9100000000450,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:canonical-stock-transfer-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000451);
