package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

/**
 * Stable callback invoked only after Agent Control has accepted an attested BPM terminal decision.
 *
 * <p>Implementations must be idempotent. A failure is retried by the BPM terminal reconciler and
 * must never undo the already persisted Agent Control decision.</p>
 */
public interface AgentApprovalContinuationApi {

    void onApprovalDecision(Long tenantId, String workOrderId, String approvalId, String decision);
}
