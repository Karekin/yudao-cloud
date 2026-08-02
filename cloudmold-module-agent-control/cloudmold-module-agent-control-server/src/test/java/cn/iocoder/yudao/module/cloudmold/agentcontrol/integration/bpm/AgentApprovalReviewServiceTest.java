package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskTransferReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentApprovalDetailView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review.AgentApprovalReviewCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApprovalReviewServiceTest {

    private static final Long TENANT_ID = 162L;
    private static final Long REVIEWER_ID = 227L;

    @Mock
    private AgentControlStoreMapper mapper;
    @Mock
    private BpmTaskService bpmTasks;
    @Mock
    private AgentApprovalWorkflowService workflows;
    @Mock
    private Task task;

    private AgentApprovalWorkflowProperties properties;
    private AgentApprovalReviewService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(REVIEWER_ID);
        loginUser.setTenantId(TENANT_ID);
        loginUser.setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));

        properties = new AgentApprovalWorkflowProperties();
        properties.setEnabled(true);
        properties.setAiReviewerUserIds(Set.of(REVIEWER_ID));
        properties.setAiReviewAllowedRiskLevels(Set.of("R2"));
        service = new AgentApprovalReviewService(mapper, bpmTasks, properties, workflows);
        lenient().when(mapper.selectWorkOrder(TENANT_ID, "work-order-1"))
                .thenReturn(new WorkOrder().setAssigneeUserId(226L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void submitsApprovalForTheAuthenticatedAssignedAiReviewer() {
        stubReview("R2", 225L);
        stubAssignedTask(REVIEWER_ID);
        stubNewOperation();
        stubOperationCompletion();
        when(task.getId()).thenReturn("task-1");

        var result = service.submitDecision(command("APPROVE"));

        ArgumentCaptor<BpmTaskApproveReqVO> request = ArgumentCaptor.forClass(BpmTaskApproveReqVO.class);
        verify(bpmTasks).approveTask(eq(REVIEWER_ID), request.capture());
        verify(workflows).observe(argThat(event -> event.getId().equals("process-1")
                && event.getTerminalOperatorUserId().equals(REVIEWER_ID)
                && event.getTerminalTaskDefinitionKey().equals(
                        YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY)));
        assertThat(request.getValue().getId()).isEqualTo("task-1");
        assertThat(request.getValue().getReason())
                .contains("AI代理审核", "model=glm-review", "run=model-run-1", "evidenceSha256=");
        assertThat(result.getStatus()).isEqualTo("BPM_DECISION_SUBMITTED");
        assertThat(result.getDecision()).isEqualTo("APPROVE");
        assertThat(result.isDuplicate()).isFalse();
    }

    @Test
    void submitsRejectionWithTheSameAiAuditEvidence() {
        stubReview("R2", 225L);
        stubAssignedTask(REVIEWER_ID);
        stubNewOperation();
        stubOperationCompletion();
        when(task.getId()).thenReturn("task-1");

        var result = service.submitDecision(command("REJECT"));

        ArgumentCaptor<BpmTaskRejectReqVO> request = ArgumentCaptor.forClass(BpmTaskRejectReqVO.class);
        verify(bpmTasks).rejectTask(eq(REVIEWER_ID), request.capture());
        assertThat(request.getValue().getId()).isEqualTo("task-1");
        assertThat(request.getValue().getReason()).contains("decision=REJECT", "evidenceSha256=");
        assertThat(result.getDecision()).isEqualTo("REJECT");
    }

    @Test
    void returnsOnlyTheGovernedReviewContextWithoutExposingTheBpmTaskId() {
        stubReview("R2", 225L);
        stubAssignedTask(REVIEWER_ID);

        var context = service.getReviewContext("approval-1");

        assertThat(context.getApprovalId()).isEqualTo("approval-1");
        assertThat(context.getTaskDefinitionKey())
                .isEqualTo(YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY);
        assertThat(context.getBusinessAction()).contains("approval.action");
        assertThat(context.getImpactObjects()).contains("skuId=sku-1");
        assertThat(context.getEvidenceSummary()).isEqualTo("库存低于安全线");
        assertThat(Arrays.stream(context.getClass().getDeclaredFields()).map(field -> field.getName()))
                .doesNotContain("businessContextJson");
        assertThat(context).hasNoNullFieldsOrPropertiesExcept(
                "title", "roleCode", "scopeHash", "taskName", "requestedAt");
    }

    @Test
    void rejectsAReviewerThatIsNotTheBpmSelectedApprover() {
        stubReview("R2", 225L);
        stubNewOperation();
        when(task.getAssignee()).thenReturn("999");
        when(task.getTaskDefinitionKey()).thenReturn(YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY);
        when(bpmTasks.getRunningTaskListByProcessInstanceId("process-1", true, null))
                .thenReturn(List.of(task));

        assertThatThrownBy(() -> service.submitDecision(command("APPROVE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no governed BPM review task is assigned to the AI reviewer");
        verify(bpmTasks, never()).approveTask(any(), any());
    }

    @Test
    void replaysACompletedDecisionWithoutCompletingTheBpmTaskAgain() {
        stubReview("R2", 225L);
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(3));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(42L);
        when(mapper.selectOperationForUpdate(42L, TENANT_ID)).thenAnswer(invocation -> new Operation()
                .setOperationId(42L)
                .setCommandType("AGENT_APPROVAL_AI_REVIEW")
                .setRequestHash(requestHash.get())
                .setAttemptToken("previous-attempt")
                .setStatus(10)
                .setResultJson("{\"approvalId\":\"approval-1\",\"decision\":\"APPROVE\","
                        + "\"status\":\"BPM_DECISION_SUBMITTED\",\"duplicate\":false}"));

        var result = service.submitDecision(command("APPROVE"));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getDecision()).isEqualTo("APPROVE");
        verify(bpmTasks, never()).approveTask(any(), any());
        verify(bpmTasks, never()).rejectTask(any(), any());
    }

    @Test
    void rejectsAuditIdentifiersThatCouldForgeTheReasonEnvelope() {
        AgentApprovalReviewCommand command = command("APPROVE");
        command.setModelId("model]\nforged=true");

        assertThatThrownBy(() -> service.submitDecision(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("modelId is invalid");
        verify(bpmTasks, never()).approveTask(any(), any());
    }

    @Test
    void rejectsRiskLevelsThatWereNotExplicitlyEnabled() {
        stubReview("R3", REVIEWER_ID);

        assertThatThrownBy(() -> service.submitDecision(command("APPROVE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("approval risk level is not enabled for AI review");
        verify(bpmTasks, never()).approveTask(any(), any());
    }

    @Test
    void remainsFailClosedWhenTheApprovalWorkflowIsDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.submitDecision(command("APPROVE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("approval workflow is disabled");
        verify(bpmTasks, never()).approveTask(any(), any());
    }

    @Test
    void takesOverAnExistingR2HumanTaskThroughAnAuditedTransfer() {
        properties.setAiReviewerOrSignEnabled(true);
        stubReview("R2", 225L);
        when(task.getId()).thenReturn("task-human");
        when(task.getAssignee()).thenReturn("225");
        when(task.getTaskDefinitionKey()).thenReturn(YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY);
        when(bpmTasks.getRunningTaskListByProcessInstanceId("process-1", true, null))
                .thenReturn(List.of(task));
        stubNewOperation();
        stubOperationCompletion();

        service.submitDecision(command("APPROVE"));

        ArgumentCaptor<BpmTaskTransferReqVO> transfer = ArgumentCaptor.forClass(BpmTaskTransferReqVO.class);
        verify(bpmTasks).transferTask(eq(225L), transfer.capture());
        assertThat(transfer.getValue().getId()).isEqualTo("task-human");
        assertThat(transfer.getValue().getAssigneeUserId()).isEqualTo(REVIEWER_ID);
        verify(bpmTasks).approveTask(eq(REVIEWER_ID), any(BpmTaskApproveReqVO.class));
    }

    @Test
    void completesEveryLegacyR3CountersignTaskAsOneAiOrSignDecision() {
        properties.setAiReviewerOrSignEnabled(true);
        properties.setAiReviewAllowedRiskLevels(Set.of("R2", "R3"));
        stubReview("R3", 225L);
        Task primary = governedTask("task-primary", "225", YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY);
        Task counter1 = governedTask("task-counter-1", "230",
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY);
        Task counter2 = governedTask("task-counter-2", "228",
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY);
        when(bpmTasks.getRunningTaskListByProcessInstanceId("process-1", true, null))
                .thenReturn(List.of(primary), List.of(primary), List.of(counter1, counter2),
                        List.of(counter2), List.of());
        stubNewOperation();
        stubOperationCompletion();

        service.submitDecision(command("APPROVE"));

        verify(bpmTasks, times(3)).transferTask(any(), any(BpmTaskTransferReqVO.class));
        verify(bpmTasks, times(3)).approveTask(eq(REVIEWER_ID), any(BpmTaskApproveReqVO.class));
    }

    @Test
    void prefersTheExplicitAiBranchAndLeavesTheHumanBranchUntouched() {
        properties.setAiReviewerOrSignEnabled(true);
        stubReview("R2", 225L);
        Task human = governedTask("task-human", "225", YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY);
        Task ai = governedTask("task-ai", String.valueOf(REVIEWER_ID),
                YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY);
        when(bpmTasks.getRunningTaskListByProcessInstanceId("process-1", true, null))
                .thenReturn(List.of(human, ai));
        stubNewOperation();
        stubOperationCompletion();

        service.submitDecision(command("APPROVE"));

        verify(bpmTasks, never()).transferTask(any(), any());
        ArgumentCaptor<BpmTaskApproveReqVO> approve = ArgumentCaptor.forClass(BpmTaskApproveReqVO.class);
        verify(bpmTasks).approveTask(eq(REVIEWER_ID), approve.capture());
        assertThat(approve.getValue().getId()).isEqualTo("task-ai");
    }

    private void stubReview(String riskLevel, Long approverUserId) {
        AgentApprovalDetailView detail = AgentApprovalDetailView.builder()
                .approvalId("approval-1")
                .workOrderId("work-order-1")
                .title("库存动作审核")
                .roleCode("inventory-operator")
                .actionCode("approval.action")
                .riskLevel(riskLevel)
                .approverUserId(approverUserId)
                .status("PENDING")
                .workflowStatus("RUNNING")
                .processInstanceId("process-1")
                .businessContextJson("{\"skuId\":\"sku-1\",\"quantity\":12,"
                        + "\"evidenceSummary\":\"库存低于安全线\"}")
                .build();
        when(mapper.selectApprovalDetail(TENANT_ID, "approval-1")).thenReturn(detail);
    }

    private void stubAssignedTask(Long assigneeUserId) {
        when(task.getAssignee()).thenReturn(String.valueOf(assigneeUserId));
        when(task.getTaskDefinitionKey()).thenReturn(YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY);
        when(bpmTasks.getRunningTaskListByProcessInstanceId("process-1", true, null))
                .thenReturn(List.of(task));
    }

    private static Task governedTask(String id, String assignee, String definitionKey) {
        Task governedTask = mock(Task.class);
        lenient().when(governedTask.getId()).thenReturn(id);
        when(governedTask.getAssignee()).thenReturn(assignee);
        when(governedTask.getTaskDefinitionKey()).thenReturn(definitionKey);
        return governedTask;
    }

    private void stubNewOperation() {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        doAnswer(invocation -> {
            attemptToken.set(invocation.getArgument(4));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(42L);
        when(mapper.selectOperationForUpdate(42L, TENANT_ID)).thenAnswer(invocation -> new Operation()
                .setOperationId(42L)
                .setAttemptToken(attemptToken.get())
                .setStatus(0));
    }

    private void stubOperationCompletion() {
        when(mapper.markOperationSucceeded(eq(42L), eq(TENANT_ID), eq("AGENT_APPROVAL"),
                eq("approval-1"), anyString(), any())).thenReturn(1);
    }

    private static AgentApprovalReviewCommand command(String decision) {
        return AgentApprovalReviewCommand.builder()
                .idempotencyKey("ai-review-approval-1-run-1")
                .approvalId("approval-1")
                .decision(decision)
                .reason("业务证据完整且所有安全门禁通过")
                .modelId("glm-review")
                .modelRunId("model-run-1")
                .evidenceSha256("a".repeat(64))
                .build();
    }
}
