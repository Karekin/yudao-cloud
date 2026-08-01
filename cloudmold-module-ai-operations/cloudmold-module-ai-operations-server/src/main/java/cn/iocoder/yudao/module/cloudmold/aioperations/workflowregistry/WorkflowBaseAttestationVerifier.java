package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

@Component
public class WorkflowBaseAttestationVerifier {

    private static final String ISSUER = "cloudmold-workflow-registry";
    private static final String ALGORITHM = "HmacSHA256";
    private static final long MAX_AGE_SECONDS = 15 * 60;
    private static final long MAX_FUTURE_SKEW_SECONDS = 5 * 60;

    private final ObjectMapper objectMapper;
    private final String secret;
    private final Clock clock;

    @Autowired
    public WorkflowBaseAttestationVerifier(
            ObjectMapper objectMapper,
            @Value("${cloudmold.workflow-registry.attestation-secret:${CLOUDMOLD_WORKFLOW_ATTESTATION_SECRET:}}")
            String secret) {
        this(objectMapper, secret, Clock.systemUTC());
    }

    WorkflowBaseAttestationVerifier(ObjectMapper objectMapper, String secret, Clock clock) {
        this.objectMapper = objectMapper;
        this.secret = secret == null ? "" : secret.trim();
        this.clock = clock;
    }

    public void verifyFresh(JsonNode attestation, JsonNode baseDefinition, String workflowId, String ownerUserId) {
        if (secret.isBlank()) {
            throw new IllegalStateException("Workflow registry attestation secret is required");
        }
        if (attestation == null || !attestation.isObject()) {
            throw new IllegalArgumentException("base_attestation must be an object");
        }
        long issuedAt = attestation.path("issued_at").asLong(Long.MIN_VALUE);
        String baseVersion = baseDefinition.path("skill_version").asText();
        String baseSha256 = WorkflowProposalRegistryService.sha256Hex(canonical(baseDefinition));
        requireEquals(ISSUER, attestation.path("issuer").asText(), "issuer");
        requireEquals(workflowId, attestation.path("workflow_id").asText(), "workflow_id");
        requireEquals(ownerUserId, attestation.path("owner_user_id").asText(), "owner_user_id");
        requireEquals(baseVersion, attestation.path("version_id").asText(), "version_id");
        requireEquals(baseSha256, attestation.path("definition_sha256").asText(), "definition_sha256");
        long now = clock.instant().getEpochSecond();
        if (issuedAt == Long.MIN_VALUE || issuedAt > now + MAX_FUTURE_SKEW_SECONDS
                || issuedAt < now - MAX_AGE_SECONDS) {
            throw new IllegalArgumentException("base_attestation is stale or has an invalid issue time");
        }

        ObjectNode material = objectMapper.createObjectNode();
        material.put("issuer", ISSUER);
        material.put("workflow_id", workflowId);
        material.put("owner_user_id", ownerUserId);
        material.put("version_id", baseVersion);
        material.put("definition_sha256", baseSha256);
        material.put("issued_at", issuedAt);
        String supplied = attestation.path("signature").asText();
        byte[] suppliedBytes;
        try {
            suppliedBytes = HexFormat.of().parseHex(supplied);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("base_attestation signature is invalid", exception);
        }
        byte[] expected = hmac(canonical(material).getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(suppliedBytes, expected)) {
            throw new IllegalArgumentException("base_attestation signature is invalid");
        }
    }

    private String canonical(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(sort(node)) + "\n";
        } catch (Exception exception) {
            throw new IllegalArgumentException("Workflow attestation JSON cannot be canonicalized", exception);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            iterator.forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, sort(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sort(value)));
            return sorted;
        }
        return node;
    }

    private byte[] hmac(byte[] material) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(material);
        } catch (Exception exception) {
            throw new IllegalStateException("Workflow registry attestation verification is unavailable", exception);
        }
    }

    private void requireEquals(String expected, String actual, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("base_attestation " + field + " does not match the authoritative base");
        }
    }
}
