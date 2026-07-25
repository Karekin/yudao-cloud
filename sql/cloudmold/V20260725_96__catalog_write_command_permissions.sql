-- Least-privilege permissions for canonical Catalog metadata updates and barcode rotation.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000209, '商品元数据更新', 'cloudmold:catalog:metadata:update', 3, 4, 9100000000001,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:catalog-write-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000209);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000210, '商品条码轮换', 'cloudmold:catalog:barcode:rotate', 3, 5, 9100000000001,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:catalog-write-permissions', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000210);
