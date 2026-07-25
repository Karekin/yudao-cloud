package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class YudaoBpmApprovalAttestationGate implements AgentApprovalWorkflowAttestationGate {

    private final AgentControlStoreMapper mapper;

    @Override
    public void assertDecisionAllowed(Long tenantId, Long operatorUserId, Approval approval, String decision) {
        ApprovalWorkflowBinding binding = mapper.selectApprovalWorkflowBinding(
                tenantId, approval.getApprovalId());
        require(binding != null, "BPM approval workflow binding is missing");
        require(Objects.equals(binding.getTenantId(), tenantId)
                        && Objects.equals(binding.getApprovalId(), approval.getApprovalId())
                        && Objects.equals(binding.getWorkOrderId(), approval.getWorkOrderId())
                        && Objects.equals(binding.getScopeHash(), approval.getScopeHash()),
                "BPM approval workflow binding drifted from the frozen approval");
        String expectedStatus = "APPROVE".equals(decision)
                ? "BPM_APPROVED_PENDING_ATTESTATION"
                : "BPM_REJECTED";
        require(expectedStatus.equals(binding.getStatus()),
                "BPM workflow terminal status does not permit this Agent Control decision");
        require(Objects.equals(binding.getApproverUserId(), operatorUserId),
                "only the BPM-selected approver may finalize this decision");
        require(Objects.equals(binding.getTerminalOperatorUserId(), operatorUserId),
                "BPM terminal task completer does not match the authenticated approver");
        require(Objects.equals(binding.getTerminalTaskDefinitionKey(),
                        YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY)
                        && binding.getTerminalTaskId() != null && !binding.getTerminalTaskId().isBlank(),
                "BPM terminal evidence does not identify the governed approval task");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
