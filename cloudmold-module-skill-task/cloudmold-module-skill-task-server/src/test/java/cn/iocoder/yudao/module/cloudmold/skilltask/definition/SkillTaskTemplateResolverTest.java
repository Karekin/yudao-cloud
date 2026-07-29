package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTaskTemplateResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillTaskTemplateResolver resolver = new SkillTaskTemplateResolver(objectMapper);

    @Test
    void resolvesTaskInputAndPriorStepExpressionsWithoutStringInterpolation() throws Exception {
        JsonNode template = objectMapper.readTree("""
                ["$task.id","$task.stepIdempotencyKey","$input.order.id",
                 "$steps.lookup.result.skuId",{"literal":"prefix-$input.order.id"}]
                """);
        JsonNode input = objectMapper.readTree("{\"order\":{\"id\":42}}");
        JsonNode lookup = objectMapper.readTree("{\"skuId\":\"SKU-1\"}");

        JsonNode result = resolver.resolve(template, input, Map.of("lookup", lookup), "task-1", "task-1:write");

        assertThat(result).isEqualTo(objectMapper.readTree("""
                ["task-1","task-1:write",42,"SKU-1",{"literal":"prefix-$input.order.id"}]
                """));
    }

    @Test
    void rejectsUnavailableValuesBeforeCapabilityInvocation() throws Exception {
        JsonNode template = objectMapper.readTree("[\"$steps.missing.result.id\"]");

        assertThatThrownBy(() -> resolver.resolve(template, objectMapper.createObjectNode(), Map.of(),
                "task-1", "task-1:step"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void resolvesArraySegmentsAndCopiesApprovedObjectsWithBoundedOverrides() throws Exception {
        JsonNode template = objectMapper.readTree("""
                [{
                  "$object":"$input.commands.0",
                  "$overrides":{
                    "/items/0/listingId":"$steps.listing.result.listingId",
                    "idempotencyKey":"$task.stepIdempotencyKey",
                    "runId":"$task.runId"
                  }
                }]
                """);
        JsonNode input = objectMapper.readTree("""
                {"commands":[{"command":"PLACE","items":[{"listingId":"approved-placeholder"}],
                  "idempotencyKey":"approved-placeholder","runId":"approved-placeholder"}]}
                """);
        JsonNode listing = objectMapper.readTree("{\"listingId\":\"listing-1\"}");

        JsonNode result = resolver.resolveValue(template, input, Map.of("listing", listing),
                "task-1", "run-1", "task-1:place");

        assertThat(result).isEqualTo(objectMapper.readTree("""
                [{"command":"PLACE","items":[{"listingId":"listing-1"}],
                  "idempotencyKey":"task-1:place","runId":"run-1"}]
                """));
        assertThat(input.at("/commands/0/items/0/listingId").asText()).isEqualTo("approved-placeholder");
    }

    @Test
    void rejectsObjectOverridesThatWereNotDeclaredByTheApprovedBase() throws Exception {
        JsonNode template = objectMapper.readTree("""
                [{"$object":"$input.commands.0","$overrides":{"/items/0/newField":"value"}}]
                """);
        JsonNode input = objectMapper.readTree("{\"commands\":[{\"items\":[{}]}]}");

        assertThatThrownBy(() -> resolver.resolve(template, input, Map.of(), "task-1", "task-1:step"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path is missing");
    }

    @Test
    void resolvesTaskApprovalAsOpaqueDeterministicEvidenceWithoutLeakingPermit() throws Exception {
        JsonNode template = objectMapper.readTree("""
                [{"approvalRef":"$task.approvalEvidenceRef"}]
                """);
        String rawApprovalRef = "signed-agent-control-permit-sensitive";

        JsonNode result = resolver.resolveValue(template, objectMapper.createObjectNode(), Map.of(),
                "task-1", "run-1", "task-1:approve", rawApprovalRef);

        assertThat(result.at("/0/approvalRef").asText())
                .startsWith("agent-control://approval-ref/sha256/")
                .doesNotContain(rawApprovalRef);
        assertThat(result).isEqualTo(resolver.resolveValue(template, objectMapper.createObjectNode(),
                Map.of(), "task-1", "run-1", "task-1:approve", rawApprovalRef));
    }

    @Test
    void rejectsApprovalEvidenceTemplateWhenTaskHasNoApproval() throws Exception {
        JsonNode template = objectMapper.readTree("[\"$task.approvalEvidenceRef\"]");

        assertThatThrownBy(() -> resolver.resolveValue(template, objectMapper.createObjectNode(),
                Map.of(), "task-1", "run-1", "task-1:approve", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval evidence is unavailable");
    }
}
