package cn.iocoder.yudao.module.cloudmold.skilltask;

import cn.iocoder.yudao.module.cloudmold.skilltask.approval.HmacSkillTaskApprovalVerifier;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskPemKeys;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SkillTaskRiskAuthorityProductionGuard {

    public SkillTaskRiskAuthorityProductionGuard(SkillTaskProperties properties, Environment environment) {
        validate(properties, environment.acceptsProfiles(Profiles.of("prod", "production")));
    }

    static void validate(SkillTaskProperties properties, boolean production) {
        if (!production) {
            return;
        }
        SkillTaskProperties.Approval approval = properties.getApproval();
        require(approval != null, "production Skill Task approval configuration is required");
        require("REMOTE_RSA".equalsIgnoreCase(approval.getAuthorityMode()),
                "production Skill Task approval authorityMode must be REMOTE_RSA");
        require(!approval.isLegacyHmacEnabled(),
                "production Skill Task approval legacy HMAC must be disabled");
        require(isBlank(approval.getHmacSecret()) && empty(approval.getKeys()),
                "production Skill Task rejects local HMAC verification material");
        require(!isBlank(approval.getIssuer()) && !isBlank(approval.getAudience()),
                "production Skill Task issuer and audience are required");
        require(approval.getAsymmetricKeys() != null && !approval.getAsymmetricKeys().isEmpty(),
                "production Skill Task requires public RSA verification keys");
        approval.getAsymmetricKeys().forEach((keyId, key) -> {
            require(keyId != null && keyId.matches("[A-Za-z0-9._-]{3,64}") && key != null,
                    "production Skill Task RSA verification key configuration is invalid");
            require("RS256".equals(key.getAlgorithm()),
                    "production Skill Task RSA verification algorithm must be RS256");
            HmacSkillTaskApprovalVerifier.requireRsaStrength(
                    SkillTaskPemKeys.rsaPublicKey(key.getPublicKeyPem()));
            require(key.getNotBefore() != null && key.getExpiresAt() != null
                            && key.getExpiresAt().isAfter(key.getNotBefore()),
                    "production Skill Task RSA key epoch is invalid");
        });
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
