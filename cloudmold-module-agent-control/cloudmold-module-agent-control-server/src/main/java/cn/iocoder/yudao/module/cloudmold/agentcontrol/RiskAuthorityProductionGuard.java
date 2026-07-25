package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.AsymmetricSigningKey;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.SkillTaskApprovalSigningProperties.RemoteSigner;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.service.RemoteRiskAuthorityExecutionPermitSigner;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskPemKeys;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
public class RiskAuthorityProductionGuard {

    public RiskAuthorityProductionGuard(SkillTaskApprovalSigningProperties properties, Environment environment) {
        validate(properties, environment.acceptsProfiles(Profiles.of("prod", "production")));
    }

    static void validate(SkillTaskApprovalSigningProperties properties, boolean production) {
        if (!production) {
            return;
        }
        require("REMOTE_RSA".equalsIgnoreCase(properties.getAuthorityMode()),
                "production Skill Task approval authorityMode must be REMOTE_RSA");
        require("cma3".equalsIgnoreCase(properties.getIssueVersion()),
                "production Skill Task approval issueVersion must be cma3");
        require(!properties.isLegacyHmacEnabled(),
                "production Skill Task approval legacy HMAC must be disabled");
        require(isBlank(properties.getHmacSecret()) && empty(properties.getKeys()),
                "production Skill Task approval rejects local HMAC key material");
        require(isBlank(properties.getActiveKeyId()),
                "production remote authority must not mix activeKeyId with authorityMode REMOTE_RSA");
        require(noPrivatePem(properties.getAsymmetricKeys()) && empty(properties.getAsymmetricKeys()),
                "production Agent Control rejects local private PEM key material");
        require(!isBlank(properties.getIssuer()) && !isBlank(properties.getAudience()),
                "production Risk Authority issuer and audience are required");

        RemoteSigner remote = properties.getRemote();
        require(remote != null, "production remote signer configuration is required");
        require(httpsEndpoint(remote.getEndpoint()),
                "production remote signer endpoint must use HTTPS");
        require(!isBlank(remote.getBearerToken()),
                "production remote signer bearerToken is required");
        require("RS256".equals(remote.getAlgorithm()),
                "production remote signer algorithm must be RS256");
        require(positive(remote.getConnectTimeout()) && positive(remote.getRequestTimeout()),
                "production remote signer timeouts must be positive");
        require(remote.getRequestTimeout().compareTo(Duration.ofSeconds(10)) <= 0,
                "production remote signer requestTimeout must not exceed 10 seconds");
        require(remote.getVerificationKeys() != null && !remote.getVerificationKeys().isEmpty(),
                "production remote signer verification keys are required");
        remote.getVerificationKeys().forEach((keyId, key) -> {
            require(keyId != null && keyId.matches("[A-Za-z0-9._-]{3,64}") && key != null,
                    "production remote signer verification key configuration is invalid");
            RemoteRiskAuthorityExecutionPermitSigner.requireRsaStrength(
                    SkillTaskPemKeys.rsaPublicKey(key.getPublicKeyPem()));
            require(key.getNotBefore() != null && key.getExpiresAt() != null
                            && key.getExpiresAt().isAfter(key.getNotBefore()),
                    "production remote signer key epoch is invalid");
        });
        String expectedKeyId = Objects.toString(remote.getExpectedKeyId(), "").trim();
        require(expectedKeyId.isEmpty() || remote.getVerificationKeys().containsKey(expectedKeyId),
                "production remote signer expectedKeyId is not present in verification keys");
    }

    private static boolean noPrivatePem(Map<String, AsymmetricSigningKey> keys) {
        return keys == null || keys.values().stream()
                .filter(Objects::nonNull)
                .allMatch(key -> isBlank(key.getPrivateKeyPem()));
    }

    private static boolean httpsEndpoint(String configured) {
        try {
            URI uri = URI.create(Objects.toString(configured, "").trim());
            return uri.isAbsolute() && uri.getHost() != null && "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    private static boolean empty(Map<?, ?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
