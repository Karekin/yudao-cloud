package cn.iocoder.yudao.module.cloudmold.rpc;

import org.apache.dubbo.rpc.Invocation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcConstants.*;

public final class CloudMoldRpcSecurity {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Map<String, Instant> NONCES = new ConcurrentHashMap<>();
    private static volatile byte[] sharedSecret;
    private static volatile Duration maxClockSkew = Duration.ofSeconds(60);
    private static volatile Clock clock = Clock.systemUTC();

    private CloudMoldRpcSecurity() {
    }

    public static void configure(String secret, long maxClockSkewSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("CLOUDMOLD_RPC_SHARED_SECRET must contain at least 32 characters");
        }
        if (maxClockSkewSeconds <= 0 || maxClockSkewSeconds > 300) {
            throw new IllegalStateException("cloudmold.rpc.max-clock-skew-seconds must be between 1 and 300");
        }
        sharedSecret = secret.getBytes(StandardCharsets.UTF_8);
        maxClockSkew = Duration.ofSeconds(maxClockSkewSeconds);
    }

    public static void sign(Invocation invocation, String capabilityId, CloudMoldRpcCallContext context) {
        requireConfigured();
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        invocation.setAttachment(CAPABILITY_ID, capabilityId);
        invocation.setAttachment(TENANT_ID, Long.toString(context.tenantId()));
        invocation.setAttachment(OPERATOR_ID, Long.toString(context.operatorId()));
        invocation.setAttachment(OPERATOR_TYPE, Integer.toString(context.operatorType()));
        invocation.setAttachment(SKILL_ID, context.skillId());
        invocation.setAttachment(RUN_ID, context.runId());
        invocation.setAttachment(TIMESTAMP, timestamp);
        invocation.setAttachment(NONCE, nonce);
        invocation.setAttachment(SIGNATURE, hmac(canonical(capabilityId, context.tenantId(), context.operatorId(),
                context.operatorType(), context.skillId(), context.runId(), timestamp, nonce)));
    }

    public static VerifiedContext verify(Invocation invocation, String expectedCapabilityId) {
        requireConfigured();
        String capabilityId = required(invocation, CAPABILITY_ID);
        if (!MessageDigest.isEqual(expectedCapabilityId.getBytes(StandardCharsets.UTF_8),
                capabilityId.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Capability attachment does not match invoked contract");
        }
        long tenantId = positiveLong(required(invocation, TENANT_ID), TENANT_ID);
        long operatorId = positiveLong(required(invocation, OPERATOR_ID), OPERATOR_ID);
        int operatorType = Math.toIntExact(positiveLong(required(invocation, OPERATOR_TYPE), OPERATOR_TYPE));
        String skillId = required(invocation, SKILL_ID);
        String runId = required(invocation, RUN_ID);
        String timestamp = required(invocation, TIMESTAMP);
        String nonce = required(invocation, NONCE);
        String signature = required(invocation, SIGNATURE);

        Instant requestTime = Instant.ofEpochSecond(positiveLong(timestamp, TIMESTAMP));
        Instant now = clock.instant();
        if (Duration.between(requestTime, now).abs().compareTo(maxClockSkew) > 0) {
            throw new SecurityException("RPC request timestamp is outside the accepted window");
        }
        evictExpiredNonces(now);
        if (NONCES.putIfAbsent(nonce, now) != null) {
            throw new SecurityException("RPC nonce has already been used");
        }
        String expected = hmac(canonical(capabilityId, tenantId, operatorId, operatorType, skillId, runId,
                timestamp, nonce));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            NONCES.remove(nonce);
            throw new SecurityException("RPC signature is invalid");
        }
        return new VerifiedContext(tenantId, operatorId, operatorType, skillId, runId, capabilityId);
    }

    private static void evictExpiredNonces(Instant now) {
        Instant cutoff = now.minus(maxClockSkew.multipliedBy(2));
        NONCES.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private static String canonical(String capabilityId, long tenantId, long operatorId, int operatorType,
                                    String skillId, String runId, String timestamp, String nonce) {
        return String.join("\n", capabilityId, Long.toString(tenantId), Long.toString(operatorId),
                Integer.toString(operatorType), skillId, runId, timestamp, nonce);
    }

    private static String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sharedSecret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot calculate RPC signature", ex);
        }
    }

    private static String required(Invocation invocation, String key) {
        Object value = invocation.getObjectAttachment(key);
        if (value == null || value.toString().isBlank()) {
            throw new SecurityException("Missing RPC attachment: " + key);
        }
        return value.toString();
    }

    private static long positiveLong(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new SecurityException("Invalid RPC attachment: " + field, ex);
        }
    }

    private static void requireConfigured() {
        if (sharedSecret == null) {
            throw new IllegalStateException("CloudMold RPC security has not been configured");
        }
    }

    static void resetForTest() {
        NONCES.clear();
        sharedSecret = null;
        clock = Clock.systemUTC();
    }

    public record VerifiedContext(long tenantId, long operatorId, int operatorType, String skillId,
                                  String runId, String capabilityId) {
    }
}
