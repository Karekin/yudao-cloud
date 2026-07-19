-- CloudMold 规范身份导航。
-- 新增「规范身份」二级菜单(9100000000070)与查询按钮(9100000000071)，挂到 CloudMold 运营下，
-- sort=5（主数据段尾：商品目录=1、库存=2、仓网=3、商家=4、身份=5）。
-- 为腾出 sort=5 位置，将现有 sort>=5 的 CloudMold 二级菜单整体后移一位。
-- 与扁平化前端路由 /cloudmold/identity 对齐，component 指向 cloudmold/identity/index。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu
SET sort = sort + 1,
    updater = 'CloudMold:identity-nav',
    update_time = UTC_TIMESTAMP(6)
WHERE parent_id = 9100000000000
  AND creator = 'CloudMold'
  AND deleted = b'0'
  AND sort >= 5;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000080, '规范身份', 'cloudmold:identity:query', 2, 5, 9100000000000,
       'identity', 'lucide:user-check', 'cloudmold/identity/index', 'CloudMoldIdentity',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000080);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000081, '规范身份查询', 'cloudmold:identity:query', 3, 1, 9100000000080,
       '', '', '', NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000081);
