package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;

public interface AgentExecutionPermitSigner {

    SignedAgentExecutionPermit sign(SkillTaskApprovalPermitClaims claims);
}
