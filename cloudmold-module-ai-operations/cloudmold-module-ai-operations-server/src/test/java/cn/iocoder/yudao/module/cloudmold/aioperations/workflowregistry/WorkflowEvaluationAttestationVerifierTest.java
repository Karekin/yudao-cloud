package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryValidationRequestDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryEvaluationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEvaluationAttestationVerifierTest {

    private static final String SECRET = "independent-validator-secret";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");
    private final WorkflowEvaluationAttestationVerifier verifier = new WorkflowEvaluationAttestationVerifier(
            objectMapper, SECRET, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void acceptsOnlySignatureBoundToAuthoritativeRequestAndCompleteResult() throws Exception {
        WorkflowRegistryValidationRequestDO validation = validation();
        WorkflowRegistryEvaluationRequest request = evaluation();
        long issuedAt = now.getEpochSecond();
        ObjectNode result = verifier.evaluationMaterial(162L, validation, request);
        String resultSha = WorkflowProposalRegistryService.sha256Hex(verifier.canonical(result));
        ObjectNode signed = objectMapper.createObjectNode()
                .put("issuer", WorkflowEvaluationAttestationVerifier.ISSUER)
                .put("validation_request_id", validation.getValidationRequestId())
                .put("challenge", validation.getChallenge())
                .put("result_sha256", resultSha)
                .put("issued_at", issuedAt);
        request.setValidatorAttestation(signed.deepCopy()
                .put("signature", hmac(verifier.canonical(signed))));

        assertThatCode(() -> verifier.verifyFresh(162L, validation, request)).doesNotThrowAnyException();

        request.setSampleCount(101);
        assertThatThrownBy(() -> verifier.verifyFresh(162L, validation, request))
                .hasMessageContaining("result_sha256");
    }

    private WorkflowRegistryValidationRequestDO validation() {
        return new WorkflowRegistryValidationRequestDO()
                .setValidationRequestId("wrq-1")
                .setTenantId(162L)
                .setSkillId("skill.example")
                .setRegistryVersionId("candidate-1")
                .setPointerVersion(3L)
                .setChallenge("challenge-1");
    }

    private WorkflowRegistryEvaluationRequest evaluation() {
        WorkflowRegistryEvaluationRequest request = new WorkflowRegistryEvaluationRequest();
        request.setWorkflowId("skill.example");
        request.setCandidateVersionId("candidate-1");
        request.setValidationRequestId("wrq-1");
        request.setExpectedPointerVersion(3L);
        request.setEvaluatorRunId("validator-run-1");
        request.setDatasetSha256("d".repeat(64));
        request.setSampleCount(100);
        request.setObservationStartedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        request.setObservationEndedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        request.setReplayPassed(true);
        request.setShadowPassed(true);
        request.setCanaryPassed(true);
        request.setGuardrailsPassed(true);
        request.setSamplePassed(true);
        request.setDqcPassed(true);
        request.setObservationWindowPassed(true);
        request.setEvidenceRefs(objectMapper.createArrayNode().add("cloudmold://validation/run-1"));
        request.setMetrics(objectMapper.createObjectNode().put("success", 100));
        request.setFailureSamples(objectMapper.createArrayNode());
        return request;
    }

    private String hmac(String material) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
    }
}
