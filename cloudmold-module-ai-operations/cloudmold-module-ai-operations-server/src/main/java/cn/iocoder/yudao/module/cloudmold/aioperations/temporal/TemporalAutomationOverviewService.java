package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal",
        name = "enabled", havingValue = "true")
public class TemporalAutomationOverviewService {

    private final AiOperationsManagedRunQueryServiceFacade workflows;
    private final AiOperationsTemporalScheduleService schedules;
    private final AiOperationsTemporalMapper mapper;

    public TemporalAutomationOverviewView get() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        List<ManagedSkillTaskWorkflowView> registered = workflows.listWorkflowsAs(
                operatorId, UserTypeEnum.ADMIN.getValue());
        Map<String, TemporalScheduleView> schedulesByWorkflow = schedules.list().stream()
                .collect(Collectors.toMap(
                        item -> key(item.getSkillId(), item.getSkillVersion()),
                        Function.identity(), (left, right) -> left));
        List<TemporalAutomationOverviewView.WorkflowAutomationView> rows =
                registered.stream()
                        .map(workflow -> row(tenantId, workflow,
                                schedulesByWorkflow.get(key(
                                        workflow.getSkillId(), workflow.getSkillVersion()))))
                        .sorted(Comparator.comparing(
                                TemporalAutomationOverviewView.WorkflowAutomationView::getSkillId)
                                .thenComparing(
                                        TemporalAutomationOverviewView.WorkflowAutomationView::getSkillVersion))
                        .toList();
        int scheduledCount = (int) rows.stream()
                .filter(item -> !"MISSING".equals(item.getScheduleState())).count();
        int healthyScheduleCount = (int) rows.stream()
                .filter(item -> "HEALTHY".equals(item.getScheduleState())).count();
        int candidateSourceCount = (int) rows.stream()
                .filter(item -> List.of("TENANT_AGGREGATE", "ROTATING_BUSINESS_SCENARIO",
                                "DOMAIN_BACKLOG", "OUTBOX_EVENT")
                        .contains(item.getDiscoverySource())).count();
        int autonomyProvenCount = (int) rows.stream()
                .filter(item -> "AUTONOMY_PROVEN".equals(item.getBusinessAutonomyState())).count();
        return TemporalAutomationOverviewView.builder()
                .registeredCount(rows.size())
                .scheduledCount(scheduledCount)
                .healthyScheduleCount(healthyScheduleCount)
                .scheduleCoverageRate(rate(healthyScheduleCount, rows.size()))
                .candidateSourceConnectedCount(candidateSourceCount)
                .candidateSourceCoverageRate(rate(candidateSourceCount, rows.size()))
                .autonomyProvenCount(autonomyProvenCount)
                .autonomyProofCoverageRate(rate(autonomyProvenCount, rows.size()))
                .workflows(rows)
                .build();
    }

    private TemporalAutomationOverviewView.WorkflowAutomationView row(
            Long tenantId, ManagedSkillTaskWorkflowView workflow,
            TemporalScheduleView schedule) {
        String discoverySource = discoverySource(workflow);
        TemporalDispatchObservationRecord observation = schedule == null ? null
                : mapper.selectLatestDispatchObservation(tenantId, schedule.getScheduleId());
        TemporalRunBindingRecord proof = schedule == null ? null
                : mapper.selectLatestRunBindingForSchedule(
                        tenantId, schedule.getScheduleId());
        String scheduleState = scheduleState(schedule);
        String autonomyState = autonomyState(
                scheduleState, discoverySource, observation, proof);
        List<String> gaps = new ArrayList<>();
        if ("MISSING".equals(scheduleState)) {
            gaps.add("SCHEDULE_MISSING");
        } else if (!"HEALTHY".equals(scheduleState)) {
            gaps.add("SCHEDULE_DRIFT");
        }
        if ("UNWIRED".equals(discoverySource)) {
            gaps.add("CANDIDATE_SOURCE_MISSING");
        } else if ("GOVERNED_MANUAL".equals(discoverySource)) {
            gaps.add("GOVERNED_WRITE_INPUT_REQUIRED");
        }
        if (!"AUTONOMY_PROVEN".equals(autonomyState)) {
            gaps.add("AUTONOMY_PROOF_MISSING");
        }
        if ("skill.cloudmold.engagement.growth-experiment-readback.v1"
                .equals(workflow.getSkillId())) {
            gaps.add("MISSING_EXPERIMENT_SOR");
        }
        return TemporalAutomationOverviewView.WorkflowAutomationView.builder()
                .skillId(workflow.getSkillId())
                .skillVersion(workflow.getSkillVersion())
                .displayName(workflow.getDisplayName())
                .scheduleState(scheduleState)
                .discoverySource(discoverySource)
                .lastDispatchOutcome(
                        observation == null ? null : observation.getOutcomeCode())
                .candidateCount(
                        observation == null || observation.getCandidateCount() == null
                                ? 0 : observation.getCandidateCount())
                .dispatchedCount(
                        observation == null || observation.getDispatchedCount() == null
                                ? 0 : observation.getDispatchedCount())
                .failedCount(
                        observation == null || observation.getFailedCount() == null
                                ? 0 : observation.getFailedCount())
                .businessAutonomyState(autonomyState)
                .gapCodes(List.copyOf(gaps))
                .proofRef(proofRef(proof))
                .build();
    }

    private static String discoverySource(ManagedSkillTaskWorkflowView workflow) {
        if (ManagedWorkflowDailyAutomationCatalog.canRunWithoutBusinessInput(
                workflow.getSkillId())) {
            return "TENANT_AGGREGATE";
        }
        if (ManagedWorkflowDailyAutomationCatalog.isRotatingBusinessScenario(
                workflow.getSkillId())) {
            return "ROTATING_BUSINESS_SCENARIO";
        }
        if (ManagedWorkflowDailyAutomationCatalog.isDomainBacklog(
                workflow.getSkillId())) {
            return "DOMAIN_BACKLOG";
        }
        if (!ManagedWorkflowOutboxRouteCatalog.eventTypes(
                workflow.getSkillId()).isEmpty()) {
            return "OUTBOX_EVENT";
        }
        return workflow.getWriteStepCount() != null
                && workflow.getWriteStepCount() > 0
                ? "GOVERNED_MANUAL" : "UNWIRED";
    }

    private static String scheduleState(TemporalScheduleView schedule) {
        if (schedule == null) {
            return "MISSING";
        }
        if ("DRIFTED".equals(schedule.getStatus())
                || schedule.getReconcileError() != null) {
            return "DRIFTED";
        }
        if (schedule.isPaused() || "PAUSED".equals(schedule.getStatus())) {
            return "PAUSED";
        }
        return "ACTIVE".equals(schedule.getStatus()) ? "HEALTHY" : "DEGRADED";
    }

    private static String autonomyState(
            String scheduleState, String discoverySource,
            TemporalDispatchObservationRecord observation,
            TemporalRunBindingRecord proof) {
        if (proof != null && "SUCCEEDED".equals(proof.getStatus())) {
            return "AUTONOMY_PROVEN";
        }
        if (proof != null && List.of(
                "QUEUED", "RUNNING", "WAITING_APPROVAL", "APPROVED",
                "TASK_SUBMITTED", "WAITING_EVENT", "BUSINESS_EVENT_SIGNALED")
                .contains(proof.getStatus())) {
            return "AUTONOMY_IN_PROGRESS";
        }
        if (proof != null && List.of(
                "FAILED", "REJECTED", "TIMED_OUT", "CANCELLED")
                .contains(proof.getStatus())) {
            return "NEEDS_REVIEW";
        }
        if (observation != null
                && "NO_ACTION_DUE".equals(observation.getOutcomeCode())
                && List.of("TENANT_AGGREGATE", "ROTATING_BUSINESS_SCENARIO",
                        "DOMAIN_BACKLOG", "OUTBOX_EVENT")
                .contains(discoverySource)) {
            return "READY_IDLE";
        }
        if ("MISSING".equals(scheduleState) || "DRIFTED".equals(scheduleState)
                || "UNWIRED".equals(discoverySource)) {
            return "BLOCKED";
        }
        return "SCHEDULED_ONLY";
    }

    private static String proofRef(TemporalRunBindingRecord proof) {
        if (proof == null || !"SUCCEEDED".equals(proof.getStatus())) {
            return null;
        }
        if (proof.getManagedRunId() != null) {
            return proof.getManagedRunId();
        }
        if (proof.getSkillTaskId() != null) {
            return proof.getSkillTaskId();
        }
        return proof.getTemporalWorkflowId();
    }

    private static String key(String skillId, String skillVersion) {
        return skillId + "@" + skillVersion;
    }

    private static double rate(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0d;
        }
        return BigDecimal.valueOf(numerator * 100.0d / denominator)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
