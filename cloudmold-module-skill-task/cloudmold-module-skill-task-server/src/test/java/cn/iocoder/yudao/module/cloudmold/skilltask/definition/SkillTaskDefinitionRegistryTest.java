package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import cn.iocoder.yudao.module.cloudmold.executor.CapabilityDescriptor;
import cn.iocoder.yudao.module.cloudmold.executor.CapabilityOperationType;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityCatalog;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void acceptsExplicitR3SubmitAndWaitComposition() throws Exception {
        when(catalog.require("cap.read")).thenReturn(descriptor("cap.read", CapabilityOperationType.READ, 1));
        SkillTaskDefinition child = readDefinition("skill.child", "R1");
        SkillTaskDefinition parent = compositeDefinition("skill.parent", "skill.child");

        assertThat(registry.validateAndIndex(List.of(parent, child)))
                .containsKeys("skill.parent@1.0.0", "skill.child@1.0.0");
        assertThat(parent.getSteps()).extracting(SkillTaskDefinition.Step::getStepKind)
                .containsExactly("SUBMIT_CHILD", "WAIT_CHILD");
    }

    @Test
    void rejectsCompositionCyclesBeforeAnyTaskCanBeSubmitted() throws Exception {
        SkillTaskDefinition first = compositeDefinition("skill.first", "skill.second");
        SkillTaskDefinition second = compositeDefinition("skill.second", "skill.first");

        assertThatThrownBy(() -> registry.validateAndIndex(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsMissingChildDefinition() throws Exception {
        SkillTaskDefinition parent = compositeDefinition("skill.parent", "skill.missing");

        assertThatThrownBy(() -> registry.validateAndIndex(List.of(parent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void acceptsBoundedReadOnlyCapabilityPolling() throws Exception {
        when(catalog.require("cap.read")).thenReturn(descriptor("cap.read", CapabilityOperationType.READ, 1));
        SkillTaskDefinition definition = SkillTaskDefinition.builder()
                .schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId("skill.poll").skillVersion("1.0.0").riskLevel("R3").maxAttempts(3)
                .steps(List.of(SkillTaskDefinition.Step.builder().stepKind("WAIT_CAPABILITY")
                        .stepCode("wait-saga").stepOrder(1).capabilityId("cap.read")
                        .arguments(objectMapper.readTree("[\"$input.id\"]"))
                        .waitSuccess(objectMapper.readTree("{\"/status\":\"SUCCEEDED\"}"))
                        .waitFailure(objectMapper.readTree("{\"/status\":[\"FAILED\",\"NEEDS_REVIEW\"]}"))
                        .pollIntervalSeconds(2).build())).build();

        assertThat(registry.validateAndIndex(List.of(definition))).containsKey("skill.poll@1.0.0");
        assertThat(definition.getSteps().get(0).getOperationType()).isEqualTo("READ");
    }

    @Test
    void rejectsWriteCapabilityPolling() throws Exception {
        when(catalog.require("cap.write")).thenReturn(descriptor("cap.write", CapabilityOperationType.WRITE, 1));
        SkillTaskDefinition definition = SkillTaskDefinition.builder()
                .schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId("skill.poll").skillVersion("1.0.0").riskLevel("R3").maxAttempts(3)
                .steps(List.of(SkillTaskDefinition.Step.builder().stepKind("WAIT_CAPABILITY")
                        .stepCode("wait-write").stepOrder(1).capabilityId("cap.write")
                        .arguments(objectMapper.readTree("[\"$input.id\"]"))
                        .waitSuccess(objectMapper.readTree("{\"/status\":\"SUCCEEDED\"}"))
                        .build())).build();

        assertThatThrownBy(() -> registry.validateAndIndex(List.of(definition)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("READ capability");
    }

    @Test
    void loadsWorkspaceFullChainCompositionAgainstTheRealCapabilityCatalog() {
        Path skillRoot = findWorkspaceSkillRoot();
        Assumptions.assumeTrue(skillRoot != null,
                "Sibling useful-scripts registry is not available in this checkout");
        SkillTaskProperties workspaceProperties = new SkillTaskProperties();
        workspaceProperties.setRegistryRoot(skillRoot);
        workspaceProperties.setFailOnEmptyRegistry(true);
        SkillTaskDefinitionRegistry workspaceRegistry = new SkillTaskDefinitionRegistry(
                objectMapper, workspaceProperties,
                new CloudMoldCapabilityCatalog(new CloudMoldRpcProperties()));

        workspaceRegistry.reload();

        SkillTaskDefinition definition = workspaceRegistry.require(
                "skill.cloudmold.commerce.full-chain-hsf.v1", "1.2.1");
        assertThat(definition.getRiskLevel()).isEqualTo("R3");
        assertThat(definition.getSteps()).hasSize(10);
        assertThat(workspaceRegistry.all()).extracting(SkillTaskDefinition::getSkillId)
                .contains("skill.cloudmold.commerce.catalog-matrix.v1",
                        "skill.cloudmold.commerce.aftersale-saga.v1",
                        "skill.cloudmold.commerce.terminal-readback.v1");
    }

    private static Path findWorkspaceSkillRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (Path candidate : List.of(current.resolve("useful-scripts/skills"),
                    current.resolveSibling("useful-scripts").resolve("skills"))) {
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        return null;
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

    private SkillTaskDefinition readDefinition(String skillId, String risk) throws Exception {
        return SkillTaskDefinition.builder().schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId(skillId).skillVersion("1.0.0").riskLevel(risk).maxAttempts(3)
                .steps(List.of(SkillTaskDefinition.Step.builder().stepCode("read").stepOrder(1)
                        .capabilityId("cap.read").operationType("READ")
                        .arguments(objectMapper.readTree("[\"$input.id\"]")).build())).build();
    }

    private SkillTaskDefinition compositeDefinition(String skillId, String childSkillId) throws Exception {
        return SkillTaskDefinition.builder().schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId(skillId).skillVersion("1.0.0").riskLevel("R3").maxAttempts(3)
                .steps(List.of(
                        SkillTaskDefinition.Step.builder().stepKind("SUBMIT_CHILD")
                                .stepCode("submit").stepOrder(1).childSkillId(childSkillId)
                                .childSkillVersion("1.0.0").arguments(objectMapper.readTree("{\"id\":\"$input.id\"}"))
                                .build(),
                        SkillTaskDefinition.Step.builder().stepKind("WAIT_CHILD")
                                .stepCode("wait").stepOrder(2).pollIntervalSeconds(2)
                                .arguments(objectMapper.readTree("[\"$steps.submit.result.childTaskId\"]"))
                                .build())).build();
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
