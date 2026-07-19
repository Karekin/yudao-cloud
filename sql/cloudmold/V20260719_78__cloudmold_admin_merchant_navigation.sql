-- CloudMold 规范商家导航。
-- 新增「规范商家」二级菜单(9100000000050)与查询按钮(9100000000051)，挂到 CloudMold 运营下，
-- sort=3（主数据段：商品目录=1、库存=2、商家=3）。
-- 为腾出 sort=3 位置，将现有 sort>=3 的 CloudMold 二级菜单整体后移一位。
-- 与扁平化前端路由 /cloudmold/merchant 对齐，component 指向 cloudmold/merchant/index。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu
SET sort = sort + 1,
    updater = 'CloudMold:merchant-nav',
    update_time = UTC_TIMESTAMP(6)
WHERE parent_id = 9100000000000
  AND creator = 'CloudMold'
  AND deleted = b'0'
  AND sort >= 3;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000050, '规范商家', 'cloudmold:merchant:query', 2, 3, 9100000000000,
       'merchant', 'lucide:store', 'cloudmold/merchant/index', 'CloudMoldMerchant',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000050);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000051, '规范商家查询', 'cloudmold:merchant:query', 3, 1, 9100000000050,
       '', '', '', NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000051);
