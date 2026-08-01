package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class WorkflowRegistryAttestationService {

    static final String ISSUER = "cloudmold-workflow-registry";
    private static final String ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String attestationSecret;
    private final Clock clock;

    @Autowired
    public WorkflowRegistryAttestationService(
            ObjectMapper objectMapper,
            @Value("${cloudmold.workflow-registry.attestation-secret:${CLOUDMOLD_WORKFLOW_ATTESTATION_SECRET:}}")
            String attestationSecret) {
        this(objectMapper, attestationSecret, Clock.systemUTC());
    }

    WorkflowRegistryAttestationService(ObjectMapper objectMapper, String attestationSecret, Clock clock) {
        this.objectMapper = objectMapper;
        this.attestationSecret = attestationSecret == null ? "" : attestationSecret.trim();
        this.clock = clock;
    }

    public WorkflowRegistryAttestationView attest(
            String workflowId,
            String ownerUserId,
            String versionId,
            JsonNode definition) {
        String secret = requireSecret();
        String definitionSha256 = sha256(definition);
        long issuedAt = clock.instant().getEpochSecond();
        ObjectNode material = objectMapper.createObjectNode();
        material.put("issuer", ISSUER);
        material.put("workflow_id", workflowId);
        material.put("owner_user_id", ownerUserId);
        material.put("version_id", versionId);
        material.put("definition_sha256", definitionSha256);
        material.put("issued_at", issuedAt);
        return new WorkflowRegistryAttestationView(
                ISSUER,
                workflowId,
                ownerUserId,
                versionId,
                definitionSha256,
                issuedAt,
                hmacSha256(secret, canonicalBytes(material)));
    }

    String sha256(JsonNode value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes(value)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String hmacSha256(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Workflow registry attestation signing is unavailable", exception);
        }
    }

    private String requireSecret() {
        if (attestationSecret.isBlank()) {
            throw new IllegalStateException(
                    "Workflow registry attestation secret is required via cloudmold.workflow-registry.attestation-secret "
                            + "or CLOUDMOLD_WORKFLOW_ATTESTATION_SECRET");
        }
        return attestationSecret;
    }

    private byte[] canonicalBytes(JsonNode value) {
        try {
            return (objectMapper.writeValueAsString(sort(value)) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize workflow registry payload", exception);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sort(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value.deepCopy();
    }
}
