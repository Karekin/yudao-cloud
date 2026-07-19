-- Repair CloudMold-owned menu labels when a manual MySQL client executed V69-V72
-- without declaring UTF-8. Exact IDs and creator ownership keep the repair isolated
-- from yudao upstream navigation.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu
SET name = CASE id
        WHEN 9100000000000 THEN 'CloudMold 运营'
        WHEN 9100000000001 THEN '规范商品目录'
        WHEN 9100000000002 THEN '规范商品查询'
        WHEN 9100000000010 THEN '规范库存'
        WHEN 9100000000011 THEN '规范库存查询'
        WHEN 9100000000020 THEN '规范交易与履约'
        WHEN 9100000000021 THEN '规范 Listing 查询'
        WHEN 9100000000022 THEN '规范 Order 查询'
        WHEN 9100000000023 THEN '规范 Payment 查询'
        WHEN 9100000000024 THEN '规范 Fulfillment 查询'
        WHEN 9100000000025 THEN '规范 AfterSale 查询'
        WHEN 9100000000030 THEN '数据就绪度'
        WHEN 9100000000031 THEN '数据就绪度查询'
    END,
    updater = 'CloudMold:utf8-repair',
    update_time = UTC_TIMESTAMP(6)
WHERE id IN (
    9100000000000, 9100000000001, 9100000000002,
    9100000000010, 9100000000011,
    9100000000020, 9100000000021, 9100000000022, 9100000000023,
    9100000000024, 9100000000025,
    9100000000030, 9100000000031
)
  AND creator = 'CloudMold'
  AND deleted = b'0';
