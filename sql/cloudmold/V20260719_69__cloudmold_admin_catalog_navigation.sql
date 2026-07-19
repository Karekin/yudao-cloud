-- CloudMold-owned navigation only. The yudao menu engine remains the platform authority.
-- IDs in the 9100000000000 range are reserved for CloudMold navigation extensions.

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000000, 'CloudMold 运营', '', 1, 6, 0, '/cloudmold', 'lucide:boxes', NULL, NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000000);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000001, '规范商品目录', 'cloudmold:catalog:query', 2, 1, 9100000000000,
       'catalog', 'lucide:shirt', 'cloudmold/catalog/index', 'CloudMoldCatalog',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000001);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000002, '规范商品查询', 'cloudmold:catalog:query', 3, 1, 9100000000001,
       '', '', '', NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000002);
