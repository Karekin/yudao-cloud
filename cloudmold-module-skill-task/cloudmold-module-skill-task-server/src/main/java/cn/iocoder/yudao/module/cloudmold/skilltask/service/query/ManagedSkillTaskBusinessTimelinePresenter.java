package cn.iocoder.yudao.module.cloudmold.skilltask.service.query;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskBusinessActionView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskBusinessPhaseView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ManagedSkillTaskBusinessTimelinePresenter {

    private final ManagedSkillTaskBusinessOutcomePresenter outcomePresenter;

    public List<ManagedSkillTaskBusinessPhaseView> present(
            Task root,
            List<Task> descendants,
            Map<String, List<Step>> stepsByTask) {
        Map<String, List<Task>> childrenByParent = descendants.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Task::getParentTaskId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<ManagedSkillTaskBusinessPhaseView> phases = new ArrayList<>();
        appendTask(phases, root, 0, childrenByParent, stepsByTask, new HashSet<>());
        for (int index = 0; index < phases.size(); index++) {
            phases.get(index).setPhaseOrder(index + 1);
        }
        return phases;
    }

    private void appendTask(
            List<ManagedSkillTaskBusinessPhaseView> phases,
            Task task,
            int depth,
            Map<String, List<Task>> childrenByParent,
            Map<String, List<Step>> stepsByTask,
            Set<String> visited) {
        if (!visited.add(task.getTaskId())) {
            return;
        }
        List<Step> steps = ordered(stepsByTask.getOrDefault(task.getTaskId(), List.of()));
        List<Step> submitSteps = steps.stream()
                .filter(this::isSubmitChild)
                .toList();
        List<Step> businessSteps = steps.stream()
                .filter(step -> !isChildOrchestration(step))
                .toList();
        if (!businessSteps.isEmpty() || submitSteps.isEmpty()) {
            phases.add(taskPhase(task, depth, businessSteps));
        }
        if (submitSteps.isEmpty()) {
            return;
        }
        List<Task> children = new ArrayList<>(childrenByParent.getOrDefault(task.getTaskId(), List.of()));
        for (Step submitStep : submitSteps) {
            Task child = findChild(submitStep, children);
            if (child == null) {
                phases.add(pendingPhase(task, submitStep, depth, steps));
                continue;
            }
            appendTask(phases, child, depth + (businessSteps.isEmpty() ? 0 : 1),
                    childrenByParent, stepsByTask, visited);
        }
    }

    private ManagedSkillTaskBusinessPhaseView taskPhase(Task task, int depth, List<Step> steps) {
        return ManagedSkillTaskBusinessPhaseView.builder()
                .phaseCode(StringUtils.hasText(task.getParentStepCode())
                        ? task.getParentStepCode() : task.getSkillId())
                .displayName(outcomePresenter.skillDisplayName(task.getSkillId()))
                .description(outcomePresenter.skillDescription(task.getSkillId()))
                .depth(depth)
                .taskId(task.getTaskId())
                .runId(task.getRunId())
                .skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion())
                .parentTaskId(task.getParentTaskId())
                .status(task.getStatus())
                .riskLevel(task.getRiskLevel())
                .approvalRequired(approvalRequired(task.getRiskLevel()))
                .approvalStatus(approvalStatus(task))
                .businessOutcome(outcomePresenter.present(task, steps))
                .actions(steps.stream().map(this::action).toList())
                .startedAt(toInstant(task.getStartedAt()))
                .completedAt(toInstant(task.getCompletedAt()))
                .build();
    }

    private ManagedSkillTaskBusinessPhaseView pendingPhase(
            Task parent,
            Step submitStep,
            int depth,
            List<Step> parentSteps) {
        String childSkillId = outcomePresenter.childWorkflowSkillId(submitStep);
        return ManagedSkillTaskBusinessPhaseView.builder()
                .phaseCode(submitStep.getStepCode())
                .displayName(outcomePresenter.childWorkflowDisplayName(submitStep))
                .description(outcomePresenter.skillDescription(childSkillId))
                .depth(depth)
                .parentTaskId(parent.getTaskId())
                .skillId(childSkillId)
                .skillVersion(submitStep.getChildSkillVersion())
                .status(orchestrationStatus(submitStep, parentSteps, parent))
                .riskLevel(parent.getRiskLevel())
                .approvalRequired(approvalRequired(parent.getRiskLevel()))
                .approvalStatus(approvalStatus(parent))
                .actions(List.of())
                .startedAt(toInstant(submitStep.getStartedAt()))
                .completedAt(toInstant(submitStep.getCompletedAt()))
                .build();
    }

    private ManagedSkillTaskBusinessActionView action(Step step) {
        return ManagedSkillTaskBusinessActionView.builder()
                .taskId(step.getTaskId())
                .stepCode(step.getStepCode())
                .displayName(outcomePresenter.stepDisplayName(step))
                .resultSummary(outcomePresenter.stepResultSummary(step))
                .actionOrder(step.getStepOrder())
                .actionType(step.getStepKind())
                .operationType(step.getOperationType())
                .status(step.getStatus())
                .attemptCount(step.getAttemptCount())
                .businessObjects(outcomePresenter.stepBusinessObjects(step))
                .evidenceSha256(step.getResultSha256())
                .errorCode(step.getLastErrorCode())
                .startedAt(toInstant(step.getStartedAt()))
                .completedAt(toInstant(step.getCompletedAt()))
                .build();
    }

    private static Task findChild(Step submitStep, List<Task> children) {
        return children.stream()
                .filter(child -> submitStep.getStepCode().equals(child.getParentStepCode())
                        || StringUtils.hasText(submitStep.getChildTaskId())
                        && submitStep.getChildTaskId().equals(child.getTaskId()))
                .min(Comparator.comparing(Task::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Task::getTaskId))
                .orElse(null);
    }

    private static String orchestrationStatus(Step submitStep, List<Step> parentSteps, Task parent) {
        String waitCode = submitStep.getStepCode().replaceFirst("^submit_", "wait_");
        Step waitStep = parentSteps.stream()
                .filter(step -> waitCode.equals(step.getStepCode()))
                .findFirst()
                .orElse(null);
        if (waitStep != null && !"PENDING".equals(waitStep.getStatus())) {
            return waitStep.getStatus();
        }
        if (!"PENDING".equals(submitStep.getStatus())) {
            return submitStep.getStatus();
        }
        if (waitCode.equals(parent.getCurrentStepCode())
                || submitStep.getStepCode().equals(parent.getCurrentStepCode())) {
            return parent.getStatus();
        }
        return "PENDING";
    }

    private static String approvalStatus(Task task) {
        if (!approvalRequired(task.getRiskLevel())) {
            return "NOT_REQUIRED";
        }
        if ("WAITING_APPROVAL".equals(task.getStatus())) {
            return "WAITING_APPROVAL";
        }
        if ("NEEDS_REVIEW".equals(task.getStatus())) {
            return "NEEDS_REVIEW";
        }
        if (StringUtils.hasText(task.getApprovalRef())
                || "RUNNING".equals(task.getStatus())
                || "SUCCEEDED".equals(task.getStatus())) {
            return "APPROVED";
        }
        return "REQUIRED";
    }

    private static boolean approvalRequired(String riskLevel) {
        return !"R1".equals(riskLevel);
    }

    private static boolean isChildOrchestration(Step step) {
        return "SUBMIT_CHILD".equals(step.getStepKind()) || "WAIT_CHILD".equals(step.getStepKind());
    }

    private boolean isSubmitChild(Step step) {
        return "SUBMIT_CHILD".equals(step.getStepKind());
    }

    private static List<Step> ordered(List<Step> steps) {
        return steps.stream()
                .sorted(Comparator.comparing(Step::getStepOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Step::getStepCode))
                .toList();
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
