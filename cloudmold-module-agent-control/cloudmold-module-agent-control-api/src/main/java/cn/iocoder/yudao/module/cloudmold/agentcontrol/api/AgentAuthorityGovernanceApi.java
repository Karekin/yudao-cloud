package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

public interface AgentAuthorityGovernanceApi {
    AgentControlResult executeAuthorityGovernance(AgentAuthorityCommand command, Long governanceUserId);
}
