package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class YudaoBpmApprovalAttestationGate implements AgentApprovalWorkflowAttestationGate {

    private final AgentControlStoreMapper mapper;
    private final AgentApprovalResponsibilityResolver responsibilityResolver;

    @Autowired
    public YudaoBpmApprovalAttestationGate(AgentControlStoreMapper mapper,
                                           AgentApprovalResponsibilityResolver responsibilityResolver) {
        this.mapper = mapper;
        this.responsibilityResolver = responsibilityResolver;
    }

    YudaoBpmApprovalAttestationGate(AgentControlStoreMapper mapper) {
        this(mapper, new AgentApprovalResponsibilityResolver(mapper));
    }

    @Override
    public void assertDecisionAllowed(Long tenantId, Long operatorUserId, Approval approval, String decision) {
        ApprovalWorkflowBinding binding = mapper.selectApprovalWorkflowBinding(
                tenantId, approval.getApprovalId());
        require(binding != null, "BPM approval workflow binding is missing");
        require(Objects.equals(binding.getTenantId(), tenantId)
                        && Objects.equals(binding.getApprovalId(), approval.getApprovalId())
                        && Objects.equals(binding.getWorkOrderId(), approval.getWorkOrderId())
                        && Objects.equals(binding.getActionCode(), approval.getActionCode())
                        && Objects.equals(binding.getScopeHash(), approval.getScopeHash()),
                "BPM approval workflow binding drifted from the frozen approval");
        String expectedStatus = "APPROVE".equals(decision)
                ? "BPM_APPROVED_PENDING_ATTESTATION"
                : "BPM_REJECTED";
        require(expectedStatus.equals(binding.getStatus()),
                "BPM workflow terminal status does not permit this Agent Control decision");
        if (!"R3".equals(binding.getRiskLevel())) {
            require(Objects.equals(binding.getApproverUserId(), operatorUserId),
                    "only the BPM-selected approver may finalize this decision");
        }
        require(Objects.equals(binding.getTerminalOperatorUserId(), operatorUserId),
                "BPM terminal task completer does not match the authenticated approver");
        WorkOrder workOrder = mapper.selectWorkOrder(tenantId, approval.getWorkOrderId());
        require(workOrder != null, "BPM approval work order is missing");
        require(Objects.equals(binding.getRoleCode(), workOrder.getRoleCode())
                        && Objects.equals(binding.getActionCode(), workOrder.getActionCode())
                        && Objects.equals(binding.getRiskLevel(), workOrder.getRiskLevel()),
                "BPM approval responsibility policy drifted from the frozen work order");
        require(workOrder.getAssigneeUserId() == null
                        || !Objects.equals(workOrder.getAssigneeUserId(), operatorUserId),
                "work-order executor cannot attest its own approval");
        require(binding.getApproverUserId() != null
                        && !Objects.equals(binding.getApproverUserId(), binding.getRequesterUserId())
                        && !Objects.equals(binding.getApproverUserId(), workOrder.getAssigneeUserId()),
                "BPM operating-principal approval authority violates separation of duties");
        require(binding.getTerminalTaskId() != null && !binding.getTerminalTaskId().isBlank(),
                "BPM terminal evidence does not identify the governed approval task");
        if ("R3".equals(binding.getRiskLevel())) {
            responsibilityResolver.assertCurrent(binding, workOrder, operatorUserId,
                    binding.getTerminalTaskDefinitionKey(), decision,
                    LocalDateTime.now(ZoneOffset.UTC));
        } else {
            require(Objects.equals(binding.getTerminalTaskDefinitionKey(),
                            YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY),
                    "BPM terminal evidence does not identify the governed approval task");
        }
    }

    @Override
    public boolean supportsR3MultiPartyApproval() {
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
