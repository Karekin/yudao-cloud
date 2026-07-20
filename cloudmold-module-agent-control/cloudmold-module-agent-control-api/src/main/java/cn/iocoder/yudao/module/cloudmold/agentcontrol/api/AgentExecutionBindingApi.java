package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

public interface AgentExecutionBindingApi {
    AgentControlResult bind(AgentExecutionBindingCommand command, Long operatorUserId);
    AgentControlResult reconcile(String bindingId, Long operatorUserId);
}
