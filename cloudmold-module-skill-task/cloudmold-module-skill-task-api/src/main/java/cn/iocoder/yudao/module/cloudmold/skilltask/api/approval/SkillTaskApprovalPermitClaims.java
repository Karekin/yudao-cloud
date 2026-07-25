package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

import java.io.Serializable;
import java.time.Instant;

public record SkillTaskApprovalPermitClaims(
        String version,
        String keyId,
        String issuer,
        String audience,
        String permitId,
        String workOrderId,
        String approvalId,
        String rootRequestIdentity,
        String skillId,
        String skillVersion,
        String definitionClosureSha256,
        String inputSha256,
        String riskLevel,
        String subject,
        long tenantId,
        Instant notBefore,
        Instant issuedAt,
        Instant expiresAt,
        String missionRunId,
        Long fencingToken,
        String leaseOwner,
        Long leaseEpoch) implements Serializable {

    /**
     * Compatibility constructor for non-mission permits. Mission permits must
     * use the canonical constructor and provide the complete lease fence.
     */
    public SkillTaskApprovalPermitClaims(
            String version,
            String keyId,
            String issuer,
            String audience,
            String permitId,
            String workOrderId,
            String approvalId,
            String rootRequestIdentity,
            String skillId,
            String skillVersion,
            String definitionClosureSha256,
            String inputSha256,
            String riskLevel,
            String subject,
            long tenantId,
            Instant notBefore,
            Instant issuedAt,
            Instant expiresAt) {
        this(version, keyId, issuer, audience, permitId, workOrderId, approvalId, rootRequestIdentity,
                skillId, skillVersion, definitionClosureSha256, inputSha256, riskLevel, subject,
                tenantId, notBefore, issuedAt, expiresAt, null, null, null, null);
    }

    public boolean missionBound() {
        return missionRunId != null || fencingToken != null || leaseOwner != null || leaseEpoch != null;
    }

    public SkillTaskApprovalPermitClaims withKeyId(String replacementKeyId) {
        return new SkillTaskApprovalPermitClaims(version, replacementKeyId, issuer, audience, permitId,
                workOrderId, approvalId, rootRequestIdentity, skillId, skillVersion,
                definitionClosureSha256, inputSha256, riskLevel, subject, tenantId,
                notBefore, issuedAt, expiresAt, missionRunId, fencingToken, leaseOwner, leaseEpoch);
    }
}
