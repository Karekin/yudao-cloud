package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;

/**
 * Keeps a workflow decision as candidate evidence until an authenticated
 * Agent Control approver attests the exact frozen approval scope.
 */
public interface AgentApprovalWorkflowAttestationGate {

    AgentApprovalWorkflowAttestationGate DISABLED = (tenantId, operatorUserId, approval, decision) -> {
    };

    void assertDecisionAllowed(Long tenantId, Long operatorUserId, Approval approval, String decision);

    /**
     * Allows a BPM-attested dedicated AI reviewer to re-evaluate an approval after only the
     * version of the same still-enabled action policy has advanced. Implementations must keep
     * returning {@code false} for ordinary human/API decisions.
     */
    default boolean permitsAiPolicyVersionDrift(Long tenantId, Long operatorUserId, Approval approval) {
        return false;
    }

    default boolean supportsR3MultiPartyApproval() {
        return false;
    }
}
