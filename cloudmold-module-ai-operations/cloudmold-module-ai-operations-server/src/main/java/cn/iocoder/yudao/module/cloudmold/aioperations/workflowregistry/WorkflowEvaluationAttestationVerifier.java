package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryValidationRequestDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryEvaluationRequest;
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
public class WorkflowEvaluationAttestationVerifier {

    static final String ISSUER = "cloudmold-independent-workflow-validator";
    private static final String ALGORITHM = "HmacSHA256";
    private static final long MAX_AGE_SECONDS = 30 * 60;
    private static final long MAX_FUTURE_SKEW_SECONDS = 5 * 60;

    private final ObjectMapper objectMapper;
    private final String secret;
    private final Clock clock;

    @Autowired
    public WorkflowEvaluationAttestationVerifier(
            ObjectMapper objectMapper,
            @Value("${cloudmold.workflow-registry.validator-attestation-secret:${CLOUDMOLD_WORKFLOW_VALIDATOR_ATTESTATION_SECRET:}}")
            String secret) {
        this(objectMapper, secret, Clock.systemUTC());
    }

    WorkflowEvaluationAttestationVerifier(ObjectMapper objectMapper, String secret, Clock clock) {
        this.objectMapper = objectMapper;
        this.secret = secret == null ? "" : secret.trim();
        this.clock = clock;
    }

    public void verifyFresh(Long tenantId, WorkflowRegistryValidationRequestDO validation,
                            WorkflowRegistryEvaluationRequest request) {
        if (secret.isBlank()) {
            throw new IllegalStateException("Workflow validator attestation secret is required");
        }
        JsonNode attestation = request.getValidatorAttestation();
        if (attestation == null || !attestation.isObject()) {
            throw new IllegalArgumentException("validator_attestation must be an object");
        }
        long issuedAt = attestation.path("issued_at").asLong(Long.MIN_VALUE);
        long now = clock.instant().getEpochSecond();
        if (issuedAt == Long.MIN_VALUE || issuedAt > now + MAX_FUTURE_SKEW_SECONDS
                || issuedAt < now - MAX_AGE_SECONDS) {
            throw new IllegalArgumentException("validator_attestation is stale or has an invalid issue time");
        }

        ObjectNode result = evaluationMaterial(tenantId, validation, request);
        String resultSha256 = WorkflowProposalRegistryService.sha256Hex(canonical(result));
        requireEquals(ISSUER, attestation.path("issuer").asText(), "issuer");
        requireEquals(validation.getValidationRequestId(), attestation.path("validation_request_id").asText(),
                "validation_request_id");
        requireEquals(validation.getChallenge(), attestation.path("challenge").asText(), "challenge");
        requireEquals(resultSha256, attestation.path("result_sha256").asText(), "result_sha256");

        ObjectNode signed = objectMapper.createObjectNode();
        signed.put("issuer", ISSUER);
        signed.put("validation_request_id", validation.getValidationRequestId());
        signed.put("challenge", validation.getChallenge());
        signed.put("result_sha256", resultSha256);
        signed.put("issued_at", issuedAt);
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(attestation.path("signature").asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("validator_attestation signature is invalid", exception);
        }
        if (!MessageDigest.isEqual(supplied, hmac(canonical(signed).getBytes(StandardCharsets.UTF_8)))) {
            throw new IllegalArgumentException("validator_attestation signature is invalid");
        }
    }

    ObjectNode evaluationMaterial(Long tenantId, WorkflowRegistryValidationRequestDO validation,
                                  WorkflowRegistryEvaluationRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("tenant_id", tenantId);
        node.put("validation_request_id", validation.getValidationRequestId());
        node.put("challenge", validation.getChallenge());
        node.put("workflow_id", request.getWorkflowId());
        node.put("candidate_version_id", request.getCandidateVersionId());
        node.put("expected_pointer_version", request.getExpectedPointerVersion());
        node.put("evaluator_run_id", request.getEvaluatorRunId());
        node.put("dataset_sha256", request.getDatasetSha256());
        node.put("sample_count", request.getSampleCount());
        node.put("observation_started_at", request.getObservationStartedAt().toString());
        node.put("observation_ended_at", request.getObservationEndedAt().toString());
        node.put("replay_passed", request.isReplayPassed());
        node.put("shadow_passed", request.isShadowPassed());
        node.put("canary_passed", request.isCanaryPassed());
        node.put("guardrails_passed", request.isGuardrailsPassed());
        node.put("sample_passed", request.isSamplePassed());
        node.put("dqc_passed", request.isDqcPassed());
        node.put("observation_window_passed", request.isObservationWindowPassed());
        node.set("evidence_refs", request.getEvidenceRefs());
        node.set("metrics", request.getMetrics());
        node.set("failure_samples", request.getFailureSamples());
        return node;
    }

    String canonical(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(sort(node)) + "\n";
        } catch (Exception exception) {
            throw new IllegalArgumentException("Workflow validator attestation JSON cannot be canonicalized", exception);
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
            throw new IllegalStateException("Workflow validator attestation verification is unavailable", exception);
        }
    }

    private void requireEquals(String expected, String actual, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("validator_attestation " + field + " does not match the authoritative validation request");
        }
    }
}
