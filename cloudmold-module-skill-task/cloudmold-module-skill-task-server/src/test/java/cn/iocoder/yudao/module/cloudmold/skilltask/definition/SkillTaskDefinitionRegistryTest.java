package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityDescriptor;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityOperationType;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillTaskDefinitionRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillTaskProperties properties = new SkillTaskProperties();
    private final CloudMoldCapabilityCatalog catalog = mock(CloudMoldCapabilityCatalog.class);
    private final SkillTaskDefinitionRegistry registry = new SkillTaskDefinitionRegistry(objectMapper, properties, catalog);

    @Test
    void acceptsApprovedWriteWithStableStepIdempotencyBinding() throws Exception {
        when(catalog.require("cap.write")).thenReturn(descriptor("cap.write", CapabilityOperationType.WRITE, 2));
        SkillTaskDefinition definition = definition("R2", """
                ["$input.merchantId","$task.stepIdempotencyKey"]
                """, true);

        assertThat(registry.validateAndIndex(List.of(definition))).containsKey("skill.test@1.0.0");
    }

    @Test
    void acceptsAnExactNestedCommandIdempotencyBinding() throws Exception {
        when(catalog.require("cap.write")).thenReturn(descriptor("cap.write", CapabilityOperationType.WRITE, 1));
        SkillTaskDefinition definition = definition("R2", """
                [{"merchantId":"$input.merchantId","idempotencyKey":"$task.stepIdempotencyKey"}]
                """, true, binding(0, "/idempotencyKey"));

        assertThat(registry.validateAndIndex(List.of(definition))).containsKey("skill.test@1.0.0");
    }

    @Test
    void rejectsWriteWithoutIdempotencyBinding() throws Exception {
        when(catalog.require("cap.write")).thenReturn(descriptor("cap.write", CapabilityOperationType.WRITE, 1));
        SkillTaskDefinition definition = definition("R2", "[\"$input.merchantId\"]", true, null);

        assertThatThrownBy(() -> registry.validateAndIndex(List.of(definition)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency_binding");
    }

    @Test
    void rejectsDefinitionArgumentCountDrift() throws Exception {
        when(catalog.require("cap.write")).thenReturn(descriptor("cap.write", CapabilityOperationType.WRITE, 3));
        SkillTaskDefinition definition = definition("R2", """
                ["$input.merchantId","$task.stepIdempotencyKey"]
                """, true, binding(1, ""));

        assertThatThrownBy(() -> registry.validateAndIndex(List.of(definition)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argument count");
    }

    @Test
    void rejectsRecursiveSkillTaskOrchestrationSteps() throws Exception {
        when(catalog.require("cap.write")).thenReturn(descriptor("cap.write", CapabilityOperationType.WRITE, 2,
                "cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi"));
        SkillTaskDefinition definition = definition("R2", """
                ["$input.merchantId","$task.stepIdempotencyKey"]
                """, true, binding(1, ""));

        assertThatThrownBy(() -> registry.validateAndIndex(List.of(definition)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be nested");
    }

    private SkillTaskDefinition definition(String risk, String arguments, boolean approval) throws Exception {
        return definition(risk, arguments, approval, binding(1, ""));
    }

    private SkillTaskDefinition definition(String risk, String arguments, boolean approval,
                                           SkillTaskDefinition.IdempotencyBinding binding) throws Exception {
        return SkillTaskDefinition.builder()
                .schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId("skill.test").skillVersion("1.0.0").riskLevel(risk).maxAttempts(3)
                .steps(List.of(SkillTaskDefinition.Step.builder()
                        .stepCode("write").stepOrder(1).capabilityId("cap.write")
                        .operationType("WRITE").approvalRequired(approval)
                        .idempotencyBinding(binding)
                        .arguments(objectMapper.readTree(arguments)).build()))
                .build();
    }

    private static SkillTaskDefinition.IdempotencyBinding binding(int argumentIndex, String jsonPointer) {
        return SkillTaskDefinition.IdempotencyBinding.builder()
                .argumentIndex(argumentIndex).jsonPointer(jsonPointer).build();
    }

    private static CapabilityDescriptor descriptor(String id, CapabilityOperationType operation, int parameters) {
        return descriptor(id, operation, parameters, "TestApi");
    }

    private static CapabilityDescriptor descriptor(String id, CapabilityOperationType operation, int parameters,
                                                   String interfaceName) {
        return new CapabilityDescriptor(id, interfaceName, "invoke",
                java.util.Collections.nCopies(parameters, "java.lang.String"), "void", operation,
                "cloudmold-internal", "1.0.0", 5_000, null);
    }
}
