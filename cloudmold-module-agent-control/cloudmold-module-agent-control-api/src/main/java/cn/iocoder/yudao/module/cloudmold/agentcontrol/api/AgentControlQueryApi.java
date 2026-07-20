package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import java.util.List;

public interface AgentControlQueryApi {
    AgentControlResult getRole(String roleCode);
    AgentControlResult getWorkOrder(String workOrderId);
    AgentControlResult getHandoff(String handoffId);
    AgentControlResult getApproval(String approvalId);
    AgentControlResult getBusinessResult(String resultId);
    List<AgentBusinessCardView> listBusinessCards(String roleCode, String cardType, String status, Integer limit);
}
