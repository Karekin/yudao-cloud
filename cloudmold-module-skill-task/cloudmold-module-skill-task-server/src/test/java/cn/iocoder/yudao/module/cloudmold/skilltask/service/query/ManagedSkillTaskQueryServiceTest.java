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
