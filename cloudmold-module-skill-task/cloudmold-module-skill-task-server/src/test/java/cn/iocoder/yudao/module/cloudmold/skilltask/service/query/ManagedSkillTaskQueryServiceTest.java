package cn.iocoder.yudao.module.cloudmold.skilltask.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedSkillTaskQueryServiceTest {

    private final SkillTaskMapper mapper = mock(SkillTaskMapper.class);
    private final SkillTaskDefinitionRegistry registry = mock(SkillTaskDefinitionRegistry.class);
    private final ManagedSkillTaskBusinessOutcomePresenter outcomePresenter =
            new ManagedSkillTaskBusinessOutcomePresenter(new ObjectMapper());
    private final ManagedSkillTaskBusinessTimelinePresenter timelinePresenter =
            new ManagedSkillTaskBusinessTimelinePresenter(outcomePresenter);
    private final ManagedSkillTaskQueryService service =
            new ManagedSkillTaskQueryService(mapper, registry, outcomePresenter, timelinePresenter);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDescribeManagedWorkflowsWithoutInventingInstanceRuntime() {
        SkillTaskDefinition definition = SkillTaskDefinition.builder()
                .skillId("skill.cloudmold.commerce.full-chain-hsf.v1")
                .skillVersion("1.2.0")
                .riskLevel("R3")
                .maxAttempts(4)
                .definitionSha256("d".repeat(64))
                .definitionClosureSha256("c".repeat(64))
                .steps(List.of(
                        SkillTaskDefinition.Step.builder()
                                .stepCode("submit-child")
                                .stepOrder(1)
                                .stepKind("SUBMIT_CHILD")
                                .operationType("ORCHESTRATE")
                                .arguments(JsonNodeFactory.instance.arrayNode())
                                .build(),
                        SkillTaskDefinition.Step.builder()
                                .stepCode("wait-child")
                                .stepOrder(2)
                                .stepKind("WAIT_CHILD")
                                .operationType("ORCHESTRATE")
                                .arguments(JsonNodeFactory.instance.arrayNode())
                                .build()))
                .build();
        when(registry.all()).thenReturn(List.of(definition));

        List<ManagedSkillTaskWorkflowView> workflows = service.listManagedWorkflows();

        assertThat(workflows).singleElement().satisfies(item -> {
            assertThat(item.getSkillId()).isEqualTo("skill.cloudmold.commerce.full-chain-hsf.v1");
            assertThat(item.getSkillVersion()).isEqualTo("1.2.0");
            assertThat(item.getDisplayName()).isEqualTo("商品售后自治全链路");
            assertThat(item.getDescription()).contains("商品建档", "售后 Saga", "终态核验");
            assertThat(item.getStepCount()).isEqualTo(2);
            assertThat(item.getWriteStepCount()).isZero();
            assertThat(item.getApprovalRequired()).isTrue();
            assertThat(item.getTriggerSource()).isEqualTo("ADMIN_CONSOLE");
            assertThat(item.getManagementSurface()).isEqualTo("DEER_FLOW");
            assertThat(item.getOrchestrationSurface()).isEqualTo("ADMIN_CONSOLE");
            assertThat(item.getDurableAuthority()).isEqualTo("SKILL_TASK");
        });
    }

    @Test
    void shouldExposeOnlyBusinessRoleWorkflows() {
        SkillTaskDefinition role = definition(
                "skill.cloudmold.commerce.product-to-listing.v1", "1.1.0", "R3");
        role.setWorkflowLevel("BUSINESS_ROLE");
        role.setOwnerRole("category-operations");
        SkillTaskDefinition readback = definition(
                "skill.cloudmold.listing.lifecycle-readback.v1", "1.0.0", "R1");
        readback.setWorkflowLevel("INTERNAL_SUBFLOW");
        when(registry.all()).thenReturn(List.of(readback, role));

        List<ManagedSkillTaskWorkflowView> workflows = service.listManagedWorkflows();

        assertThat(workflows).singleElement().satisfies(item -> {
            assertThat(item.getSkillId()).isEqualTo(role.getSkillId());
            assertThat(item.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
            assertThat(item.getOwnerRole()).isEqualTo("category-operations");
        });
    }

    @Test
    void shouldDescribeNewBusinessWorkflowsWithTruthfulCompletionBoundary() {
        SkillTaskDefinition productToListing = definition(
                "skill.cloudmold.commerce.product-to-listing.v1", "1.0.0", "R3");
        SkillTaskDefinition financeClose = definition(
                "skill.cloudmold.finance.close-lifecycle.v1", "1.0.0", "R3");
        SkillTaskDefinition fulfillmentException = definition(
                "skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1", "1.0.0", "R3");
        SkillTaskDefinition qualityLifecycle = definition(
                "skill.cloudmold.quality.inspection-recall-lifecycle.v1", "1.0.0", "R3");
        SkillTaskDefinition crossborderLifecycle = definition(
                "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1", "1.0.0", "R3");
        SkillTaskDefinition bondedCustomsLifecycle = definition(
                "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1", "1.0.0", "R3");
        SkillTaskDefinition replenishmentPrepare = definition(
                "skill.cloudmold.supply-planning.prepare.v1", "1.0.0", "R2");
        when(registry.all()).thenReturn(List.of(
                productToListing, financeClose, fulfillmentException, crossborderLifecycle,
                bondedCustomsLifecycle,
                qualityLifecycle, replenishmentPrepare));

        List<ManagedSkillTaskWorkflowView> workflows = service.listManagedWorkflows();

        assertThat(workflows).extracting(
                        ManagedSkillTaskWorkflowView::getDisplayName,
                        ManagedSkillTaskWorkflowView::getDescription)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "自动铺品",
                                "串联规范商品建档、商家店铺准备、商品刊登审核发布与终态回读；缺少真实渠道回执时明确标记待渠道确认。"),
                        org.assertj.core.groups.Tuple.tuple(
                                "保税仓关务闭环",
                                "模拟保税仓关务为全新已支付订单完成准入评估、商品归类、订单/支付/物流三单对碰、税费计算、风险与法务会签、海关受理、保税放行、境内妥投和关单；当前限定受控测试规则包。"),
                        org.assertj.core.groups.Tuple.tuple(
                                "跨境履约与关务合规闭环",
                                "模拟跨境运营为全新已支付订单完成受控直邮合规评估、AI 路线推荐、风险与法务会签、申报与三单校验、国际承运、清关放行、妥投和关单；当前限定 CN→US 测试规则包。"),
                        org.assertj.core.groups.Tuple.tuple(
                                "财务结算关账闭环",
                                "模拟财务结算岗位以制单、复核双身份完成账单导入、差异调整、结算、凭证、过账、关账与终态验收；每天生成全新账期。"),
                        org.assertj.core.groups.Tuple.tuple(
                                "履约异常处置闭环",
                                "模拟物流经理为全新在途订单识别异常、诊断影响、制定方案、经过审批、恢复交付、完成订单并关闭异常；每天生成全新订单与异常案例。"),
                        org.assertj.core.groups.Tuple.tuple(
                                "质量检验与召回闭环",
                                "模拟质量运营岗位完成标准发布、双人持证检验、CAPA、批次召回、库存隔离和终态验收；每天生成全新质检批次。"),
                        org.assertj.core.groups.Tuple.tuple(
                                "补货单准备",
                                "将已批准的补货建议转换为真实采购或调拨草稿，并明确后续等待的供应商或仓储事件。"));
    }

    @Test
    void shouldDescribeEveryManagedReadbackWorkflowWithoutApproval() {
        List<String> skillIds = List.of(
                "skill.cloudmold.operations.daily-business-control.v1",
                "skill.cloudmold.operations.weekly-business-review.v1",
                "skill.cloudmold.merchant.onboarding-readback.v1",
                "skill.cloudmold.engagement.promotion-campaign-readback.v1",
                "skill.cloudmold.engagement.growth-experiment-readback.v1",
                "skill.cloudmold.commerce.order-to-cash-readback.v1",
                "skill.cloudmold.commerce.order-cancellation-readback.v1",
                "skill.cloudmold.commerce.fulfillment-exception-readback.v1",
                "skill.cloudmold.commerce.return-refund-readback.v1",
                "skill.cloudmold.customer-service.resolution-readback.v1",
                "skill.cloudmold.quality.recall-readback.v1",
                "skill.cloudmold.risk.dispute-readback.v1",
                "skill.cloudmold.payment.reconciliation-readback.v1",
                "skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                "skill.cloudmold.supplier.sourcing-decision-readback.v1",
                "skill.cloudmold.finance.close-readiness.v1",
                "skill.cloudmold.listing.lifecycle-readback.v1",
                "skill.cloudmold.warehouse.allocation-transfer-readback.v1",
                "skill.cloudmold.warehouse.inbound-readback.v1");
        when(registry.all()).thenReturn(skillIds.stream()
                .map(skillId -> definition(skillId, "1.0.0", "R1"))
                .toList());

        List<ManagedSkillTaskWorkflowView> workflows = service.listManagedWorkflows();

        assertThat(workflows).hasSize(19)
                .allSatisfy(item -> {
                    assertThat(item.getApprovalRequired()).isFalse();
                    assertThat(item.getWriteStepCount()).isZero();
                    assertThat(item.getDescription()).doesNotContain("已完成");
                });
        assertThat(workflows).extracting(ManagedSkillTaskWorkflowView::getDisplayName)
                .containsExactlyInAnyOrder(
                        "日经营控制",
                        "周经营复盘",
                        "商家入驻终态跟踪",
                        "促销活动终态跟踪",
                        "增长实验终态跟踪",
                        "订单到回款终态跟踪",
                        "订单取消终态跟踪",
                        "履约异常终态跟踪",
                        "退货退款终态跟踪",
                        "客户问题解决终态跟踪",
                        "质量召回终态跟踪",
                        "风险争议终态跟踪",
                        "支付对账终态跟踪",
                        "供应商采购确认跟踪",
                        "供应商寻源定标终态跟踪",
                        "财务关账准备度跟踪",
                        "商品刊登生命周期终态跟踪",
                        "库存调拨终态跟踪",
                        "仓库入库终态跟踪");
    }

    @Test
    void shouldDescribeOperationalOrderCancellationWithHonestPspBoundary() {
        SkillTaskDefinition definition = SkillTaskDefinition.builder()
                .skillId("skill.cloudmold.commerce.order-cancellation-operational.v1")
                .skillVersion("1.0.0")
                .riskLevel("R3")
                .maxAttempts(3)
                .definitionSha256("d".repeat(64))
                .definitionClosureSha256("c".repeat(64))
                .steps(List.of(
                        SkillTaskDefinition.Step.builder()
                                .stepCode("start_order_cancellation")
                                .stepOrder(1)
                                .operationType("WRITE")
                                .arguments(JsonNodeFactory.instance.arrayNode())
                                .build(),
                        SkillTaskDefinition.Step.builder()
                                .stepCode("wait_order_cancellation")
                                .stepOrder(2)
                                .stepKind("WAIT_CAPABILITY")
                                .operationType("READ")
                                .arguments(JsonNodeFactory.instance.arrayNode())
                                .build()))
                .build();
        when(registry.all()).thenReturn(List.of(definition));

        List<ManagedSkillTaskWorkflowView> workflows = service.listManagedWorkflows();

        assertThat(workflows).singleElement().satisfies(item -> {
            assertThat(item.getDisplayName()).isEqualTo("订单取消补偿闭环");
            assertThat(item.getDescription()).contains("真实订单取消补偿 Saga START", "真实 PSP");
            assertThat(item.getRiskLevel()).isEqualTo("R3");
            assertThat(item.getWriteStepCount()).isEqualTo(1);
            assertThat(item.getApprovalRequired()).isTrue();
        });
    }

    @Test
    void shouldSanitizeManagedRunDetailWhileLinkingDirectChildTasks() {
        Task parent = task("task-parent", "run-parent", "skill.parent", "1.2.0", "R3", "RUNNING", "submit-child");
        parent.setInputSha256("i".repeat(64));
        parent.setDefinitionSha256("d".repeat(64));
        parent.setDefinitionClosureSha256("c".repeat(64));
        parent.setApprovalRef("ticket-raw");
        parent.setCreatedAt(LocalDateTime.parse("2026-07-26T09:00:00"));
        parent.setUpdatedAt(LocalDateTime.parse("2026-07-26T09:30:00"));
        parent.setVersion(5L);

        Step capabilityStep = new Step();
        capabilityStep.setTaskId("task-parent");
        capabilityStep.setStepCode("submit-child");
        capabilityStep.setStepOrder(1);
        capabilityStep.setStepKind("SUBMIT_CHILD");
        capabilityStep.setOperationType("ORCHESTRATE");
        capabilityStep.setChildSkillId("skill.child");
        capabilityStep.setChildSkillVersion("1.0.0");
        capabilityStep.setChildTaskId("task-child");
        capabilityStep.setStatus("SUCCEEDED");
        capabilityStep.setAttemptCount(1);
        capabilityStep.setRequestSha256("r".repeat(64));
        capabilityStep.setResultSha256("s".repeat(64));
        capabilityStep.setResultJson("{\"secret\":\"must-not-leak\"}");
        capabilityStep.setLastErrorCode("NONE");
        capabilityStep.setLastErrorMessage("contains payload");

        Task child = task("task-child", "run-child", "skill.child", "1.0.0", "R2", "SUCCEEDED", "done");
        child.setParentTaskId("task-parent");
        child.setParentStepCode("submit-child");
        child.setInputSha256("x".repeat(64));
        child.setDefinitionSha256("y".repeat(64));
        child.setDefinitionClosureSha256("z".repeat(64));
        child.setTerminalResultSha256("t".repeat(64));
        child.setCreatedAt(LocalDateTime.parse("2026-07-26T09:05:00"));
        child.setUpdatedAt(LocalDateTime.parse("2026-07-26T09:20:00"));
        child.setCompletedAt(LocalDateTime.parse("2026-07-26T09:25:00"));
        child.setVersion(2L);
        Step childBusinessStep = succeededStep("task-child", "activate_spu", 1,
                "{\"entityType\":\"SPU\",\"entityId\":\"spu-id\","
                        + "\"businessCode\":\"SPU-100\",\"currentStatus\":\"ACTIVE\"}");

        when(mapper.selectTask(7L, "task-parent")).thenReturn(parent);
        when(mapper.selectChildren(7L, "task-parent")).thenReturn(List.of(child));
        when(mapper.selectSteps(7L, "task-parent")).thenReturn(List.of(capabilityStep));
        when(mapper.selectStepsForTasks(7L, List.of("task-child"))).thenReturn(List.of(childBusinessStep));

        ManagedSkillTaskDetailView detail = service.getManagedRun(" task-parent ");

        assertThat(detail.getTask().getTaskId()).isEqualTo("task-parent");
        assertThat(detail.getSteps()).singleElement().satisfies(step -> {
            assertThat(step.getStepCode()).isEqualTo("submit-child");
            assertThat(step.getRequestSha256()).isEqualTo("r".repeat(64));
            assertThat(step.getResultSha256()).isEqualTo("s".repeat(64));
            assertThat(step.getLastErrorCode()).isEqualTo("NONE");
            assertThat(step.getChildTask()).isNotNull();
            assertThat(step.getChildTask().getTaskId()).isEqualTo("task-child");
            assertThat(step.getChildTask().getTerminalResultSha256()).isEqualTo("t".repeat(64));
        });
        assertThat(detail.getBusinessPhases()).singleElement().satisfies(phase -> {
            assertThat(phase.getTaskId()).isEqualTo("task-child");
            assertThat(phase.getApprovalStatus()).isEqualTo("APPROVED");
            assertThat(phase.getActions()).singleElement().satisfies(action -> {
                assertThat(action.getDisplayName()).isEqualTo("启用 SPU");
                assertThat(action.getResultSummary()).isEqualTo("SPU SPU-100 已变更为 已启用");
                assertThat(action.getBusinessObjects()).singleElement()
                        .extracting("businessCode", "status")
                        .containsExactly("SPU-100", "ACTIVE");
            });
        });
        verify(mapper).selectTask(7L, "task-parent");
    }

    @Test
    void shouldPresentCatalogCompletionAsConcreteBusinessOutcome() {
        Task task = task("task-catalog", "run-catalog", "skill.cloudmold.commerce.catalog-matrix.v1",
                "1.2.0", "R2", "SUCCEEDED", null);
        task.setInputJson("""
                {"definitions":[
                  {"spuCode":"SPU-100","skuCode":"SKU-BLACK-S","colorCode":"BLACK","sizeCode":"S"},
                  {"spuCode":"SPU-100","skuCode":"SKU-WHITE-M","colorCode":"WHITE","sizeCode":"M"}
                ]}
                """);
        task.setTerminalResultSha256("e".repeat(64));
        Step defineOne = succeededStep("task-catalog", "define_1", 1,
                "{\"created\":true,\"canonicalSpuId\":\"spu-id\",\"canonicalSkuId\":\"sku-1\"}");
        defineOne.setRequestJson("[{\"skuCode\":\"SKU-BLACK-S\"}]");
        Step defineTwo = succeededStep("task-catalog", "define_2", 2,
                "{\"created\":true,\"canonicalSpuId\":\"spu-id\",\"canonicalSkuId\":\"sku-2\"}");
        defineTwo.setRequestJson("[{\"skuCode\":\"SKU-WHITE-M\"}]");
        Step activateSpu = succeededStep("task-catalog", "activate_spu", 3,
                "{\"entityType\":\"SPU\",\"businessCode\":\"SPU-100\",\"currentStatus\":\"ACTIVE\"}");

        when(mapper.selectTask(7L, "task-catalog")).thenReturn(task);
        when(mapper.selectChildren(7L, "task-catalog")).thenReturn(List.of());
        when(mapper.selectSteps(7L, "task-catalog")).thenReturn(List.of(defineOne, defineTwo, activateSpu));

        ManagedSkillTaskDetailView detail = service.getManagedRun("task-catalog");

        assertThat(detail.getTask().getBusinessOutcome()).satisfies(outcome -> {
            assertThat(outcome.getHeadline()).isEqualTo("新品 SPU-100 已完成建档并启用");
            assertThat(outcome.getSummary()).contains("商品生命周期审批及启用完成");
            assertThat(outcome.getMetrics()).extracting("label", "value")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("SPU", "1"),
                            org.assertj.core.groups.Tuple.tuple("SKU", "2"),
                            org.assertj.core.groups.Tuple.tuple("颜色", "2"),
                            org.assertj.core.groups.Tuple.tuple("尺码", "2"));
            assertThat(outcome.getBusinessObjects()).hasSize(3);
            assertThat(outcome.getEvidenceSha256()).isEqualTo("e".repeat(64));
        });
        assertThat(detail.getSteps().get(0).getDisplayName()).isEqualTo("创建第 1 个 SKU");
        assertThat(detail.getSteps().get(0).getResultSummary()).isEqualTo("已创建 SKU SKU-BLACK-S");
    }

    @Test
    void shouldKeepUnstartedChildWorkflowVisibleAsPendingBusinessPhase() {
        Task parent = task("task-chain", "run-chain", "skill.cloudmold.commerce.full-chain-hsf.v1",
                "1.2.0", "R3", "NEEDS_REVIEW", "wait_catalog");
        parent.setCreatedAt(LocalDateTime.parse("2026-07-26T10:00:00"));
        parent.setUpdatedAt(LocalDateTime.parse("2026-07-26T10:10:00"));
        Step submitCatalog = orchestrationStep("task-chain", "submit_catalog", 1, "SUBMIT_CHILD", "SUCCEEDED");
        submitCatalog.setChildTaskId("task-catalog-child");
        Step waitCatalog = orchestrationStep("task-chain", "wait_catalog", 2, "WAIT_CHILD", "NEEDS_REVIEW");
        Step submitMaster = orchestrationStep("task-chain", "submit_master", 3, "SUBMIT_CHILD", "PENDING");
        Step waitMaster = orchestrationStep("task-chain", "wait_master", 4, "WAIT_CHILD", "PENDING");
        Task child = task("task-catalog-child", "run-catalog-child",
                "skill.cloudmold.commerce.catalog-matrix.v1", "1.2.0",
                "R2", "NEEDS_REVIEW", "approve_spu");
        child.setParentTaskId("task-chain");
        child.setParentStepCode("submit_catalog");
        child.setCreatedAt(LocalDateTime.parse("2026-07-26T10:01:00"));
        child.setUpdatedAt(LocalDateTime.parse("2026-07-26T10:09:00"));
        Step childAction = succeededStep("task-catalog-child", "submit_spu", 1,
                "{\"entityType\":\"SPU\",\"businessCode\":\"SPU-200\",\"currentStatus\":\"SUBMITTED\"}");

        when(mapper.selectTask(7L, "task-chain")).thenReturn(parent);
        when(mapper.selectChildren(7L, "task-chain")).thenReturn(List.of(child));
        when(mapper.selectSteps(7L, "task-chain"))
                .thenReturn(List.of(submitCatalog, waitCatalog, submitMaster, waitMaster));
        when(mapper.selectStepsForTasks(7L, List.of("task-catalog-child"))).thenReturn(List.of(childAction));

        ManagedSkillTaskDetailView detail = service.getManagedRun("task-chain");

        assertThat(detail.getBusinessPhases()).hasSize(2);
        assertThat(detail.getBusinessPhases().get(0)).satisfies(phase -> {
            assertThat(phase.getDisplayName()).isEqualTo("商品款色码建档");
            assertThat(phase.getStatus()).isEqualTo("NEEDS_REVIEW");
            assertThat(phase.getActions()).singleElement()
                    .extracting("displayName", "resultSummary")
                    .containsExactly("提交 SPU 审核", "SPU SPU-200 已变更为 已提交");
        });
        assertThat(detail.getBusinessPhases().get(1)).satisfies(phase -> {
            assertThat(phase.getDisplayName()).isEqualTo("商家与仓网主数据准备");
            assertThat(phase.getStatus()).isEqualTo("PENDING");
            assertThat(phase.getTaskId()).isNull();
            assertThat(phase.getActions()).isEmpty();
        });
    }

    @Test
    void shouldNormalizeManagedRunPageFiltersBeforeDelegatingToMapper() {
        ManagedSkillTaskRunPageRequest request = new ManagedSkillTaskRunPageRequest();
        request.setPageNo(2);
        request.setPageSize(5);
        request.setTaskId(" task-1 ");
        request.setRunId(" run-1 ");
        request.setSkillId(" skill-a ");
        request.setStatus(" running ");
        request.setRiskLevel(" r2 ");
        when(mapper.countManagedRunPage(7L, "task-1", "run-1", "skill-a", "RUNNING", "R2")).thenReturn(0L);

        var result = service.getManagedRunPage(request);

        assertThat(result.getTotal()).isZero();
        verify(mapper).countManagedRunPage(7L, "task-1", "run-1", "skill-a", "RUNNING", "R2");
    }

    private static Task task(String taskId, String runId, String skillId, String skillVersion, String riskLevel,
                             String status, String currentStepCode) {
        Task task = new Task();
        task.setTaskId(taskId);
        task.setRunId(runId);
        task.setSkillId(skillId);
        task.setSkillVersion(skillVersion);
        task.setRiskLevel(riskLevel);
        task.setStatus(status);
        task.setCurrentStepCode(currentStepCode);
        task.setAttemptCount(1);
        task.setMaxAttempts(4);
        return task;
    }

    private static SkillTaskDefinition definition(String skillId, String version, String riskLevel) {
        return SkillTaskDefinition.builder()
                .skillId(skillId)
                .skillVersion(version)
                .riskLevel(riskLevel)
                .maxAttempts(3)
                .definitionSha256("d".repeat(64))
                .definitionClosureSha256("c".repeat(64))
                .steps(List.of())
                .build();
    }

    private static Step succeededStep(String taskId, String stepCode, int stepOrder, String resultJson) {
        Step step = new Step();
        step.setTaskId(taskId);
        step.setStepCode(stepCode);
        step.setStepOrder(stepOrder);
        step.setStepKind("CAPABILITY");
        step.setOperationType("WRITE");
        step.setStatus("SUCCEEDED");
        step.setAttemptCount(1);
        step.setResultJson(resultJson);
        return step;
    }

    private static Step orchestrationStep(
            String taskId, String stepCode, int stepOrder, String stepKind, String status) {
        Step step = new Step();
        step.setTaskId(taskId);
        step.setStepCode(stepCode);
        step.setStepOrder(stepOrder);
        step.setStepKind(stepKind);
        step.setOperationType("ORCHESTRATE");
        step.setStatus(status);
        step.setAttemptCount(0);
        return step;
    }
}
