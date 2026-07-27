package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Agent 审批的冻结业务快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentApprovalDetailView implements Serializable {
    private static final long serialVersionUID = 1L;

    private String approvalId;
    private String workOrderId;
    private String title;
    private String roleCode;
    private String actionCode;
    private String riskLevel;
    private Long requesterUserId;
    private Long approverUserId;
    private String scopeHash;
    private String status;
    private String workflowStatus;
    private String processInstanceId;
    private String reasonCode;
    private String skillId;
    private String skillVersion;
    private String businessContextJson;
    private Instant requestedAt;
}
