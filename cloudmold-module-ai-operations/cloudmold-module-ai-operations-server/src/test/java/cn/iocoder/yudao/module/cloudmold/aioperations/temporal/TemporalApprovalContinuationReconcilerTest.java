package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlQueryApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalApprovalContinuationReconcilerTest {

    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final AgentControlQueryApi agentQueries = mock(AgentControlQueryApi.class);
    private final TemporalApprovalContinuationAdapter continuation =
            mock(TemporalApprovalContinuationAdapter.class);
    private final TemporalApprovalContinuationReconciler reconciler =
            new TemporalApprovalContinuationReconciler(mapper, agentQueries, continuation);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldResumePersistedApprovedBindingAfterRestart() {
        TemporalRunBindingRecord binding = binding();
        when(mapper.selectWaitingApprovalBindings(100)).thenReturn(List.of(binding));
        when(agentQueries.getApproval("approval-1")).thenReturn(
                AgentControlResult.builder().status("APPROVED").build());

        reconciler.reconcile();

        verify(continuation).onApprovalDecision(162L, "work-order-1", "approval-1", "APPROVE");
    }

    @Test
    void shouldLeavePendingDecisionRetryable() {
        TemporalRunBindingRecord binding = binding();
        when(mapper.selectWaitingApprovalBindings(100)).thenReturn(List.of(binding));
        when(agentQueries.getApproval("approval-1")).thenReturn(
                AgentControlResult.builder().status("PENDING").build());

        reconciler.reconcile();

        verify(continuation, never()).onApprovalDecision(
                162L, "work-order-1", "approval-1", "APPROVE");
    }

    private static TemporalRunBindingRecord binding() {
        return new TemporalRunBindingRecord().setTenantId(162L).setTemporalRunId("run-1")
                .setTemporalWorkflowId("workflow-1").setScheduleId("schedule-1")
                .setWorkOrderId("work-order-1").setApprovalId("approval-1")
                .setStatus("WAITING_APPROVAL");
    }
}
