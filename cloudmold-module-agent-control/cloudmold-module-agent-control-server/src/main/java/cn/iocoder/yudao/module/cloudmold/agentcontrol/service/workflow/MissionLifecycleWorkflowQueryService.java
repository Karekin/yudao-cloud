package cn.iocoder.yudao.module.cloudmold.agentcontrol.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleGoalView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkOrderView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Mission;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.MissionGoal;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MissionLifecycleWorkflowQueryService {

    private static final String STOCKOUT_TEMPLATE = "mission.inventory-stockout-response.v1";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");

    private final AgentControlStoreMapper mapper;

    public MissionLifecycleWorkflowQueryService(AgentControlStoreMapper mapper) {
        this.mapper = mapper;
    }

    public MissionLifecycleWorkflowView inspect(String missionId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedMissionId = requireRef(missionId, "missionId");
        Mission mission = mapper.selectMission(tenantId, normalizedMissionId);
        require(mission != null, "mission not found");
        require(STOCKOUT_TEMPLATE.equals(mission.getMissionType()),
                "mission is not the fixed stockout mission lifecycle template");
        List<MissionGoal> goals = mapper.selectMissionGoals(tenantId, normalizedMissionId);
        List<WorkOrder> workOrders = mapper.selectMissionWorkOrders(tenantId, normalizedMissionId);
        return MissionLifecycleWorkflowView.builder()
                .workflowScope(MissionLifecycleWorkflowCommandService.WORKFLOW_SCOPE)
                .missionId(mission.getMissionId())
                .missionType(mission.getMissionType())
                .templateVersion(mission.getTemplateVersion())
                .title(mission.getTitle())
                .objectiveJson(mission.getObjectiveJson())
                .correlationId(mission.getCorrelationId())
                .status(mission.getStatus())
                .supervisorUserId(mission.getSupervisorUserId())
                .version(mission.getVersion())
                .startedAt(toInstant(mission.getStartedAt()))
                .deadlineAt(toInstant(mission.getDeadlineAt()))
                .completedAt(toInstant(mission.getCompletedAt()))
                .goals(goals.stream().map(this::mapGoal).toList())
                .workOrders(workOrders.stream().map(this::mapWorkOrder).toList())
                .build();
    }

    private MissionLifecycleGoalView mapGoal(MissionGoal goal) {
        return MissionLifecycleGoalView.builder()
                .goalId(goal.getGoalId())
                .goalCode(goal.getGoalCode())
                .title(goal.getTitle())
                .status(goal.getStatus())
                .version(goal.getVersion())
                .createdAt(toInstant(goal.getCreatedAt()))
                .updatedAt(toInstant(goal.getUpdatedAt()))
                .build();
    }

    private MissionLifecycleWorkOrderView mapWorkOrder(WorkOrder workOrder) {
        return MissionLifecycleWorkOrderView.builder()
                .workOrderId(workOrder.getWorkOrderId())
                .goalId(workOrder.getGoalId())
                .parentWorkOrderId(workOrder.getParentWorkOrderId())
                .title(workOrder.getTitle())
                .roleCode(workOrder.getRoleCode())
                .actionCode(workOrder.getActionCode())
                .status(workOrder.getStatus())
                .riskLevel(workOrder.getRiskLevel())
                .executionRequired(workOrder.getExecutionRequired())
                .skillId(workOrder.getSkillId())
                .skillVersion(workOrder.getSkillVersion())
                .approvalId(workOrder.getApprovalId())
                .requesterUserId(workOrder.getRequesterUserId())
                .assigneeUserId(workOrder.getAssigneeUserId())
                .waitingReasonCode(workOrder.getWaitingReasonCode())
                .activeRunId(workOrder.getActiveRunId())
                .version(workOrder.getVersion())
                .createdAt(toInstant(workOrder.getCreatedAt()))
                .readyAt(toInstant(workOrder.getReadyAt()))
                .deadlineAt(toInstant(workOrder.getDeadlineAt()))
                .updatedAt(toInstant(workOrder.getUpdatedAt()))
                .completedAt(toInstant(workOrder.getCompletedAt()))
                .build();
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String requireRef(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        String trimmed = value.trim();
        require(SAFE_REF.matcher(trimmed).matches(), field + " is invalid");
        return trimmed;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
