-- CloudMold 规范仓网主数据导航。
-- 新增「规范仓网」二级菜单(9100000000060)与查询按钮(9100000000061)，挂到 CloudMold 运营下，
-- sort=3（主数据段：商品目录=1、库存=2、仓网=3、商家=4）；库存与仓配聚拢成组。
-- 为腾出 sort=3 位置，将现有 sort>=3 的 CloudMold 二级菜单整体后移一位。
-- 与扁平化前端路由 /cloudmold/warehouse 对齐，component 指向 cloudmold/warehouse/index。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu
SET sort = sort + 1,
    updater = 'CloudMold:warehouse-nav',
    update_time = UTC_TIMESTAMP(6)
WHERE parent_id = 9100000000000
  AND creator = 'CloudMold'
  AND deleted = b'0'
  AND sort >= 3;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000060, '规范仓网', 'cloudmold:warehouse:query', 2, 3, 9100000000000,
       'warehouse', 'lucide:warehouse', 'cloudmold/warehouse/index', 'CloudMoldWarehouse',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000060);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000061, '规范仓网查询', 'cloudmold:warehouse:query', 3, 1, 9100000000060,
       '', '', '', NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000061);
