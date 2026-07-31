package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import java.util.List;

public interface AgentControlQueryApi {
    AgentControlResult getRole(String roleCode);
    AgentControlResult getWorkOrder(String workOrderId);
    AgentControlResult getHandoff(String handoffId);
    AgentControlResult getApproval(String approvalId);
    AgentApprovalDetailView getApprovalDetail(String approvalId);
    AgentApprovalBoardStatsView getApprovalBoardStats();
    AgentControlResult getBusinessResult(String resultId);
    List<AgentBusinessCardView> listBusinessCards(String roleCode, String cardType, String status, Integer limit);

    /**
     * 列出岗位角色授予记录（管理员治理只读，用于角色授予管理页 + 撤销回填）。
     *
     * @param roleCode    可选，按角色编码精确过滤
     * @param status      可选，按状态过滤（ACTIVE/REVOKED/EXPIRED）
     * @param actorUserId 可选，按被授予用户过滤
     * @param limit       1-200，默认 50
     */
    List<ActorRoleGrantView> listActorRoleGrants(String roleCode, String status, Long actorUserId, Integer limit);
}
