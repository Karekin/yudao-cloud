package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;

public interface AgentApprovalWorkflowAdapter {

    String start(ApprovalWorkflowStartCandidate candidate);

}
