package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityGovernanceApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedWorkflowAgentGovernanceSeederTest {

    @Test
    void shouldCreateMissingRolePolicyAndRequesterGrant() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        TemporalApprovalPolicyRecord approval = new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE");
        when(mapper.selectApprovalPolicy(162L)).thenReturn(approval);
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.wms.operations.v1").skillVersion("1.1.0")
                .riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand> command =
                ArgumentCaptor.forClass(
                        cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand.class);
        verify(commands, org.mockito.Mockito.times(4)).execute(command.capture(), eq(227L));
        assertThat(command.getAllValues())
                .extracting(value -> value.getOperation().name())
                .containsExactly("DEFINE_ROLE", "SET_ACTION_POLICY", "DEFINE_ROLE", "DEFINE_ROLE");
        verify(authority, org.mockito.Mockito.times(2))
                .executeAuthorityGovernance(any(), eq(227L));
        verify(authority).executeAuthorityGovernance(any(), eq(225L));
    }

    @Test
    void shouldLeaveExistingGovernanceUntouched() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.countActiveAgentRole(162L, "customer-service")).thenReturn(1);
        when(mapper.countEnabledAgentActionPolicy(
                162L, "customer-service", "ticket.resolve")).thenReturn(1);
        when(mapper.countEffectiveAgentRoleGrant(
                162L, 226L, "customer-service")).thenReturn(1);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.customer-service.resolution-lifecycle.v1")
                .riskLevel("R2").approvalRequired(true).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 0, 0, 0));
        verify(commands, never()).execute(any(), any());
        verify(authority, never()).executeAuthorityGovernance(any(), any());
    }

    @Test
    void shouldSeedIndependentQualityAndOperationsLeadResponsibility() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.quality.inspection-recall-lifecycle.v1")
                .skillVersion("1.0.0").riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        verify(authority, org.mockito.Mockito.times(2))
                .executeAuthorityGovernance(any(), eq(227L));
        verify(authority).executeAuthorityGovernance(any(), eq(225L));
    }

    @Test
    void shouldSeedLogisticsRoleWithCustomerServiceAndOperationsLeadResponsibility() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1")
                .skillVersion("1.0.0").riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand> command =
                ArgumentCaptor.forClass(
                        cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand.class);
        verify(commands, org.mockito.Mockito.times(4)).execute(command.capture(), eq(227L));
        assertThat(command.getAllValues().get(0).getRole().getRoleCode())
                .isEqualTo("logistics-operations");
        assertThat(command.getAllValues().get(1).getActionPolicy().getActionCode())
                .isEqualTo("fulfillment.exception-resolution");
    }

    @Test
    void shouldSeedCrossborderRoleWithIndependentRiskAndLegalResponsibility() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1")
                .skillVersion("1.0.0").riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand> command =
                ArgumentCaptor.forClass(
                        cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand.class);
        verify(commands, org.mockito.Mockito.times(4)).execute(command.capture(), eq(227L));
        assertThat(command.getAllValues().get(0).getRole().getRoleCode())
                .isEqualTo("crossborder-operations");
        assertThat(command.getAllValues().get(1).getActionPolicy().getActionCode())
                .isEqualTo("crossborder.fulfillment-compliance");
    }

    @Test
    void shouldSeedBondedCustomsRoleWithIndependentRiskAndLegalResponsibility() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.crossborder.bonded-customs-lifecycle.v1")
                .skillVersion("1.0.0").riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand> command =
                ArgumentCaptor.forClass(
                        cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand.class);
        verify(commands, org.mockito.Mockito.times(4)).execute(command.capture(), eq(227L));
        assertThat(command.getAllValues().get(0).getRole().getRoleCode())
                .isEqualTo("bonded-customs-operations");
        assertThat(command.getAllValues().get(1).getActionPolicy().getActionCode())
                .isEqualTo("crossborder.bonded-customs");
    }

    @Test
    void shouldSeedPartnerMarketingRoleWithIndependentRiskAndLegalResponsibility() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.partner-marketing.kol-media-operations.v1")
                .skillVersion("1.0.0").riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand> command =
                ArgumentCaptor.forClass(
                        cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand.class);
        verify(commands, org.mockito.Mockito.times(4)).execute(command.capture(), eq(227L));
        assertThat(command.getAllValues().get(0).getRole().getRoleCode())
                .isEqualTo("partner-marketing-operations");
        assertThat(command.getAllValues().get(1).getActionPolicy().getActionCode())
                .isEqualTo("partner-marketing.kol-media-operations");
    }

    @Test
    void shouldSeedSopReleaseWithBuyerAndFinanceResponsibility() {
        AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
        AgentAuthorityGovernanceApi authority = mock(AgentAuthorityGovernanceApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setRequesterUserId(226L)
                .setApproverUserId(225L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.supply-planning.sop-lifecycle.v1")
                .skillVersion("1.0.0").riskLevel("R3").approvalRequired(true)
                .definitionClosureSha256("a".repeat(64)).build();

        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult result =
                new ManagedWorkflowAgentGovernanceSeeder(commands, authority, mapper)
                        .reconcile(162L, List.of(workflow));

        assertThat(result).isEqualTo(
                new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(1, 3, 1, 3));
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand> command =
                ArgumentCaptor.forClass(
                        cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand.class);
        verify(commands, org.mockito.Mockito.times(4)).execute(command.capture(), eq(227L));
        assertThat(command.getAllValues().get(0).getRole().getRoleCode())
                .isEqualTo("supply-planning");
        assertThat(command.getAllValues().get(1).getActionPolicy().getActionCode())
                .isEqualTo("supply-planning.sop-release");
    }
}
