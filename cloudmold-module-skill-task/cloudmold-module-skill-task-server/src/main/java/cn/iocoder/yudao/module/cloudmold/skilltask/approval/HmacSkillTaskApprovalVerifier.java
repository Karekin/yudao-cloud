package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalScope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class HmacSkillTaskApprovalVerifier implements SkillTaskApprovalVerifier {

    private final byte[] secret;
    private final Duration maxValidity;
    private final Duration clockSkew;
    private final Clock clock;

    public HmacSkillTaskApprovalVerifier(SkillTaskProperties properties, Clock clock) {
        SkillTaskProperties.Approval approval = properties.getApproval();
        String configured = approval == null ? "" : Objects.toString(approval.getHmacSecret(), "").trim();
        if (!configured.isEmpty() && configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Skill Task approval HMAC secret must contain at least 32 bytes");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.maxValidity = positive(approval == null ? null : approval.getMaxValidity(), "maxValidity");
        this.clockSkew = nonNegative(approval == null ? null : approval.getClockSkew(), "clockSkew");
        this.clock = clock;
    }

    @Override
    public SkillTaskApprovalEvidence verify(SkillTaskApprovalContext context) {
        Objects.requireNonNull(context, "context");
        if (secret.length == 0) {
            throw new SecurityException("R2/R3 Skill approvals are disabled because no approval authority is configured");
        }
        try {
            String reference = requireText(context.approvalRef(), "approvalRef");
            SkillTaskApprovalRefCodec.ParsedApprovalRef parsed = SkillTaskApprovalRefCodec.parse(reference);
            byte[] expected = SkillTaskApprovalRefCodec.sign(secret, message(context, parsed.approvalId(),
                    parsed.expiresAt().getEpochSecond()));
            if (!MessageDigest.isEqual(expected, parsed.signature())) {
                throw new SecurityException("approvalRef signature or scope is invalid");
            }
            Instant now = clock.instant();
            Instant expiresAt = parsed.expiresAt();
            if (!expiresAt.plus(clockSkew).isAfter(now)) {
                throw new SecurityException("approvalRef has expired");
            }
            if (expiresAt.isAfter(now.plus(maxValidity).plus(clockSkew))) {
                throw new SecurityException("approvalRef validity exceeds the configured maximum");
            }
            return new SkillTaskApprovalEvidence(parsed.approvalId(), SkillTaskApprovalRefCodec.sha256(reference),
                    expiresAt, SkillTaskApprovalRefCodec.VERIFIER);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException(ex.getMessage(), ex);
        }
    }

    static String message(SkillTaskApprovalContext context, String approvalId, long expiresEpoch) {
        return SkillTaskApprovalRefCodec.message(new SkillTaskApprovalScope(context.tenantId(), context.operatorId(),
                context.operatorType(), context.skillId(), context.skillVersion(), context.inputSha256(),
                context.riskLevel()), approvalId, expiresEpoch);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("Skill Task approval " + field + " must be positive");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException("Skill Task approval " + field + " must not be negative");
        }
        return value;
    }
}
