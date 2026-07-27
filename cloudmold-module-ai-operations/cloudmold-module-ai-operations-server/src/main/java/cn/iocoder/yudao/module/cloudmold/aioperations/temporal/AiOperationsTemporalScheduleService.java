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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
                        .setWorkflowId(scheduleId + "-workflow")
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
                .setInputJson(request.getInputJson()).setIntervalSeconds(request.getIntervalSeconds())
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

    public void trigger(String scheduleId) {
        scheduleHandle(scheduleId).trigger(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
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
        ScheduleDescription description = scheduleClient.getHandle(record.getScheduleId()).describe();
        List<java.time.Instant> future = description.getInfo().getNextActionTimes();
        List<java.time.Instant> recent = description.getInfo().getRecentActions().stream()
                .map(action -> action.getScheduledAt()).toList();
        return TemporalScheduleView.builder()
                .scheduleId(record.getScheduleId()).displayName(record.getDisplayName())
                .description(record.getDescription()).skillId(record.getSkillId())
                .skillVersion(record.getSkillVersion()).intervalSeconds(record.getIntervalSeconds())
                .timeZone(record.getTimeZone()).status(record.getStatus())
                .paused(description.getSchedule().getState().isPaused())
                .overlapPolicy(record.getOverlapPolicy()).temporalNamespace(record.getTemporalNamespace())
                .temporalTaskQueue(record.getTemporalTaskQueue())
                .nextActionAt(future.isEmpty() ? null : future.get(0))
                .lastActionAt(recent.isEmpty() ? null : recent.get(recent.size() - 1))
                .build();
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
