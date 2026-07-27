package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalGateChildWorkflowImplTest {

    @Test
    void shouldIgnoreDuplicateDecisionAfterApprovalChosen() {
        ApprovalGateChildWorkflowImpl workflow = new ApprovalGateChildWorkflowImpl();
        TemporalManagedRunState seed = TemporalManagedRunState.builder()
                .status("WAITING_APPROVAL").phase("APPROVAL_GATE")
                .workOrderId("wo-1").approvalId("ap-1").build();
        workflow.pause("first-pause");
        workflow.resume("first-resume");
        workflow.cancel("first-cancel");

        assertThat(seed.getStatus()).isEqualTo("WAITING_APPROVAL");
    }
}
