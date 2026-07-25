-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Eight centers are visible and ordered exactly once below CloudMold.
SELECT expected.id, expected.name
FROM (
    SELECT 9100000000100 AS id, '经营驾驶舱' AS name, 1 AS sort, 'executive' AS path
    UNION ALL SELECT 9100000000110, '商家经营中心', 2, 'merchant-center'
    UNION ALL SELECT 9100000000120, '增长营销', 3, 'growth'
    UNION ALL SELECT 9100000000130, '买家旅程', 4, 'buyer-journey'
    UNION ALL SELECT 9100000000140, '供应链', 5, 'supply-chain'
    UNION ALL SELECT 9100000000150, '鉴别质检中心', 6, 'quality-center'
    UNION ALL SELECT 9100000000160, '信任保障', 7, 'trust'
    UNION ALL SELECT 9100000000170, '财务与风控', 8, 'finance-risk'
) expected
LEFT JOIN system_menu actual
  ON actual.id = expected.id
 AND actual.name = expected.name
 AND actual.sort = expected.sort
 AND actual.path = expected.path
 AND actual.parent_id = 9100000000000
 AND actual.type = 1
 AND actual.status = 0
 AND actual.visible = b'1'
 AND actual.deleted = b'0'
WHERE actual.id IS NULL;

-- All business pages sit below the intended center and are navigable.
SELECT expected.id, expected.name
FROM (
    SELECT 9100000000030 AS id, 9100000000100 AS parent_id, '数据健康' AS name
    UNION ALL SELECT 9100000000300, 9100000000100, '运营智能'
    UNION ALL SELECT 9100000000301, 9100000000100, '元数据治理'
    UNION ALL SELECT 9100000000302, 9100000000100, '事件与数据契约'
    UNION ALL SELECT 9100000000303, 9100000000100, 'AI 运营'
    UNION ALL SELECT 9100000000050, 9100000000110, '商家管理'
    UNION ALL SELECT 9100000000080, 9100000000110, '经营主体与授权'
    UNION ALL SELECT 9100000000001, 9100000000110, '商品管理'
    UNION ALL SELECT 9100000000040, 9100000000110, '渠道商品'
    UNION ALL SELECT 9100000000310, 9100000000120, '营销活动'
    UNION ALL SELECT 9100000000311, 9100000000120, '用户互动'
    UNION ALL SELECT 9100000000312, 9100000000120, '行为分析'
    UNION ALL SELECT 9100000000313, 9100000000120, '游戏化增长'
    UNION ALL SELECT 9100000000041, 9100000000130, '订单管理'
    UNION ALL SELECT 9100000000043, 9100000000130, '发货履约'
    UNION ALL SELECT 9100000000044, 9100000000130, '售后退款'
    UNION ALL SELECT 9100000000340, 9100000000130, '客户服务'
    UNION ALL SELECT 9100000000320, 9100000000140, '供应链控制塔'
    UNION ALL SELECT 9100000000010, 9100000000140, '库存管理'
    UNION ALL SELECT 9100000000060, 9100000000140, '仓库与库位'
    UNION ALL SELECT 9100000000330, 9100000000150, '鉴别质检工作台'
    UNION ALL SELECT 9100000000350, 9100000000160, '信任风控'
    UNION ALL SELECT 9100000000042, 9100000000170, '支付记录'
    UNION ALL SELECT 9100000000360, 9100000000170, '令牌与账户'
) expected
LEFT JOIN system_menu actual
  ON actual.id = expected.id
 AND actual.parent_id = expected.parent_id
 AND actual.name = expected.name
 AND actual.type = 2
 AND actual.status = 0
 AND actual.visible = b'1'
 AND actual.deleted = b'0'
WHERE actual.id IS NULL;

-- No sibling route path may collide in dynamic routing.
SELECT parent_id, path, COUNT(*) AS duplicate_count
FROM system_menu
WHERE id BETWEEN 9100000000000 AND 9100000000999
  AND deleted = b'0'
  AND visible = b'1'
  AND type IN (1, 2)
GROUP BY parent_id, path
HAVING COUNT(*) > 1;

-- Operational pages must expose all declared least-privilege permissions.
SELECT expected.permission
FROM (
    SELECT 'cloudmold:operations-intelligence:query' AS permission
    UNION ALL SELECT 'cloudmold:metadata:query'
    UNION ALL SELECT 'cloudmold:data-readiness:query'
    UNION ALL SELECT 'cloudmold:ai-operations:query'
    UNION ALL SELECT 'cloudmold:promotion:query'
    UNION ALL SELECT 'cloudmold:engagement:favorite:query'
    UNION ALL SELECT 'cloudmold:engagement:notification:query'
    UNION ALL SELECT 'cloudmold:engagement:community:query'
    UNION ALL SELECT 'cloudmold:commerce-behavior:query'
    UNION ALL SELECT 'cloudmold:gamification:query'
    UNION ALL SELECT 'cloudmold:inventory:query'
    UNION ALL SELECT 'cloudmold:fulfillment:query'
    UNION ALL SELECT 'cloudmold:aftersale:query'
    UNION ALL SELECT 'cloudmold:customer-service:query'
    UNION ALL SELECT 'cloudmold:risk:query'
    UNION ALL SELECT 'cloudmold:token-platform:query'
) expected
LEFT JOIN system_menu actual
  ON actual.permission = expected.permission
 AND actual.deleted = b'0'
 AND actual.status = 0
WHERE actual.id IS NULL;
