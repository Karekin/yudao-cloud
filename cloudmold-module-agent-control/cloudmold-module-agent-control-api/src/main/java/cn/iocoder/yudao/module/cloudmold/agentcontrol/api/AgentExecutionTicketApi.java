package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

public interface AgentExecutionTicketApi {

    AgentExecutionTicketResult issue(AgentExecutionTicketCommand command, Long operatorUserId);

}
