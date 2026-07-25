-- CloudMold 管理后台命令权限。
-- 仅新增 yudao System 菜单/权限记录，不改变上游权限引擎。
-- 业务角色必须显式授予对应按钮权限；超级管理员仍由上游权限引擎处理。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000200, '商品定义', 'cloudmold:catalog:sku:define', 3, 2, 9100000000001,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000200);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000201, '商品生命周期', 'cloudmold:catalog:lifecycle', 3, 3, 9100000000001,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000201);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000202, '库存账本命令', 'cloudmold:inventory:command', 3, 2, 9100000000010,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000202);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000203, '渠道刊登命令', 'cloudmold:listing:command', 3, 2, 9100000000040,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000203);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000204, '订单命令', 'cloudmold:order:command', 3, 2, 9100000000041,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000204);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000205, '履约命令', 'cloudmold:fulfillment:command', 3, 2, 9100000000043,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000205);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000206, '售后命令', 'cloudmold:aftersale:command', 3, 2, 9100000000044,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000206);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000207, '商家状态命令', 'cloudmold:merchant:command', 3, 2, 9100000000050,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000207);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000208, '仓网状态命令', 'cloudmold:warehouse:command', 3, 2, 9100000000060,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:command-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000208);
