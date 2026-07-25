package cn.iocoder.yudao.module.cloudmold.agentcontrol;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.skill-task.approval")
public class SkillTaskApprovalSigningProperties {

    /**
     * Compatibility-only cma1 secret. Production should configure an active keyed signer and disable this path.
     */
    private String hmacSecret = "";
    private boolean legacyHmacEnabled = true;
    private String activeKeyId = "";
    /**
     * auto preserves the legacy issuance path: keyed cma2 when activeKeyId is configured, cma1 otherwise.
     * cma3 opts into the claims-bound permit while keeping the signer behind the local HMAC port.
     */
    private String issueVersion = "auto";
    private Map<String, SigningKey> keys = new LinkedHashMap<>();
    private Duration maxValidity = Duration.ofHours(4);

    @Data
    public static class SigningKey {

        private String secret = "";
        private Instant notBefore;
        private Instant expiresAt;
        private Instant revokedAt;
    }

}
