package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalScheduleCreateReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleState;
import io.temporal.client.schedules.ScheduleUpdate;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class AiOperationsTemporalScheduleService {

    private final ScheduleClient scheduleClient;
    private final AiOperationsTemporalMapper mapper;
    private final AiOperationsTemporalProperties properties;
    private final AiOperationsManagedRunQueryServiceFacade workflows;

    public List<TemporalScheduleView> list() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return mapper.selectSchedules(tenantId).stream().map(this::view).toList();
    }

    public TemporalScheduleView create(TemporalScheduleCreateReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        ManagedSkillTaskWorkflowView workflow = workflows.requireWorkflow(
                request.getSkillId(), request.getSkillVersion());
        if (Boolean.TRUE.equals(workflow.getApprovalRequired())) {
            require(StrUtil.isNotBlank(request.getRoleCode()) && StrUtil.isNotBlank(request.getActionCode()),
                    "R2/R3 scheduled workflow requires a BPM roleCode and actionCode");
        }
        String scheduleId = temporalScheduleId(tenantId, request.getScheduleId());
        require(mapper.selectSchedule(tenantId, scheduleId) == null, "Temporal schedule already exists");
        TemporalManagedRunRequest workflowRequest = TemporalManagedRunRequest.builder()
                .tenantId(tenantId).scheduleId(scheduleId)
                .skillId(request.getSkillId()).skillVersion(request.getSkillVersion())
                .inputJson(request.getInputJson()).operatorUserId(operatorId)
                .operatorUserType(UserTypeEnum.ADMIN.getValue())
                .roleCode(request.getRoleCode()).actionCode(request.getActionCode()).build();
        ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                .setWorkflowType(TemporalManagedRunWorkflow.class)
                .setArguments(workflowRequest)
                .setOptions(WorkflowOptions.newBuilder()
                        .setWorkflowId(TemporalManagedWorkflowIds.scheduleWorkflowId(workflowRequest))
                        .setRequestId(TemporalManagedWorkflowIds.scheduleRequestId(workflowRequest))
                        .setMemo(java.util.Map.of(
                                "tenantId", tenantId,
                                "scheduleId", scheduleId,
                                "skillId", request.getSkillId(),
                                "skillVersion", request.getSkillVersion()))
                        .setSearchAttributes(TemporalManagedSearchAttributes.from(workflowRequest,
                                TemporalManagedRunState.builder()
                                        .status("QUEUED").phase("PREPARE").build()))
                        .setTaskQueue(properties.getTaskQueue())
                        .setWorkflowRunTimeout(Duration.ofDays(7))
                        .build())
                .build();
        Schedule schedule = Schedule.newBuilder()
                .setAction(action)
                .setSpec(ScheduleSpec.newBuilder()
                        .setIntervals(List.of(new ScheduleIntervalSpec(
                                Duration.ofSeconds(request.getIntervalSeconds()))))
                        .setTimeZoneName(request.getTimeZone())
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .setCatchupWindow(Duration.ofHours(1))
                        .build())
                .setState(ScheduleState.newBuilder().setPaused(request.isPaused()).build())
                .build();
        ScheduleHandle handle = scheduleClient.createSchedule(
                scheduleId, schedule, ScheduleOptions.newBuilder().build());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        TemporalScheduleRecord record = new TemporalScheduleRecord()
                .setTenantId(tenantId).setScheduleId(scheduleId)
                .setDisplayName(request.getDisplayName()).setDescription(request.getDescription())
                .setSkillId(request.getSkillId()).setSkillVersion(request.getSkillVersion())
                .setInputJson(request.getInputJson()).setInputStrategy("STATIC")
                .setIntervalSeconds(request.getIntervalSeconds()).setCronExpression(null)
                .setTimeZone(request.getTimeZone()).setOverlapPolicy("SKIP")
                .setOperatorUserId(operatorId).setOperatorUserType(UserTypeEnum.ADMIN.getValue())
                .setRoleCode(request.getRoleCode()).setActionCode(request.getActionCode())
                .setStatus(request.isPaused() ? "PAUSED" : "ACTIVE")
                .setTemporalNamespace(properties.getNamespace()).setTemporalTaskQueue(properties.getTaskQueue())
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        try {
            require(mapper.insertSchedule(record) == 1, "Failed to persist Temporal schedule");
        } catch (RuntimeException exception) {
            handle.delete();
            throw exception;
        }
        return view(record);
    }

    /**
     * Reconciles one desired daily discovery Schedule against both MySQL and Temporal.
     * The Schedule itself stays short-lived and fans out business-keyed managed runs,
     * so a run waiting for BPM does not block the next day's discovery.
     *
     * @return {@code true} when a new database schedule record was created
     */
    public boolean reconcileManagedDaily(ManagedSkillTaskWorkflowView workflow,
                                         AiOperationsTemporalSeedProperties seedProperties) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String scheduleId = temporalScheduleId(
                tenantId,
                ManagedWorkflowDailyAutomationCatalog.dailyRequest(workflow, seedProperties).getScheduleId());
        TemporalScheduleCreateReqVO request =
                ManagedWorkflowDailyAutomationCatalog.dailyRequest(workflow, seedProperties);
        TemporalDailyDispatchRequest dispatchRequest =
                ManagedWorkflowDailyAutomationCatalog.dispatchRequest(
                        tenantId, scheduleId, workflow, seedProperties);
        String cronExpression =
                ManagedWorkflowDailyAutomationCatalog.cronExpression(workflow.getSkillId());
        String desiredPolicySha256 =
                ManagedWorkflowDailyAutomationCatalog.desiredPolicySha256(workflow, seedProperties);
        Schedule desired = managedDailySchedule(
                workflow, request, dispatchRequest, cronExpression);
        ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
        boolean temporalExists = true;
        try {
            handle.describe();
        } catch (RuntimeException exception) {
            if (!hasGrpcStatus(exception, Status.Code.NOT_FOUND)) {
                throw exception;
            }
            temporalExists = false;
        }
        if (temporalExists) {
            handle.update(ignored -> new ScheduleUpdate(desired));
        } else {
            try {
                handle = scheduleClient.createSchedule(
                        scheduleId, desired, ScheduleOptions.newBuilder().build());
            } catch (RuntimeException exception) {
                if (!hasGrpcStatus(exception, Status.Code.ALREADY_EXISTS)) {
                    throw exception;
                }
                handle = scheduleClient.getHandle(scheduleId);
                handle.update(ignored -> new ScheduleUpdate(desired));
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        TemporalScheduleRecord existing = mapper.selectSchedule(tenantId, scheduleId);
        TemporalScheduleRecord desiredRecord = new TemporalScheduleRecord()
                .setTenantId(tenantId).setScheduleId(scheduleId)
                .setDisplayName(request.getDisplayName()).setDescription(request.getDescription())
                .setSkillId(workflow.getSkillId()).setSkillVersion(workflow.getSkillVersion())
                .setInputJson(ManagedWorkflowDailyAutomationCatalog.DAILY_DISCOVERY_INPUT)
                .setInputStrategy(dispatchRequest.getInputStrategy())
                .setIntervalSeconds(ManagedWorkflowDailyAutomationCatalog
                        .intervalSeconds(workflow.getSkillId())).setCronExpression(cronExpression)
                .setTimeZone(seedProperties.getTimeZone()).setOverlapPolicy("SKIP")
                .setOperatorUserId(seedProperties.getOperatorUserId())
                .setOperatorUserType(seedProperties.getOperatorUserType())
                .setRoleCode(dispatchRequest.getRoleCode()).setActionCode(dispatchRequest.getActionCode())
                .setStatus(seedProperties.isPaused() ? "PAUSED" : "ACTIVE")
                .setTemporalNamespace(properties.getNamespace())
                .setTemporalTaskQueue(properties.getTaskQueue())
                .setDesiredPolicySha256(desiredPolicySha256)
                .setDefinitionClosureSha256(workflow.getDefinitionClosureSha256())
                .setLastReconciledAt(now).setReconcileError(null)
                .setVersion(existing == null ? 1L : existing.getVersion())
                .setCreatedAt(existing == null ? now : existing.getCreatedAt())
                .setUpdatedAt(now);
        if (existing == null) {
            require(mapper.insertSchedule(desiredRecord) == 1,
                    "Failed to persist managed daily Temporal schedule");
            mapper.markScheduleReconciled(tenantId, scheduleId, desiredPolicySha256,
                    workflow.getDefinitionClosureSha256(), now);
            return true;
        }
        require(mapper.updateScheduleDefinition(desiredRecord) == 1,
                "Failed to reconcile managed daily Temporal schedule");
        return false;
    }

    /**
     * Pauses legacy managed-daily schedules whose definitions are now internal subflows.
     * Manually created schedules are never touched because they do not carry the
     * managed discovery input marker.
     */
    public int pauseObsoleteManagedDaily(Set<String> desiredSkillIds) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        int paused = 0;
        for (TemporalScheduleRecord record : mapper.selectSchedules(tenantId)) {
            if (!ManagedWorkflowDailyAutomationCatalog.isDailyDiscovery(record.getInputJson())
                    || desiredSkillIds.contains(record.getSkillId())
                    || "PAUSED".equals(record.getStatus())) {
                continue;
            }
            try {
                scheduleClient.getHandle(record.getScheduleId())
                        .pause("Definition is now an internal subflow");
            } catch (RuntimeException exception) {
                if (!hasGrpcStatus(exception, Status.Code.NOT_FOUND)) {
                    throw exception;
                }
            }
            mapper.updateScheduleStatus(tenantId, record.getScheduleId(), "PAUSED",
                    LocalDateTime.now(ZoneOffset.UTC));
            paused++;
        }
        return paused;
    }

    public void trigger(String scheduleId) {
        scheduleHandle(scheduleId).trigger(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
    }

    void triggerRecovery(String scheduleId) {
        scheduleHandle(scheduleId).trigger(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_ALLOW_ALL);
    }

    public void pause(String scheduleId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        scheduleHandle(scheduleId).pause("Paused from CloudMold AI Operations");
        mapper.updateScheduleStatus(tenantId, scheduleId, "PAUSED", LocalDateTime.now(ZoneOffset.UTC));
    }

    public void resume(String scheduleId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        scheduleHandle(scheduleId).unpause("Resumed from CloudMold AI Operations");
        mapper.updateScheduleStatus(tenantId, scheduleId, "ACTIVE", LocalDateTime.now(ZoneOffset.UTC));
    }

    private ScheduleHandle scheduleHandle(String scheduleId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        require(mapper.selectSchedule(tenantId, scheduleId) != null, "Temporal schedule not found");
        return scheduleClient.getHandle(scheduleId);
    }

    private TemporalScheduleView view(TemporalScheduleRecord record) {
        ScheduleDescription description;
        try {
            description = scheduleClient.getHandle(record.getScheduleId()).describe();
        } catch (RuntimeException exception) {
            return TemporalScheduleView.builder()
                    .scheduleId(record.getScheduleId()).displayName(record.getDisplayName())
                    .description(record.getDescription()).skillId(record.getSkillId())
                    .skillVersion(record.getSkillVersion()).intervalSeconds(record.getIntervalSeconds())
                    .cronExpression(record.getCronExpression()).inputStrategy(record.getInputStrategy())
                    .timeZone(record.getTimeZone()).status("DRIFTED")
                    .paused(true).overlapPolicy(record.getOverlapPolicy())
                    .temporalNamespace(record.getTemporalNamespace())
                    .temporalTaskQueue(record.getTemporalTaskQueue())
                    .definitionClosureSha256(record.getDefinitionClosureSha256())
                    .lastReconciledAt(toInstant(record.getLastReconciledAt()))
                    .reconcileError("Temporal Schedule unavailable: " + rootMessage(exception))
                    .build();
        }
        List<java.time.Instant> future = description.getInfo().getNextActionTimes();
        List<java.time.Instant> recent = description.getInfo().getRecentActions().stream()
                .map(action -> action.getScheduledAt()).toList();
        return TemporalScheduleView.builder()
                .scheduleId(record.getScheduleId()).displayName(record.getDisplayName())
                .description(record.getDescription()).skillId(record.getSkillId())
                .skillVersion(record.getSkillVersion()).intervalSeconds(record.getIntervalSeconds())
                .cronExpression(record.getCronExpression()).inputStrategy(record.getInputStrategy())
                .timeZone(record.getTimeZone()).status(record.getStatus())
                .paused(description.getSchedule().getState().isPaused())
                .overlapPolicy(record.getOverlapPolicy()).temporalNamespace(record.getTemporalNamespace())
                .temporalTaskQueue(record.getTemporalTaskQueue())
                .definitionClosureSha256(record.getDefinitionClosureSha256())
                .lastReconciledAt(toInstant(record.getLastReconciledAt()))
                .reconcileError(record.getReconcileError())
                .nextActionAt(future.isEmpty() ? null : future.get(0))
                .lastActionAt(recent.isEmpty() ? null : recent.get(recent.size() - 1))
                .build();
    }

    private Schedule managedDailySchedule(
            ManagedSkillTaskWorkflowView workflow,
            TemporalScheduleCreateReqVO request,
            TemporalDailyDispatchRequest dispatchRequest,
            String cronExpression) {
        ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                .setWorkflowType(TemporalManagedDailyDispatchWorkflow.class)
                .setArguments(dispatchRequest)
                .setOptions(WorkflowOptions.newBuilder()
                        .setWorkflowId(TemporalManagedWorkflowIds.dailyDispatchWorkflowId(dispatchRequest))
                        .setRequestId(TemporalManagedWorkflowIds.dailyDispatchRequestId(dispatchRequest))
                        .setMemo(java.util.Map.of(
                                "tenantId", dispatchRequest.getTenantId(),
                                "scheduleId", dispatchRequest.getScheduleId(),
                                "skillId", workflow.getSkillId(),
                                "skillVersion", workflow.getSkillVersion(),
                                "inputStrategy", dispatchRequest.getInputStrategy()))
                        .setTaskQueue(properties.getTaskQueue())
                        .setWorkflowRunTimeout(Duration.ofMinutes(15))
                        .build())
                .build();
        return Schedule.newBuilder()
                .setAction(action)
                .setSpec(ScheduleSpec.newBuilder()
                        .setCronExpressions(List.of(cronExpression))
                        .setTimeZoneName(request.getTimeZone())
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .setCatchupWindow(Duration.ofHours(1))
                        .setPauseOnFailure(false)
                        .build())
                .setState(ScheduleState.newBuilder().setPaused(request.isPaused()).build())
                .build();
    }

    private static java.time.Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static boolean hasGrpcStatus(Throwable throwable, Status.Code expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StatusRuntimeException status
                    && status.getStatus().getCode() == expected) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    private static String temporalScheduleId(Long tenantId, String requestedId) {
        String normalized = requestedId.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        require(!normalized.isBlank(), "scheduleId is invalid");
        return "cloudmold-t" + tenantId + "-" + normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
