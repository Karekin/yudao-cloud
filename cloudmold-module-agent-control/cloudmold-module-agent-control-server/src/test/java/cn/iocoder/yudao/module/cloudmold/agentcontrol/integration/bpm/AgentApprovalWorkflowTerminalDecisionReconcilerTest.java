package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommandApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowDecisionCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentApprovalWorkflowTerminalDecisionReconcilerTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentControlCommandApi commands = mock(AgentControlCommandApi.class);
    private final AgentApprovalWorkflowProperties properties = new AgentApprovalWorkflowProperties();
    private final AgentApprovalWorkflowTerminalDecisionReconciler reconciler =
            new AgentApprovalWorkflowTerminalDecisionReconciler(mapper, commands, properties);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void approvedCandidateCallsCommandApiInsideCandidateTenant() {
        properties.setBatchSize(20);
        ApprovalWorkflowDecisionCandidate candidate = candidate(17L, "approval-1",
                "BPM_APPROVED_PENDING_ATTESTATION", 200L, 3L, 5L);
        when(mapper.selectApprovalWorkflowDecisionCandidates(20)).thenReturn(List.of(candidate));
        when(commands.execute(any(), eq(200L))).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(17L);
            return AgentControlResult.builder().aggregateType("role_approval").aggregateId("approval-1")
                    .aggregateVersion(4L).status("APPROVED").duplicate(false).build();
        });

        reconciler.reconcile();

        ArgumentCaptor<AgentControlCommand> command = ArgumentCaptor.forClass(AgentControlCommand.class);
        verify(commands).execute(command.capture(), eq(200L));
        assertThat(command.getValue().getApproval().getDecision()).isEqualTo("APPROVE");
        assertThat(command.getValue().getApproval().getApprovalExpectedVersion()).isEqualTo(3L);
        assertThat(command.getValue().getApproval().getWorkOrderExpectedVersion()).isEqualTo(5L);
        assertThat(command.getValue().getIdempotencyKey())
                .isEqualTo("bpm-terminal-decision:17:approval-1:APPROVE:a3:w5");
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void rejectedCandidateCallsCommandApiWithRejectDecision() {
        properties.setBatchSize(20);
        ApprovalWorkflowDecisionCandidate candidate = candidate(23L, "approval-2",
                "BPM_REJECTED", 300L, 4L, 6L);
        when(mapper.selectApprovalWorkflowDecisionCandidates(20)).thenReturn(List.of(candidate));
        when(commands.execute(any(), eq(300L))).thenReturn(AgentControlResult.builder()
                .aggregateType("role_approval").aggregateId("approval-2").aggregateVersion(5L)
                .status("REJECTED").duplicate(false).build());

        reconciler.reconcile();

        verify(commands).execute(argThat(command ->
                        "REJECT".equals(command.getApproval().getDecision())
                                && "BPM_REJECT_ATTESTED".equals(command.getApproval().getReasonCode())
                                && command.getApproval().getApprovalExpectedVersion().equals(4L)
                                && command.getApproval().getWorkOrderExpectedVersion().equals(6L)),
                eq(300L));
    }

    @Test
    void invalidCandidateIsDeferredWithoutCallingCommandApi() {
        properties.setBatchSize(20);
        ApprovalWorkflowDecisionCandidate candidate = candidate(17L, "approval-3",
                "BPM_APPROVED_PENDING_ATTESTATION", null, 1L, 2L);
        when(mapper.selectApprovalWorkflowDecisionCandidates(20)).thenReturn(List.of(candidate));

        reconciler.reconcile();

        verifyNoInteractions(commands);
    }

    @Test
    void duplicateRestartUsesStableIdempotencyKey() {
        properties.setBatchSize(20);
        ApprovalWorkflowDecisionCandidate candidate = candidate(17L, "approval-4",
                "BPM_APPROVED_PENDING_ATTESTATION", 200L, 7L, 9L);
        when(mapper.selectApprovalWorkflowDecisionCandidates(20))
                .thenReturn(List.of(candidate), List.of(candidate));
        when(commands.execute(any(), eq(200L))).thenReturn(AgentControlResult.builder()
                .aggregateType("role_approval").aggregateId("approval-4").aggregateVersion(8L)
                .status("APPROVED").duplicate(true).build());

        reconciler.reconcile();
        reconciler.reconcile();

        ArgumentCaptor<AgentControlCommand> command = ArgumentCaptor.forClass(AgentControlCommand.class);
        verify(commands, times(2)).execute(command.capture(), eq(200L));
        assertThat(command.getAllValues()).extracting(AgentControlCommand::getIdempotencyKey)
                .containsExactly(
                        "bpm-terminal-decision:17:approval-4:APPROVE:a7:w9",
                        "bpm-terminal-decision:17:approval-4:APPROVE:a7:w9");
    }

    private static ApprovalWorkflowDecisionCandidate candidate(Long tenantId, String approvalId,
                                                               String observedStatus,
                                                               Long terminalOperatorUserId,
                                                               Long approvalVersion,
                                                               Long workOrderVersion) {
        return new ApprovalWorkflowDecisionCandidate().setTenantId(tenantId).setApprovalId(approvalId)
                .setWorkOrderId("work-" + approvalId).setObservedStatus(observedStatus)
                .setTerminalOperatorUserId(terminalOperatorUserId)
                .setApprovalVersion(approvalVersion).setWorkOrderVersion(workOrderVersion);
    }
}
