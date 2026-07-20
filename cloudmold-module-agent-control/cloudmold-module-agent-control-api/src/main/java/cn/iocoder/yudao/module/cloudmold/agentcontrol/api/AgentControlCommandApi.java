package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

public interface AgentControlCommandApi {
    AgentControlResult execute(AgentControlCommand command, Long operatorUserId);
}
