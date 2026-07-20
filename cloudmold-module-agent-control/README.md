# CloudMold Agent Autonomy Kernel（休眠候选）

> 未完成真实安全端点 smoke、重启 E2E 和业务数据闭环前，不得把本模块装配到 `yudao-server`，也不得通过 MCP 暴露。

该 CloudMold 红区模块现在包含三层权威能力：

- Agent Control：岗位、动作策略、主体岗位授权、独立审批授权、工作单、后继式交接、审批、结果和审计。
- Execution Binding：动作策略冻结 Skill ID/版本/定义闭包哈希/输入哈希；可执行工作单只能由精确匹配的 SkillTask 终态凭证完成。
- Mission Runtime：Mission、Goal、工作依赖、事件订阅、定时器、Agent Run、租约、单调 fencing token、checkpoint、Inbox/Outbox 和恢复 reconciler。

首个固定模板为 `mission.inventory-stockout-response.v1`，编译为：

```text
库控缺断码诊断
→ 买手制定补货方案
→ R3 补货执行
→ 库控验证到货与库存
→ 客服影响处置
```

模板持久化 5 个独立岗位工作单、4 条依赖和 3 条跨岗位交接。交接不会再改写来源工作单的岗位或动作；目标岗位拥有新的工作单、策略快照和审批边界。

已实现的关键不变量：

- 租户来自 `TenantContextHolder`，业务主体必须有当前有效岗位授权；授权读取使用事务共享锁，与并发撤权线性化。
- 审批权与执行权分离，审批授权精确绑定 approval、role、action、risk 和冻结 scope。
- 审批人不需要、也不会因为审批而获得对应岗位的执行授权；Mission 到达审批节点时自动创建审批单和待办事件，但精确审批授权仍由独立治理面签发。
- R3 必须独立审批；调用方时间不能回填数据库与审计时间。
- 可执行工作单拒绝调用方 `evidenceRef`，仅接受 SkillTask `SUCCEEDED` 终态凭证。
- 失败进入 `NEEDS_REVIEW` 的 SkillTask 可以被显式换代：旧绑定标记为 `SUPERSEDED`，新绑定必须使用连续 generation 和新的任务 ID。
- SkillTask 凭证覆盖定义闭包、输入和有序步骤/子任务结果哈希。
- 每个后继工作单都从前序 BusinessResult 和结构化 Checkpoint 派生业务上下文；可执行后继的输入哈希在派生后重新冻结，缺少结构化计划时禁止前序工作完成。
- 同一工作单同一时刻只有一个有效运行租约；接管会增加 fencing token，旧 Worker 不能写 checkpoint。
- 业务事件通过 Inbox 去重；事件订阅只支持服务端固定的 `EXACT_AGGREGATE` 匹配器。
- 定时器和已完成依赖由可开关的 reconciler 自动唤醒，默认开关关闭。
- Mission Runtime 状态变化写入独立 Agent Control Outbox；审计记录不冒充集成事件。

运行开关：

```yaml
cloudmold:
  agent-control:
    runtime:
      enabled: false
      reconcile-delay-ms: 2000
```

当前限制：

- 模块仍未装配进正式应用，因此没有生产 API、菜单或 DeerFlow adapter；当前是可验证的休眠实现，不是已上线自治平台。
- 缺断码模板、租约、事件和依赖恢复已通过单测与隔离 MySQL 演练，但尚未完成真实服务重启 E2E。
- `buyer.execute-replenishment` 只能在本地测试中绑定 Legacy ERP Skill；规范 Procurement SoR 和预算账本未完成前，真实采购保持 R3 人工审批并关闭无人生产写入。
- 自动授权过期尚未逐条产生 lifecycle audit；当前过期授权会失效，但上线前仍需补审计投影。

剩余激活条件见 [NEXT_SLICE.md](NEXT_SLICE.md)。
