package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YudaoBpmApprovalAttestationGateTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final YudaoBpmApprovalAttestationGate gate = new YudaoBpmApprovalAttestationGate(mapper);

    private void allowIndependentApprover() {
        when(mapper.selectWorkOrder(17L, "work-1"))
                .thenReturn(new WorkOrder().setTenantId(17L).setWorkOrderId("work-1")
                        .setRoleCode("buyer").setActionCode("purchase.commit")
                        .setRiskLevel("R2").setAssigneeUserId(300L));
    }

    @Test
    void approveRequiresMatchingBpmApprovalCandidate() {
        allowIndependentApprover();
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION"));

        assertThatCode(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsApproveBeforeBpmTerminalAndRejectsScopeDrift() {
        allowIndependentApprover();
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
        allowIndependentApprover();
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_REJECTED"));

        assertThatCode(() -> gate.assertDecisionAllowed(17L, 200L, approval, "REJECT"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDifferentCallerAndMissingTerminalCompleter() {
        allowIndependentApprover();
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

    @Test
    void rejectsWorkOrderExecutorSelfApprovalEvenWithMatchingBpmEvidence() {
        Approval approval = approval();
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1"))
                .thenReturn(binding("BPM_APPROVED_PENDING_ATTESTATION"));
        when(mapper.selectWorkOrder(17L, "work-1"))
                .thenReturn(new WorkOrder().setTenantId(17L).setWorkOrderId("work-1")
                        .setRoleCode("buyer").setActionCode("purchase.commit")
                        .setRiskLevel("R2").setAssigneeUserId(200L));

        assertThatThrownBy(() -> gate.assertDecisionAllowed(17L, 200L, approval, "APPROVE"))
                .hasMessage("work-order executor cannot attest its own approval");
    }

    @Test
    void allowsR3ApprovalAndRejectionOnlyThroughCurrentResponsibilityPolicy() {
        AgentApprovalResponsibilityResolver resolver = mock(AgentApprovalResponsibilityResolver.class);
        YudaoBpmApprovalAttestationGate r3Gate =
                new YudaoBpmApprovalAttestationGate(mapper, resolver);
        Approval approval = approval();
        WorkOrder workOrder = new WorkOrder().setTenantId(17L).setWorkOrderId("work-1")
                .setRoleCode("buyer").setActionCode("purchase.commit")
                .setRiskLevel("R3").setAssigneeUserId(300L);
        when(mapper.selectWorkOrder(17L, "work-1")).thenReturn(workOrder);
        ApprovalWorkflowBinding approved = binding("BPM_APPROVED_PENDING_ATTESTATION")
                .setRiskLevel("R3").setTerminalOperatorUserId(210L)
                .setTerminalTaskDefinitionKey(
                        YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY);
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1")).thenReturn(approved);
        when(resolver.assertCurrent(any(), any(), any(), any(), any(), any(LocalDateTime.class)))
                .thenReturn(new AgentApprovalResponsibilityResolver.Resolution(
                        List.of("buyer", "finance"), List.of(210L, 220L), "b".repeat(64)));

        assertThatCode(() -> r3Gate.assertDecisionAllowed(17L, 210L, approval, "APPROVE"))
                .doesNotThrowAnyException();

        ApprovalWorkflowBinding rejected = binding("BPM_REJECTED").setRiskLevel("R3");
        when(mapper.selectApprovalWorkflowBinding(17L, "approval-1")).thenReturn(rejected);
        assertThatCode(() -> r3Gate.assertDecisionAllowed(17L, 200L, approval, "REJECT"))
                .doesNotThrowAnyException();
    }

    private static Approval approval() {
        return new Approval().setApprovalId("approval-1").setTenantId(17L).setWorkOrderId("work-1")
                .setActionCode("purchase.commit").setScopeHash("a".repeat(64));
    }

    private static ApprovalWorkflowBinding binding(String status) {
        return new ApprovalWorkflowBinding().setApprovalId("approval-1").setTenantId(17L)
                .setWorkOrderId("work-1").setActionCode("purchase.commit").setRoleCode("buyer")
                .setRiskLevel("R2").setRequesterUserId(100L)
                .setScopeHash("a".repeat(64)).setStatus(status)
                .setApproverUserId(200L).setTerminalOperatorUserId(200L)
                .setTerminalTaskId("task-1").setTerminalTaskDefinitionKey("approval_review");
    }
}
