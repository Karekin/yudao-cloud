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
    private Approval approval = new Approval();

    @Data
    public static class Approval {

        /**
         * Compatibility-only cma1 secret. Production should use keys and disable legacy HMAC.
         */
        private String hmacSecret = "";
        private boolean legacyHmacEnabled = true;
        private Map<String, VerificationKey> keys = new LinkedHashMap<>();
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
}
