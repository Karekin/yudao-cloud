package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.AsymmetricSigningKey;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.SigningKey;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalScope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
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
            case CMA1 -> SkillTaskApprovalRefCodec.issue(material.hmacSecret(),
                    new SkillTaskApprovalScope(normalized.tenantId(), subjectUserId(normalized.subject()),
                            subjectUserType(normalized.subject()), normalized.skillId(), normalized.skillVersion(),
                            normalized.inputSha256(), normalized.riskLevel()),
                    normalized.approvalId(), normalized.expiresAt());
            case CMA2 -> SkillTaskApprovalRefCodec.issueKeyed(material.hmacSecret(), material.keyId(),
                    new SkillTaskApprovalScope(normalized.tenantId(), subjectUserId(normalized.subject()),
                            subjectUserType(normalized.subject()), normalized.skillId(), normalized.skillVersion(),
                            normalized.inputSha256(), normalized.riskLevel()),
                    normalized.approvalId(), normalized.issuedAt(), normalized.expiresAt());
            case CMA3, CMA3_RSA -> issueClaims(material, normalized.withKeyId(material.keyId()));
        };
        return new SignedAgentExecutionPermit(approvalRef, SkillTaskApprovalRefCodec.sha256(approvalRef),
                switch (mode) {
                    case CMA1 -> SkillTaskApprovalRefCodec.LEGACY_VERSION;
                    case CMA2 -> SkillTaskApprovalRefCodec.VERSION;
                    case CMA3, CMA3_RSA -> SkillTaskApprovalRefCodec.CLAIMS_VERSION;
                }, material.keyId(), normalized.expiresAt());
    }

    private SigningMode signingMode() {
        AuthorityMode authorityMode = authorityMode();
        String configured = Objects.toString(properties.getIssueVersion(), "auto").trim().toLowerCase(Locale.ROOT);
        return switch (configured) {
            case "", "auto" -> authorityMode == AuthorityMode.PEM_RSA
                    ? SigningMode.CMA3_RSA
                    : properties.getActiveKeyId() == null || properties.getActiveKeyId().isBlank()
                    ? SigningMode.CMA1 : SigningMode.CMA2;
            case "cma1" -> SigningMode.CMA1;
            case "cma2" -> SigningMode.CMA2;
            case "cma3" -> authorityMode == AuthorityMode.PEM_RSA ? SigningMode.CMA3_RSA : SigningMode.CMA3;
            default -> throw new IllegalStateException("unsupported Skill Task approval issueVersion");
        };
    }

    private SigningMaterial signingMaterial(Instant now, SigningMode mode) {
        AuthorityMode authorityMode = authorityMode();
        String activeKeyId = Objects.toString(properties.getActiveKeyId(), "").trim();
        if ((mode == SigningMode.CMA2 || mode == SigningMode.CMA3) && authorityMode != AuthorityMode.LOCAL_TEST_HMAC) {
            throw new IllegalStateException("HMAC Skill Task approval signing is restricted to LOCAL_TEST");
        }
        if (mode == SigningMode.CMA3_RSA) {
            if (activeKeyId.isEmpty()) {
                throw new IllegalStateException("active Skill Task approval signing key is not configured");
            }
            AsymmetricSigningKey key = properties.getAsymmetricKeys().get(activeKeyId);
            if (key == null) {
                throw new IllegalStateException("active Skill Task approval signing key is not configured");
            }
            PrivateKey privateKey = rsaPrivateKey(key.getPrivateKeyPem());
            Instant notBefore = requireNonNull(key.getNotBefore(), "active signing key notBefore is missing");
            Instant expiresAt = requireNonNull(key.getExpiresAt(), "active signing key expiresAt is missing");
            require(!now.isBefore(notBefore), "active Skill Task approval signing key is not active yet");
            require(expiresAt.isAfter(now), "active Skill Task approval signing key has expired");
            if (key.getRevokedAt() != null && !now.isBefore(key.getRevokedAt())) {
                throw new IllegalStateException("active Skill Task approval signing key is revoked");
            }
            return SigningMaterial.rsa(activeKeyId, privateKey, expiresAt);
        }
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
            return SigningMaterial.hmac(activeKeyId, secret, expiresAt);
        }
        if (authorityMode != AuthorityMode.LOCAL_TEST_HMAC) {
            throw new IllegalStateException("HMAC Skill Task approval signing is restricted to LOCAL_TEST");
        }
        if (mode == SigningMode.CMA1 && !properties.isLegacyHmacEnabled()) {
            throw new IllegalStateException(
                    "Skill Task approval signing is disabled because no active keyed signer is configured");
        }
        return SigningMaterial.hmac(SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID,
                requireSecret(properties.getHmacSecret()), null);
    }

    private String issueClaims(SigningMaterial material, SkillTaskApprovalPermitClaims claims) {
        if (material.privateKey() == null) {
            return SkillTaskApprovalRefCodec.issueClaims(material.hmacSecret(), claims);
        }
        String payload = SkillTaskApprovalRefCodec.claimsPayload(claims);
        byte[] signature = rsaSign(material.privateKey(), SkillTaskApprovalRefCodec.claimsMessage(material.keyId(), payload));
        return SkillTaskApprovalRefCodec.issueClaimsWithSignature(claims, signature);
    }

    private static byte[] rsaSign(PrivateKey privateKey, String message) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("Skill Task approval RSA signing failed", ex);
        }
    }

    private static PrivateKey rsaPrivateKey(String pem) {
        String value = Objects.toString(pem, "").trim()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        if (value.isEmpty()) {
            throw new IllegalStateException("Skill Task approval privateKeyPem is required");
        }
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(value)));
        } catch (Exception ex) {
            throw new IllegalStateException("Skill Task approval privateKeyPem is invalid", ex);
        }
    }

    private AuthorityMode authorityMode() {
        String configured = Objects.toString(properties.getAuthorityMode(), "LOCAL_TEST_HMAC")
                .trim().toUpperCase(Locale.ROOT);
        return switch (configured) {
            case "LOCAL_TEST_HMAC" -> AuthorityMode.LOCAL_TEST_HMAC;
            case "PEM_RSA" -> AuthorityMode.PEM_RSA;
            default -> throw new IllegalStateException("unsupported Skill Task approval authorityMode");
        };
    }

    private static SkillTaskApprovalPermitClaims requireClaims(SkillTaskApprovalPermitClaims claims) {
        SkillTaskApprovalPermitClaims value = Objects.requireNonNull(claims, "claims");
        SkillTaskApprovalRefCodec.claimsPayload(value);
        return value;
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
        CMA1, CMA2, CMA3, CMA3_RSA
    }

    private enum AuthorityMode {
        LOCAL_TEST_HMAC, PEM_RSA
    }

    private record SigningMaterial(String keyId, byte[] hmacSecret, PrivateKey privateKey, Instant expiresAt) {
        private static SigningMaterial hmac(String keyId, byte[] secret, Instant expiresAt) {
            return new SigningMaterial(keyId, secret, null, expiresAt);
        }

        private static SigningMaterial rsa(String keyId, PrivateKey privateKey, Instant expiresAt) {
            return new SigningMaterial(keyId, null, privateKey, expiresAt);
        }
    }
}
