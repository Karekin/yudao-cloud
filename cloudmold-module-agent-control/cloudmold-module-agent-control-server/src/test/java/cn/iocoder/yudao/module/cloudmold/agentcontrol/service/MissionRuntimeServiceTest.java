package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm.AgentApprovalWorkflowRegistrar;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionRuntimeServiceTest {
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T04:00:00Z"), ZoneOffset.UTC);
    private final AgentApprovalWorkflowRegistrar approvalWorkflows = mock(AgentApprovalWorkflowRegistrar.class);
    private final MissionRuntimeService service = new MissionRuntimeService(mapper, clock, approvalWorkflows);

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
    void expiredInProgressLeaseTakeoverGetsANewFencingTokenAndOldRunCannotCheckpoint() {
        WorkOrder work = new WorkOrder().setWorkOrderId("wo-1").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("IN_PROGRESS").setAssigneeUserId(102L)
                .setActiveRunId("run-old").setVersion(3L);
        AgentRunLease expired = new AgentRunLease().setWorkOrderId("wo-1").setRunId("run-old").setStatus("ACTIVE")
                .setLeaseOwner("worker-old").setLeaseToken("lease-old").setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59))
                .setFencingToken(4L).setVersion(2L);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(work);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(102L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        when(mapper.selectMissionForUpdate(17L, "mission-1")).thenReturn(new Mission().setStatus("ACTIVE"));
        when(mapper.selectRunLeaseForUpdate(17L, "wo-1")).thenReturn(expired);
        when(mapper.expireRunLease(17L, "wo-1", "run-old", 4L,
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.finishAgentRun(17L, "run-old", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.upsertRunLease(any())).thenReturn(2);
        when(mapper.insertAgentRun(any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-1"), eq(3L), eq("IN_PROGRESS"), eq("IN_PROGRESS"),
                isNull(), anyString(), any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        AgentRunLeaseView lease = service.claim(AgentRunClaimCommand.builder().workOrderId("wo-1")
                .workOrderExpectedVersion(3L).leaseOwner("worker-new").build(), 102L);
        assertThat(lease.getFencingToken()).isEqualTo(5L);
        verify(mapper).expireRunLease(17L, "wo-1", "run-old", 4L,
                LocalDateTime.of(2026, 7, 19, 4, 0));
        verify(mapper).finishAgentRun(17L, "run-old", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0));

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
    void staleSecondTakeoverAttemptLosesOnUpdatedWorkOrderVersion() {
        WorkOrder beforeTakeover = new WorkOrder().setWorkOrderId("wo-1").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("IN_PROGRESS").setAssigneeUserId(102L)
                .setActiveRunId("run-old").setVersion(3L);
        WorkOrder afterTakeover = new WorkOrder().setWorkOrderId("wo-1").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("IN_PROGRESS").setAssigneeUserId(102L)
                .setActiveRunId("run-new").setVersion(4L);
        AgentRunLease expired = new AgentRunLease().setWorkOrderId("wo-1").setRunId("run-old").setStatus("ACTIVE")
                .setLeaseOwner("worker-old").setLeaseToken("lease-old").setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59))
                .setFencingToken(4L).setVersion(2L);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(beforeTakeover, afterTakeover);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(102L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        when(mapper.selectMissionForUpdate(17L, "mission-1")).thenReturn(new Mission().setStatus("ACTIVE"));
        when(mapper.selectRunLeaseForUpdate(17L, "wo-1")).thenReturn(expired);
        when(mapper.expireRunLease(17L, "wo-1", "run-old", 4L,
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.finishAgentRun(17L, "run-old", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.upsertRunLease(any())).thenReturn(1);
        when(mapper.insertAgentRun(any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-1"), eq(3L), eq("IN_PROGRESS"), eq("IN_PROGRESS"),
                isNull(), anyString(), any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        service.claim(AgentRunClaimCommand.builder().workOrderId("wo-1")
                .workOrderExpectedVersion(3L).leaseOwner("worker-new").build(), 102L);

        assertThatThrownBy(() -> service.claim(AgentRunClaimCommand.builder().workOrderId("wo-1")
                .workOrderExpectedVersion(3L).leaseOwner("worker-racer").build(), 102L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("work order version is stale");
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
    void waitsForGovernedMetricThresholdWithFreshnessAndReleasesTheRun() {
        WorkOrder work = new WorkOrder().setWorkOrderId("wo-metric").setMissionId("mission-1")
                .setStatus("IN_PROGRESS").setAssigneeUserId(102L).setVersion(4L);
        AgentRunLease lease = new AgentRunLease().setRunId("run-metric").setLeaseOwner("worker-1")
                .setLeaseToken("lease-1").setFencingToken(7L).setStatus("ACTIVE")
                .setLeaseUntil(LocalDateTime.of(2026, 7, 19, 4, 1));
        when(mapper.selectWorkOrderForUpdate(17L, "wo-metric")).thenReturn(work);
        when(mapper.selectRunLeaseForUpdate(17L, "wo-metric")).thenReturn(lease);
        when(mapper.insertMetricSubscription(any())).thenReturn(1);
        when(mapper.releaseRunLease(eq(17L), eq("wo-metric"), eq("run-metric"),
                eq("worker-1"), eq("lease-1"), eq(7L), any())).thenReturn(1);
        when(mapper.finishAgentRun(eq(17L), eq("run-metric"), eq("WAITING"), any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-metric"), eq(4L),
                eq("IN_PROGRESS"), eq("WAITING_METRIC"), eq("METRIC"), isNull(), any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        AgentControlResult result = service.waitFor(AgentWaitCommand.builder()
                .workOrderId("wo-metric").runId("run-metric").leaseOwner("worker-1")
                .leaseToken("lease-1").fencingToken(7L).waitType("METRIC")
                .metricId("supply.stockout-risk").metricVersion("v2")
                .dimensionHash("a".repeat(64)).comparisonOperator("GTE")
                .thresholdValue(new BigDecimal("1800")).unitCode("BASIS_POINTS")
                .maxAgeSeconds(300).build(), 102L);

        assertThat(result.getStatus()).isEqualTo("WAITING_METRIC");
        ArgumentCaptor<MetricSubscription> subscription =
                ArgumentCaptor.forClass(MetricSubscription.class);
        verify(mapper).insertMetricSubscription(subscription.capture());
        assertThat(subscription.getValue().getMetricId()).isEqualTo("supply.stockout-risk");
        assertThat(subscription.getValue().getComparisonOperator()).isEqualTo("GTE");
        assertThat(subscription.getValue().getThresholdValue())
                .isEqualByComparingTo("1800");
        assertThat(subscription.getValue().getMaxAgeSeconds()).isEqualTo(300);
    }

    @Test
    void freshMetricBreachWakesWorkButStaleObservationFailsClosed() {
        MetricSubscription fresh = new MetricSubscription().setSubscriptionId("metric-sub-1")
                .setWorkOrderId("wo-metric").setComparisonOperator("GTE")
                .setThresholdValue(new BigDecimal("1800")).setMaxAgeSeconds(300).setVersion(1L);
        when(mapper.insertMetricObservation(any())).thenReturn(1, 1);
        when(mapper.selectMatchingMetricSubscriptions(17L, "supply.stockout-risk", "v2",
                "a".repeat(64), "BASIS_POINTS")).thenReturn(List.of(fresh));
        when(mapper.matchMetricSubscription(eq(17L), eq("metric-sub-1"), eq(1L),
                eq("obs-fresh"), eq(new BigDecimal("2200")), eq("b".repeat(64)), any())).thenReturn(1);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-metric")).thenReturn(
                new WorkOrder().setWorkOrderId("wo-metric").setStatus("WAITING_METRIC").setVersion(5L));
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-metric"), eq(5L),
                eq("WAITING_METRIC"), eq("READY"), isNull(), isNull(), any())).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        AgentControlResult matched = service.observeMetric(metricObservation(
                "obs-fresh", new BigDecimal("2200"), Instant.parse("2026-07-19T03:59:00Z")));
        assertThat(matched.getStatus()).isEqualTo("MATCHED");

        AgentControlResult stale = service.observeMetric(metricObservation(
                "obs-stale", new BigDecimal("2400"), Instant.parse("2026-07-19T03:00:00Z")));
        assertThat(stale.getStatus()).isEqualTo("STALE_NO_MATCH");
        verify(mapper, times(1)).matchMetricSubscription(anyLong(), any(), anyLong(),
                any(), any(), any(), any());
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
        verify(approvalWorkflows).register(eq(17L), argThat(value ->
                        value.getWorkOrderId().equals("wo-execute")
                                && value.getRequesterUserId().equals(900L)
                                && "PENDING".equals(value.getStatus())),
                same(successor), eq(LocalDateTime.of(2026, 7, 19, 4, 0)));
        verify(mapper).releaseRunLease(eq(17L), eq("wo-prepare"), eq("run-prepare-current"),
                eq("worker-prepare"), eq("lease-prepare"), eq(7L), any());
        verify(mapper).finishAgentRun(eq(17L), eq("run-prepare-current"), eq("COMPLETED"), any());
    }

    @Test
    void recoverExpiredRunIsIdempotentAfterTheLeaseHasAlreadyBeenExpired() {
        WorkOrder work = new WorkOrder().setWorkOrderId("wo-recover").setMissionId("mission-1").setStatus("IN_PROGRESS")
                .setActiveRunId("run-recover").setVersion(8L);
        AgentRunLease expiredLease = new AgentRunLease().setRunId("run-recover").setStatus("ACTIVE")
                .setFencingToken(9L).setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59));
        AgentRunLease alreadyExpiredLease = new AgentRunLease().setRunId("run-recover").setStatus("EXPIRED")
                .setFencingToken(9L).setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59));
        when(mapper.selectWorkOrderForUpdate(17L, "wo-recover")).thenReturn(work, work);
        when(mapper.selectRunLeaseForUpdate(17L, "wo-recover")).thenReturn(expiredLease, alreadyExpiredLease);
        when(mapper.expireRunLease(17L, "wo-recover", "run-recover", 9L,
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.finishAgentRun(17L, "run-recover", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(service.recoverExpiredRun("wo-recover").getStatus()).isEqualTo("RUN_EXPIRED");
        assertThat(service.recoverExpiredRun("wo-recover").getStatus()).isEqualTo("NO_ACTION");
        verify(mapper, times(1)).expireRunLease(17L, "wo-recover", "run-recover", 9L,
                LocalDateTime.of(2026, 7, 19, 4, 0));
        verify(mapper, times(1)).finishAgentRun(17L, "run-recover", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0));
    }

    @Test
    void shortContinuousRunTakeoversMonotonicallyIncreaseFencingTokens() {
        WorkOrder firstReady = new WorkOrder().setWorkOrderId("wo-loop").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("READY").setAssigneeUserId(102L).setVersion(3L);
        WorkOrder secondTakeover = new WorkOrder().setWorkOrderId("wo-loop").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("IN_PROGRESS").setAssigneeUserId(102L)
                .setActiveRunId("run-1").setVersion(4L);
        WorkOrder thirdTakeover = new WorkOrder().setWorkOrderId("wo-loop").setMissionId("mission-1").setRoleCode("buyer")
                .setActionCode("buyer.prepare-replenishment").setStatus("IN_PROGRESS").setAssigneeUserId(102L)
                .setActiveRunId("run-2").setVersion(5L);
        AgentRunLease lease1 = new AgentRunLease().setWorkOrderId("wo-loop").setRunId("run-1").setStatus("ACTIVE")
                .setLeaseOwner("worker-1").setLeaseToken("lease-1")
                .setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59)).setFencingToken(1L).setVersion(1L);
        AgentRunLease lease2 = new AgentRunLease().setWorkOrderId("wo-loop").setRunId("run-2").setStatus("ACTIVE")
                .setLeaseOwner("worker-2").setLeaseToken("lease-2")
                .setLeaseUntil(LocalDateTime.of(2026, 7, 19, 3, 59)).setFencingToken(2L).setVersion(2L);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-loop")).thenReturn(firstReady, secondTakeover, thirdTakeover);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(102L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        when(mapper.selectMissionForUpdate(17L, "mission-1")).thenReturn(new Mission().setStatus("ACTIVE"));
        when(mapper.selectRunLeaseForUpdate(17L, "wo-loop")).thenReturn(null, lease1, lease2);
        when(mapper.upsertRunLease(any())).thenReturn(1);
        when(mapper.insertAgentRun(any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-loop"), eq(3L), eq("READY"), eq("IN_PROGRESS"),
                isNull(), anyString(), any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-loop"), eq(4L), eq("IN_PROGRESS"), eq("IN_PROGRESS"),
                isNull(), anyString(), any())).thenReturn(1);
        when(mapper.transitionMissionWorkOrder(eq(17L), eq("wo-loop"), eq(5L), eq("IN_PROGRESS"), eq("IN_PROGRESS"),
                isNull(), anyString(), any())).thenReturn(1);
        when(mapper.expireRunLease(17L, "wo-loop", "run-1", 1L,
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.expireRunLease(17L, "wo-loop", "run-2", 2L,
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.finishAgentRun(17L, "run-1", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.finishAgentRun(17L, "run-2", "EXPIRED",
                LocalDateTime.of(2026, 7, 19, 4, 0))).thenReturn(1);
        when(mapper.insertAgentOutbox(any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        AgentRunLeaseView first = service.claim(AgentRunClaimCommand.builder().workOrderId("wo-loop")
                .workOrderExpectedVersion(3L).leaseOwner("worker-1").build(), 102L);
        AgentRunLeaseView second = service.claim(AgentRunClaimCommand.builder().workOrderId("wo-loop")
                .workOrderExpectedVersion(4L).leaseOwner("worker-2").build(), 102L);
        AgentRunLeaseView third = service.claim(AgentRunClaimCommand.builder().workOrderId("wo-loop")
                .workOrderExpectedVersion(5L).leaseOwner("worker-3").build(), 102L);

        assertThat(first.getFencingToken()).isEqualTo(1L);
        assertThat(second.getFencingToken()).isEqualTo(2L);
        assertThat(third.getFencingToken()).isEqualTo(3L);
    }

    private static MissionMetricObservationCommand metricObservation(
            String observationId, BigDecimal value, Instant observedAt) {
        return MissionMetricObservationCommand.builder().observationId(observationId)
                .tenantId(17L).metricId("supply.stockout-risk").metricVersion("v2")
                .dimensionHash("a".repeat(64)).value(value).unitCode("BASIS_POINTS")
                .sourceQueryId("starrocks-query-20260719").evidenceSha256("b".repeat(64))
                .observedAt(observedAt).build();
    }
}
