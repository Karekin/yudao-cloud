package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import java.time.Instant;

public record SkillTaskApprovalEvidence(String approvalId, String referenceSha256,
                                        Instant expiresAt, String verifier) {
}
