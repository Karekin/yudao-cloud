package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm.AgentApprovalWorkflowAttestationGate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm.AgentApprovalWorkflowRegistrar;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentControlServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentApprovalWorkflowAttestationGate approvalAttestations =
            mock(AgentApprovalWorkflowAttestationGate.class);
    private final AgentControlServiceImpl service = new AgentControlServiceImpl(mapper,
            Clock.fixed(NOW, ZoneOffset.UTC), AgentApprovalWorkflowRegistrar.DISABLED, approvalAttestations);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final AtomicReference<WorkOrder> workOrder = new AtomicReference<>();
    private final AtomicReference<Approval> approval = new AtomicReference<>();
    private final AtomicReference<Handoff> handoff = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        when(mapper.insertOrResolveOperation(eq(17L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(1L);
        when(mapper.selectOperationForUpdate(1L, 17L)).thenAnswer(invocation -> new Operation()
                .setOperationId(1L).setTenantId(17L).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(anyLong(), eq(17L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);
        when(approvalAttestations.supportsR3MultiPartyApproval()).thenReturn(true);
        when(mapper.insertAgentOutbox(any(), eq(17L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.selectRole(eq(17L), anyString())).thenAnswer(invocation -> activeRole(invocation.getArgument(1)));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new ActorRoleGrant().setTenantId(17L)
                        .setActorUserId(invocation.getArgument(1)).setRoleCode(invocation.getArgument(2))
                        .setStatus("ACTIVE").setVersion(1L));
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> new ApprovalAuthorityGrant().setTenantId(17L)
                        .setApproverUserId(invocation.getArgument(1)).setApprovalId(invocation.getArgument(2))
                        .setRoleCode(invocation.getArgument(3)).setActionCode(invocation.getArgument(4))
                        .setRiskLevel(invocation.getArgument(5)).setScopeHash(invocation.getArgument(6))
                        .setStatus("ACTIVE").setVersion(1L));
        when(mapper.insertWorkOrder(any())).thenAnswer(invocation -> {
            workOrder.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectWorkOrder(eq(17L), anyString())).thenAnswer(
                invocation -> currentWorkOrder(invocation.getArgument(1)));
        when(mapper.selectWorkOrderForUpdate(eq(17L), anyString())).thenAnswer(
                invocation -> currentWorkOrder(invocation.getArgument(1)));
        when(mapper.insertApproval(any())).thenAnswer(invocation -> {
            approval.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectApprovalForUpdate(eq(17L), anyString())).thenAnswer(invocation -> {
            Approval row = approval.get();
            return row != null && row.getApprovalId().equals(invocation.getArgument(1)) ? row : null;
        });
        when(mapper.insertHandoff(any())).thenAnswer(invocation -> {
            handoff.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectHandoffForUpdate(eq(17L), anyString())).thenAnswer(invocation -> {
            Handoff row = handoff.get();
            return row != null && row.getHandoffId().equals(invocation.getArgument(1)) ? row : null;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void candidateServiceRequiresAnActiveRolePolicyForWorkOrderCreation() {
        when(mapper.selectRole(17L, "inventory-control")).thenReturn(activeRole("inventory-control"));
        when(mapper.selectActionPolicy(17L, "inventory-control", "inventory.rebalance"))
                .thenReturn(policy("inventory-control", "inventory.rebalance", false));

        AgentControlResult result = service.execute(base(AgentControlOperation.CREATE_WORK_ORDER, "wo-create-1")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-1")
                        .roleCode("inventory-control").actionCode("inventory.rebalance")
                        .title("处理断码与积压").businessContextJson("{\"style_code\":\"YS-001\"}").build())
                .build(), 100L);

        assertThat(result).extracting(AgentControlResult::getAggregateType,
                AgentControlResult::getAggregateId, AgentControlResult::getStatus)
                .containsExactly("role_work_order", "wo-1", "READY");
        assertThat(workOrder.get()).extracting(WorkOrder::getTenantId, WorkOrder::getRequesterUserId,
                WorkOrder::getAssigneeUserId, WorkOrder::getStatus)
                .containsExactly(17L, 100L, 100L, "READY");
        verify(mapper).insertAuditEvent(argThat(event -> event.getTenantId().equals(17L)
                && event.getEventType().equals("agent_control.work_order.created")));
    }

    @Test
    void updatesAnExistingActionPolicyWithANewFrozenSkillBinding() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 0, 0);
        RoleActionPolicy existing = new RoleActionPolicy()
                .setPolicyId("policy-aftersale")
                .setTenantId(17L)
                .setRoleCode("customer-service")
                .setActionCode("aftersale.refund")
                .setPermissionMode("ALLOW")
                .setRiskLevel("R3")
                .setApprovalRequired(true)
                .setEnabled(true)
                .setExecutionRequired(true)
                .setSkillId("skill.cloudmold.commerce.aftersale-saga.v1")
                .setSkillVersion("1.2.0")
                .setSkillDefinitionClosureSha256("a".repeat(64))
                .setVersion(1L)
                .setCreatedAt(createdAt)
                .setUpdatedAt(createdAt);
        when(mapper.selectActionPolicy(17L, "customer-service", "aftersale.refund"))
                .thenReturn(existing);
        when(mapper.updateActionPolicy(any(), eq(1L))).thenReturn(1);

        AgentControlResult result = service.execute(base(
                        AgentControlOperation.SET_ACTION_POLICY, "policy-upgrade-aftersale-1")
                .actionPolicy(AgentControlCommand.RoleActionPolicyDefinition.builder()
                        .roleCode("customer-service")
                        .actionCode("aftersale.refund")
                        .riskLevel("R3")
                        .approvalRequired(true)
                        .executionRequired(true)
                        .skillId("skill.cloudmold.commerce.aftersale-saga.v1")
                        .skillVersion("1.3.0")
                        .skillDefinitionClosureSha256("b".repeat(64))
                        .build())
                .build(), 227L);

        ArgumentCaptor<RoleActionPolicy> updated = ArgumentCaptor.forClass(RoleActionPolicy.class);
        verify(mapper).updateActionPolicy(updated.capture(), eq(1L));
        assertThat(updated.getValue())
                .extracting(RoleActionPolicy::getPolicyId, RoleActionPolicy::getSkillVersion,
                        RoleActionPolicy::getSkillDefinitionClosureSha256,
                        RoleActionPolicy::getVersion, RoleActionPolicy::getCreatedAt)
                .containsExactly("policy-aftersale", "1.3.0", "b".repeat(64), 2L, createdAt);
        assertThat(result)
                .extracting(AgentControlResult::getAggregateId, AgentControlResult::getAggregateVersion)
                .containsExactly("policy-aftersale", 2L);
        verify(mapper, never()).insertActionPolicy(any());
    }

    @Test
    void returnsTheFrozenBusinessContextForOneApproval() {
        AgentApprovalDetailView detail = AgentApprovalDetailView.builder()
                .approvalId("approval-detail-1")
                .workOrderId("wo-detail-1")
                .title("创建并启用新品")
                .roleCode("merchandising")
                .actionCode("auto-listing-hourly-v2")
                .riskLevel("R2")
                .businessContextJson("{\"definitions\":[{\"spuCode\":\"YS-001\"}]}")
                .build();
        when(mapper.selectApprovalDetail(17L, "approval-detail-1")).thenReturn(detail);

        AgentApprovalDetailView result = service.getApprovalDetail("approval-detail-1");

        assertThat(result).isSameAs(detail);
        assertThat(result.getBusinessContextJson()).contains("\"spuCode\":\"YS-001\"");
        verify(mapper).selectApprovalDetail(17L, "approval-detail-1");
    }

    @Test
    void requiresDifferentAuthenticatedUserBeforeApprovalBoundWorkCanStart() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-risk-1").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit")
                .setRequesterUserId(100L).setStatus("WAITING_APPROVAL").setVersion(1L);
        workOrder.set(row);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        freeze(row, approvalPolicy, "{}");
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);
        when(mapper.attachApproval(eq(17L), eq("wo-risk-1"), eq(1L), eq("approval-1"), any())).thenAnswer(invocation -> {
            row.setApprovalId("approval-1").setVersion(2L); return 1;
        });
        service.execute(base(AgentControlOperation.REQUEST_APPROVAL, "approval-request-1")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-1")
                        .workOrderId("wo-risk-1").reasonCode("PURCHASE_COMMIT")
                        .workOrderExpectedVersion(1L).build())
                .build(), 100L);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.DECIDE_APPROVAL, "approval-self-1")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-1")
                        .decision("APPROVE").reasonCode("WITHIN_BUDGET").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build()).build(), 100L))
                .hasMessage("approval requester and approver must be different authenticated users");

        row.setAssigneeUserId(200L);
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.DECIDE_APPROVAL,
                "approval-executor-self-1")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-1")
                        .decision("APPROVE").reasonCode("WITHIN_BUDGET").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build()).build(), 200L))
                .hasMessage("work-order executor and approver must be different authenticated users");
        row.setAssigneeUserId(null);

        when(mapper.decideApproval(eq(17L), eq("approval-1"), eq(1L), eq("APPROVED"), eq(200L),
                eq("WITHIN_BUDGET"), any()))
                .thenAnswer(invocation -> { approval.get().setStatus("APPROVED").setApproverUserId(200L).setVersion(2L); return 1; });
        when(mapper.transitionWorkOrder(eq(17L), eq("wo-risk-1"), eq(2L), eq("WAITING_APPROVAL"), eq("READY"),
                isNull(), isNull(), any()))
                .thenAnswer(invocation -> { row.setStatus("READY").setVersion(3L); return 1; });
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(200L), eq("buyer"), any())).thenReturn(null);
        AgentControlResult decided = service.execute(base(AgentControlOperation.DECIDE_APPROVAL, "approval-decision-1")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-1")
                        .decision("APPROVE").reasonCode("WITHIN_BUDGET").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build()).build(), 200L);

        assertThat(decided.getStatus()).isEqualTo("APPROVED");
        assertThat(row.getStatus()).isEqualTo("READY");
        verify(approvalAttestations).assertDecisionAllowed(17L, 200L, approval.get(), "APPROVE");
        verify(mapper, never()).selectEffectiveActorRoleGrant(eq(17L), eq(200L), eq("buyer"), any());
        verify(mapper).insertAuditEvent(argThat(event -> event.getActorUserId().equals(200L)
                && event.getEventType().equals("agent_control.approval.decided")));
        verify(mapper).insertAgentOutbox(anyString(), eq(17L), eq("role_work_order"), eq("wo-risk-1"),
                eq("agent_control.work_order.ready"),
                argThat(payload -> payload.contains("\"notificationType\":\"WORK_ORDER_READY\"")
                        && payload.contains("\"approvalId\":\"approval-1\"")), any());
    }

    @Test
    void rejectedApprovalCancelsTheWorkOrderAndEmitsNotification() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-risk-reject").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit")
                .setRequesterUserId(100L).setApprovalId("approval-reject").setStatus("WAITING_APPROVAL")
                .setVersion(2L);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        freeze(row, approvalPolicy, "{}");
        workOrder.set(row);
        Approval approvalRow = new Approval().setApprovalId("approval-reject").setTenantId(17L)
                .setWorkOrderId("wo-risk-reject").setActionCode("purchase.commit").setRequesterUserId(100L)
                .setScopeHash(scopeHash(row)).setStatus("PENDING").setVersion(1L);
        approval.set(approvalRow);
        // A policy change must not prevent a BPM-attested rejection from cancelling the frozen work.
        approvalPolicy.setVersion(2L);
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);
        when(mapper.decideApproval(eq(17L), eq("approval-reject"), eq(1L), eq("REJECTED"), eq(200L),
                eq("OUTSIDE_POLICY"), any()))
                .thenAnswer(invocation -> {
                    approvalRow.setStatus("REJECTED").setApproverUserId(200L).setVersion(2L);
                    return 1;
                });
        when(mapper.transitionWorkOrder(eq(17L), eq("wo-risk-reject"), eq(2L), eq("WAITING_APPROVAL"),
                eq("CANCELLED"), isNull(), isNull(), any()))
                .thenAnswer(invocation -> {
                    row.setStatus("CANCELLED").setVersion(3L);
                    return 1;
                });

        AgentControlResult decided = service.execute(base(AgentControlOperation.DECIDE_APPROVAL, "approval-reject-1")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-reject")
                        .decision("REJECT").reasonCode("OUTSIDE_POLICY").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build()).build(), 200L);

        assertThat(decided.getStatus()).isEqualTo("REJECTED");
        assertThat(row.getStatus()).isEqualTo("CANCELLED");
        verify(mapper).insertAgentOutbox(anyString(), eq(17L), eq("role_work_order"), eq("wo-risk-reject"),
                eq("agent_control.work_order.cancelled"),
                argThat(payload -> payload.contains("\"notificationType\":\"WORK_ORDER_CANCELLED\"")
                        && payload.contains("\"approvalId\":\"approval-reject\"")), any());
    }

    @Test
    void approvalStillFailsClosedWhenTheFrozenPolicySnapshotHasAdvanced() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-policy-drift").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit")
                .setRequesterUserId(100L).setApprovalId("approval-policy-drift")
                .setStatus("WAITING_APPROVAL").setVersion(2L);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        freeze(row, approvalPolicy, "{}");
        workOrder.set(row);
        Approval approvalRow = new Approval().setApprovalId("approval-policy-drift").setTenantId(17L)
                .setWorkOrderId("wo-policy-drift").setActionCode("purchase.commit").setRequesterUserId(100L)
                .setScopeHash(scopeHash(row)).setStatus("PENDING").setVersion(1L);
        approval.set(approvalRow);
        approvalPolicy.setVersion(2L);
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.DECIDE_APPROVAL,
                "approval-policy-drift")
                .approval(AgentControlCommand.ApprovalDefinition.builder()
                        .approvalId("approval-policy-drift").decision("APPROVE")
                        .reasonCode("WITHIN_BUDGET").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build())
                .build(), 200L))
                .hasMessage("work order action policy snapshot no longer matches the configured policy");
        verify(mapper, never()).decideApproval(anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyString(), any());
    }

    @Test
    void dedicatedAiBpmAttestationMayAcceptPurePolicyVersionDrift() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-ai-policy-drift").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit")
                .setRequesterUserId(100L).setApprovalId("approval-ai-policy-drift")
                .setStatus("WAITING_APPROVAL").setVersion(2L);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        freeze(row, approvalPolicy, "{}");
        workOrder.set(row);
        Approval approvalRow = new Approval().setApprovalId("approval-ai-policy-drift").setTenantId(17L)
                .setWorkOrderId("wo-ai-policy-drift").setActionCode("purchase.commit").setRequesterUserId(100L)
                .setScopeHash(scopeHash(row)).setStatus("PENDING").setVersion(1L);
        approval.set(approvalRow);
        approvalPolicy.setVersion(2L);
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);
        when(approvalAttestations.permitsAiPolicyVersionDrift(17L, 200L, approvalRow)).thenReturn(true);
        when(mapper.decideApproval(eq(17L), eq("approval-ai-policy-drift"), eq(1L), eq("APPROVED"),
                eq(200L), eq("AI_POLICY_REVIEW"), any())).thenReturn(1);
        when(mapper.transitionWorkOrder(eq(17L), eq("wo-ai-policy-drift"), eq(2L),
                eq("WAITING_APPROVAL"), eq("READY"), isNull(), isNull(), any())).thenReturn(1);

        AgentControlResult result = service.execute(base(AgentControlOperation.DECIDE_APPROVAL,
                        "approval-ai-policy-drift")
                .approval(AgentControlCommand.ApprovalDefinition.builder()
                        .approvalId("approval-ai-policy-drift").decision("APPROVE")
                        .reasonCode("AI_POLICY_REVIEW").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build())
                .build(), 200L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(approvalAttestations).assertDecisionAllowed(17L, 200L, approvalRow, "APPROVE");
    }

    @Test
    void registersBpmBindingInTheApprovalRequestTransaction() {
        AgentApprovalWorkflowRegistrar workflows = mock(AgentApprovalWorkflowRegistrar.class);
        AgentControlServiceImpl integratedService = new AgentControlServiceImpl(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), workflows);
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-bpm-1").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit")
                .setRequesterUserId(100L).setStatus("WAITING_APPROVAL").setVersion(1L);
        workOrder.set(row);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        freeze(row, approvalPolicy, "{}");
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);
        when(mapper.attachApproval(eq(17L), eq("wo-bpm-1"), eq(1L), eq("approval-bpm-1"), any()))
                .thenReturn(1);

        integratedService.execute(base(AgentControlOperation.REQUEST_APPROVAL, "approval-bpm-request-1")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-bpm-1")
                        .workOrderId("wo-bpm-1").reasonCode("PURCHASE_COMMIT")
                        .workOrderExpectedVersion(1L).build())
                .build(), 100L);

        verify(workflows).register(eq(17L), argThat(value ->
                        value.getApprovalId().equals("approval-bpm-1")
                                && value.getScopeHash().equals(approval.get().getScopeHash())),
                same(row), eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
    }

    @Test
    void acceptsHandoffAndMovesOwnershipWithoutCompletingTheWork() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-2").setTenantId(17L)
                .setRoleCode("planning").setActionCode("assortment.review")
                .setRequesterUserId(100L).setAssigneeUserId(100L).setStatus("IN_PROGRESS").setVersion(2L);
        workOrder.set(row);
        when(mapper.selectRole(17L, "buyer")).thenReturn(activeRole("buyer"));
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.plan"))
                .thenReturn(policy("buyer", "purchase.plan", false));
        service.execute(base(AgentControlOperation.CREATE_HANDOFF, "handoff-create-1")
                .handoff(AgentControlCommand.HandoffDefinition.builder().handoffId("handoff-1")
                        .workOrderId("wo-2").toRoleCode("buyer").toActionCode("purchase.plan")
                        .summary("请确认采购数量和交期")
                        .workOrderExpectedVersion(2L).build()).build(), 100L);
        when(mapper.acceptHandoff(eq(17L), eq("handoff-1"), eq(1L), eq(200L), any()))
                .thenAnswer(invocation -> { handoff.get().setStatus("ACCEPTED").setAcceptedByUserId(200L).setVersion(2L); return 1; });
        when(mapper.acceptWorkOrderHandoff(eq(17L), eq("wo-2"), eq(2L), eq("planning"),
                eq("assortment.review"), eq("buyer"), eq("purchase.plan"), eq(200L), any()))
                .thenAnswer(invocation -> { row.setRoleCode("buyer").setActionCode("purchase.plan")
                        .setAssigneeUserId(200L).setVersion(3L); return 1; });

        AgentControlResult accepted = service.execute(base(AgentControlOperation.ACCEPT_HANDOFF, "handoff-accept-1")
                .handoff(AgentControlCommand.HandoffDefinition.builder().handoffId("handoff-1")
                        .handoffExpectedVersion(1L).workOrderExpectedVersion(2L).build()).build(), 200L);

        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
        assertThat(row).extracting(WorkOrder::getRoleCode, WorkOrder::getActionCode,
                WorkOrder::getAssigneeUserId, WorkOrder::getStatus)
                .containsExactly("buyer", "purchase.plan", 200L, "IN_PROGRESS");
    }

    @Test
    void recordsBusinessResultAndCompletesOnlyAnInProgressWorkOrder() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-3").setTenantId(17L)
                .setRoleCode("inventory-control").setActionCode("inventory.rebalance")
                .setRequesterUserId(100L).setAssigneeUserId(100L).setStatus("IN_PROGRESS").setVersion(2L);
        workOrder.set(row);
        when(mapper.insertBusinessResult(any())).thenReturn(1);
        when(mapper.transitionWorkOrder(eq(17L), eq("wo-3"), eq(2L), eq("IN_PROGRESS"), eq("COMPLETED"),
                eq(100L), isNull(), any()))
                .thenAnswer(invocation -> { row.setStatus("COMPLETED").setVersion(3L); return 1; });

        AgentControlResult result = service.execute(base(AgentControlOperation.RECORD_BUSINESS_RESULT, "result-1")
                .businessResult(AgentControlCommand.BusinessResultDefinition.builder().resultId("result-1")
                        .workOrderId("wo-3").outcomeCode("ACTION_PLAN_ISSUED")
                        .summary("已形成 P1/P2/P3 库存动作并完成岗位交接")
                        .evidenceRef("restricted:role-work/wo-3/result-1")
                        .workOrderExpectedVersion(2L).build()).build(), 100L);

        assertThat(result.getStatus()).isEqualTo("RECORDED");
        assertThat(row.getStatus()).isEqualTo("COMPLETED");
        verify(mapper).insertBusinessResult(argThat(value -> value.getTenantId().equals(17L)
                && value.getRecordedByUserId().equals(100L)));
    }

    @Test
    void missionPlanCannotCompleteWithoutStructuredOutputForExecutableSuccessor() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-plan").setTenantId(17L).setMissionId("mission-1")
                .setRoleCode("buyer").setActionCode("buyer.prepare-replenishment")
                .setRequesterUserId(900L).setAssigneeUserId(100L).setStatus("IN_PROGRESS")
                .setExecutionRequired(false).setActiveRunId("run-current").setVersion(2L);
        workOrder.set(row);
        when(mapper.countWaitingExecutableSuccessors(17L, "wo-plan")).thenReturn(1);
        when(mapper.selectMissionCheckpointForRunForUpdate(17L, "wo-plan", "run-current")).thenReturn(null);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.RECORD_BUSINESS_RESULT,
                "mission-plan-without-checkpoint")
                .businessResult(AgentControlCommand.BusinessResultDefinition.builder().resultId("result-plan")
                        .workOrderId("wo-plan").outcomeCode("PLAN_READY").summary("补货计划已形成")
                        .evidenceRef("restricted:plan-1").workOrderExpectedVersion(2L).build()).build(), 100L))
                .hasMessage("current mission run must checkpoint structured output before unlocking executable successor work");
        verify(mapper, never()).insertBusinessResult(any());
    }

    @Test
    void rejectsWorkOrderWhenConfiguredRolePolicyIsMissing() {
        when(mapper.selectRole(17L, "inventory-control")).thenReturn(activeRole("inventory-control"));
        when(mapper.selectActionPolicy(17L, "inventory-control", "payment.refund")).thenReturn(null);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.CREATE_WORK_ORDER, "wo-denied-1")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-denied")
                        .roleCode("inventory-control").actionCode("payment.refund").title("越权退款").build())
                .build(), 100L)).hasMessage("role action is not present in the configured policy catalog");
        verify(mapper, never()).insertWorkOrder(any());
    }

    @Test
    void rejectsEveryWorkCommandWhenActorDoesNotHaveTheExactRoleGrant() {
        when(mapper.selectRole(17L, "inventory-control")).thenReturn(activeRole("inventory-control"));
        when(mapper.selectActionPolicy(17L, "inventory-control", "inventory.rebalance"))
                .thenReturn(policy("inventory-control", "inventory.rebalance", false));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(404L), eq("inventory-control"), any()))
                .thenReturn(null);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.CREATE_WORK_ORDER, "wo-no-grant")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-no-grant")
                        .roleCode("inventory-control").actionCode("inventory.rebalance")
                        .title("未授权工作单").businessContextJson("{}").build()).build(), 404L))
                .hasMessage("authenticated actor has no effective grant for role inventory-control");
        verify(mapper, never()).insertWorkOrder(any());
    }

    @Test
    void rejectsRemainingWorkMutationsWithoutTheCurrentRoleGrant() {
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(404L), anyString(), any()))
                .thenReturn(null);

        WorkOrder start = new WorkOrder().setWorkOrderId("wo-start-no-grant").setTenantId(17L)
                .setRoleCode("inventory-control").setActionCode("inventory.rebalance")
                .setRequesterUserId(100L).setStatus("READY").setVersion(1L);
        workOrder.set(start);
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.START_WORK_ORDER, "start-no-grant")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId(start.getWorkOrderId())
                        .workOrderExpectedVersion(1L).build()).build(), 404L))
                .hasMessage("authenticated actor has no effective grant for role inventory-control");

        WorkOrder source = new WorkOrder().setWorkOrderId("wo-handoff-no-grant").setTenantId(17L)
                .setRoleCode("inventory-control").setActionCode("inventory.rebalance")
                .setRequesterUserId(100L).setAssigneeUserId(100L).setStatus("IN_PROGRESS").setVersion(2L);
        workOrder.set(source);
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.plan"))
                .thenReturn(policy("buyer", "purchase.plan", false));
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.CREATE_HANDOFF, "handoff-no-grant")
                .handoff(AgentControlCommand.HandoffDefinition.builder().handoffId("handoff-no-grant")
                        .workOrderId(source.getWorkOrderId()).toRoleCode("buyer").toActionCode("purchase.plan")
                        .summary("请买手确认采购方案").workOrderExpectedVersion(2L).build()).build(), 404L))
                .hasMessage("authenticated actor has no effective grant for role inventory-control");

        Handoff pending = new Handoff().setHandoffId("accept-no-grant").setTenantId(17L)
                .setWorkOrderId(source.getWorkOrderId()).setFromRoleCode("inventory-control")
                .setFromActionCode("inventory.rebalance").setToRoleCode("buyer")
                .setToActionCode("purchase.plan").setRequestedByUserId(100L).setStatus("PENDING").setVersion(1L);
        handoff.set(pending);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(404L), eq("buyer"), any())).thenReturn(null);
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.ACCEPT_HANDOFF, "accept-no-grant")
                .handoff(AgentControlCommand.HandoffDefinition.builder().handoffId(pending.getHandoffId())
                        .handoffExpectedVersion(1L).workOrderExpectedVersion(2L).build()).build(), 404L))
                .hasMessage("authenticated actor has no effective grant for role buyer");

        WorkOrder waiting = new WorkOrder().setWorkOrderId("wo-request-no-grant").setTenantId(17L)
                .setRoleCode("inventory-control").setActionCode("inventory.rebalance")
                .setRequesterUserId(404L).setStatus("WAITING_APPROVAL").setVersion(1L);
        workOrder.set(waiting);
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.REQUEST_APPROVAL, "request-no-grant")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-no-role")
                        .workOrderId(waiting.getWorkOrderId()).reasonCode("REQUIRES_APPROVAL")
                        .workOrderExpectedVersion(1L).build()).build(), 404L))
                .hasMessage("authenticated actor has no effective grant for role inventory-control");

        WorkOrder completing = new WorkOrder().setWorkOrderId("wo-result-no-grant").setTenantId(17L)
                .setRoleCode("inventory-control").setActionCode("inventory.rebalance")
                .setRequesterUserId(100L).setAssigneeUserId(404L).setStatus("IN_PROGRESS").setVersion(2L);
        workOrder.set(completing);
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.RECORD_BUSINESS_RESULT,
                        "result-no-grant")
                .businessResult(AgentControlCommand.BusinessResultDefinition.builder().resultId("result-no-grant")
                        .workOrderId(completing.getWorkOrderId()).outcomeCode("ACTION_COMPLETED")
                        .summary("库存动作完成").evidenceRef("restricted:result-no-grant")
                        .workOrderExpectedVersion(2L).build()).build(), 404L))
                .hasMessage("authenticated actor has no effective grant for role inventory-control");
    }

    @Test
    void rejectsTechnicalProtocolParametersFromBusinessResultSummary() {
        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.RECORD_BUSINESS_RESULT, "result-leak-1")
                .businessResult(AgentControlCommand.BusinessResultDefinition.builder().resultId("result-leak-1")
                        .workOrderId("wo-3").outcomeCode("ACTION_PLAN_ISSUED")
                        .summary("请使用 cloudmold_skill_task_submit 并传入 tenantId=17")
                        .evidenceRef("restricted:role-work/wo-3/result-leak-1").build()).build(), 100L))
                .hasMessage("business result summary must not expose technical protocol parameters");
        verify(mapper, never()).insertBusinessResult(any());
    }

    @Test
    void rejectsTechnicalProtocolParametersFromBusinessContext() {
        when(mapper.selectRole(17L, "inventory-control")).thenReturn(activeRole("inventory-control"));
        when(mapper.selectActionPolicy(17L, "inventory-control", "inventory.rebalance"))
                .thenReturn(policy("inventory-control", "inventory.rebalance", false));

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.CREATE_WORK_ORDER, "context-leak-1")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-leak")
                        .roleCode("inventory-control").actionCode("inventory.rebalance").title("库存分析")
                        .businessContextJson("{\"clientRequestKey\":\"raw-technical-key\"}").build()).build(), 100L))
                .hasMessage("businessContextJson must not contain technical execution parameters");
        verify(mapper, never()).insertWorkOrder(any());
    }

    @Test
    void acceptsFrozenBusinessReplayCoordinatesWithoutAcceptingAuthorityFields() {
        when(mapper.selectRole(17L, "inventory-control")).thenReturn(activeRole("inventory-control"));
        when(mapper.selectActionPolicy(17L, "inventory-control", "inventory.rebalance"))
                .thenReturn(policy("inventory-control", "inventory.rebalance", false));

        service.execute(base(AgentControlOperation.CREATE_WORK_ORDER, "context-replay-1")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-replay")
                        .roleCode("inventory-control").actionCode("inventory.rebalance").title("库存分析")
                        .businessContextJson("""
                                {"commands":[{"runId":"run-1","idempotencyKey":"inventory-rebalance-1"}]}
                                """).build()).build(), 100L);

        assertThat(workOrder.get().getBusinessContextJson())
                .isEqualTo("{\"commands\":[{\"idempotencyKey\":\"inventory-rebalance-1\",\"runId\":\"run-1\"}]}");
    }

    @Test
    void rejectsStaleWorkOrderVersionBeforeStateMutation() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-stale").setTenantId(17L)
                .setRoleCode("inventory-control").setActionCode("inventory.rebalance")
                .setRequesterUserId(100L).setStatus("READY").setVersion(2L);
        workOrder.set(row);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.START_WORK_ORDER, "start-stale-1")
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-stale")
                        .workOrderExpectedVersion(1L).build()).build(), 100L))
                .hasMessage("workOrderExpectedVersion is stale");
        verify(mapper, never()).transitionWorkOrder(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void rejectsApprovalWhenFrozenActionContextHasDrifted() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-scope").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit").setBusinessContextJson("{\"sku\":\"A\"}")
                .setRequesterUserId(100L).setStatus("WAITING_APPROVAL").setVersion(1L);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        approvalPolicy.setRiskLevel("R2");
        freeze(row, approvalPolicy, row.getBusinessContextJson());
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);
        workOrder.set(row);
        when(mapper.attachApproval(eq(17L), eq("wo-scope"), eq(1L), eq("approval-scope"), any()))
                .thenAnswer(invocation -> { row.setApprovalId("approval-scope").setVersion(2L); return 1; });
        service.execute(base(AgentControlOperation.REQUEST_APPROVAL, "approval-scope-request")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-scope")
                        .workOrderId("wo-scope").reasonCode("PURCHASE_COMMIT")
                        .workOrderExpectedVersion(1L).build())
                .build(), 100L);
        row.setExecutionInputSha256("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.DECIDE_APPROVAL,
                "approval-scope-decision")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-scope")
                        .decision("APPROVE").reasonCode("WITHIN_BUDGET").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build())
                .build(), 200L)).hasMessage("approval scope drifted after the request was frozen");
        verify(mapper, never()).decideApproval(anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyString(), any());
    }

    @Test
    void approvalDecisionRequiresAnExactEffectiveApproverGrant() {
        WorkOrder row = new WorkOrder().setWorkOrderId("wo-exact-approval").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit").setBusinessContextJson("{\"sku\":\"A\"}")
                .setRequesterUserId(100L).setApprovalId("approval-exact").setStatus("WAITING_APPROVAL")
                .setVersion(2L);
        RoleActionPolicy approvalPolicy = policy("buyer", "purchase.commit", true);
        approvalPolicy.setRiskLevel("R2");
        freeze(row, approvalPolicy, row.getBusinessContextJson());
        workOrder.set(row);
        Approval approvalRow = new Approval().setApprovalId("approval-exact").setTenantId(17L)
                .setWorkOrderId(row.getWorkOrderId()).setActionCode(row.getActionCode()).setRequesterUserId(100L)
                .setScopeHash(scopeHash(row)).setStatus("PENDING").setVersion(1L);
        approval.set(approvalRow);
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(approvalPolicy);
        when(mapper.selectEffectiveApprovalAuthorityGrant(eq(17L), eq(200L), eq("approval-exact"),
                eq("buyer"), eq("purchase.commit"), eq("R2"), eq(approvalRow.getScopeHash()), any()))
                .thenReturn(null);

        assertThatThrownBy(() -> service.execute(base(AgentControlOperation.DECIDE_APPROVAL, "approval-no-grant")
                .approval(AgentControlCommand.ApprovalDefinition.builder().approvalId("approval-exact")
                        .decision("APPROVE").reasonCode("WITHIN_BUDGET").approvalExpectedVersion(1L)
                        .workOrderExpectedVersion(2L).build()).build(), 200L))
                .hasMessage("authenticated actor has no exact effective approver grant");
        verify(mapper, never()).decideApproval(anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyString(), any());
    }

    @Test
    void usesServerClockForAuditInsteadOfCallerOccurredAt() {
        when(mapper.selectRole(17L, "inventory-control")).thenReturn(activeRole("inventory-control"));
        when(mapper.selectActionPolicy(17L, "inventory-control", "inventory.rebalance"))
                .thenReturn(policy("inventory-control", "inventory.rebalance", false));
        service.execute(base(AgentControlOperation.CREATE_WORK_ORDER, "clock-authority-1")
                .occurredAt(Instant.EPOCH)
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder().workOrderId("wo-clock")
                        .roleCode("inventory-control").actionCode("inventory.rebalance")
                        .title("验证服务端审计时间").businessContextJson("{}").build()).build(), 100L);

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mapper).insertAuditEvent(audit.capture());
        assertThat(audit.getValue().getOccurredAt())
                .isNotEqualTo(LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC));
    }

    private AgentControlCommand.AgentControlCommandBuilder base(AgentControlOperation operation, String key) {
        return AgentControlCommand.builder().operation(operation).idempotencyKey(key).occurredAt(NOW);
    }

    private RoleDefinition activeRole(String roleCode) {
        return new RoleDefinition().setTenantId(17L).setRoleCode(roleCode).setStatus("ACTIVE").setVersion(1L);
    }

    private RoleActionPolicy policy(String roleCode, String actionCode, boolean approvalRequired) {
        return new RoleActionPolicy().setPolicyId("policy-" + roleCode + "-" + actionCode)
                .setTenantId(17L).setRoleCode(roleCode).setActionCode(actionCode)
                .setPermissionMode("ALLOW").setRiskLevel(approvalRequired ? "R3" : "R1")
                .setApprovalRequired(approvalRequired).setExecutionRequired(false)
                .setEnabled(true).setVersion(1L);
    }

    private WorkOrder currentWorkOrder(String id) {
        WorkOrder row = workOrder.get();
        return row != null && row.getWorkOrderId().equals(id) ? row : null;
    }

    private String scopeHash(WorkOrder value) {
        return frozenScopeHash(value);
    }

    private String frozenScopeHash(WorkOrder value) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("\n",
                String.valueOf(value.getTenantId()), value.getWorkOrderId(), value.getRoleCode(),
                value.getActionCode(), java.util.Objects.toString(value.getActionPolicyId(), "-"),
                java.util.Objects.toString(value.getActionPolicyVersion(), "-"),
                java.util.Objects.toString(value.getSkillId(), "-"),
                java.util.Objects.toString(value.getSkillVersion(), "-"),
                java.util.Objects.toString(value.getSkillDefinitionClosureSha256(), "-"),
                java.util.Objects.toString(value.getExecutionInputSha256(), "-"),
                java.util.Objects.toString(value.getRiskLevel(), "-")));
    }

    private void freeze(WorkOrder row, RoleActionPolicy policy, String businessContextJson) {
        row.setActionPolicyId(policy.getPolicyId()).setActionPolicyVersion(policy.getVersion())
                .setRiskLevel(policy.getRiskLevel()).setExecutionRequired(policy.getExecutionRequired())
                .setSkillId(policy.getSkillId()).setSkillVersion(policy.getSkillVersion())
                .setSkillDefinitionClosureSha256(policy.getSkillDefinitionClosureSha256())
                .setExecutionInputSha256(cn.hutool.crypto.digest.DigestUtil.sha256Hex(businessContextJson));
    }
}
