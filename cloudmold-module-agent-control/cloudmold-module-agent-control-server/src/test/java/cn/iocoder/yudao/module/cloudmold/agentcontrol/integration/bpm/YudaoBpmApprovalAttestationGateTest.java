package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YudaoBpmApprovalAttestationGateTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final YudaoBpmApprovalAttestationGate gate = new YudaoBpmApprovalAttestationGate(mapper);

    @Test
    void approveRequiresMatchingBpmApprovalCandidate() {
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION"));

        assertThatCode(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsApproveBeforeBpmTerminalAndRejectsScopeDrift() {
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("RUNNING"));

        assertThatThrownBy(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .hasMessage("BPM workflow terminal status does not permit this Agent Control decision");

        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION").setScopeHash("b".repeat(64)));
        assertThatThrownBy(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .hasMessage("BPM approval workflow binding drifted from the frozen approval");
    }

    @Test
    void rejectRequiresMatchingBpmRejection() {
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_REJECTED"));

        assertThatCode(() -> gate.assertDecisionAllowed(17L, 200L, approval, "REJECT"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDifferentCallerAndMissingTerminalCompleter() {
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION"));

        assertThatThrownBy(() -> gate.assertDecisionAllowed(17L, 201L, approval, "APPROVE"))
                .hasMessage("only the BPM-selected approver may finalize this decision");

        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION").setTerminalOperatorUserId(null));
        assertThatThrownBy(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .hasMessage("BPM terminal task completer does not match the authenticated approver");

        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION")
                        .setTerminalTaskDefinitionKey("unrelated_task"));
        assertThatThrownBy(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .hasMessage("BPM terminal evidence does not identify the governed approval task");
    }

    private static Approval approval() {
        return new Approval().setApprovalId("approval-1").setTenantId(17L).setWorkOrderId("work-1")
                .setScopeHash("a".repeat(64));
    }

    private static ApprovalWorkflowBinding binding(String status) {
        return new ApprovalWorkflowBinding().setApprovalId("approval-1").setTenantId(17L)
                .setWorkOrderId("work-1").setScopeHash("a".repeat(64)).setStatus(status)
                .setApproverUserId(200L).setTerminalOperatorUserId(200L)
                .setTerminalTaskId("task-1").setTerminalTaskDefinitionKey("approval_review");
    }
}
