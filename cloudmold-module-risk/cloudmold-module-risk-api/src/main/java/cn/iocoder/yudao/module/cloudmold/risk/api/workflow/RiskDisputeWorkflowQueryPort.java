package cn.iocoder.yudao.module.cloudmold.risk.api.workflow;

/**
 * Temporal 可重复调用的只读事实端口。业务写入仍走既有受治理命令。
 */
public interface RiskDisputeWorkflowQueryPort {
    RiskDisputeWorkflowResult inspect(String disputeId);
}
