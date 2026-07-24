-- Reorganize CloudMold administration navigation into business centers.
-- The yudao menu engine remains unchanged; only CloudMold-owned rows are updated.
-- Existing menu IDs and permissions are preserved so role bindings remain valid.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_admin_menu_ia_backup (
    migration_key VARCHAR(64) NOT NULL,
    menu_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    permission VARCHAR(100) NOT NULL DEFAULT '',
    type TINYINT NOT NULL,
    sort INT NOT NULL,
    parent_id BIGINT NOT NULL,
    path VARCHAR(200) NOT NULL DEFAULT '',
    icon VARCHAR(100) NOT NULL DEFAULT '',
    component VARCHAR(255) NULL,
    component_name VARCHAR(255) NULL,
    status TINYINT NOT NULL,
    visible BIT(1) NOT NULL,
    keep_alive BIT(1) NOT NULL,
    always_show BIT(1) NOT NULL,
    updater VARCHAR(64) NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (migration_key, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='CloudMold administration menu information-architecture rollback snapshot';

INSERT IGNORE INTO cloudmold_admin_menu_ia_backup
    (migration_key, menu_id, name, permission, type, sort, parent_id, path, icon,
     component, component_name, status, visible, keep_alive, always_show, updater, update_time)
SELECT 'V20260724_82', id, name, permission, type, sort, parent_id, path, icon,
       component, component_name, status, visible, keep_alive, always_show, updater, update_time
FROM system_menu
WHERE id IN (
    9100000000000, 9100000000001, 9100000000002,
    9100000000010, 9100000000011,
    9100000000020, 9100000000021, 9100000000022, 9100000000023,
    9100000000024, 9100000000025,
    9100000000030, 9100000000031,
    9100000000040, 9100000000041, 9100000000042, 9100000000043, 9100000000044,
    9100000000050, 9100000000051,
    9100000000060, 9100000000061,
    9100000000070, 9100000000071, 9100000000072, 9100000000073,
    9100000000080, 9100000000081
)
  AND creator = 'CloudMold'
  AND deleted = b'0';

UPDATE system_menu
SET name = 'CloudMold',
    updater = 'CloudMold:business-navigation',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000000
  AND creator = 'CloudMold'
  AND deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000100, '商品中心', '', 1, 1, 9100000000000,
       'product-center', 'lucide:shirt', NULL, NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:business-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000100);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000110, '商家与渠道', '', 1, 2, 9100000000000,
       'merchant-channel', 'lucide:store', NULL, NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:business-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000110);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000120, '库存与仓储', '', 1, 3, 9100000000000,
       'inventory-warehouse', 'lucide:warehouse', NULL, NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:business-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000120);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000130, '订单与履约', '', 1, 4, 9100000000000,
       'order-fulfillment', 'lucide:shopping-bag', NULL, NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:business-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000130);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000140, '数据运营', '', 1, 5, 9100000000000,
       'data-operations', 'lucide:database', NULL, NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:business-navigation', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000140);

UPDATE system_menu
SET name = '商品管理', parent_id = 9100000000100, path = 'products', sort = 1,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000001 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '渠道商品', parent_id = 9100000000100, path = 'channel-products', sort = 2,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000040 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '商家管理', parent_id = 9100000000110, path = 'merchants', sort = 1,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000050 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '经营主体与授权', parent_id = 9100000000110, path = 'identities', sort = 2,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000080 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '库存管理', parent_id = 9100000000120, path = 'inventory', sort = 1,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000010 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '仓库与库位', parent_id = 9100000000120, path = 'warehouses', sort = 2,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000060 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '订单管理', parent_id = 9100000000130, path = 'orders', sort = 1,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000041 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '支付记录', parent_id = 9100000000130, path = 'payments', sort = 2,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000042 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '发货履约', parent_id = 9100000000130, path = 'fulfillments', sort = 3,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000043 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '售后退款', parent_id = 9100000000130, path = 'aftersales', sort = 4,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000044 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '数据健康', parent_id = 9100000000140, path = 'health', sort = 1,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000030 AND creator = 'CloudMold' AND deleted = b'0';

-- Preserve the aggregate commerce route for old bookmarks, but remove the duplicate menu entry.
UPDATE system_menu
SET name = '交易概览', sort = 90, visible = b'0',
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000020 AND creator = 'CloudMold' AND deleted = b'0';

-- Query permission buttons follow their actual business pages.
UPDATE system_menu
SET name = '商品查询', parent_id = 9100000000001,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000002 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '库存查询', parent_id = 9100000000010,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000011 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '渠道商品查询', parent_id = 9100000000040,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000021 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '订单查询', parent_id = 9100000000041,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000022 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '支付查询', parent_id = 9100000000042,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000023 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '履约查询', parent_id = 9100000000043,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000024 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '售后查询', parent_id = 9100000000044,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000025 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '数据健康查询', parent_id = 9100000000030,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000031 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '商家查询', parent_id = 9100000000050,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000051 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '仓库查询', parent_id = 9100000000060,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000061 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET name = '经营主体查询', parent_id = 9100000000080,
    updater = 'CloudMold:business-navigation', update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000081 AND creator = 'CloudMold' AND deleted = b'0';

-- Agent Control is assembled but default-off. Keep its permissions and rows for activation,
-- while removing the unavailable capability from normal navigation.
UPDATE system_menu
SET status = 1, visible = b'0',
    updater = 'CloudMold:business-navigation',
    update_time = UTC_TIMESTAMP(6)
WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
  AND creator = 'CloudMold'
  AND deleted = b'0';
