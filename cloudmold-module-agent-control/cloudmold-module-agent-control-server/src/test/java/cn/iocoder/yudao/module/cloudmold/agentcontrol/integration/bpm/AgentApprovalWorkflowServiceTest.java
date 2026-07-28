package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentApprovalWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T01:00:00Z");
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentApprovalWorkflowAdapter adapter = mock(AgentApprovalWorkflowAdapter.class);
    private final AgentApprovalResponsibilityResolver responsibilityResolver =
            mock(AgentApprovalResponsibilityResolver.class);
    private final AgentApprovalWorkflowProperties properties = properties();
    private final AgentApprovalWorkflowService service = new AgentApprovalWorkflowService(
            mapper, adapter, responsibilityResolver, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUpResponsibilityResolution() {
        lenient().when(responsibilityResolver.resolve(any(), any())).thenAnswer(invocation -> {
            ApprovalWorkflowStartCandidate candidate = invocation.getArgument(0);
            candidate.setResponsibilityRoleCodes(List.of("buyer", "finance"))
                    .setResponsibilityApproverUserIds(List.of(210L, 220L))
                    .setResponsibilityAuthoritySha256("b".repeat(64));
            return new AgentApprovalResponsibilityResolver.Resolution(
                    List.of("buyer", "finance"), List.of(210L, 220L), "b".repeat(64));
        });
    }

    @Test
    void registersFrozenBindingIdempotently() {
        Approval approval = approval();
        WorkOrder workOrder = workOrder();
        when(mapper.insertApprovalWorkflowBinding(any())).thenReturn(1);

        service.register(17L, approval, workOrder, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        verify(mapper).insertApprovalWorkflowBinding(argThat(binding ->
                binding.getTenantId().equals(17L)
                        && binding.getApprovalId().equals("approval-1")
                        && binding.getApproverUserId() == null
                        && binding.getScopeHash().equals("a".repeat(64))
                        && binding.getProcessDefinitionKey().equals("cloudmold-agent-approval-v1")
                        && binding.getBusinessKey().equals("cloudmold-agent-approval:17:approval-1")
                        && binding.getStatus().equals("START_REQUESTED")
                        && binding.getStartAttemptCount() == 0));
    }

    @Test
    void rejectsAnIdempotencyCollisionWithDifferentFrozenScope() {
        when(mapper.insertApprovalWorkflowBinding(any())).thenReturn(0);
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1")).thenReturn(
                new ApprovalWorkflowBinding().setTenantId(17L).setApprovalId("approval-1")
                        .setWorkOrderId("work-1").setScopeHash("b".repeat(64))
                        .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                        .setBusinessKey("cloudmold-agent-approval:17:approval-1"));

        assertThatThrownBy(() -> service.register(17L, approval(), workOrder(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .hasMessage("approval workflow binding conflicts with the frozen approval scope");
    }

    @Test
    void rejectsCrossTenantRegistration() {
        Approval approval = approval().setTenantId(18L);

        assertThatThrownBy(() -> service.register(17L, approval, workOrder(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .hasMessage("approval workflow registration crosses a tenant or work-order boundary");

        verifyNoInteractions(mapper, adapter);
    }

    @Test
    void claimsExactlyOnceBeforeStartingBpm() {
        ApprovalWorkflowStartCandidate candidate = candidate();
        when(mapper.claimApprovalWorkflowStart(eq(17L), eq("approval-1"), eq(1L), eq(200L),
                eq("[\"buyer\",\"finance\"]"), eq("[210,220]"), eq("b".repeat(64)),
                anyString(), any()))
                .thenReturn(1);
        when(adapter.start(candidate)).thenReturn("process-1");
        when(mapper.markApprovalWorkflowRunning(eq(17L), eq("approval-1"), anyString(),
                eq("process-1"), any())).thenReturn(1);

        assertThat(service.start(candidate)).isTrue();

        verify(adapter).start(candidate);
        verify(mapper).markApprovalWorkflowRunning(eq(17L), eq("approval-1"), anyString(),
                eq("process-1"), any());
    }

    @Test
    void concurrentLoserDoesNotCallBpm() {
        when(mapper.claimApprovalWorkflowStart(eq(17L), eq("approval-1"), eq(1L), eq(200L),
                eq("[\"buyer\",\"finance\"]"), eq("[210,220]"), eq("b".repeat(64)),
                anyString(), any()))
                .thenReturn(0);

        assertThat(service.start(candidate())).isFalse();

        verifyNoInteractions(adapter);
    }

    @Test
    void ambiguousStartFailureStopsAutomaticRetry() {
        ApprovalWorkflowStartCandidate candidate = candidate();
        when(mapper.claimApprovalWorkflowStart(eq(17L), eq("approval-1"), eq(1L), eq(200L),
                eq("[\"buyer\",\"finance\"]"), eq("[210,220]"), eq("b".repeat(64)),
                anyString(), any()))
                .thenReturn(1);
        when(adapter.start(candidate)).thenThrow(new IllegalStateException("timeout after send"));
        when(mapper.markApprovalWorkflowStartUncertain(eq(17L), eq("approval-1"), anyString(),
                eq("IllegalStateException"), any())).thenReturn(1);

        assertThat(service.start(candidate)).isTrue();

        verify(adapter).start(candidate);
        verify(mapper).markApprovalWorkflowStartUncertain(eq(17L), eq("approval-1"), anyString(),
                eq("IllegalStateException"), any());
        verifyNoMoreInteractions(adapter);
    }

    @Test
    void recordsApprovalAsEvidenceWithoutDecidingAgentApproval() {
        ApprovalWorkflowBinding binding = binding("RUNNING", "process-1");
        when(mapper.selectApprovalWorkflowBindingForEvent(
                "cloudmold-agent-approval-v1", "process-1", binding.getBusinessKey())).thenReturn(binding);
        when(mapper.insertApprovalWorkflowEvent(any())).thenReturn(1);
        when(mapper.markApprovalWorkflowTerminal(eq(17L), eq("approval-1"), eq("process-1"),
                eq(BpmProcessInstanceStatusEnum.APPROVE.getStatus()),
                eq("BPM_APPROVED_PENDING_ATTESTATION"), anyString(), eq(200L),
                eq("task-1"), eq("approval_review"), any())).thenReturn(1);

        service.observe(event(BpmProcessInstanceStatusEnum.APPROVE.getStatus()));

        verify(mapper).markApprovalWorkflowTerminal(eq(17L), eq("approval-1"), eq("process-1"),
                eq(BpmProcessInstanceStatusEnum.APPROVE.getStatus()),
                eq("BPM_APPROVED_PENDING_ATTESTATION"), anyString(), eq(200L),
                eq("task-1"), eq("approval_review"), any());
        verify(mapper, never()).decideApproval(anyLong(), anyString(), anyLong(), anyString(),
                anyLong(), anyString(), any());
    }

    @Test
    void duplicateTerminalEventIsImmutable() {
        ApprovalWorkflowBinding binding = binding("RUNNING", "process-1");
        when(mapper.selectApprovalWorkflowBindingForEvent(
                "cloudmold-agent-approval-v1", "process-1", binding.getBusinessKey())).thenReturn(binding);
        when(mapper.insertApprovalWorkflowEvent(any())).thenReturn(0);

        service.observe(event(BpmProcessInstanceStatusEnum.REJECT.getStatus()));

        verify(mapper, never()).markApprovalWorkflowTerminal(anyLong(), anyString(), anyString(),
                anyInt(), anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void rejectsMismatchedProcessInstanceBeforeRecordingEvent() {
        ApprovalWorkflowBinding binding = binding("RUNNING", "different-process");
        when(mapper.selectApprovalWorkflowBindingForEvent(
                "cloudmold-agent-approval-v1", "process-1", binding.getBusinessKey())).thenReturn(binding);

        assertThatThrownBy(() -> service.observe(event(BpmProcessInstanceStatusEnum.CANCEL.getStatus())))
                .hasMessage("BPM event process instance does not match the approval binding");

        verify(mapper, never()).insertApprovalWorkflowEvent(any());
    }

    @Test
    void lateTerminalEventResolvesAnUncertainStart() {
        ApprovalWorkflowBinding binding = binding("START_UNCERTAIN", null);
        when(mapper.selectApprovalWorkflowBindingForEvent(
                "cloudmold-agent-approval-v1", "process-1", binding.getBusinessKey())).thenReturn(binding);
        when(mapper.insertApprovalWorkflowEvent(any())).thenReturn(1);
        when(mapper.markApprovalWorkflowTerminal(eq(17L), eq("approval-1"), eq("process-1"),
                eq(BpmProcessInstanceStatusEnum.REJECT.getStatus()), eq("BPM_REJECTED"),
                anyString(), eq(200L), eq("task-1"), eq("approval_review"), any())).thenReturn(1);

        service.observe(event(BpmProcessInstanceStatusEnum.REJECT.getStatus()));

        verify(mapper).markApprovalWorkflowTerminal(eq(17L), eq("approval-1"), eq("process-1"),
                eq(BpmProcessInstanceStatusEnum.REJECT.getStatus()), eq("BPM_REJECTED"),
                anyString(), eq(200L), eq("task-1"), eq("approval_review"), any());
    }

    @Test
    void ignoresNonTerminalProcessEvent() {
        service.observe(event(BpmProcessInstanceStatusEnum.RUNNING.getStatus()));

        verifyNoInteractions(mapper, adapter);
    }

    private static AgentApprovalWorkflowProperties properties() {
        AgentApprovalWorkflowProperties value = new AgentApprovalWorkflowProperties();
        value.setEnabled(true);
        value.setProcessDefinitionKey("cloudmold-agent-approval-v1");
        return value;
    }

    private static Approval approval() {
        return new Approval().setApprovalId("approval-1").setTenantId(17L).setWorkOrderId("work-1")
                .setActionCode("purchase.commit").setRequesterUserId(100L)
                .setApproverUserId(200L)
                .setScopeHash("a".repeat(64)).setStatus("PENDING").setVersion(1L);
    }

    private static WorkOrder workOrder() {
        return new WorkOrder().setWorkOrderId("work-1").setTenantId(17L).setRoleCode("buyer")
                .setActionCode("purchase.commit").setRiskLevel("R3").setRequesterUserId(100L)
                .setStatus("WAITING_APPROVAL").setVersion(2L);
    }

    private static ApprovalWorkflowStartCandidate candidate() {
        return new ApprovalWorkflowStartCandidate().setTenantId(17L).setApprovalId("approval-1")
                .setWorkOrderId("work-1").setActionCode("purchase.commit").setRoleCode("buyer")
                .setRiskLevel("R3").setRequesterUserId(100L).setApproverUserId(200L).setScopeHash("a".repeat(64))
                .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                .setBusinessKey("cloudmold-agent-approval:17:approval-1")
                .setStatus("START_REQUESTED").setVersion(1L);
    }

    private static ApprovalWorkflowBinding binding(String status, String processInstanceId) {
        return new ApprovalWorkflowBinding().setTenantId(17L).setApprovalId("approval-1")
                .setWorkOrderId("work-1").setStatus(status).setProcessInstanceId(processInstanceId)
                .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                .setBusinessKey("cloudmold-agent-approval:17:approval-1");
    }

    private static BpmProcessInstanceStatusEvent event(Integer status) {
        return new BpmProcessInstanceStatusEvent("test").setId("process-1")
                .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                .setBusinessKey("cloudmold-agent-approval:17:approval-1")
                .setStatus(status).setReason("restricted human comment")
                .setTerminalOperatorUserId(200L).setTerminalTaskId("task-1")
                .setTerminalTaskDefinitionKey("approval_review");
    }

}
