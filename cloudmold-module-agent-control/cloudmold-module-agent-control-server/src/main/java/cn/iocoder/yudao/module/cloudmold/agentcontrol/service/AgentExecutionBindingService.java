package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionBindingApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionBindingCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionRuntimeApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskTerminalProofView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class AgentExecutionBindingService implements AgentExecutionBindingApi {
    private final AgentControlStoreMapper mapper;
    private final SkillTaskQueryApi skillTasks;
    private final MissionRuntimeApi missionRuntime;
    private final Clock clock;

    @Autowired
    public AgentExecutionBindingService(AgentControlStoreMapper mapper, SkillTaskQueryApi skillTasks,
                                        MissionRuntimeApi missionRuntime) {
        this(mapper, skillTasks, missionRuntime, Clock.systemUTC());
    }

    AgentExecutionBindingService(AgentControlStoreMapper mapper, SkillTaskQueryApi skillTasks,
                                 MissionRuntimeApi missionRuntime, Clock clock) {
        this.mapper = mapper;
        this.skillTasks = skillTasks;
        this.missionRuntime = missionRuntime;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult bind(AgentExecutionBindingCommand command, Long operatorUserId) {
        require(command != null, "command is required");
        requireActor(operatorUserId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = now();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                requireRef(command.getWorkOrderId(), "workOrderId")), "work order not found");
        require(Objects.equals(command.getWorkOrderExpectedVersion(), workOrder.getVersion()),
                "workOrderExpectedVersion is stale");
        require("IN_PROGRESS".equals(workOrder.getStatus()), "execution binding requires IN_PROGRESS work order");
        require(Boolean.TRUE.equals(workOrder.getExecutionRequired()), "work order has no frozen execution requirement");
        require(operatorUserId.equals(workOrder.getAssigneeUserId()), "only the work-order assignee can bind execution");
        requireRoleGrant(tenantId, operatorUserId, workOrder.getRoleCode(), now);
        ExecutionBinding previous = mapper.selectLatestExecutionBindingForUpdate(tenantId, workOrder.getWorkOrderId());
        int generation = command.getExecutionGeneration() == null ? 1 : command.getExecutionGeneration();
        if (previous == null) {
            require(generation == 1, "initial executionGeneration must be 1");
            require(command.getSupersededBindingId() == null, "initial binding must not supersede another binding");
        } else {
            require(Objects.equals(command.getSupersededBindingId(), previous.getBindingId()),
                    "rebind must name the latest execution binding");
            require(generation == previous.getExecutionGeneration() + 1,
                    "rebind executionGeneration must increment by one");
            require("BOUND".equals(previous.getStatus()), "only a BOUND execution can be superseded");
            requireText(command.getSupersedeReasonCode(), "supersedeReasonCode", 128);
            SkillTaskView previousTask = querySkillTask(tenantId, operatorUserId, workOrder,
                    () -> skillTasks.get(previous.getSkillTaskId()));
            boolean staleRunBinding = !Objects.equals(previousTask.getRunId(), workOrder.getActiveRunId());
            if (!staleRunBinding) {
                require("NEEDS_REVIEW".equals(previousTask.getStatus()),
                        "only a NEEDS_REVIEW Skill Task can be replaced");
            }
            require(!Objects.equals(previous.getSkillTaskId(), command.getSkillTaskId()),
                    "replacement Skill Task must be a new task");
        }
        String skillTaskId = requireRef(command.getSkillTaskId(), "skillTaskId");
        SkillTaskView task = querySkillTask(tenantId, operatorUserId, workOrder,
                () -> skillTasks.get(skillTaskId));
        verifyFrozenExecution(workOrder, task);
        ExecutionBinding binding = new ExecutionBinding().setBindingId(valueOrUuid(command.getBindingId()))
                .setTenantId(tenantId).setWorkOrderId(workOrder.getWorkOrderId())
                .setExecutionGeneration(generation)
                .setSkillTaskId(task.getTaskId()).setSkillId(task.getSkillId()).setSkillVersion(task.getSkillVersion())
                .setSkillDefinitionClosureSha256(task.getDefinitionClosureSha256())
                .setInputSha256(task.getInputSha256()).setStatus("BOUND").setVersion(1L)
                .setBoundAt(now).setUpdatedAt(now);
        require(binding.getExecutionGeneration() > 0, "executionGeneration must be positive");
        if (previous != null) {
            require(mapper.supersedeExecutionBinding(tenantId, previous.getBindingId(), previous.getVersion(), now) == 1,
                    "failed to supersede the previous execution binding");
            appendAudit(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(), operatorUserId,
                    "agent_control.execution.superseded", Map.of("bindingId", previous.getBindingId(),
                            "skillTaskId", previous.getSkillTaskId(),
                            "reasonCode", command.getSupersedeReasonCode()), now);
        }
        require(mapper.insertExecutionBinding(binding) == 1, "failed to persist immutable execution binding");
        appendAudit(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(), operatorUserId,
                "agent_control.execution.bound", Map.of("bindingId", binding.getBindingId(),
                        "skillTaskId", binding.getSkillTaskId()), now);
        return result("execution_binding", binding.getBindingId(), 1L, "BOUND");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult reconcile(String bindingId, Long operatorUserId) {
        requireActor(operatorUserId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = now();
        ExecutionBinding binding = requireNonNull(mapper.selectExecutionBindingForUpdate(tenantId,
                requireRef(bindingId, "bindingId")), "execution binding not found");
        if ("EXECUTION_SUCCEEDED".equals(binding.getStatus())) {
            return result("execution_binding", binding.getBindingId(), binding.getVersion(), binding.getStatus());
        }
        require("BOUND".equals(binding.getStatus()), "execution binding is not reconcilable");
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, binding.getWorkOrderId()),
                "work order not found");
        require("IN_PROGRESS".equals(workOrder.getStatus()), "work order is not awaiting execution proof");
        require(operatorUserId.equals(workOrder.getAssigneeUserId()), "only the work-order assignee can reconcile");
        requireRoleGrant(tenantId, operatorUserId, workOrder.getRoleCode(), now);
        SkillTaskTerminalProofView proof = querySkillTask(tenantId, operatorUserId, workOrder,
                () -> skillTasks.getTerminalProof(binding.getSkillTaskId()));
        require(Objects.equals(workOrder.getActiveRunId(), proof.getRunId()),
                "terminal proof belongs to a stale Agent run");
        verifyTerminalProof(binding, proof);
        require(mapper.acceptExecutionBinding(tenantId, binding.getBindingId(), binding.getVersion(),
                proof.getTerminalResultSha256(), now) == 1, "execution binding acceptance conflict");
        String resultId = UUID.randomUUID().toString();
        String outcomeCode = normalizedOutcome("EXECUTION_SUCCEEDED");
        String summary = "岗位动作已由受控 SkillTask 执行并通过终态凭证核验";
        BusinessResult businessResult = new BusinessResult().setResultId(resultId).setTenantId(tenantId)
                .setWorkOrderId(workOrder.getWorkOrderId()).setOutcomeCode(outcomeCode).setSummary(summary)
                .setEvidenceRef("skilltask:" + proof.getTaskId() + ":sha256:" + proof.getTerminalResultSha256())
                .setRecordedByUserId(operatorUserId).setRecordedAt(now);
        require(mapper.insertBusinessResult(businessResult) == 1, "failed to persist verified business result");
        appendAudit(tenantId, businessResult.getResultId(), 1L, operatorUserId,
                "agent_control.business_result.recorded", Map.of("workOrderId", workOrder.getWorkOrderId(),
                        "bindingId", binding.getBindingId()), now, "business_result");
        require(mapper.transitionWorkOrder(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                "IN_PROGRESS", "COMPLETED", operatorUserId, null, now) == 1,
                "verified work-order completion conflict");
        appendAudit(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion() + 1, operatorUserId,
                "agent_control.execution.verified", Map.of("bindingId", binding.getBindingId(),
                        "terminalResultSha256", proof.getTerminalResultSha256()), now);
        if (workOrder.getMissionId() != null) {
            missionRuntime.resolveCompletedWorkOrder(workOrder.getWorkOrderId());
        }
        return result("execution_binding", binding.getBindingId(), binding.getVersion() + 1,
                "EXECUTION_SUCCEEDED");
    }

    private void verifyFrozenExecution(WorkOrder workOrder, SkillTaskView task) {
        require(Objects.equals(workOrder.getActiveRunId(), task.getRunId()),
                "Skill Task does not belong to the current Agent run");
        require(Objects.equals(workOrder.getSkillId(), task.getSkillId()), "Skill ID does not match frozen action");
        require(Objects.equals(workOrder.getSkillVersion(), task.getSkillVersion()),
                "Skill version does not match frozen action");
        require(Objects.equals(workOrder.getSkillDefinitionClosureSha256(), task.getDefinitionClosureSha256()),
                "Skill definition closure does not match frozen action");
        require(Objects.equals(workOrder.getExecutionInputSha256(), task.getInputSha256()),
                "Skill input does not match server-frozen work-order input");
        require(Objects.equals(workOrder.getRiskLevel(), task.getRiskLevel()), "Skill risk does not match action policy");
    }

    private void verifyTerminalProof(ExecutionBinding binding, SkillTaskTerminalProofView proof) {
        require("SUCCEEDED".equals(proof.getStatus()), "Skill Task is not SUCCEEDED");
        require(Objects.equals(binding.getTenantId(), proof.getTenantId()), "terminal proof tenant mismatch");
        require(Objects.equals(binding.getSkillTaskId(), proof.getTaskId()), "terminal proof task mismatch");
        require(Objects.equals(binding.getSkillId(), proof.getSkillId()), "terminal proof Skill mismatch");
        require(Objects.equals(binding.getSkillVersion(), proof.getSkillVersion()), "terminal proof version mismatch");
        require(Objects.equals(binding.getSkillDefinitionClosureSha256(), proof.getDefinitionClosureSha256()),
                "terminal proof definition closure mismatch");
        require(Objects.equals(binding.getInputSha256(), proof.getInputSha256()), "terminal proof input mismatch");
        require(proof.getTerminalResultSha256() != null && proof.getTerminalResultSha256().matches("[0-9a-f]{64}"),
                "terminal result hash is invalid");
    }

    private void requireRoleGrant(Long tenantId, Long actorId, String roleCode, LocalDateTime now) {
        require(mapper.selectEffectiveActorRoleGrant(tenantId, actorId, roleCode, now) != null,
                "authenticated actor has no effective role grant");
    }

    private <T> T querySkillTask(Long tenantId, Long operatorUserId, WorkOrder workOrder, Supplier<T> query) {
        String runId = requireRef(workOrder.getActiveRunId(), "activeRunId");
        String skillId = requireRef(workOrder.getSkillId(), "skillId");
        CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(tenantId, operatorUserId,
                UserTypeEnum.ADMIN.getValue(), skillId, runId);
        try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(context)) {
            return query.get();
        }
    }

    private void appendAudit(Long tenantId, String workOrderId, Long version, Long actorId, String eventType,
                             Map<String, ?> detail, LocalDateTime now) {
        appendAudit(tenantId, workOrderId, version, actorId, eventType, detail, now, "role_work_order");
    }

    private void appendAudit(Long tenantId, String aggregateId, Long version, Long actorId, String eventType,
                             Map<String, ?> detail, LocalDateTime now, String aggregateType) {
        AuditEvent audit = new AuditEvent().setAuditEventId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAggregateType(aggregateType).setAggregateId(aggregateId).setAggregateVersion(version)
                .setEventType(eventType).setActorUserId(actorId).setDetailJson(JsonUtils.toJsonString(detail))
                .setOccurredAt(now).setCreatedAt(now);
        require(mapper.insertAuditEvent(audit) == 1, "failed to append execution audit");
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static void requireActor(Long value) { require(value != null && value > 0, "operatorUserId is required"); }
    private static String normalizedOutcome(String value) { return value; }
    private static String requireRef(String value, String field) {
        require(value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}"), "invalid " + field);
        return value;
    }
    private static String valueOrUuid(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : requireRef(value, "id"); }
    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, "invalid " + field);
    }
    private static <T> T requireNonNull(T value, String message) { require(value != null, message); return value; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static AgentControlResult result(String type, String id, Long version, String status) {
        return AgentControlResult.builder().aggregateType(type).aggregateId(id).aggregateVersion(version)
                .status(status).duplicate(false).build();
    }
}
