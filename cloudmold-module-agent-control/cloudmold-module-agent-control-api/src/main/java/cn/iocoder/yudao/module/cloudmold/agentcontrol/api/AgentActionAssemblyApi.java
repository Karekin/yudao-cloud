package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

public interface AgentActionAssemblyApi {

    AgentActionAssemblyResult assembleAndSubmit(AgentActionAssemblyCommand command, Long actorUserId);

}
