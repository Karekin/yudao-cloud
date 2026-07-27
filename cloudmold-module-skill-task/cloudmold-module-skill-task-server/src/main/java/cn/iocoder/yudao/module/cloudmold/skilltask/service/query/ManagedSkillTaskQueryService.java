package cn.iocoder.yudao.module.cloudmold.skilltask.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskChildRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskStepView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagedSkillTaskQueryService {

    static final String TRIGGER_SOURCE = "ADMIN_CONSOLE";
    static final String MANAGEMENT_SURFACE = "DEER_FLOW";
    static final String DURABLE_AUTHORITY = "SKILL_TASK";
    private static final Map<String, WorkflowPresentation> WORKFLOW_PRESENTATIONS = Map.of(
            "skill.cloudmold.catalog.inspect-active-sku.v1",
            new WorkflowPresentation("在售 SKU 查询",
                    "查询当前租户指定 SKU 的规范商品与在售状态，不修改业务数据。"),
            "skill.cloudmold.inventory.stockout-diagnosis.v1",
            new WorkflowPresentation("尺码缺断码诊断",
                    "按 SPU 检查各尺码可售库存，识别缺货与低库存风险，不修改业务数据。"),
            "skill.cloudmold.commerce.catalog-matrix.v1",
            new WorkflowPresentation("商品款色码建档",
                    "建立款式、SPU 与 6 个 SKU，并完成商品生命周期激活。"),
            "skill.cloudmold.commerce.aftersale-saga.v1",
            new WorkflowPresentation("售后退款全链路",
                    "贯通发布、库存、下单支付、履约、退货质检、退款和库存恢复。"),
            "skill.cloudmold.commerce.legacy-projection-plan.v1",
            new WorkflowPresentation("旧系统投影预检",
                    "只读规划规范 SKU 向 Mall、ERP 与 WMS 的兼容投影，不执行旧系统写入。"),
            "skill.cloudmold.commerce.reuse-ready-master.v1",
            new WorkflowPresentation("商家与仓网主数据准备",
                    "校验身份与 ERP 仓，创建并激活商家店铺，绑定可用仓网。"),
            "skill.cloudmold.commerce.terminal-readback.v1",
            new WorkflowPresentation("全链路终态核验",
                    "只读核验商品、商家、刊登、订单、支付、履约、售后及仓网终态。"),
            "skill.cloudmold.commerce.full-chain-hsf.v1",
            new WorkflowPresentation("商品售后自治全链路",
                    "依次编排商品建档、旧系统投影、商家仓网、售后 Saga 与终态核验。"),
            "skill.cloudmold.commerce.product-to-listing.v1",
            new WorkflowPresentation("自动铺品",
                    "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读；缺少真实渠道回执时明确标记待渠道确认。"),
            "skill.cloudmold.supply-planning.prepare.v1",
            new WorkflowPresentation("补货单准备",
                    "将已批准的补货建议转换为真实采购或调拨草稿，并明确后续等待的供应商或仓储事件。")
    );

    private final SkillTaskMapper mapper;
    private final SkillTaskDefinitionRegistry definitionRegistry;
    private final ManagedSkillTaskBusinessOutcomePresenter outcomePresenter;
    private final ManagedSkillTaskBusinessTimelinePresenter timelinePresenter;

    public List<ManagedSkillTaskWorkflowView> listManagedWorkflows() {
        return definitionRegistry.all().stream()
                .sorted(Comparator.comparing(SkillTaskDefinition::getSkillId)
                        .thenComparing(SkillTaskDefinition::getSkillVersion))
                .map(this::toWorkflowItem)
                .toList();
    }

    public PageResult<ManagedSkillTaskRunView> getManagedRunPage(ManagedSkillTaskRunPageRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String taskId = normalize(request.getTaskId());
        String runId = normalize(request.getRunId());
        String skillId = normalize(request.getSkillId());
        String status = normalizeUpper(request.getStatus());
        String riskLevel = normalizeUpper(request.getRiskLevel());
        long total = mapper.countManagedRunPage(tenantId, taskId, runId, skillId, status, riskLevel);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<Task> tasks = mapper.selectManagedRunPage(tenantId, taskId, runId, skillId, status, riskLevel,
                offset, request.getPageSize());
        if (tasks.isEmpty()) {
            return new PageResult<>(List.of(), total);
        }
        Map<String, List<Step>> stepsByTask = mapper.selectStepsForTasks(tenantId,
                        tasks.stream().map(Task::getTaskId).toList()).stream()
                .collect(Collectors.groupingBy(Step::getTaskId, LinkedHashMap::new, Collectors.toList()));
        return new PageResult<>(tasks.stream()
                .map(task -> toRunSummary(task, stepsByTask.getOrDefault(task.getTaskId(), List.of())))
                .toList(), total);
    }

    public ManagedSkillTaskDetailView getManagedRun(String taskId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Task task = requireTask(tenantId, normalizeRequired(taskId, "taskId"));
        List<Task> descendants = loadDescendants(tenantId, task);
        Map<String, Task> childrenByParentStep = new LinkedHashMap<>();
        for (Task child : descendants) {
            if (!task.getTaskId().equals(child.getParentTaskId())) {
                continue;
            }
            childrenByParentStep.putIfAbsent(child.getParentStepCode(), child);
        }
        List<Step> steps = mapper.selectSteps(tenantId, task.getTaskId());
        Map<String, List<Step>> stepsByTask = new LinkedHashMap<>();
        stepsByTask.put(task.getTaskId(), steps);
        if (!descendants.isEmpty()) {
            stepsByTask.putAll(mapper.selectStepsForTasks(tenantId,
                            descendants.stream().map(Task::getTaskId).toList()).stream()
                    .collect(Collectors.groupingBy(
                            Step::getTaskId, LinkedHashMap::new, Collectors.toList())));
        }
        return ManagedSkillTaskDetailView.builder()
                .task(toRunSummary(task, steps))
                .businessPhases(timelinePresenter.present(task, descendants, stepsByTask))
                .steps(steps.stream()
                        .map(step -> toStepItem(step, childrenByParentStep.get(step.getStepCode())))
                        .toList())
                .build();
    }

    private List<Task> loadDescendants(Long tenantId, Task root) {
        final int maxDepth = 4;
        final int maxTasks = 64;
        List<Task> descendants = new ArrayList<>();
        List<Task> frontier = List.of(root);
        Set<String> visited = new HashSet<>();
        visited.add(root.getTaskId());
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<Task> next = new ArrayList<>();
            for (Task parent : frontier) {
                for (Task child : mapper.selectChildren(tenantId, parent.getTaskId())) {
                    if (!visited.add(child.getTaskId())) {
                        continue;
                    }
                    descendants.add(child);
                    next.add(child);
                    if (descendants.size() >= maxTasks) {
                        return descendants;
                    }
                }
            }
            frontier = next;
        }
        return descendants;
    }

    private ManagedSkillTaskWorkflowView toWorkflowItem(SkillTaskDefinition definition) {
        WorkflowPresentation presentation = WORKFLOW_PRESENTATIONS.get(definition.getSkillId());
        if (presentation == null) {
            throw new IllegalStateException("Managed workflow presentation is missing: " + definition.getSkillId());
        }
        long writeStepCount = definition.getSteps().stream()
                .filter(step -> "WRITE".equals(step.getOperationType()))
                .count();
        return ManagedSkillTaskWorkflowView.builder()
                .skillId(definition.getSkillId())
                .skillVersion(definition.getSkillVersion())
                .displayName(presentation.displayName())
                .description(presentation.description())
                .riskLevel(definition.getRiskLevel())
                .maxAttempts(definition.getMaxAttempts())
                .stepCount(definition.getSteps().size())
                .writeStepCount((int) writeStepCount)
                .approvalRequired(!"R1".equals(definition.getRiskLevel()))
                .definitionSha256(definition.getDefinitionSha256())
                .definitionClosureSha256(definition.getDefinitionClosureSha256())
                .triggerSource(TRIGGER_SOURCE)
                .managementSurface(MANAGEMENT_SURFACE)
                .orchestrationSurface(TRIGGER_SOURCE)
                .durableAuthority(DURABLE_AUTHORITY)
                .build();
    }

    private record WorkflowPresentation(String displayName, String description) {
    }

    private ManagedSkillTaskRunView toRunSummary(Task task, List<Step> steps) {
        return ManagedSkillTaskRunView.builder()
                .taskId(task.getTaskId())
                .runId(task.getRunId())
                .skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion())
                .inputSha256(task.getInputSha256())
                .definitionSha256(task.getDefinitionSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256())
                .terminalResultSha256(task.getTerminalResultSha256())
                .businessOutcome(outcomePresenter.present(task, steps))
                .riskLevel(task.getRiskLevel())
                .parentTaskId(task.getParentTaskId())
                .parentStepCode(task.getParentStepCode())
                .status(task.getStatus())
                .currentStepCode(task.getCurrentStepCode())
                .attemptCount(task.getAttemptCount())
                .maxAttempts(task.getMaxAttempts())
                .version(task.getVersion())
                .startedAt(toInstant(task.getStartedAt()))
                .completedAt(toInstant(task.getCompletedAt()))
                .createdAt(toInstant(task.getCreatedAt()))
                .updatedAt(toInstant(task.getUpdatedAt()))
                .build();
    }

    private ManagedSkillTaskStepView toStepItem(Step step, Task childTask) {
        return ManagedSkillTaskStepView.builder()
                .taskId(step.getTaskId())
                .stepCode(step.getStepCode())
                .displayName(outcomePresenter.stepDisplayName(step))
                .resultSummary(outcomePresenter.stepResultSummary(step))
                .stepOrder(step.getStepOrder())
                .stepKind(step.getStepKind())
                .capabilityId(step.getCapabilityId())
                .operationType(step.getOperationType())
                .childSkillId(step.getChildSkillId())
                .childSkillVersion(step.getChildSkillVersion())
                .childTaskId(step.getChildTaskId())
                .pollIntervalSeconds(step.getPollIntervalSeconds())
                .idempotencyKey(step.getIdempotencyKey())
                .status(step.getStatus())
                .attemptCount(step.getAttemptCount())
                .requestSha256(step.getRequestSha256())
                .resultSha256(step.getResultSha256())
                .lastErrorCode(step.getLastErrorCode())
                .startedAt(toInstant(step.getStartedAt()))
                .completedAt(toInstant(step.getCompletedAt()))
                .createdAt(toInstant(step.getCreatedAt()))
                .updatedAt(toInstant(step.getUpdatedAt()))
                .childTask(childTask == null ? null : toChildSummary(childTask))
                .build();
    }

    private ManagedSkillTaskChildRunView toChildSummary(Task task) {
        return ManagedSkillTaskChildRunView.builder()
                .taskId(task.getTaskId())
                .runId(task.getRunId())
                .skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion())
                .inputSha256(task.getInputSha256())
                .definitionSha256(task.getDefinitionSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256())
                .terminalResultSha256(task.getTerminalResultSha256())
                .riskLevel(task.getRiskLevel())
                .status(task.getStatus())
                .currentStepCode(task.getCurrentStepCode())
                .version(task.getVersion())
                .startedAt(toInstant(task.getStartedAt()))
                .completedAt(toInstant(task.getCompletedAt()))
                .createdAt(toInstant(task.getCreatedAt()))
                .updatedAt(toInstant(task.getUpdatedAt()))
                .build();
    }

    private Task requireTask(Long tenantId, String taskId) {
        Task task = mapper.selectTask(tenantId, taskId);
        if (task == null) {
            throw new IllegalArgumentException("Skill task does not exist: " + taskId);
        }
        return task;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
