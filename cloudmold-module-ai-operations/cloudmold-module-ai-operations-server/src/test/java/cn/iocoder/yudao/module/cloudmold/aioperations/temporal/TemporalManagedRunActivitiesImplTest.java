package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityGovernanceApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityOperation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentBusinessCardView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommandApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlQueryApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunCommandService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalManagedRunActivitiesImplTest {

    private final AiOperationsManagedRunQueryServiceFacade workflows =
            mock(AiOperationsManagedRunQueryServiceFacade.class);
    private final AiOperationsManagedRunCommandService managedRuns =
            mock(AiOperationsManagedRunCommandService.class);
    private final AgentControlCommandApi agentCommands = mock(AgentControlCommandApi.class);
    private final AgentControlQueryApi agentQueries = mock(AgentControlQueryApi.class);
    private final AgentAuthorityGovernanceApi authorityGovernance =
            mock(AgentAuthorityGovernanceApi.class);
    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final TemporalManagedRunActivitiesImpl activities = new TemporalManagedRunActivitiesImpl(
            workflows, managedRuns, agentCommands, agentQueries, authorityGovernance, mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRouteHighRiskDecisionToOperatingPrincipal() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L).scheduleId("cloudmold-t162-hourly-listing")
                .skillId("skill.cloudmold.commerce.catalog").skillVersion("1.2.0")
                .inputJson("{}").operatorUserId(225L).operatorUserType(2)
                .roleCode("merchandising").actionCode("catalog.publish").build();
        when(workflows.requireWorkflowAs(request.getSkillId(), request.getSkillVersion(), 225L, 2))
                .thenReturn(ManagedSkillTaskWorkflowView.builder().approvalRequired(true).build());
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L).setApproverUserId(225L)
                .setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(agentCommands.execute(any(), eq(226L))).thenReturn(
                AgentControlResult.builder().status("WAITING_APPROVAL").aggregateVersion(1L).build(),
                AgentControlResult.builder().status("PENDING").aggregateVersion(1L).build());
        when(agentQueries.listBusinessCards("merchandising", "APPROVAL", "PENDING", 100))
                .thenReturn(List.of(AgentBusinessCardView.builder()
                        .cardType("APPROVAL").cardId("tap-4e65d3fbe8ad6535681b021b")
                        .roleCode("merchandising").actionCode("catalog.publish")
                        .riskLevel("R3")
                        .scopeHash("a".repeat(64)).requesterUserId(226L).build()));

        TemporalManagedRunState result = activities.prepare(
                request, "workflow-1", "run-1");

        assertThat(result.getStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.getExecutionUserId()).isEqualTo(226L);
        ArgumentCaptor<AgentAuthorityCommand> command =
                ArgumentCaptor.forClass(AgentAuthorityCommand.class);
        verify(authorityGovernance).executeAuthorityGovernance(command.capture(), eq(227L));
        assertThat(command.getValue().getOperation()).isEqualTo(AgentAuthorityOperation.GRANT_APPROVER);
        assertThat(command.getValue().getApprovalGrant().getApproverUserId()).isEqualTo(225L);
        assertThat(command.getValue().getApprovalGrant().getApprovalId())
                .isEqualTo("tap-4e65d3fbe8ad6535681b021b");
        verify(mapper).upsertRunBinding(any());
    }
}
