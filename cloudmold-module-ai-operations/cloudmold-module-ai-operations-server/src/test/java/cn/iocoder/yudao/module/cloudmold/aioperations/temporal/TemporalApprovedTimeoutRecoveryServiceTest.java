package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalApprovedTimeoutRecoveryServiceTest {

    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final AiOperationsTemporalScheduleService schedules =
            mock(AiOperationsTemporalScheduleService.class);
    private final TemporalApprovedTimeoutRecoveryService recovery =
            new TemporalApprovedTimeoutRecoveryService(mapper, schedules);

    @Test
    void shouldClaimAndDispatchExactlyOnce() {
        TemporalRunBindingRecord binding = binding();
        when(mapper.claimApprovedTimeoutRecovery(eq(162L), eq("run-1"), any())).thenReturn(1);
        when(mapper.markApprovedTimeoutRecoveryDispatched(eq(162L), eq("run-1"), any())).thenReturn(1);

        assertThat(recovery.recover(binding)).isTrue();

        verify(schedules).triggerRecovery("schedule-1");
        verify(mapper).markApprovedTimeoutRecoveryDispatched(eq(162L), eq("run-1"), any());
    }

    @Test
    void shouldNotDispatchWhenAnotherReconcilerOwnsTheClaim() {
        TemporalRunBindingRecord binding = binding();
        when(mapper.claimApprovedTimeoutRecovery(eq(162L), eq("run-1"), any())).thenReturn(0);

        assertThat(recovery.recover(binding)).isFalse();

        verify(schedules, never()).triggerRecovery("schedule-1");
    }

    private static TemporalRunBindingRecord binding() {
        return new TemporalRunBindingRecord().setTenantId(162L).setTemporalRunId("run-1")
                .setTemporalWorkflowId("workflow-1").setScheduleId("schedule-1")
                .setWorkOrderId("work-order-1").setApprovalId("approval-1")
                .setStatus("WAITING_APPROVAL");
    }
}
