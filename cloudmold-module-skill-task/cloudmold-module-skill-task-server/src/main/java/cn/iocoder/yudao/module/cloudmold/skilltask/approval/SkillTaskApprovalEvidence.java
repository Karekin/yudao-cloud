package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitSummary;

import java.time.Instant;

public record SkillTaskApprovalEvidence(SkillTaskApprovalPermitClaims claims, String referenceSha256,
                                        String verifier) {

    public String approvalId() {
        return claims.approvalId();
    }

    public Instant expiresAt() {
        return claims.expiresAt();
    }

    public String permitId() {
        return claims.permitId();
    }

    public String workOrderId() {
        return claims.workOrderId();
    }

    public String rootRequestIdentity() {
        return claims.rootRequestIdentity();
    }

    public String definitionClosureSha256() {
        return claims.definitionClosureSha256();
    }

    public SkillTaskApprovalPermitSummary summary() {
        return SkillTaskApprovalPermitSummary.builder()
                .permitVersion(claims.version())
                .keyId(claims.keyId())
                .permitId(claims.permitId())
                .workOrderId(claims.workOrderId())
                .approvalId(claims.approvalId())
                .verifier(verifier)
                .referenceSha256(referenceSha256)
                .issuedAt(claims.issuedAt())
                .expiresAt(claims.expiresAt())
                .build();
    }
}
