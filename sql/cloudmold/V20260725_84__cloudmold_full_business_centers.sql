-- Expand CloudMold administration into the eight business centers used by the
-- commerce analytics operating model. Existing menu IDs and permissions remain
-- stable; previously assembled CloudMold pages become discoverable.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO cloudmold_admin_menu_ia_backup
    (migration_key, menu_id, name, permission, type, sort, parent_id, path, icon,
     component, component_name, status, visible, keep_alive, always_show, updater, update_time)
SELECT 'V20260725_84', id, name, permission, type, sort, parent_id, path, icon,
       component, component_name, status, visible, keep_alive, always_show, updater, update_time
FROM system_menu
WHERE id BETWEEN 9100000000000 AND 9100000000999
  AND creator = 'CloudMold'
  AND deleted = b'0';

UPDATE system_menu
SET name = CASE id
        WHEN 9100000000100 THEN '经营驾驶舱'
        WHEN 9100000000110 THEN '商家经营中心'
        WHEN 9100000000120 THEN '增长营销'
        WHEN 9100000000130 THEN '买家旅程'
        WHEN 9100000000140 THEN '供应链'
    END,
    path = CASE id
        WHEN 9100000000100 THEN 'executive'
        WHEN 9100000000110 THEN 'merchant-center'
        WHEN 9100000000120 THEN 'growth'
        WHEN 9100000000130 THEN 'buyer-journey'
        WHEN 9100000000140 THEN 'supply-chain'
    END,
    icon = CASE id
        WHEN 9100000000100 THEN 'lucide:gauge'
        WHEN 9100000000110 THEN 'lucide:store'
        WHEN 9100000000120 THEN 'lucide:megaphone'
        WHEN 9100000000130 THEN 'lucide:route'
        WHEN 9100000000140 THEN 'lucide:waypoints'
    END,
    sort = CASE id
        WHEN 9100000000100 THEN 1
        WHEN 9100000000110 THEN 2
        WHEN 9100000000120 THEN 3
        WHEN 9100000000130 THEN 4
        WHEN 9100000000140 THEN 5
    END,
    status = 0,
    visible = b'1',
    updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id IN (
    9100000000100, 9100000000110, 9100000000120,
    9100000000130, 9100000000140
)
  AND creator = 'CloudMold'
  AND deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT candidate.id, candidate.name, '', 1, candidate.sort, 9100000000000,
       candidate.path, candidate.icon, NULL, NULL, 0, b'1', b'1', b'1',
       'CloudMold', UTC_TIMESTAMP(6), 'CloudMold:full-business-centers', UTC_TIMESTAMP(6), b'0'
FROM (
    SELECT 9100000000150 AS id, '鉴别质检中心' AS name, 6 AS sort,
           'quality-center' AS path, 'lucide:badge-check' AS icon
    UNION ALL
    SELECT 9100000000160, '信任保障', 7, 'trust', 'lucide:shield-check'
    UNION ALL
    SELECT 9100000000170, '财务与风控', 8, 'finance-risk', 'lucide:landmark'
) candidate
LEFT JOIN system_menu existing ON existing.id = candidate.id
WHERE existing.id IS NULL;

-- Re-home stable business pages. Keeping the IDs preserves existing role/menu
-- bindings and the command permissions added by V20260725_83.
UPDATE system_menu
SET parent_id = 9100000000100, name = '数据健康', path = 'data-health', sort = 1,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000030 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000110, name = '商家管理', path = 'merchants', sort = 1,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000050 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000110, name = '经营主体与授权', path = 'identities', sort = 2,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000080 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000110, name = '商品管理', path = 'products', sort = 3,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000001 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000110, name = '渠道商品', path = 'channel-products', sort = 4,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000040 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000130, name = '订单管理', path = 'orders', sort = 1,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000041 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000130, name = '发货履约', path = 'fulfillments', sort = 2,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000043 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000130, name = '售后退款', path = 'aftersales', sort = 3,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000044 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000140, name = '库存管理', path = 'inventory', sort = 2,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000010 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000140, name = '仓库与库位', path = 'warehouses', sort = 3,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000060 AND creator = 'CloudMold' AND deleted = b'0';

UPDATE system_menu
SET parent_id = 9100000000170, name = '支付记录', path = 'payments', sort = 1,
    status = 0, visible = b'1', updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000042 AND creator = 'CloudMold' AND deleted = b'0';

