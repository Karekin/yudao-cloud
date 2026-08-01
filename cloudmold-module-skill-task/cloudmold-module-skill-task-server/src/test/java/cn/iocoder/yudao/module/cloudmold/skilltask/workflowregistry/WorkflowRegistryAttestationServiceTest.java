package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRegistryAttestationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void signsAttestationWithPythonCompatibleCanonicalVector() throws Exception {
        WorkflowRegistryAttestationService service = new WorkflowRegistryAttestationService(
                objectMapper,
                "registry-secret",
                Clock.fixed(Instant.ofEpochSecond(1780000000L), ZoneOffset.UTC));

        WorkflowRegistryAttestationView attestation = service.attest(
                "skill.cloudmold.inventory.stockout-diagnosis.v1",
                "42",
                "1.0.0",
                objectMapper.readTree("""
                        {
                          "schema_version": "cloudmold.skill-task-definition/v1",
                          "skill_id": "skill.cloudmold.inventory.stockout-diagnosis.v1",
                          "skill_version": "1.0.0",
                          "risk_level": "R1",
                          "steps": [
                            {
                              "step_code": "diagnose",
                              "step_order": 1,
                              "capability_id": "capability.cloudmold.inventory.query.v1",
                              "operation_type": "READ",
                              "arguments": ["$input.skuId"]
                            }
                          ]
                        }
                        """));

        assertThat(attestation.definitionSha256())
                .isEqualTo("aa887b233076243c8b10990fb247f2e5ea21e71ce53b1472b4936e3dc1a6808a");
        assertThat(attestation.signature())
                .isEqualTo("14cdf8650a0bec47a2db93111d40a78ea7441970f00718e956d74748feeab850");
    }

    @Test
    void failsClosedWhenAttestationSecretIsMissing() throws Exception {
        WorkflowRegistryAttestationService service = new WorkflowRegistryAttestationService(
                objectMapper,
                "   ",
                Clock.fixed(Instant.ofEpochSecond(1780000000L), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.attest(
                "skill.test",
                "42",
                "1.0.0",
                objectMapper.readTree("{\"skill_id\":\"skill.test\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow registry attestation secret is required");
    }

    @Test
    void hashesDefinitionsWithTrailingNewlineCanonicalization() throws Exception {
        WorkflowRegistryAttestationService service = new WorkflowRegistryAttestationService(
                objectMapper,
                "registry-secret",
                Clock.fixed(Instant.ofEpochSecond(1780000000L), ZoneOffset.UTC));

        String hash = service.sha256(objectMapper.readTree("""
                {"b":1,"a":{"z":2,"y":[3,{"x":4}]}}
                """));

        assertThat(hash).isEqualTo("06bf1700c350755fe81eac961d5687aeb1fc6a02041ca620af272b9a47a9f703");
    }
}
