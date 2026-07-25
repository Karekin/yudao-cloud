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
        Instant expiresAt) implements Serializable {
}
