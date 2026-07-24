package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

public record SkillTaskApprovalScope(long tenantId, long operatorId, int operatorType,
                                     String skillId, String skillVersion, String inputSha256,
                                     String riskLevel) {
}
