package cn.iocoder.yudao.module.cloudmold.skilltask;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "cloudmold.skill-task")
public class SkillTaskProperties {

    private Path registryRoot = Path.of("/app/skills");
    private boolean failOnEmptyRegistry = true;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration workerDelay = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofMinutes(5);
    private int batchSize = 20;
    private int defaultMaxAttempts = 5;
    private int maxPayloadBytes = 1_048_576;
    private int maxErrorMessageLength = 2_000;
    private boolean workerEnabled = true;
    private DynamicRegistry dynamic = new DynamicRegistry();
    private Approval approval = new Approval();

    @Data
    public static class DynamicRegistry {

        /**
         * Tenant-scoped bridge from E2 workflow registry pointers to executable SkillTask definitions.
         * Explicit semantic versions continue to work even when the bridge is disabled.
         */
        private boolean enabled = true;
        private String activeAlias = "ACTIVE";
    }

    @Data
    public static class Approval {

        /**
         * Compatibility-only cma1 secret. Production should use keys and disable legacy HMAC.
         */
        private String hmacSecret = "";
        private boolean legacyHmacEnabled = true;
        private String authorityMode = "LOCAL_TEST_HMAC";
        private String issuer = "cloudmold.agent-control";
        private String audience = "cloudmold.skill-task";
        private Map<String, VerificationKey> keys = new LinkedHashMap<>();
        private Map<String, AsymmetricVerificationKey> asymmetricKeys = new LinkedHashMap<>();
        private Duration maxValidity = Duration.ofHours(4);
        private Duration clockSkew = Duration.ofSeconds(30);
    }

    @Data
    public static class VerificationKey {

        /**
         * Secret owned by the external approval authority. It is never exposed through MCP.
         */
        private String secret = "";
        private Instant notBefore;
        private Instant expiresAt;
        private Instant revokedAt;
    }

    @Data
    public static class AsymmetricVerificationKey {

        private String algorithm = "RS256";
        private String publicKeyPem = "";
        private Instant notBefore;
        private Instant expiresAt;
        private Instant revokedAt;
    }
}
