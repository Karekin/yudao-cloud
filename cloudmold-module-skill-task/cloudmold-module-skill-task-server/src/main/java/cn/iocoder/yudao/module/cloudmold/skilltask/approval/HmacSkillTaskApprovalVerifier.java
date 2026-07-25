package cn.iocoder.yudao.module.cloudmold.skilltask.approval;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalScope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class HmacSkillTaskApprovalVerifier implements SkillTaskApprovalVerifier {

    private final byte[] legacySecret;
    private final boolean legacyEnabled;
    private final Map<String, VerificationKeyMaterial> keys;
    private final Duration maxValidity;
    private final Duration clockSkew;
    private final Clock clock;

    public HmacSkillTaskApprovalVerifier(SkillTaskProperties properties, Clock clock) {
        SkillTaskProperties.Approval approval = properties.getApproval();
        String configured = approval == null ? "" : Objects.toString(approval.getHmacSecret(), "").trim();
        if (!configured.isEmpty() && configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Skill Task approval HMAC secret must contain at least 32 bytes");
        }
        this.legacySecret = configured.getBytes(StandardCharsets.UTF_8);
        this.legacyEnabled = approval != null && approval.isLegacyHmacEnabled();
        this.keys = configuredKeys(approval);
        this.maxValidity = positive(approval == null ? null : approval.getMaxValidity(), "maxValidity");
        this.clockSkew = nonNegative(approval == null ? null : approval.getClockSkew(), "clockSkew");
        this.clock = clock;
    }

    @Override
    public SkillTaskApprovalEvidence verify(SkillTaskApprovalContext context) {
        Objects.requireNonNull(context, "context");
        if (legacySecret.length == 0 && keys.isEmpty()) {
            throw new SecurityException("R2/R3 Skill approvals are disabled because no approval authority is configured");
        }
        try {
            String reference = requireText(context.approvalRef(), "approvalRef");
            if (reference.startsWith(SkillTaskApprovalRefCodec.CLAIMS_VERSION + ":")) {
                return verifyClaims(context, reference);
            }
            return reference.startsWith(SkillTaskApprovalRefCodec.VERSION + ":")
                    ? verifyKeyed(context, reference) : verifyLegacy(context, reference);
        } catch (IllegalArgumentException ex) {
            throw new SecurityException(ex.getMessage(), ex);
        }
    }

    private SkillTaskApprovalEvidence verifyClaims(SkillTaskApprovalContext context, String reference) {
        SkillTaskApprovalRefCodec.ParsedClaimsApprovalRef parsed = SkillTaskApprovalRefCodec.parseClaims(reference);
        SkillTaskApprovalPermitClaims claims = parsed.claims();
        VerificationKeyMaterial key = claims.keyId().equals(SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID)
                ? localKeyMaterial(claims.issuedAt()) : keys.get(claims.keyId());
        if (key == null) {
            throw new SecurityException("approvalRef signing key is unknown");
        }
        byte[] expected = SkillTaskApprovalRefCodec.sign(key.secret(),
                SkillTaskApprovalRefCodec.claimsMessage(claims.keyId(), parsed.payload()));
        requireSignature(expected, parsed.signature());
        Instant now = clock.instant();
        requireClaimsScope(context, claims);
        if (claims.notBefore().isAfter(claims.issuedAt())) {
            throw new SecurityException("approvalRef lifetime is invalid");
        }
        if (claims.issuedAt().isAfter(now.plus(clockSkew))) {
            throw new SecurityException("approvalRef issued-at is in the future");
        }
        if (claims.notBefore().isAfter(now.plus(clockSkew))) {
            throw new SecurityException("approvalRef is not active yet");
        }
        if (key.revokedAt() != null && !now.isBefore(key.revokedAt())) {
            throw new SecurityException("approvalRef signing key is revoked");
        }
        if (claims.issuedAt().plus(clockSkew).isBefore(key.notBefore())) {
            throw new SecurityException("approvalRef was issued before its signing key became active");
        }
        if (key.expiresAt() != null && claims.expiresAt().isAfter(key.expiresAt())) {
            throw new SecurityException("approvalRef exceeds its signing key lifetime");
        }
        if (Duration.between(claims.issuedAt(), claims.expiresAt()).compareTo(maxValidity) > 0) {
            throw new SecurityException("approvalRef validity exceeds the configured maximum");
        }
        requireNotExpired(claims.expiresAt(), now);
        return new SkillTaskApprovalEvidence(claims, SkillTaskApprovalRefCodec.sha256(reference),
                SkillTaskApprovalRefCodec.CLAIMS_VERIFIER + ":" + claims.keyId());
    }

    private SkillTaskApprovalEvidence verifyKeyed(SkillTaskApprovalContext context, String reference) {
        SkillTaskApprovalRefCodec.ParsedKeyedApprovalRef parsed =
                SkillTaskApprovalRefCodec.parseKeyed(reference);
        String referenceSha256 = SkillTaskApprovalRefCodec.sha256(reference);
        VerificationKeyMaterial key = parsed.keyId().equals(SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID)
                ? localKeyMaterial(parsed.issuedAt()) : keys.get(parsed.keyId());
        if (key == null) {
            throw new SecurityException("approvalRef signing key is unknown");
        }
        byte[] expected = SkillTaskApprovalRefCodec.sign(key.secret(), SkillTaskApprovalRefCodec.keyedMessage(
                scope(context), parsed.keyId(), parsed.approvalId(), parsed.issuedAt().getEpochSecond(),
                parsed.expiresAt().getEpochSecond()));
        requireSignature(expected, parsed.signature());
        Instant now = clock.instant();
        if (key.revokedAt() != null && !now.isBefore(key.revokedAt())) {
            throw new SecurityException("approvalRef signing key is revoked");
        }
        if (parsed.issuedAt().plus(clockSkew).isBefore(key.notBefore())) {
            throw new SecurityException("approvalRef was issued before its signing key became active");
        }
        if (parsed.issuedAt().isAfter(now.plus(clockSkew))) {
            throw new SecurityException("approvalRef issued-at is in the future");
        }
        if (!parsed.expiresAt().isAfter(parsed.issuedAt())) {
            throw new SecurityException("approvalRef lifetime is invalid");
        }
        if (key.expiresAt() != null && parsed.expiresAt().isAfter(key.expiresAt())) {
            throw new SecurityException("approvalRef exceeds its signing key lifetime");
        }
        if (Duration.between(parsed.issuedAt(), parsed.expiresAt()).compareTo(maxValidity) > 0) {
            throw new SecurityException("approvalRef validity exceeds the configured maximum");
        }
        requireNotExpired(parsed.expiresAt(), now);
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims(SkillTaskApprovalRefCodec.VERSION,
                parsed.keyId(), SkillTaskApprovalRefCodec.DEFAULT_ISSUER, SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                referenceSha256, "-", parsed.approvalId(), referenceSha256, context.skillId(), context.skillVersion(),
                context.definitionClosureSha256(), context.inputSha256(), context.riskLevel(),
                subject(context), context.tenantId(), parsed.issuedAt(), parsed.issuedAt(), parsed.expiresAt());
        return new SkillTaskApprovalEvidence(claims, referenceSha256,
                SkillTaskApprovalRefCodec.VERIFIER + ":" + parsed.keyId());
    }

    private SkillTaskApprovalEvidence verifyLegacy(SkillTaskApprovalContext context, String reference) {
        if (!legacyEnabled || legacySecret.length == 0) {
            throw new SecurityException("legacy cma1 approvals are disabled");
        }
        SkillTaskApprovalRefCodec.ParsedApprovalRef parsed = SkillTaskApprovalRefCodec.parse(reference);
        byte[] expected = SkillTaskApprovalRefCodec.sign(legacySecret, message(context, parsed.approvalId(),
                parsed.expiresAt().getEpochSecond()));
        requireSignature(expected, parsed.signature());
        Instant now = clock.instant();
        requireNotExpired(parsed.expiresAt(), now);
        if (parsed.expiresAt().isAfter(now.plus(maxValidity).plus(clockSkew))) {
            throw new SecurityException("approvalRef validity exceeds the configured maximum");
        }
        String referenceSha256 = SkillTaskApprovalRefCodec.sha256(reference);
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims(SkillTaskApprovalRefCodec.LEGACY_VERSION,
                SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID, SkillTaskApprovalRefCodec.DEFAULT_ISSUER,
                SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE, referenceSha256, "-", parsed.approvalId(),
                referenceSha256,
                context.skillId(), context.skillVersion(), context.definitionClosureSha256(), context.inputSha256(),
                context.riskLevel(), subject(context), context.tenantId(), now.minus(clockSkew), now,
                parsed.expiresAt());
        return new SkillTaskApprovalEvidence(claims, referenceSha256,
                SkillTaskApprovalRefCodec.LEGACY_VERIFIER);
    }

    private void requireNotExpired(Instant expiresAt, Instant now) {
        if (!expiresAt.plus(clockSkew).isAfter(now)) {
            throw new SecurityException("approvalRef has expired");
        }
    }

    private static void requireSignature(byte[] expected, byte[] actual) {
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("approvalRef signature or scope is invalid");
        }
    }

    private static SkillTaskApprovalScope scope(SkillTaskApprovalContext context) {
        return new SkillTaskApprovalScope(context.tenantId(), context.operatorId(), context.operatorType(),
                context.skillId(), context.skillVersion(), context.inputSha256(), context.riskLevel());
    }

    static String message(SkillTaskApprovalContext context, String approvalId, long expiresEpoch) {
        return SkillTaskApprovalRefCodec.message(scope(context), approvalId, expiresEpoch);
    }

    private VerificationKeyMaterial localKeyMaterial(Instant issuedAt) {
        if (legacySecret.length == 0) {
            return null;
        }
        return new VerificationKeyMaterial(legacySecret, issuedAt.minus(clockSkew), null, null);
    }

    private static Map<String, VerificationKeyMaterial> configuredKeys(SkillTaskProperties.Approval approval) {
        Map<String, VerificationKeyMaterial> result = new LinkedHashMap<>();
        if (approval == null || approval.getKeys() == null) {
            return Map.of();
        }
        approval.getKeys().forEach((keyId, configured) -> {
            if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{3,64}") || configured == null) {
                throw new IllegalStateException("Skill Task approval key configuration is invalid");
            }
            byte[] secret = Objects.toString(configured.getSecret(), "").trim().getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException("Skill Task approval key secret must contain at least 32 bytes");
            }
            Instant notBefore = requireInstant(configured.getNotBefore(), "notBefore");
            Instant expiresAt = requireInstant(configured.getExpiresAt(), "expiresAt");
            if (!expiresAt.isAfter(notBefore)) {
                throw new IllegalStateException("Skill Task approval key expiresAt must be after notBefore");
            }
            if (result.put(keyId, new VerificationKeyMaterial(secret, notBefore, expiresAt,
                    configured.getRevokedAt())) != null) {
                throw new IllegalStateException("Skill Task approval keyId is duplicated");
            }
        });
        return Map.copyOf(result);
    }

    private static Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalStateException("Skill Task approval key " + field + " is required");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String subject(SkillTaskApprovalContext context) {
        return context.operatorType() + ":" + context.operatorId();
    }

    private static void requireClaimsScope(SkillTaskApprovalContext context, SkillTaskApprovalPermitClaims claims) {
        if (!claims.issuer().equals(SkillTaskApprovalRefCodec.DEFAULT_ISSUER)
                || !claims.audience().equals(SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE)) {
            throw new SecurityException("approvalRef issuer or audience is invalid");
        }
        if (claims.tenantId() != context.tenantId()
                || !subject(context).equals(claims.subject())
                || !context.skillId().equals(claims.skillId())
                || !context.skillVersion().equals(claims.skillVersion())
                || !context.definitionClosureSha256().equals(claims.definitionClosureSha256())
                || !context.inputSha256().equals(claims.inputSha256())
                || !context.riskLevel().equalsIgnoreCase(claims.riskLevel())) {
            throw new SecurityException("approvalRef signature or scope is invalid");
        }
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

    private record VerificationKeyMaterial(byte[] secret, Instant notBefore, Instant expiresAt, Instant revokedAt) {
    }
}
