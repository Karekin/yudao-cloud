package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.SigningKey;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalScope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class HmacAgentExecutionPermitSigner implements AgentExecutionPermitSigner {

    private final SkillTaskApprovalSigningProperties properties;

    public HmacAgentExecutionPermitSigner(SkillTaskApprovalSigningProperties properties) {
        this.properties = properties;
    }

    @Override
    public SignedAgentExecutionPermit sign(SkillTaskApprovalPermitClaims claims) {
        SkillTaskApprovalPermitClaims normalized = requireClaims(claims);
        Duration maxValidity = requirePositive(properties.getMaxValidity(), "maxValidity");
        if (Duration.between(normalized.issuedAt(), normalized.expiresAt()).compareTo(maxValidity) > 0) {
            throw new IllegalStateException("execution ticket validity exceeds the configured maximum");
        }
        SigningMode mode = signingMode();
        SigningMaterial material = signingMaterial(normalized.issuedAt(), mode);
        if (material.expiresAt() != null && normalized.expiresAt().isAfter(material.expiresAt())) {
            throw new IllegalStateException("execution ticket validity exceeds the active signing key lifetime");
        }
        String approvalRef = switch (mode) {
            case CMA1 -> SkillTaskApprovalRefCodec.issue(material.secret(),
                    new SkillTaskApprovalScope(normalized.tenantId(), subjectUserId(normalized.subject()),
                            subjectUserType(normalized.subject()), normalized.skillId(), normalized.skillVersion(),
                            normalized.inputSha256(), normalized.riskLevel()),
                    normalized.approvalId(), normalized.expiresAt());
            case CMA2 -> SkillTaskApprovalRefCodec.issueKeyed(material.secret(), material.keyId(),
                    new SkillTaskApprovalScope(normalized.tenantId(), subjectUserId(normalized.subject()),
                            subjectUserType(normalized.subject()), normalized.skillId(), normalized.skillVersion(),
                            normalized.inputSha256(), normalized.riskLevel()),
                    normalized.approvalId(), normalized.issuedAt(), normalized.expiresAt());
            case CMA3 -> SkillTaskApprovalRefCodec.issueClaims(material.secret(),
                    new SkillTaskApprovalPermitClaims(SkillTaskApprovalRefCodec.CLAIMS_VERSION, material.keyId(),
                            normalized.issuer(), normalized.audience(), normalized.permitId(),
                            normalized.workOrderId(), normalized.approvalId(), normalized.rootRequestIdentity(),
                            normalized.skillId(), normalized.skillVersion(),
                            normalized.definitionClosureSha256(), normalized.inputSha256(),
                            normalized.riskLevel(), normalized.subject(), normalized.tenantId(),
                            normalized.notBefore(), normalized.issuedAt(), normalized.expiresAt()));
        };
        return new SignedAgentExecutionPermit(approvalRef, SkillTaskApprovalRefCodec.sha256(approvalRef),
                switch (mode) {
                    case CMA1 -> SkillTaskApprovalRefCodec.LEGACY_VERSION;
                    case CMA2 -> SkillTaskApprovalRefCodec.VERSION;
                    case CMA3 -> SkillTaskApprovalRefCodec.CLAIMS_VERSION;
                }, material.keyId(), normalized.expiresAt());
    }

    private SigningMode signingMode() {
        String configured = Objects.toString(properties.getIssueVersion(), "auto").trim().toLowerCase(Locale.ROOT);
        return switch (configured) {
            case "", "auto" -> properties.getActiveKeyId() == null || properties.getActiveKeyId().isBlank()
                    ? SigningMode.CMA1 : SigningMode.CMA2;
            case "cma1" -> SigningMode.CMA1;
            case "cma2" -> SigningMode.CMA2;
            case "cma3" -> SigningMode.CMA3;
            default -> throw new IllegalStateException("unsupported Skill Task approval issueVersion");
        };
    }

    private SigningMaterial signingMaterial(Instant now, SigningMode mode) {
        String activeKeyId = Objects.toString(properties.getActiveKeyId(), "").trim();
        if ((mode == SigningMode.CMA2 || mode == SigningMode.CMA3) && !activeKeyId.isEmpty()) {
            SigningKey key = properties.getKeys().get(activeKeyId);
            if (key == null) {
                throw new IllegalStateException("active Skill Task approval signing key is not configured");
            }
            byte[] secret = requireSecret(key.getSecret());
            Instant notBefore = requireNonNull(key.getNotBefore(), "active signing key notBefore is missing");
            Instant expiresAt = requireNonNull(key.getExpiresAt(), "active signing key expiresAt is missing");
            require(!now.isBefore(notBefore), "active Skill Task approval signing key is not active yet");
            require(expiresAt.isAfter(now), "active Skill Task approval signing key has expired");
            if (key.getRevokedAt() != null && !now.isBefore(key.getRevokedAt())) {
                throw new IllegalStateException("active Skill Task approval signing key is revoked");
            }
            return new SigningMaterial(activeKeyId, secret, expiresAt);
        }
        if (mode == SigningMode.CMA1 && !properties.isLegacyHmacEnabled()) {
            throw new IllegalStateException(
                    "Skill Task approval signing is disabled because no active keyed signer is configured");
        }
        return new SigningMaterial(SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID,
                requireSecret(properties.getHmacSecret()), null);
    }

    private static SkillTaskApprovalPermitClaims requireClaims(SkillTaskApprovalPermitClaims claims) {
        SkillTaskApprovalPermitClaims value = Objects.requireNonNull(claims, "claims");
        return new SkillTaskApprovalPermitClaims(
                Objects.requireNonNull(value.version(), "version"),
                Objects.requireNonNull(value.keyId(), "keyId"),
                Objects.requireNonNull(value.issuer(), "issuer"),
                Objects.requireNonNull(value.audience(), "audience"),
                Objects.requireNonNull(value.permitId(), "permitId"),
                Objects.requireNonNull(value.workOrderId(), "workOrderId"),
                Objects.requireNonNull(value.approvalId(), "approvalId"),
                Objects.requireNonNull(value.rootRequestIdentity(), "rootRequestIdentity"),
                Objects.requireNonNull(value.skillId(), "skillId"),
                Objects.requireNonNull(value.skillVersion(), "skillVersion"),
                Objects.requireNonNull(value.definitionClosureSha256(), "definitionClosureSha256"),
                Objects.requireNonNull(value.inputSha256(), "inputSha256"),
                Objects.requireNonNull(value.riskLevel(), "riskLevel"),
                Objects.requireNonNull(value.subject(), "subject"),
                value.tenantId(),
                Objects.requireNonNull(value.notBefore(), "notBefore"),
                Objects.requireNonNull(value.issuedAt(), "issuedAt"),
                Objects.requireNonNull(value.expiresAt(), "expiresAt"));
    }

    private static int subjectUserType(String subject) {
        return Integer.parseInt(subject.split(":", -1)[0]);
    }

    private static long subjectUserId(String subject) {
        return Long.parseLong(subject.split(":", -1)[1]);
    }

    private static byte[] requireSecret(String configured) {
        String value = Objects.toString(configured, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "Skill Task approval signing is disabled because no signing secret is configured");
        }
        byte[] secret = value.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("Skill Task approval signing secret must contain at least 32 bytes");
        }
        return secret;
    }

    private static Duration requirePositive(Duration value, String field) {
        require(value != null && !value.isZero() && !value.isNegative(), "invalid " + field);
        return value;
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private enum SigningMode {
        CMA1, CMA2, CMA3
    }

    private record SigningMaterial(String keyId, byte[] secret, Instant expiresAt) {
    }
}
