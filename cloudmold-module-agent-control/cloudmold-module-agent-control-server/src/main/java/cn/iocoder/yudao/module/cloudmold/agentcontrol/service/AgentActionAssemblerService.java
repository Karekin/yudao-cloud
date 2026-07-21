package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.AgentControlProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ExecutionBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class AgentActionAssemblerService implements AgentActionAssemblyApi {

    static final String STOCKOUT_ACTION = "inventory.detect-size-stockout";
    static final String STOCKOUT_SKILL = "skill.cloudmold.inventory.stockout-diagnosis.v1";
    static final String STOCKOUT_SKILL_VERSION = "1.0.0";

    private final AgentControlStoreMapper mapper;
    private final SkillTaskCommandApi skillTasks;
    private final AgentExecutionBindingApi executionBindings;
    private final AgentControlProperties properties;

    public AgentActionAssemblerService(AgentControlStoreMapper mapper, SkillTaskCommandApi skillTasks,
                                       AgentExecutionBindingApi executionBindings,
                                       AgentControlProperties properties) {
        this.mapper = mapper;
        this.skillTasks = skillTasks;
        this.executionBindings = executionBindings;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentActionAssemblyResult assembleAndSubmit(AgentActionAssemblyCommand command, Long actorUserId) {
        require(properties.isActionSubmissionEnabled(), "Agent action submission is disabled");
        require(command != null, "command is required");
        require(actorUserId != null && actorUserId > 0, "actorUserId is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                requireRef(command.getWorkOrderId(), "workOrderId")), "work order not found");
        require(Objects.equals(command.getWorkOrderExpectedVersion(), workOrder.getVersion()),
                "workOrderExpectedVersion is stale");
        require("IN_PROGRESS".equals(workOrder.getStatus()), "action submission requires IN_PROGRESS work order");
        require(actorUserId.equals(workOrder.getAssigneeUserId()), "actor is not the work-order assignee");
        require(mapper.selectEffectiveActorRoleGrant(tenantId, actorUserId, workOrder.getRoleCode(),
                LocalDateTime.now(ZoneOffset.UTC)) != null, "actor has no effective role grant");
        require(Boolean.TRUE.equals(workOrder.getExecutionRequired()), "work order has no executable action");
        require(STOCKOUT_ACTION.equals(workOrder.getActionCode()), "action is not assembled by this release");
        require("R1".equals(workOrder.getRiskLevel()), "stockout diagnosis must remain R1 read-only");
        require(STOCKOUT_SKILL.equals(workOrder.getSkillId())
                        && STOCKOUT_SKILL_VERSION.equals(workOrder.getSkillVersion()),
                "work order does not use the fixed stockout diagnosis Skill");
        require(workOrder.getActiveRunId() != null, "mission work order has no active Agent run");

        String inputJson = AgentControlJson.canonicalBusinessObject(workOrder.getBusinessContextJson(),
                "businessContextJson");
        require(Objects.equals(workOrder.getExecutionInputSha256(), AgentControlJson.sha256(inputJson)),
                "work-order execution input is not the canonical server-frozen business context");
        String requestKey = "agent-control:" + workOrder.getWorkOrderId() + ":execution:1";
        SkillTaskView task;
        CloudMoldRpcCallContext rpcContext = new CloudMoldRpcCallContext(tenantId, actorUserId,
                UserTypeEnum.ADMIN.getValue(), STOCKOUT_SKILL, workOrder.getActiveRunId());
        try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(rpcContext)) {
            task = skillTasks.submit(SkillTaskSubmitCommand.builder().skillId(STOCKOUT_SKILL)
                    .skillVersion(STOCKOUT_SKILL_VERSION).runId(workOrder.getActiveRunId())
                    .clientRequestKey(requestKey).inputJson(inputJson).riskLevel("R1").approvalRef(null).build());
        }

        ExecutionBinding existing = mapper.selectLatestExecutionBindingForUpdate(tenantId, workOrder.getWorkOrderId());
        if (existing != null) {
            require(Objects.equals(existing.getSkillTaskId(), task.getTaskId()),
                    "work order is already bound to a different Skill Task");
            return result(workOrder, task.getTaskId(), existing.getBindingId(), existing.getStatus(), true);
        }
        AgentControlResult binding = executionBindings.bind(AgentExecutionBindingCommand.builder()
                .workOrderId(workOrder.getWorkOrderId()).workOrderExpectedVersion(workOrder.getVersion())
                .executionGeneration(1).skillTaskId(task.getTaskId()).build(), actorUserId);
        return result(workOrder, task.getTaskId(), binding.getAggregateId(), binding.getStatus(), false);
    }

    private static AgentActionAssemblyResult result(WorkOrder workOrder, String taskId, String bindingId,
                                                    String status, boolean duplicate) {
        return AgentActionAssemblyResult.builder().workOrderId(workOrder.getWorkOrderId())
                .actionCode(workOrder.getActionCode()).skillTaskId(taskId).executionBindingId(bindingId)
                .status(status).duplicate(duplicate).build();
    }

    private static String requireRef(String value, String field) {
        require(value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}"), "invalid " + field);
        return value;
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

}