-- Add pages that already have a backend query surface and a completed frontend.
INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT candidate.id, candidate.name, candidate.permission, 2, candidate.sort,
       candidate.parent_id, candidate.path, candidate.icon, candidate.component,
       candidate.component_name, 0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:full-business-centers', UTC_TIMESTAMP(6), b'0'
FROM (
    SELECT 9100000000300 AS id, '运营智能' AS name,
           'cloudmold:operations-intelligence:query' AS permission, 2 AS sort,
           9100000000100 AS parent_id, 'operations-intelligence' AS path,
           'lucide:brain-circuit' AS icon,
           'cloudmold/operations-intelligence/index' AS component,
           'CloudMoldOperationsAlert' AS component_name
    UNION ALL SELECT 9100000000301, '元数据治理', 'cloudmold:metadata:query', 3,
           9100000000100, 'metadata', 'lucide:network',
           'cloudmold/metadata/index', 'CloudMoldMetadataDefinition'
    UNION ALL SELECT 9100000000302, '事件与数据契约', 'cloudmold:data-readiness:query', 4,
           9100000000100, 'data-contract', 'lucide:workflow',
           'cloudmold/data-contract/index', 'CloudMoldEventOutbox'
    UNION ALL SELECT 9100000000303, 'AI 运营', 'cloudmold:ai-operations:query', 5,
           9100000000100, 'ai-operations', 'lucide:bot',
           'cloudmold/ai-operations/index', 'CloudMoldAiWorkflowRun'
    UNION ALL SELECT 9100000000310, '营销活动', 'cloudmold:promotion:query', 1,
           9100000000120, 'promotions', 'lucide:badge-percent',
           'cloudmold/promotion/index', 'CloudMoldPromotionCampaign'
    UNION ALL SELECT 9100000000311, '用户互动', 'cloudmold:engagement:favorite:query', 2,
           9100000000120, 'engagement', 'lucide:heart-handshake',
           'cloudmold/engagement/index', 'CloudMoldEngagementCampaign'
    UNION ALL SELECT 9100000000312, '行为分析', 'cloudmold:commerce-behavior:query', 3,
           9100000000120, 'behavior', 'lucide:mouse-pointer-click',
           'cloudmold/commerce-behavior/index', 'CloudMoldCommerceBehaviorEvent'
    UNION ALL SELECT 9100000000313, '游戏化增长', 'cloudmold:gamification:query', 4,
           9100000000120, 'gamification', 'lucide:gamepad-2',
           'cloudmold/gamification/index', 'CloudMoldGamificationAccount'
    UNION ALL SELECT 9100000000320, '供应链控制塔', '', 1,
           9100000000140, 'overview', 'lucide:radar',
           'cloudmold/supply-chain/index', 'CloudMoldSupplyChainControlTower'
    UNION ALL SELECT 9100000000330, '鉴别质检工作台', '', 1,
           9100000000150, 'workbench', 'lucide:scan-search',
           'cloudmold/quality/index', 'CloudMoldQualityControlCenter'
    UNION ALL SELECT 9100000000340, '客户服务', 'cloudmold:customer-service:query', 4,
           9100000000130, 'customer-service', 'lucide:headset',
           'cloudmold/customer-service/index', 'CloudMoldCustomerServiceTicket'
    UNION ALL SELECT 9100000000350, '信任风控', 'cloudmold:risk:query', 1,
           9100000000160, 'risk-review', 'lucide:shield-alert',
           'cloudmold/risk/index', 'CloudMoldRiskReview'
    UNION ALL SELECT 9100000000360, '令牌与账户', 'cloudmold:token-platform:query', 2,
           9100000000170, 'token-platform', 'lucide:coins',
           'cloudmold/token-platform/index', 'CloudMoldTokenPlatformAccount'
) candidate
LEFT JOIN system_menu existing ON existing.id = candidate.id
WHERE existing.id IS NULL;

-- Expose both read and command permissions below the operational pages. Roles
-- can therefore be granted least privilege without changing the yudao engine.
INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT candidate.id, candidate.name, candidate.permission, 3, candidate.sort,
       candidate.parent_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       'CloudMold', UTC_TIMESTAMP(6), 'CloudMold:full-business-centers',
       UTC_TIMESTAMP(6), b'0'
