package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review;

/**
 * Narrow HSF contract for an explicitly assigned AI reviewer.
 *
 * <p>The caller never supplies an approver or BPM task id. The provider derives both from the
 * authenticated RPC principal and the frozen Agent Control approval binding.</p>
 */
public interface AgentApprovalReviewApi {

    AgentApprovalReviewContext getReviewContext(String approvalId);

    AgentApprovalReviewResult submitDecision(AgentApprovalReviewCommand command);
}
