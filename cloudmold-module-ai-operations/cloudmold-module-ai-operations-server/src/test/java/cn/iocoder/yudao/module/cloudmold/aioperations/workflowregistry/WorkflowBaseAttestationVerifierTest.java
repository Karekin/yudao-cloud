package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowBaseAttestationVerifierTest {

    private static final String SECRET = "registry-secret";
    private static final Instant NOW = Instant.ofEpochSecond(1_785_580_000L);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowBaseAttestationVerifier verifier = new WorkflowBaseAttestationVerifier(
            objectMapper, SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsFreshServerIssuedProofAndRejectsTampering() throws Exception {
        ObjectNode base = objectMapper.createObjectNode()
                .put("schema_version", "cloudmold.skill-task-definition/v1")
                .put("skill_id", "skill.test")
                .put("skill_version", "1.0.0");
        ObjectNode proof = proof(base, NOW.getEpochSecond());

        assertThatCode(() -> verifier.verifyFresh(proof, base, "skill.test", "101"))
                .doesNotThrowAnyException();

        proof.put("owner_user_id", "attacker");
        assertThatThrownBy(() -> verifier.verifyFresh(proof, base, "skill.test", "101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner_user_id");
    }

    @Test
    void rejectsStaleAndForgedProofs() throws Exception {
        ObjectNode base = objectMapper.createObjectNode()
                .put("skill_id", "skill.test")
                .put("skill_version", "1.0.0");
        ObjectNode stale = proof(base, NOW.minusSeconds(901).getEpochSecond());
        assertThatThrownBy(() -> verifier.verifyFresh(stale, base, "skill.test", "101"))
                .hasMessageContaining("stale");

        ObjectNode forged = proof(base, NOW.getEpochSecond());
        forged.put("signature", "00".repeat(32));
        assertThatThrownBy(() -> verifier.verifyFresh(forged, base, "skill.test", "101"))
                .hasMessageContaining("signature");
    }

    private ObjectNode proof(ObjectNode base, long issuedAt) throws Exception {
        String baseSha = WorkflowProposalRegistryService.sha256Hex(canonical(base));
        ObjectNode material = objectMapper.createObjectNode()
                .put("issuer", "cloudmold-workflow-registry")
                .put("workflow_id", "skill.test")
                .put("owner_user_id", "101")
                .put("version_id", "1.0.0")
                .put("definition_sha256", baseSha)
                .put("issued_at", issuedAt);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        material.put("signature", HexFormat.of().formatHex(mac.doFinal(canonical(material)
                .getBytes(StandardCharsets.UTF_8))));
        return material;
    }

    private String canonical(ObjectNode node) throws Exception {
        var sorted = objectMapper.createObjectNode();
        node.fieldNames().forEachRemaining(name -> { });
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.stream().sorted().forEach(name -> sorted.set(name, node.get(name)));
        return objectMapper.writeValueAsString(sorted) + "\n";
    }
}