FROM (
    SELECT 9100000000400 AS id, '运营智能查询' AS name,
           'cloudmold:operations-intelligence:query' AS permission, 1 AS sort,
           9100000000300 AS parent_id
    UNION ALL SELECT 9100000000401, '运营智能操作',
           'cloudmold:operations-intelligence:command', 2, 9100000000300
    UNION ALL SELECT 9100000000402, '元数据查询',
           'cloudmold:metadata:query', 1, 9100000000301
    UNION ALL SELECT 9100000000403, '元数据操作',
           'cloudmold:metadata:command', 2, 9100000000301
    UNION ALL SELECT 9100000000404, '事件外发查询',
           'cloudmold:data-readiness:query', 1, 9100000000302
    UNION ALL SELECT 9100000000405, 'AI 运营查询',
           'cloudmold:ai-operations:query', 1, 9100000000303
    UNION ALL SELECT 9100000000406, 'AI 运营操作',
           'cloudmold:ai-operations:command', 2, 9100000000303
    UNION ALL SELECT 9100000000410, '营销活动查询',
           'cloudmold:promotion:query', 1, 9100000000310
    UNION ALL SELECT 9100000000411, '营销活动操作',
           'cloudmold:promotion:command', 2, 9100000000310
    UNION ALL SELECT 9100000000412, '收藏查询',
           'cloudmold:engagement:favorite:query', 1, 9100000000311
    UNION ALL SELECT 9100000000413, '收藏操作',
           'cloudmold:engagement:favorite:write', 2, 9100000000311
    UNION ALL SELECT 9100000000414, '通知查询',
           'cloudmold:engagement:notification:query', 3, 9100000000311
    UNION ALL SELECT 9100000000415, '通知操作',
           'cloudmold:engagement:notification:write', 4, 9100000000311
    UNION ALL SELECT 9100000000416, '社区查询',
           'cloudmold:engagement:community:query', 5, 9100000000311
    UNION ALL SELECT 9100000000417, '社区操作',
           'cloudmold:engagement:community:write', 6, 9100000000311
    UNION ALL SELECT 9100000000418, '行为查询',
           'cloudmold:commerce-behavior:query', 1, 9100000000312
    UNION ALL SELECT 9100000000419, '行为操作',
           'cloudmold:commerce-behavior:command', 2, 9100000000312
    UNION ALL SELECT 9100000000420, '游戏化查询',
           'cloudmold:gamification:query', 1, 9100000000313
    UNION ALL SELECT 9100000000421, '游戏化操作',
           'cloudmold:gamification:command', 2, 9100000000313
    UNION ALL SELECT 9100000000422, '供应链库存查询',
           'cloudmold:inventory:query', 1, 9100000000320
    UNION ALL SELECT 9100000000423, '供应链履约查询',
           'cloudmold:fulfillment:query', 2, 9100000000320
    UNION ALL SELECT 9100000000424, '质量库存查询',
           'cloudmold:inventory:query', 1, 9100000000330
    UNION ALL SELECT 9100000000425, '质量售后查询',
           'cloudmold:aftersale:query', 2, 9100000000330
    UNION ALL SELECT 9100000000426, '客户服务查询',
           'cloudmold:customer-service:query', 1, 9100000000340
    UNION ALL SELECT 9100000000427, '客户服务操作',
           'cloudmold:customer-service:command', 2, 9100000000340
    UNION ALL SELECT 9100000000428, '信任风控查询',
           'cloudmold:risk:query', 1, 9100000000350
    UNION ALL SELECT 9100000000429, '信任风控操作',
           'cloudmold:risk:command', 2, 9100000000350
    UNION ALL SELECT 9100000000430, '令牌账户查询',
           'cloudmold:token-platform:query', 1, 9100000000360
    UNION ALL SELECT 9100000000431, '令牌账户操作',
           'cloudmold:token-platform:command', 2, 9100000000360
) candidate
LEFT JOIN system_menu existing ON existing.id = candidate.id
WHERE existing.id IS NULL;

-- Keep aggregate/experimental entries for compatible bookmarks, but avoid
-- duplicate navigation until their production admission gates pass.
UPDATE system_menu
SET visible = b'0',
    updater = 'CloudMold:full-business-centers',
    update_time = UTC_TIMESTAMP(6)
WHERE id IN (
    9100000000020,
    9100000000070, 9100000000071, 9100000000072, 9100000000073
)
  AND creator = 'CloudMold'
  AND deleted = b'0';
