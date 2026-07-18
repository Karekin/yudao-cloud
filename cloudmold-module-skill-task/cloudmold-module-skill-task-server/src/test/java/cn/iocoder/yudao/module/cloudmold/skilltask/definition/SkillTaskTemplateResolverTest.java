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
}
