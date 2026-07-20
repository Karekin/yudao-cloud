package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

public record SkillTaskApprovalContext(long tenantId, long operatorId, int operatorType,
                                       String skillId, String skillVersion, String inputSha256,
                                       String riskLevel, String approvalRef) {
}
