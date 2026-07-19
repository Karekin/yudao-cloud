-- CloudMold-owned Inventory navigation. Legacy ERP/WMS inventory roots are retired separately
-- only after the canonical replacement pages are available.

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000010, '规范库存', 'cloudmold:inventory:query', 2, 2, 9100000000000,
       'inventory', 'lucide:warehouse', 'cloudmold/inventory/index', 'CloudMoldInventory',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000010);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000011, '规范库存查询', 'cloudmold:inventory:query', 3, 1, 9100000000010,
       '', '', '', NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000011);
