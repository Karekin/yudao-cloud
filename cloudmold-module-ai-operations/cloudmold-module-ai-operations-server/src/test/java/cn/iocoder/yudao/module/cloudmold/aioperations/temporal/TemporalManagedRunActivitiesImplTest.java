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
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskTerminalProofView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
    private final SkillTaskQueryApi skillTaskQueries = mock(SkillTaskQueryApi.class);
    private final SupplyPlanningQueryApi supplyPlanningQueries = mock(SupplyPlanningQueryApi.class);
    private final YudaoWarehouseInboundQueryApi warehouseInboundQueries =
            mock(YudaoWarehouseInboundQueryApi.class);
    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final TemporalManagedRunActivitiesImpl activities = new TemporalManagedRunActivitiesImpl(
            workflows, managedRuns, agentCommands, agentQueries, authorityGovernance, skillTaskQueries,
            supplyPlanningQueries, warehouseInboundQueries, mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        CloudMoldRpcCallContext.clear();
    }

    @Test
    void shouldRouteHighRiskDecisionToOperatingPrincipal() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L).scheduleId("cloudmold-t162-hourly-listing")
                .skillId("skill.cloudmold.commerce.catalog").skillVersion("1.2.0")
                .inputJson("{}").operatorUserId(1L).operatorUserType(1)
                .roleCode("merchandising").actionCode("catalog.publish").build();
        when(workflows.requireWorkflowAs(request.getSkillId(), request.getSkillVersion(), 1L, 1))
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

        when(agentQueries.getWorkOrder(result.getWorkOrderId())).thenReturn(
                AgentControlResult.builder().status("READY").aggregateVersion(3L).build());
        when(managedRuns.triggerAs(any(), eq(226L), eq(2)))
                .thenReturn(cn.iocoder.yudao.module.cloudmold.aioperations.service.command.ManagedSkillTaskTriggerResult
                        .builder().runId("managed-1").taskId("task-1").build());

        TemporalManagedRunState running = activities.resumeApproved(request, result);

        assertThat(running.getStatus()).isEqualTo("RUNNING");
        verify(managedRuns).triggerAs(any(), eq(226L), eq(2));
    }

    @Test
    void shouldPauseResumeCancelAndTimeoutWithinApprovalGate() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L).scheduleId("cloudmold-t162-hourly-listing")
                .skillId("skill.cloudmold.commerce.catalog").skillVersion("1.2.0")
                .inputJson("{}").operatorUserId(225L).operatorUserType(2)
                .build();
        TemporalManagedRunState waiting = TemporalManagedRunState.builder()
                .status("WAITING_APPROVAL").phase("APPROVAL_GATE")
                .temporalRunId("run-1").temporalWorkflowId("workflow-1")
                .workOrderId("work-order-1").approvalId("approval-1")
                .managedRunId("managed-run-1").taskId("task-1")
                .build();

        TemporalManagedRunState paused = activities.pause(request, waiting, "manual-pause");
        assertThat(paused.getStatus()).isEqualTo("PAUSED");
        assertThat(paused.getPauseReason()).isEqualTo("manual-pause");
        verify(mapper).updateRunBinding(eq(162L), eq("run-1"), eq("PAUSED"),
                eq("MANUAL_PAUSE"), eq("managed-run-1"), eq("task-1"), any());

        TemporalManagedRunState resumed = activities.resume(request, paused, "manual-resume");
        assertThat(resumed.getStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(resumed.getPhase()).isEqualTo("APPROVAL_GATE");
        verify(mapper).updateRunBinding(eq(162L), eq("run-1"), eq("WAITING_APPROVAL"),
                eq(null), eq("managed-run-1"), eq("task-1"), any());

        TemporalManagedRunState cancelled = activities.cancel(request, resumed, "manual-cancel");
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelReason()).isEqualTo("manual-cancel");
        assertThat(cancelled.getBusinessResult().getOutcomeCode()).isEqualTo("MANUAL_CANCELLED");
        verify(mapper).updateRunBinding(eq(162L), eq("run-1"), eq("CANCELLED"),
                eq("MANUAL_CANCELLED"), eq("managed-run-1"), eq("task-1"), any());

        TemporalManagedRunState timedOut = activities.timeout(request, resumed);
        assertThat(timedOut.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(timedOut.getBusinessResult().getOutcomeCode()).isEqualTo("APPROVAL_TIMEOUT");
        verify(mapper).updateRunBinding(eq(162L), eq("run-1"), eq("TIMED_OUT"),
                eq("APPROVAL_TIMEOUT"), eq("managed-run-1"), eq("task-1"), any());
    }

    @Test
    void shouldPrepareR1WithoutSubmittingAndCloseOnSkillTaskTerminal() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L).scheduleId("cloudmold-t162-hourly-listing")
                .skillId("skill.cloudmold.commerce.catalog").skillVersion("1.2.0")
                .inputJson("{}").operatorUserId(225L).operatorUserType(2)
                .build();
        when(workflows.requireWorkflowAs(request.getSkillId(), request.getSkillVersion(), 225L, 2))
                .thenReturn(ManagedSkillTaskWorkflowView.builder().approvalRequired(false).build());
        when(managedRuns.triggerAs(any(), eq(225L), eq(2)))
                .thenReturn(cn.iocoder.yudao.module.cloudmold.aioperations.service.command.ManagedSkillTaskTriggerResult.builder()
                        .runId("run-r1").taskId("task-r1").build());
        when(skillTaskQueries.get("task-r1")).thenAnswer(invocation -> {
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(
                    new CloudMoldRpcCallContext(162L, 225L, 2,
                            "skill.cloudmold.commerce.catalog", "temporal:run-r1"));
            return SkillTaskView.builder()
                    .taskId("task-r1").runId("run-r1").status("SUCCEEDED").build();
        });
        when(skillTaskQueries.getTerminalProof("task-r1")).thenReturn(SkillTaskTerminalProofView.builder()
                .taskId("task-r1").runId("run-r1").status("SUCCEEDED")
                .terminalResultSha256("proof-1").build());

        TemporalManagedRunState prepared = activities.prepare(request, "workflow-r1", "run-r1");
        assertThat(prepared.getStatus()).isEqualTo("READY_FOR_SUBMISSION");

        TemporalManagedRunState running = activities.resumeApproved(request, prepared);
        assertThat(running.getStatus()).isEqualTo("RUNNING");
        assertThat(running.getTaskId()).isEqualTo("task-r1");

        TemporalManagedRunState refreshed = activities.refreshSkillTask(request, running);
        assertThat(refreshed.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(refreshed.getBusinessResult().getEvidenceRef()).isEqualTo("proof-1");
    }

    @Test
    void shouldEnterReplenishmentBusinessEventGateFromDomainCandidateInput() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L).scheduleId("cloudmold-t162-managed-daily-supply-planning-prepare")
                .skillId("skill.cloudmold.supply-planning.prepare.v1").skillVersion("1.0.0")
                .inputJson("""
                        {"proposalId":"proposal-1","recommendationId":"recommendation-1",
                         "conversionId":"conversion-1","occurredAt":"2026-07-29T09:30:00Z"}
                        """)
                .operatorUserId(226L).operatorUserType(2)
                .build();
        TemporalManagedRunState running = TemporalManagedRunState.builder()
                .status("RUNNING").phase("SKILL_TASK")
                .temporalRunId("run-replenishment").temporalWorkflowId("workflow-replenishment")
                .managedRunId("managed-replenishment").taskId("task-replenishment")
                .build();
        when(skillTaskQueries.get("task-replenishment")).thenReturn(SkillTaskView.builder()
                .taskId("task-replenishment").runId("managed-replenishment").status("SUCCEEDED").build());
        when(skillTaskQueries.getTerminalProof("task-replenishment"))
                .thenReturn(SkillTaskTerminalProofView.builder()
                        .taskId("task-replenishment").runId("managed-replenishment")
                        .status("SUCCEEDED").terminalResultSha256("proof-replenishment").build());
        when(supplyPlanningQueries.requireReplenishmentBusinessStage("recommendation-1"))
                .thenReturn(ReplenishmentBusinessStageView.builder()
                        .recommendationId("recommendation-1")
                        .targetType("TRANSFER_REQUEST")
                        .projectionSourceSystem("YUDAO_WMS")
                        .projectionDocumentType("MOVEMENT_ORDER")
                        .projectionExternalDocumentId("4")
                        .projectionDocumentStatus("PREPARE")
                        .nextWaitingEventCode("TRANSFER_OUTBOUND")
                        .nextWaitingEventLabel("等待调拨出库")
                        .build());

        TemporalManagedRunState refreshed = activities.refreshSkillTask(request, running);

        assertThat(refreshed.getStatus()).isEqualTo("WAITING_EVENT");
        assertThat(refreshed.getBusinessReferenceId()).isEqualTo("recommendation-1");
        assertThat(refreshed.getWaitingOn()).isEqualTo("TRANSFER_OUTBOUND");
        verify(mapper, times(2)).markRunBindingWaitingEvent(eq(162L), eq("run-replenishment"),
                eq("recommendation-1"), eq("managed-replenishment"), eq("task-replenishment"), any());
    }

    @Test
    void shouldCompleteReplenishmentRunWhenWmsTransferIsFinished() {
        TemporalManagedRunRequest request = TemporalManagedRunRequest.builder()
                .tenantId(162L).scheduleId("cloudmold-t162-managed-daily-supply-planning-prepare")
                .skillId("skill.cloudmold.supply-planning.prepare.v1").skillVersion("1.0.0")
                .inputJson("""
                        {"proposalId":"proposal-finished",
                         "recommendationId":"recommendation-finished",
                         "occurredAt":"2026-07-29T10:30:00Z"}
                        """)
                .operatorUserId(226L).operatorUserType(2)
                .build();
        TemporalManagedRunState waiting = TemporalManagedRunState.builder()
                .status("WAITING_EVENT").phase("BUSINESS_EVENT_GATE")
                .temporalRunId("run-finished").temporalWorkflowId("workflow-finished")
                .managedRunId("managed-finished").taskId("task-finished")
                .businessReferenceId("recommendation-finished")
                .waitingOn("TRANSFER_OUTBOUND")
                .build();
        when(supplyPlanningQueries.requireReplenishmentBusinessStage("recommendation-finished"))
                .thenReturn(ReplenishmentBusinessStageView.builder()
                        .recommendationId("recommendation-finished")
                        .targetType("TRANSFER_REQUEST")
                        .projectionSourceSystem("YUDAO_WMS")
                        .projectionDocumentType("MOVEMENT_ORDER")
                        .projectionExternalDocumentId("6")
                        .projectionDocumentStatus("FINISHED")
                        .nextWaitingEventCode("NONE")
                        .nextWaitingEventLabel("调拨已完成")
                        .build());

        TemporalManagedRunState completed =
                activities.refreshBusinessEventWait(request, waiting, "6");

        assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(completed.getPhase()).isEqualTo("COMPLETED");
        assertThat(completed.getBusinessResult().getOutcomeCode())
                .isEqualTo("REPLENISHMENT_COMPLETED");
        assertThat(completed.getBusinessResult().getSummary())
                .isEqualTo("补货调拨已完成并更新库存");
        verify(mapper).updateRunBinding(
                eq(162L), eq("run-finished"), eq("SUCCEEDED"), isNull(),
                eq("managed-finished"), eq("task-finished"), any());
    }
}
