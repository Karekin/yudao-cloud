package cn.iocoder.yudao.module.cloudmold.risk.api.workflow;

/**
 * Governed, repeatable read contract for risk-dispute workflows.
 */
public interface RiskDisputeWorkflowQueryApi {

    RiskDisputeWorkflowResult inspect(String disputeId);
}
