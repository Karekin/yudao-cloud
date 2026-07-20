package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionRuntimeServiceTest {
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T04:00:00Z"), ZoneOffset.UTC);
    private final MissionRuntimeService service = new MissionRuntimeService(mapper, clock);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(17L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void compilesTheStockoutTemplateIntoFivePersistentRoleWorkItems() {
        when(mapper.selectRole(eq(17L), anyString())).thenReturn(new RoleDefinition().setStatus("ACTIVE"));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), anyLong(), anyString(), any())).thenReturn(new ActorRoleGrant());
        when(mapper.selectActionPolicy(eq(17L), anyString(), anyString())).thenAnswer(invocation ->
                new RoleActionPolicy().setPolicyId("policy-" + invocation.getArgument(2, String.class))
                        .setVersion(1L).setRiskLevel(invocation.getArgument(2, String.class).contains("execute") ? "R3" : "R1")
                        .setApprovalRequired(invocation.getArgument(2, String.class).contains("execute"))
                        .setExecutionRequired(false).setEnabled(true).setPermissionMode("ALLOW"));
        when(mapper.insertMission(any())).thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);
        when(mapper.insertMissionGoal(any())).thenReturn(1);
        when(mapper.insertWorkOrder(any())).thenReturn(1);
        when(mapper.insertWorkDependency(any())).thenReturn(1);
        when(mapper.insertHandoff(any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.startStockoutMission(StockoutMissionCommand.builder().missionId("mission-1")
                .title("夏季连衣裙缺断码响应").objectiveJson("{\"styleCode\":\"YS-1\"}")
                .correlationId("stockout-YS-1").inventoryAgentUserId(101L).buyerAgentUserId(102L)
                .customerServiceAgentUserId(103L).build(), 900L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<WorkOrder> work = ArgumentCaptor.forClass(WorkOrder.class);
        verify(mapper, times(5)).insertWorkOrder(work.capture());
        assertThat(work.getAllValues()).extracting(WorkOrder::getActionCode).containsExactly(
                "inventory.detect-size-stockout", "buyer.prepare-replenishment", "buyer.execute-replenishment",
                "inventory.verify-replenishment", "customer-service.prepare-stockout-impact-response");
        assertThat(work.getAllValues()).extracting(WorkOrder::getStatus)
                .containsExactly("READY", "WAITING_DEPENDENCY", "WAITING_DEPENDENCY", "WAITING_DEPENDENCY", "WAITING_DEPENDENCY");
        verify(mapper, times(4)).insertWorkDependency(any());
        verify(mapper, times(3)).insertHandoff(any());
    }

    @Test
    void expiredLeaseTakeoverGetsANewFencingTokenAndOldRunCannotCheckpoint() {
        WorkOrder work = new WorkOrder().setWorkOrderId("wo-1").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("READY").setAssigneeUserId(102L).setVersion(3L);
        AgentRunLease expired = new AgentRunLease().setWorkOrderId("wo-1").setStatus("ACTIVE")
                .setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59)).setFencingToken(4L).setVersion(2L);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(work);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(102L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        when(mapper.selectMissionForUpdate(17L, "mission-1")).thenReturn(new Mission().setStatus("ACTIVE"));
        when(mapper.selectRunLeaseForUpdate(17L, "wo-1")).thenReturn(expired);
        when(mapper.upsertRunLease(any())).thenReturn(2);
        when(mapper.insertAgentRun(any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-1"), eq(3L), eq("READY"), eq("IN_PROGRESS"),
                isNull(), anyString(), any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        AgentRunLeaseView lease = service.claim(AgentRunClaimCommand.builder().workOrderId("wo-1")
                .workOrderExpectedVersion(3L).leaseOwner("worker-new").build(), 102L);
        assertThat(lease.getFencingToken()).isEqualTo(5L);

        AgentRunLease active = new AgentRunLease().setRunId(lease.getRunId()).setLeaseOwner("worker-new")
                .setLeaseToken(lease.getLeaseToken()).setFencingToken(5L).setStatus("ACTIVE")
                .setLeaseUntil(LocalDateTime.of(2026, 7, 19, 4, 1));
        work.setStatus("IN_PROGRESS");
        when(mapper.selectRunLeaseForUpdate(17L, "wo-1")).thenReturn(active);
        assertThatThrownBy(() -> service.checkpoint(AgentCheckpointCommand.builder().workOrderId("wo-1")
                .runId(lease.getRunId()).leaseOwner("worker-new").leaseToken(lease.getLeaseToken())
                .fencingToken(4L).decisionCode("WAIT").decisionJson("{}").build(), 102L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("stale fencing");
        verify(mapper, never()).insertMissionCheckpoint(any());
    }

    @Test
    void duplicateBusinessEventCannotWakeTheSameWorkTwice() {
        when(mapper.insertAgentEventInbox(eq(17L), eq("event-1"), any(), any(), any())).thenReturn(0);
        when(mapper.selectAgentEventInboxPayloadForUpdate(17L, "event-1")).thenReturn("a".repeat(64));
        var result = service.matchEvent(MissionEventCommand.builder().tenantId(17L).eventId("event-1")
                .eventType("legacy.erp.purchase_in.status_changed").schemaVersion("v1")
                .sourceSystem("legacy-erp").aggregateType("purchase-in").aggregateId("PI-1")
                .payloadSha256("a".repeat(64)).build());
        assertThat(result.isDuplicate()).isTrue();
        verify(mapper, never()).selectMatchingSubscriptions(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsReusedEventIdWithDifferentPayload() {
        when(mapper.insertAgentEventInbox(eq(17L), eq("event-conflict"), any(), any(), any())).thenReturn(0);
        when(mapper.selectAgentEventInboxPayloadForUpdate(17L, "event-conflict")).thenReturn("b".repeat(64));

        assertThatThrownBy(() -> service.matchEvent(MissionEventCommand.builder().tenantId(17L)
                .eventId("event-conflict").eventType("inventory.changed").schemaVersion("v1")
                .sourceSystem("inventory").aggregateType("sku").aggregateId("SKU-1")
                .payloadSha256("a".repeat(64)).build()))
                .hasMessage("eventId was already used with a different payload");
    }

    @Test
    void derivesExecutableInputFromPredecessorPlanAndCreatesApprovalRequest() {
        WorkOrder completed = new WorkOrder().setWorkOrderId("wo-prepare").setMissionId("mission-1")
                .setGoalId("goal-prepare").setRoleCode("buyer").setActionCode("buyer.prepare-replenishment")
                .setStatus("COMPLETED").setActiveRunId("run-prepare-current").setVersion(5L);
        Mission mission = new Mission().setMissionId("mission-1").setStatus("ACTIVE").setSupervisorUserId(900L)
                .setObjectiveJson("{\"styleCode\":\"YS-1\"}");
        BusinessResult result = new BusinessResult().setResultId("result-prepare").setWorkOrderId("wo-prepare")
                .setOutcomeCode("PLAN_READY").setSummary("补货方案已形成").setEvidenceRef("restricted:plan-1");
        MissionCheckpoint checkpoint = new MissionCheckpoint().setCheckpointId("checkpoint-plan")
                .setDecisionCode("REPLENISHMENT_PLAN").setDecisionJson("{\"sku\":\"YS-1-BLACK-M\",\"qty\":120}")
                .setDecisionSha256("c".repeat(64));
        WorkDependency dependency = new WorkDependency().setDependencyId("dep-execute")
                .setSuccessorWorkOrderId("wo-execute").setVersion(1L);
        WorkOrder successor = new WorkOrder().setWorkOrderId("wo-execute").setTenantId(17L)
                .setMissionId("mission-1").setRoleCode("buyer").setActionCode("buyer.execute-replenishment")
                .setStatus("WAITING_DEPENDENCY").setRequesterUserId(900L).setAssigneeUserId(102L)
                .setActionPolicyId("policy-execute").setActionPolicyVersion(1L).setRiskLevel("R3")
                .setExecutionRequired(true).setSkillId("skill.procure").setSkillVersion("1.0.0")
                .setSkillDefinitionClosureSha256("d".repeat(64)).setExecutionInputSha256("a".repeat(64))
                .setVersion(1L);
        RoleActionPolicy policy = new RoleActionPolicy().setPolicyId("policy-execute").setVersion(1L)
                .setApprovalRequired(true).setExecutionRequired(true).setEnabled(true).setPermissionMode("ALLOW")
                .setRiskLevel("R3");
        when(mapper.selectWorkOrderForUpdate(17L, "wo-prepare")).thenReturn(completed);
        when(mapper.selectMissionForUpdate(17L, "mission-1")).thenReturn(mission);
        when(mapper.selectBusinessResultByWorkOrderForUpdate(17L, "wo-prepare")).thenReturn(result);
        when(mapper.selectMissionCheckpointForRunForUpdate(17L, "wo-prepare", "run-prepare-current"))
                .thenReturn(checkpoint);
        AgentRunLease activeLease = new AgentRunLease().setRunId("run-prepare-current")
                .setLeaseOwner("worker-prepare").setLeaseToken("lease-prepare").setFencingToken(7L)
                .setStatus("ACTIVE");
        when(mapper.selectRunLeaseForUpdate(17L, "wo-prepare")).thenReturn(activeLease);
        when(mapper.releaseRunLease(eq(17L), eq("wo-prepare"), eq("run-prepare-current"),
                eq("worker-prepare"), eq("lease-prepare"), eq(7L), any())).thenReturn(1);
        when(mapper.selectWaitingDependenciesByPredecessor(17L, "wo-prepare")).thenReturn(List.of(dependency));
        when(mapper.satisfyDependency(eq(17L), eq("dep-execute"), eq(1L), any())).thenReturn(1);
        when(mapper.countUnsatisfiedDependencies(17L, "wo-execute")).thenReturn(0);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-execute")).thenReturn(successor);
        when(mapper.selectActionPolicy(17L, "buyer", "buyer.execute-replenishment")).thenReturn(policy);
        when(mapper.deriveSuccessorContext(eq(17L), eq("wo-execute"), eq(1L), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-execute"), eq(2L),
                eq("WAITING_DEPENDENCY"), eq("WAITING_APPROVAL"), isNull(), isNull(), any())).thenReturn(1);
        when(mapper.acceptSuccessorHandoff(eq(17L), eq("wo-prepare"), eq("wo-execute"), eq(102L), any()))
                .thenReturn(1);
        when(mapper.insertApproval(any())).thenReturn(1);
        when(mapper.attachApproval(eq(17L), eq("wo-execute"), eq(3L), anyString(), any())).thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.countIncompleteMissionWorkOrders(17L, "mission-1")).thenReturn(1);

        assertThat(service.resolveCompletedWorkOrder("wo-prepare").getStatus()).isEqualTo("ADVANCED");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> inputHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).deriveSuccessorContext(eq(17L), eq("wo-execute"), eq(1L), context.capture(),
                inputHash.capture(), any());
        assertThat(context.getValue()).contains("REPLENISHMENT_PLAN", "YS-1-BLACK-M", "qty");
        assertThat(inputHash.getValue()).hasSize(64).isNotEqualTo("a".repeat(64));
        verify(mapper).insertApproval(argThat(approval -> approval.getWorkOrderId().equals("wo-execute")
                && approval.getRequesterUserId().equals(900L) && approval.getScopeHash().length() == 64));
        verify(mapper).releaseRunLease(eq(17L), eq("wo-prepare"), eq("run-prepare-current"),
                eq("worker-prepare"), eq("lease-prepare"), eq(7L), any());
        verify(mapper).finishAgentRun(eq(17L), eq("run-prepare-current"), eq("COMPLETED"), any());
    }
}
