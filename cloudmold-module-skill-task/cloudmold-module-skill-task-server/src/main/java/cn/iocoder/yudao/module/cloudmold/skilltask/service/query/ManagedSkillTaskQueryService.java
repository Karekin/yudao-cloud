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
    private static final Map<String, WorkflowPresentation> WORKFLOW_PRESENTATIONS = Map.ofEntries(
            Map.entry("skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
                    new WorkflowPresentation("缺断码 Mission 生命周期",
                            "在审批后创建固定缺断码处置 Mission，并回读五段真实工作图；不宣称支持通用 Charter、预算、KPI 或动态 DAG。")),
            Map.entry("skill.cloudmold.catalog.inspect-active-sku.v1",
                    new WorkflowPresentation("在售 SKU 查询",
                            "查询当前租户指定 SKU 的规范商品与在售状态，不修改业务数据。")),
            Map.entry("skill.cloudmold.catalog.assortment-wave-readiness.v1",
                    new WorkflowPresentation("波段企划准备度",
                            "按年度、季节和波段汇总真实款式、SPU 与 SKU，并明确趋势、价格带、款量、毛利及供给计划缺口。")),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1",
                    new WorkflowPresentation("尺码缺断码诊断",
                            "按 SPU 检查各尺码可售库存，识别缺货与低库存风险，不修改业务数据。")),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1",
                    new WorkflowPresentation("商品刊登生命周期终态跟踪",
                            "持续核验刊登发布、渠道回执、暂停、下架、归档及销售资格失效后的批量下架结果。")),
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1",
                    new WorkflowPresentation("商品款色码建档",
                            "建立款式、SPU 与 6 个 SKU，并完成商品生命周期激活。")),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1",
                    new WorkflowPresentation("售后退款全链路",
                            "贯通发布、库存、下单支付、履约、退货质检、退款和库存恢复。")),
            Map.entry("skill.cloudmold.commerce.legacy-projection-plan.v1",
                    new WorkflowPresentation("旧系统投影预检",
                            "只读规划规范 SKU 向 Mall、ERP 与 WMS 的兼容投影，不执行旧系统写入。")),
            Map.entry("skill.cloudmold.commerce.reuse-ready-master.v1",
                    new WorkflowPresentation("商家与仓网主数据准备",
                            "校验身份与 ERP 仓，创建并激活商家店铺，绑定可用仓网。")),
            Map.entry("skill.cloudmold.commerce.terminal-readback.v1",
                    new WorkflowPresentation("全链路终态核验",
                            "只读核验商品、商家、刊登、订单、支付、履约、售后及仓网终态。")),
            Map.entry("skill.cloudmold.commerce.full-chain-hsf.v1",
                    new WorkflowPresentation("商品售后自治全链路",
                            "依次编排商品建档、旧系统投影、商家仓网、售后 Saga 与终态核验。")),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1",
                    new WorkflowPresentation("自动铺品",
                            "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读；缺少真实渠道回执时明确标记待渠道确认。")),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1",
                    new WorkflowPresentation("AI 自主经营日",
                            "每日生成全新商品与刊登，再由独立消费者身份完成选购、支付履约、售后、客服和社区种草闭环。")),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1",
                    new WorkflowPresentation("消费者选购与服务全旅程",
                            "模拟真实会员完成搜索、商详、收藏、加购、结算、下单支付、履约、售后、咨询与社区发布。")),
            Map.entry("skill.cloudmold.supply-planning.prepare.v1",
                    new WorkflowPresentation("补货单准备",
                            "将已批准的补货建议转换为真实采购或调拨草稿，并明确后续等待的供应商或仓储事件。")),
            Map.entry("skill.cloudmold.operations.daily-business-control.v1",
                    new WorkflowPresentation("日经营控制",
                            "只读汇总当日经营指标、异常与建议工单；事实不完整时保持待数据状态。")),
            Map.entry("skill.cloudmold.operations.weekly-business-review.v1",
                    new WorkflowPresentation("周经营复盘",
                            "只读汇总周度目标偏差、异常与行动建议；事实不完整时保持待数据状态。")),
            Map.entry("skill.cloudmold.merchant.onboarding-readback.v1",
                    new WorkflowPresentation("商家入驻终态跟踪",
                            "持续回读商家申请、门店与授权终态，不代替人工审批或领域写入。")),
            Map.entry("skill.cloudmold.merchant.onboarding-lifecycle.v1",
                    new WorkflowPresentation("商家入驻经营闭环",
                            "模拟招商运营完成资料建档、提交、审核、批准、商家激活、店铺激活与终态验收；每天创建全新的测试商家。")),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1",
                    new WorkflowPresentation("促销活动投放闭环",
                            "模拟活动运营完成活动启用、人群触达、渠道发送、送达、打开、点击、活动收尾与终态验收；每天生成全新活动。")),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1",
                    new WorkflowPresentation("增长实验决策闭环",
                            "模拟增长运营完成实验活动、分组、曝光、指标快照、护栏判断、显著性结论与终态验收；每天生成全新实验。")),
            Map.entry("skill.cloudmold.supplier.sourcing-lifecycle.v1",
                    new WorkflowPresentation("供应商寻源定标闭环",
                            "模拟采购寻源岗位完成双供应商准入、RFQ、双报价、样品评估、成本产能比较、定标与终态验收；每天生成全新寻源案例。")),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1",
                    new WorkflowPresentation("采购订单履约闭环",
                            "基于最近一次真实定标结果创建采购订单，完成审批下发、供应商确认与终态验收；每天生成全新采购订单。")),
            Map.entry("skill.cloudmold.wms.operations.v1",
                    new WorkflowPresentation("仓储收发调盘闭环",
                            "模拟仓储运营完成商家与双仓造数、商品建档、收货、调拨、出库、盘点及库存验收；每天生成全新仓储单据与库存轨迹。")),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1",
                    new WorkflowPresentation("智能补货执行闭环",
                            "模拟补货运营从需求场景、双供应商寻源、定标、采购下发到收货、调拨、出库与库存验收；日常补货与大促补货按日期自动轮换。")),
            Map.entry("skill.cloudmold.customer-service.resolution-lifecycle.v1",
                    new WorkflowPresentation("客服咨询解决闭环",
                            "模拟真实用户就最近订单发起咨询，客服完成建单、关联订单、接收消息、分派、处理、解决、满意度反馈、关单与终态验收。")),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-readback.v1",
                    new WorkflowPresentation("促销活动终态跟踪",
                            "持续回读活动与投放结果；依赖数据未齐备时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.engagement.growth-experiment-readback.v1",
                    new WorkflowPresentation("增长实验终态跟踪",
                            "持续回读增长实验结果；样本或归因未齐备时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.commerce.order-cancellation-operational.v1",
                    new WorkflowPresentation("订单取消补偿闭环",
                            "提交真实订单取消补偿 Saga START，并持续回读至成功或人工复核；"
                                    + "PAID_UNSHIPPED 当前仅自动覆盖 INTERNAL_TEST 支付退款，真实 PSP 仍由外部权威负责。")),
            Map.entry("skill.cloudmold.commerce.order-to-cash-readback.v1",
                    new WorkflowPresentation("订单到回款终态跟踪",
                            "持续核验订单、库存、支付与履约事实，直至订单到回款链路形成终态。")),
            Map.entry("skill.cloudmold.commerce.order-cancellation-readback.v1",
                    new WorkflowPresentation("订单取消终态跟踪",
                            "持续核验取消 Saga、库存释放与退款事实，异常或人工处理会明确留痕。")),
            Map.entry("skill.cloudmold.commerce.fulfillment-exception-readback.v1",
                    new WorkflowPresentation("履约异常终态跟踪",
                            "持续核验订单履约异常的处理结果，未闭环时保持等待或人工处理状态。")),
            Map.entry("skill.cloudmold.commerce.return-refund-readback.v1",
                    new WorkflowPresentation("退货退款终态跟踪",
                            "持续核验售后、退货质检、退款与库存恢复事实，直至闭环终态。")),
            Map.entry("skill.cloudmold.customer-service.resolution-readback.v1",
                    new WorkflowPresentation("客户问题解决终态跟踪",
                            "持续回读客服工单和解决结果，不越过客服审批或领域写入边界。")),
            Map.entry("skill.cloudmold.quality.recall-readback.v1",
                    new WorkflowPresentation("质量召回终态跟踪",
                            "持续回读质量召回动作及影响范围，证据不完整时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.quality.inspection-recall-lifecycle.v1",
                    new WorkflowPresentation("质量检验与召回闭环",
                            "模拟质量运营岗位完成标准发布、双人持证检验、CAPA、批次召回、库存隔离和终态验收；每天生成全新质检批次。")),
            Map.entry("skill.cloudmold.risk.dispute-readback.v1",
                    new WorkflowPresentation("风险争议终态跟踪",
                            "持续回读风险争议处置结果，未决或待人工裁定时不会误报成功。")),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1",
                    new WorkflowPresentation("支付对账终态跟踪",
                            "持续核验订单与支付对账结果，账实未一致时保持等待并展示阻塞项。")),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                    new WorkflowPresentation("供应商采购确认跟踪",
                            "从真实采购单创建事件持续核验供应商确认状态；仅覆盖采购确认，不冒充 RFQ、比价、样品或供应商准入。")),
            Map.entry("skill.cloudmold.supplier.sourcing-decision-readback.v1",
                    new WorkflowPresentation("供应商寻源定标终态跟踪",
                            "从真实 RFQ 创建事件持续核验多供应商报价、样品评估、准入和定标结果；仅领域 AWARDED 终态视为成功。")),
            Map.entry("skill.cloudmold.finance.close-readiness.v1",
                    new WorkflowPresentation("财务关账准备度跟踪",
                            "从真实会计期间开启事件持续核验渠道账单、对账差异、结算批次和凭证；仅领域 CLOSED 终态视为成功。")),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1",
                    new WorkflowPresentation("财务结算关账闭环",
                            "模拟财务结算岗位以制单、复核双身份完成账单导入、差异调整、结算、凭证、过账、关账与终态验收；每天生成全新账期。")),
            Map.entry("skill.cloudmold.warehouse.allocation-transfer-readback.v1",
                    new WorkflowPresentation("库存调拨终态跟踪",
                            "从已批准的补货转换事件持续核验真实 WMS 移库单；仅移库完成视为成功，作废明确进入失败终态。")),
            Map.entry("skill.cloudmold.warehouse.inbound-readback.v1",
                    new WorkflowPresentation("仓库入库终态跟踪",
                            "持续核验 ASN、收货与上架状态；仅上架完成视为成功，ASN 取消明确进入失败终态。"))
    );

    private final SkillTaskMapper mapper;
    private final SkillTaskDefinitionRegistry definitionRegistry;
    private final ManagedSkillTaskBusinessOutcomePresenter outcomePresenter;
    private final ManagedSkillTaskBusinessTimelinePresenter timelinePresenter;

    public List<ManagedSkillTaskWorkflowView> listManagedWorkflows() {
        return definitionRegistry.all().stream()
                .filter(definition -> "BUSINESS_ROLE".equals(definition.getWorkflowLevel()))
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
                .workflowLevel(definition.getWorkflowLevel())
                .ownerRole(definition.getOwnerRole())
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
