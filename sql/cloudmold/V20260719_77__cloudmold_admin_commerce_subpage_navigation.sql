-- CloudMold-owned Commerce subpage navigation.
-- 将 Listing / Order / Payment / Fulfillment / AfterSale 五个规范交易履约实体
-- 从原「规范交易与履约」聚合页内的权限按钮（type=3，不可见于侧栏）提升为
-- CloudMold 运营下的二级菜单（type=2），与扁平化的前端路由 /cloudmold/<entity> 对齐。
-- ID 9100000000040-44 为本次新增菜单保留；聚合菜单 9100000000020 及其查询按钮
-- 9100000000021-25 保留为领域概览入口与权限点，不做改动。
-- 数据就绪度菜单 9100000000030 的 sort 由 4 调整为 9，让出 4-8 给交易实体菜单，
-- 使「规范交易与履约」概览与五个实体菜单在侧栏中聚拢成组。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu
SET sort = 9,
    updater = 'CloudMold:commerce-subpages',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000030
  AND creator = 'CloudMold'
  AND deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000040, '渠道刊登', 'cloudmold:listing:query', 2, 4, 9100000000000,
       'listing', 'lucide:megaphone', 'cloudmold/commerce/listing/index', 'CloudMoldCommerceListing',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000040);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000041, '订单', 'cloudmold:order:query', 2, 5, 9100000000000,
       'order', 'lucide:shopping-cart', 'cloudmold/commerce/order/index', 'CloudMoldCommerceOrder',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000041);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000042, '支付', 'cloudmold:payment:query', 2, 6, 9100000000000,
       'payment', 'lucide:credit-card', 'cloudmold/commerce/payment/index', 'CloudMoldCommercePayment',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000042);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000043, '履约', 'cloudmold:fulfillment:query', 2, 7, 9100000000000,
       'fulfillment', 'lucide:truck', 'cloudmold/commerce/fulfillment/index', 'CloudMoldCommerceFulfillment',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000043);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000044, '售后', 'cloudmold:aftersale:query', 2, 8, 9100000000000,
       'aftersale', 'lucide:undo-2', 'cloudmold/commerce/aftersale/index', 'CloudMoldCommerceAfterSale',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000044);
