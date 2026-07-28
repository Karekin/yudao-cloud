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

    default boolean supportsR3MultiPartyApproval() {
        return false;
    }
}
