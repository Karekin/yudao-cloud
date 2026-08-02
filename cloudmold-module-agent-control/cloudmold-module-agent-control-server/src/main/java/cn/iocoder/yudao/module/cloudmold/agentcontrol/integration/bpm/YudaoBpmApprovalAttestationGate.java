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
    private final AgentApprovalWorkflowProperties properties;

    @Autowired
    public YudaoBpmApprovalAttestationGate(AgentControlStoreMapper mapper,
                                           AgentApprovalResponsibilityResolver responsibilityResolver,
                                           AgentApprovalWorkflowProperties properties) {
        this.mapper = mapper;
        this.responsibilityResolver = responsibilityResolver;
        this.properties = properties;
    }

    YudaoBpmApprovalAttestationGate(AgentControlStoreMapper mapper) {
        this(mapper, new AgentApprovalResponsibilityResolver(mapper), new AgentApprovalWorkflowProperties());
    }

    YudaoBpmApprovalAttestationGate(AgentControlStoreMapper mapper,
                                    AgentApprovalResponsibilityResolver responsibilityResolver) {
        this(mapper, responsibilityResolver, new AgentApprovalWorkflowProperties());
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
        require(Objects.equals(binding.getApproverUserId(), operatorUserId),
                "only the BPM-selected operating principal may finalize this decision");
        require(binding.getTerminalOperatorUserId() != null
                        && binding.getTerminalOperatorUserId() > 0,
                "BPM terminal task completer is missing");
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
        boolean aiTerminal = isAiTerminal(binding, workOrder);
        if ("R3".equals(binding.getRiskLevel())) {
            if (aiTerminal) {
                responsibilityResolver.assertSnapshotCurrent(binding, workOrder,
                        LocalDateTime.now(ZoneOffset.UTC));
            } else {
                responsibilityResolver.assertCurrent(binding, workOrder, binding.getTerminalOperatorUserId(),
                        binding.getTerminalTaskDefinitionKey(), decision,
                        LocalDateTime.now(ZoneOffset.UTC));
            }
        } else {
            if (!aiTerminal) {
                require(Objects.equals(binding.getTerminalOperatorUserId(), operatorUserId),
                        "BPM terminal task completer does not match the authenticated approver");
                require(Objects.equals(binding.getTerminalTaskDefinitionKey(),
                                YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY),
                        "BPM terminal evidence does not identify the governed approval task");
            }
        }
    }

    @Override
    public boolean supportsR3MultiPartyApproval() {
        return true;
    }

    @Override
    public boolean permitsAiPolicyVersionDrift(Long tenantId, Long operatorUserId, Approval approval) {
        ApprovalWorkflowBinding binding = mapper.selectApprovalWorkflowBinding(tenantId, approval.getApprovalId());
        if (binding == null
                || !"BPM_APPROVED_PENDING_ATTESTATION".equals(binding.getStatus())
                || !Objects.equals(binding.getApproverUserId(), operatorUserId)
                || !Objects.equals(binding.getTenantId(), tenantId)
                || !Objects.equals(binding.getWorkOrderId(), approval.getWorkOrderId())
                || !Objects.equals(binding.getActionCode(), approval.getActionCode())
                || !Objects.equals(binding.getScopeHash(), approval.getScopeHash())) {
            return false;
        }
        WorkOrder workOrder = mapper.selectWorkOrder(tenantId, approval.getWorkOrderId());
        return workOrder != null && isAiTerminal(binding, workOrder);
    }

    private boolean isAiTerminal(ApprovalWorkflowBinding binding, WorkOrder workOrder) {
        Long terminalUserId = binding.getTerminalOperatorUserId();
        if (!properties.isAiReviewerOrSignEnabled()
                || !properties.getAiReviewerUserIds().contains(terminalUserId)
                || !properties.getAiReviewAllowedRiskLevels().contains(binding.getRiskLevel())) {
            return false;
        }
        require(!Objects.equals(terminalUserId, binding.getRequesterUserId())
                        && !Objects.equals(terminalUserId, workOrder.getAssigneeUserId())
                        && !Objects.equals(terminalUserId, binding.getApproverUserId()),
                "AI OR-sign reviewer violates separation of duties");
        require(Objects.equals(binding.getTerminalTaskDefinitionKey(),
                        YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY)
                        || Objects.equals(binding.getTerminalTaskDefinitionKey(),
                        YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY)
                        || Objects.equals(binding.getTerminalTaskDefinitionKey(),
                        YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY),
                "AI terminal evidence does not identify a governed approval task");
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
