package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;

@Data
class PendingApprovalAssignmentRecord {
    private String approvalId;
    private Long requesterUserId;
    private String scopeHash;
    private String roleCode;
    private String actionCode;
    private String riskLevel;
    private String skillId;
    private String skillVersion;
}
