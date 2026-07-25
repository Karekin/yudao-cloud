# Agent Autonomy Kernel 激活门禁

## 已完成的休眠候选能力

- [x] 主体到岗位的租户级、版本化、可撤销、带有效期授权。
- [x] 独立审批授权，精确绑定 approval、role、action、risk 和 scope。
- [x] 治理端点与普通工作命令分离，拒绝自授权、自审批和治理绕过。
- [x] 每个工作命令 fail-closed 校验岗位授权；授权读取与并发撤权加锁。
- [x] SkillTask 定义闭包哈希、任务级终态结果哈希和只读 terminal proof API。
- [x] 可执行动作策略、工作单快照、ExecutionBinding 和服务端生成 BusinessResult。
- [x] 可执行工作单拒绝调用方 evidence；错任务、错版本、错输入、错定义和非终态均拒绝。
- [x] `NEEDS_REVIEW` 执行支持显式 supersede/rebind，generation 连续递增且旧任务不能再次完成工作单。
- [x] BusinessMission、Goal、Dependency、EventSubscription、Timer、Run、Lease、Checkpoint、Inbox/Outbox。
- [x] 固定缺断码模板：5 个岗位工作单、4 条依赖、3 个后继式跨岗位交接。
- [x] 前序结构化 Checkpoint 派生并冻结后继输入；R3 后继自动创建审批请求，审批人不继承执行岗位权限。
- [x] 事件去重、定时唤醒、依赖恢复、租约接管和 fencing token 负向测试。
- [x] 模块/中央迁移镜像、隔离 MySQL 非空夹具和 35 项 DQC 全 0。
- [x] CI 编译测试默认休眠候选；模块已装配但所有运行开关默认关闭。
- [x] P1.0 首切片：隔离 Maven BPM profile、Approval/ProcessInstance 绑定、单次启动 claim、
  重复/乱序终态事件收据和防越权候选状态。

## P0 激活前仍必须完成

- [ ] 真实 secured endpoint smoke：无授权主体、伪造 terminal proof、scope 漂移全部从 HTTP/Dubbo 入口拒绝。
- [x] keyed `cma2` 兼容升级：key ID、issuedAt、key ring 轮换、硬撤销、key/permit 到期与旧 `cma1`
  显式关闭均有自动化负向测试；审计只记录版本、key ID 与票据摘要。
- [ ] Agent Control 签发、SkillTask 公钥验签的非共享密钥 execution permit；当前 HMAC approval 不能作为最终跨服务信任模型。
- [ ] 至少一次真实进程重启 E2E：提交后写回前、SkillTask 成功后工作单完成前、事件匹配后唤醒前。
- [ ] 为授权自动过期追加逐条 lifecycle audit，并补非空 DQC。
- [ ] 通过激活评审后，才允许打开生产运行开关；MCP 暴露需要独立评审。

## P1.0 BPM 审批融合仍需完成

- [ ] 在 yudao BPM 中发布固定 key `cloudmold-agent-approval-v1` 的流程定义，并冻结 definition id/version 证据。
- [ ] 建立 BPM task approver 到 Agent Control exact approval grant 的实名 attestation，禁止只凭流程终态放行。
- [ ] 完成模块迁移的中央 V91+ 镜像、回滚、checksum 和 DQC；不得与并行 V83–V90 抢序号。
- [ ] 完成真实审批 E2E：请求、待办、不同审批人通过/拒绝、重复回调、服务重启和 `START_UNCERTAIN` 人工对账。
- [ ] DeerFlow 只调用受治理的请求/查询工具，不保存或改写 BPM、Approval、Risk 与 SkillTask 权威状态。

## 首条真实业务 E2E

- [ ] 注册确定性 `skill.cloudmold.inventory.stockout-diagnosis.v1`，读取真实 SKU+尺码库存事实。
- [ ] 建立版本化 ActionAssembler；模型不得传 tenant、operator、run、approval 或 SkillTask 原始参数。
- [ ] 将 Legacy ERP 采购场景注册为明确标记 `LOCAL_TEST / LEGACY_ERP_ADAPTER` 的 SkillTask。
- [ ] 补 `legacy.erp.purchase_in.status_changed` Outbox 事件，不能冒充规范库存事件。
- [ ] 验证唯一一次 R3 采购审批、重复 Timer/Event 不重复采购、到货后自动唤醒库控。
- [ ] 客服无受影响订单时也生成 `NO_CUSTOMER_IMPACT` 结果。
- [ ] Procurement SoR、供应商权威和预算账本完成前，不得开放无人生产采购。

## 长期演进

- [ ] DeerFlow 仅实现 `RoleAgentRuntimePort`，不保存 Mission、WorkOrder、Approval、Timer 或领域权威。
- [ ] 增加超时升级、暂停/恢复、取消、补偿和独立预算额度。
- [ ] 增加岗位收件箱、审批卡、结果卡和经营总控 Mission 看板。
- [ ] 用同一 correlation/run 对账 Agent Control、SkillTask、ERP、领域 Outbox 和 StarRocks。
