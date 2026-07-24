# V82 CloudMold 菜单业务中心迁移账本（启动前预检）

- 计划日期：2026-07-24
- 负责人：CloudMold Platform
- 回滚方式：`rollback/V20260724_82__restore_cloudmold_admin_business_navigation.sql`
- 全量资产清单：`deploy/demo/cloudmold-migrations.sha256`

## 1. 变更清单

### SQL 变更

1. `V20260724_82__cloudmold_admin_business_navigation.sql`
2. `rollback/V20260724_82__restore_cloudmold_admin_business_navigation.sql`
3. `V20260724_82__business_navigation_preflight.sql`
4. `tests/cloudmold_admin_business_navigation_dqc.sql`

### 变更范围（系统菜单重构）

- 新增 5 个 CloudMold 业务中心菜单：
  - 商品中心 `9100000000100`
  - 商家与渠道 `9100000000110`
  - 库存与仓储 `9100000000120`
  - 订单与履约 `9100000000130`
  - 数据运营 `9100000000140`
- 重排既有菜单归属与页签名（`CloudMold` 业务中心内）：
  - 商品管理/渠道商品/商家管理/经营主体与授权/库存管理/仓库与库位/订单管理/支付记录/发货履约/售后退款/数据健康
- 旧菜单聚合项 `9100000000020`（交易概览）转为隐藏聚合，用于老书签兼容
- Agent Control 及授权链路保留但默认离线：`status=1` 且 `visible=0`

## 2. SHA-256 校验和（变更上线前锁定）

```text
V20260724_82__cloudmold_admin_business_navigation.sql
  a09920ee6eaeec8895255c05b4e6dd46db9f7ad7839429bab64a8033580320df

rollback/V20260724_82__restore_cloudmold_admin_business_navigation.sql
  21173576cb3ffa3dbae8927c41af7dc937388718ec8ad69ad0736d7d11357c8a

V20260724_82__business_navigation_preflight.sql
  641e261d78455523a7bd386b3bdc15183ead00c7b4e112183aa481c348e70615

tests/cloudmold_admin_business_navigation_dqc.sql
  d9e7626616a5b29fff0900e2cd3957def52fad5c1259461a2bbea6457cd4e5a0
```

## 3. 启动前预检（数据库）

> 所有检查结果应返回 0 行。

- 先执行 `deploy/demo/cloudmold-migration-manifest.sh --check`，拒绝缺失、
  新增或内容被改写的 SQL 资产。
- 发布过程单独应用 `V20260724_82__cloudmold_admin_business_navigation.sql`；
  启动预检不会自动修改数据库。
- 跑 `deploy/demo/database-preflight.sh`；它默认执行
  `V20260724_82__business_navigation_preflight.sql` 零行检查。
- 再以 `tests/cloudmold_admin_business_navigation_dqc.sql` 形成发布证据。
- 检查回退快照表是否完整：
  - `cloudmold_admin_menu_ia_backup` 中 migration_key=`V20260724_82` 行数应为 28
- 启动环境必须通过数据库级别基础预检：MySQL 8+、必要表存在、连接到目标库
- 启动前必须确认 backup 与回滚 SQL 已随同 release 一起归档

### 启动即席 SQL（建议）

```sql
-- 生成本页校验清单（便于记录到 evidence bundle）
SELECT COUNT(*) AS backup_row_count
FROM cloudmold_admin_menu_ia_backup
WHERE migration_key='V20260724_82';

-- 运行 V82 DQC（返回 0 行=通过）
SOURCE sql/cloudmold/tests/cloudmold_admin_business_navigation_dqc.sql;
```

## 4. 发布/回退边界

- 正式发布前确认 `status=1` 的 Agent Control 未出现在可见菜单。
- 回退需先执行：
  1. `rollback/V20260724_82__restore_cloudmold_admin_business_navigation.sql`
  2. 核对旧 `CloudMold` 根菜单恢复状态
  3. 验证新增业务中心菜单不再可见（`9100000000100~9100000000140` 已删除）

## 5. 受控写与负向验收绑定建议（后续切片）

- 该切片仅收口“菜单与入口”；受控写幂等/非法状态/故障恢复在下一 P0 切片做 E2E。
- 旧入口负向证明请以自动化路由测试与浏览器链路双轨记录（见 `cloudmold.test.ts`）。
