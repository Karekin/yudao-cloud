-- CloudMold 岗位协同中心导航与细粒度权限。
-- 页面只展示审批、交接和业务结果；命令与治理权限不随页面查询权限自动授予。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000070, '岗位协同中心', 'cloudmold:agent-control:query', 2, 20, 9100000000000,
       'agent-control', 'lucide:workflow', 'cloudmold/agent-control/index', 'CloudMoldAgentControl',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000070);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000071, '岗位事项查询', 'cloudmold:agent-control:query', 3, 1, 9100000000070,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000071);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000072, '岗位动作执行', 'cloudmold:agent-control:command', 3, 2, 9100000000070,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000072);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000073, '岗位权限治理', 'cloudmold:agent-control:govern', 3, 3, 9100000000070,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000073);
