package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsTemporalSeedRunnerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReconcileEveryRegisteredWorkflowForEveryConfiguredTenant() {
        AiOperationsTemporalScheduleService schedules =
                mock(AiOperationsTemporalScheduleService.class);
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        ManagedWorkflowAgentGovernanceSeeder governance =
                mock(ManagedWorkflowAgentGovernanceSeeder.class);
        AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();
        properties.setEnabled(true);
        properties.setTenantIds(List.of("162", "163"));
        List<ManagedSkillTaskWorkflowView> registered =
                ManagedWorkflowDailyAutomationFixtures.workflows();
        List<String> reconciled = new ArrayList<>();
        when(workflows.listWorkflowsAs(
                properties.getOperatorUserId(), properties.getOperatorUserType()))
                .thenReturn(registered);
        when(governance.reconcile(any(), same(registered)))
                .thenReturn(new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(8, 0, 0, 0));
        when(schedules.reconcileManagedDaily(
                any(ManagedSkillTaskWorkflowView.class), same(properties)))
                .thenAnswer(invocation -> {
                    ManagedSkillTaskWorkflowView workflow = invocation.getArgument(0);
                    reconciled.add(TenantContextHolder.getRequiredTenantId()
                            + "|" + workflow.getSkillId() + "@" + workflow.getSkillVersion());
                    return true;
                });

        AiOperationsTemporalSeedRunner runner =
                new AiOperationsTemporalSeedRunner(schedules, properties, workflows, governance);
        runner.reconcile();

        Set<String> expected = properties.getTenantIds().stream()
                .flatMap(tenantId -> registered.stream()
                        .map(workflow -> tenantId + "|" + workflow.getSkillId()
                                + "@" + workflow.getSkillVersion()))
                .collect(Collectors.toSet());
        int expectedReconcileCount = properties.getTenantIds().size() * registered.size();
        assertThat(reconciled)
                .hasSize(expectedReconcileCount)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expected);
        verify(schedules, times(expectedReconcileCount))
                .reconcileManagedDaily(any(), any(AiOperationsTemporalSeedProperties.class));
        verify(workflows, times(2)).listWorkflowsAs(
                properties.getOperatorUserId(), properties.getOperatorUserType());
        verify(governance, times(2)).reconcile(any(), same(registered));
        verify(schedules, times(2)).pauseObsoleteManagedDaily(
                registered.stream().map(ManagedSkillTaskWorkflowView::getSkillId)
                        .collect(Collectors.toSet()));
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldIsolateOneWorkflowFailureAndContinueAcrossTenants() {
        AiOperationsTemporalScheduleService schedules =
                mock(AiOperationsTemporalScheduleService.class);
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        ManagedWorkflowAgentGovernanceSeeder governance =
                mock(ManagedWorkflowAgentGovernanceSeeder.class);
        AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();
        properties.setEnabled(true);
        properties.setTenantIds(List.of("162", "163"));
        List<ManagedSkillTaskWorkflowView> registered =
                ManagedWorkflowDailyAutomationFixtures.workflows();
        String failingSkillId = registered.get(1).getSkillId();
        String finalSkillId = registered.get(registered.size() - 1).getSkillId();
        List<String> attempts = new ArrayList<>();
        when(workflows.listWorkflowsAs(
                properties.getOperatorUserId(), properties.getOperatorUserType()))
                .thenReturn(registered);
        when(governance.reconcile(any(), same(registered)))
                .thenReturn(new ManagedWorkflowAgentGovernanceSeeder.ReconcileResult(8, 0, 0, 0));
        when(schedules.reconcileManagedDaily(
                any(ManagedSkillTaskWorkflowView.class), same(properties)))
                .thenAnswer(invocation -> {
                    ManagedSkillTaskWorkflowView workflow = invocation.getArgument(0);
                    Long tenantId = TenantContextHolder.getRequiredTenantId();
                    attempts.add(tenantId + "|" + workflow.getSkillId());
                    if (tenantId == 162L && failingSkillId.equals(workflow.getSkillId())) {
                        throw new IllegalStateException("fixture schedule failure");
                    }
                    return true;
                });

        AiOperationsTemporalSeedRunner runner =
                new AiOperationsTemporalSeedRunner(schedules, properties, workflows, governance);
        runner.reconcile();

        int expectedAttemptCount = properties.getTenantIds().size() * registered.size();
        assertThat(attempts)
                .hasSize(expectedAttemptCount)
                .contains("162|" + failingSkillId,
                        "162|" + finalSkillId,
                        "163|" + failingSkillId,
                        "163|" + finalSkillId);
        assertThat(attempts.indexOf("162|" + finalSkillId))
                .isGreaterThan(attempts.indexOf("162|" + failingSkillId));
        verify(schedules, times(2)).pauseObsoleteManagedDaily(
                registered.stream().map(ManagedSkillTaskWorkflowView::getSkillId)
                        .collect(Collectors.toSet()));
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }
}
