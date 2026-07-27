package cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow;

/**
 * Temporal 可重复调用的只读事实端口。业务写入仍走既有受治理命令。
 */
public interface CustomerResolutionWorkflowQueryPort {
    CustomerResolutionWorkflowResult inspect(String ticketId);
}
