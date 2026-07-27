package cn.iocoder.yudao.module.cloudmold.aioperations.service.command;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.ManagedSkillTaskTriggerReqVO;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsManagedRunCommandServiceTest {

    private final AiOperationsManagedRunQueryServiceFacade workflowQuery =
            mock(AiOperationsManagedRunQueryServiceFacade.class);
    private final AgentExecutionTicketApi executionTicketApi = mock(AgentExecutionTicketApi.class);
    private final SkillTaskCommandApi skillTaskCommandApi = mock(SkillTaskCommandApi.class);
    private final AiOperationsManagedRunCommandService service =
            new AiOperationsManagedRunCommandService(workflowQuery, executionTicketApi, skillTaskCommandApi);
    private MockedStatic<SecurityFrameworkUtils> securityFrameworkUtils;
    private SecurityContext webSecurityContext;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(101L);
        loginUser.setUserType(2);
        securityFrameworkUtils = mockStatic(SecurityFrameworkUtils.class);
        securityFrameworkUtils.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser);
        webSecurityContext = SecurityContextHolder.createEmptyContext();
        webSecurityContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
        SecurityContextHolder.setContext(webSecurityContext);
    }

    @AfterEach
    void tearDown() {
        CloudMoldRpcCallContext.clear();
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        securityFrameworkUtils.close();
    }

    @Test
    void shouldTriggerR1DirectlyFromAdminConsole() {
        ManagedSkillTaskTriggerReqVO request = request();
        when(workflowQuery.requireWorkflow(request.getSkillId(), request.getSkillVersion()))
                .thenReturn(workflow(false, "R1"));
        when(skillTaskCommandApi.submit(any())).thenAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(CloudMoldRpcCallContext.requireCurrent().skillId()).isEqualTo("ai-operations.console");
            var command = invocation.getArgument(0,
                    cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand.class);
            assertThat(command.getApprovalRef()).isNull();
            assertThat(command.getRiskLevel()).isEqualTo("R1");
            return task("R1");
        });

        ManagedSkillTaskTriggerResult result = service.trigger(request);

        assertThat(result.getTaskId()).isEqualTo("task-1");
        assertThat(result.getApprovalRefSha256()).isNull();
        assertThat(SecurityContextHolder.getContext()).isSameAs(webSecurityContext);
        verify(executionTicketApi, never()).issue(any(), any());
    }

    @Test
    void shouldExchangeApprovedContextServerSideWithoutReturningRawTicket() {
        ManagedSkillTaskTriggerReqVO request = request();
        ManagedSkillTaskTriggerReqVO.ApprovalContext approval = new ManagedSkillTaskTriggerReqVO.ApprovalContext();
        approval.setWorkOrderId("wo-1");
        approval.setApprovalId("approval-1");
        approval.setWorkOrderExpectedVersion(3L);
        approval.setValidForSeconds(300L);
        request.setApproval(approval);
        when(workflowQuery.requireWorkflow(request.getSkillId(), request.getSkillVersion()))
                .thenReturn(workflow(true, "R3"));
        when(executionTicketApi.issue(any(), any())).thenReturn(AgentExecutionTicketResult.builder()
                .approvalRef("cma1.raw-secret")
                .approvalRefSha256("a".repeat(64))
                .expiresAt(Instant.parse("2026-07-26T04:00:00Z"))
                .build());
        when(skillTaskCommandApi.submit(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0,
                    cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand.class);
            assertThat(command.getApprovalRef()).isEqualTo("cma1.raw-secret");
            return task("R3");
        });

        ManagedSkillTaskTriggerResult result = service.trigger(request);

        assertThat(result.getApprovalRefSha256()).isEqualTo("a".repeat(64));
        assertThat(result.toString()).doesNotContain("cma1.raw-secret");
        verify(executionTicketApi).issue(any(), org.mockito.ArgumentMatchers.eq(101L));
    }

    @Test
    void shouldRejectApprovalContextForR1() {
        ManagedSkillTaskTriggerReqVO request = request();
        request.setApproval(new ManagedSkillTaskTriggerReqVO.ApprovalContext());
        when(workflowQuery.requireWorkflow(request.getSkillId(), request.getSkillVersion()))
                .thenReturn(workflow(false, "R1"));

        assertThatThrownBy(() -> service.trigger(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("R1");
        verify(skillTaskCommandApi, never()).submit(any());
    }

    private static ManagedSkillTaskTriggerReqVO request() {
        ManagedSkillTaskTriggerReqVO request = new ManagedSkillTaskTriggerReqVO();
        request.setSkillId("skill-a");
        request.setSkillVersion("1.0.0");
        request.setRunId("run-1");
        request.setClientRequestKey("request-1");
        request.setInputJson("{}");
        return request;
    }

    private static ManagedSkillTaskWorkflowView workflow(boolean approvalRequired, String riskLevel) {
        return ManagedSkillTaskWorkflowView.builder()
                .skillId("skill-a")
                .skillVersion("1.0.0")
                .riskLevel(riskLevel)
                .approvalRequired(approvalRequired)
                .build();
    }

    private static SkillTaskView task(String riskLevel) {
        return SkillTaskView.builder()
                .taskId("task-1")
                .runId("run-1")
                .skillId("skill-a")
                .skillVersion("1.0.0")
                .riskLevel(riskLevel)
                .status("QUEUED")
                .currentStepCode("start")
                .inputSha256("i".repeat(64))
                .definitionClosureSha256("d".repeat(64))
                .createdAt(Instant.parse("2026-07-26T03:00:00Z"))
                .build();
    }
}
