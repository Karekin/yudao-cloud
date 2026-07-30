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
                "skill.cloudmold.commerce.full-chain-hsf.v1", "1.3.0");
        SkillTaskDefinition cancellation = workspaceRegistry.require(
                "skill.cloudmold.commerce.order-cancellation-operational.v1", "1.1.0");
        assertThat(definition.getRiskLevel()).isEqualTo("R3");
        assertThat(definition.getSteps()).hasSize(8);
        assertThat(cancellation.getRiskLevel()).isEqualTo("R3");
        assertThat(cancellation.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(cancellation.getOwnerRole()).isEqualTo("order-exception-operator");
        assertThat(cancellation.getSteps()).hasSize(10);
        assertThat(cancellation.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(5);
        assertThat(cancellation.getSteps()).extracting(SkillTaskDefinition.Step::getStepKind)
                .containsExactly("SUBMIT_CHILD", "WAIT_CHILD", "CAPABILITY", "CAPABILITY",
                        "CAPABILITY", "CAPABILITY", "WAIT_CAPABILITY", "CAPABILITY",
                        "WAIT_CAPABILITY", "WAIT_CAPABILITY");
        SkillTaskDefinition paidUnshippedOrder = workspaceRegistry.require(
                "skill.cloudmold.consumer.paid-unshipped-order-scenario.v1", "1.0.0");
        assertThat(paidUnshippedOrder.getWorkflowLevel()).isEqualTo("INTERNAL_SUBFLOW");
        assertThat(paidUnshippedOrder.getSteps()).hasSize(9);
        assertThat(paidUnshippedOrder.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(7);
        SkillTaskDefinition financeClose = workspaceRegistry.require(
                "skill.cloudmold.finance.close-readiness.v1", "1.0.0");
        assertThat(financeClose.getSteps()).singleElement().satisfies(step -> {
            assertThat(step.getCapabilityId())
                    .isEqualTo("capability.cloudmold.finance.finance-close-query.require-period.v1");
            assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("CLOSED");
        });
        SkillTaskDefinition financeLifecycle = workspaceRegistry.require(
                "skill.cloudmold.finance.close-lifecycle.v1", "1.0.0");
        assertThat(financeLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(financeLifecycle.getOwnerRole()).isEqualTo("finance-operations");
        assertThat(financeLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(financeLifecycle.getSteps()).hasSize(12);
        assertThat(financeLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(11);
        assertThat(financeLifecycle.getSteps().get(financeLifecycle.getSteps().size() - 1).getWaitSuccess()
                .path("/status").asText()).isEqualTo("CLOSED");
        SkillTaskDefinition qualityLifecycle = workspaceRegistry.require(
                "skill.cloudmold.quality.inspection-recall-lifecycle.v1", "1.0.0");
        assertThat(qualityLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(qualityLifecycle.getOwnerRole()).isEqualTo("quality-operations");
        assertThat(qualityLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(qualityLifecycle.getSteps()).hasSize(21);
        assertThat(qualityLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(20);
        assertThat(qualityLifecycle.getSteps().get(qualityLifecycle.getSteps().size() - 1))
                .satisfies(step -> {
                    assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
                    assertThat(step.getCapabilityId()).isEqualTo(
                            "capability.cloudmold.quality.quality-recall-workflow-query.inspect.v1");
                    assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("SUCCEEDED");
                });
        SkillTaskDefinition inTransitScenario = workspaceRegistry.require(
                "skill.cloudmold.consumer.in-transit-order-scenario.v1", "1.0.0");
        assertThat(inTransitScenario.getWorkflowLevel()).isEqualTo("INTERNAL_SUBFLOW");
        assertThat(inTransitScenario.getRiskLevel()).isEqualTo("R3");
        assertThat(inTransitScenario.getSteps()).hasSize(23);
        SkillTaskDefinition fulfillmentExceptionLifecycle = workspaceRegistry.require(
                "skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1", "1.0.0");
        assertThat(fulfillmentExceptionLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(fulfillmentExceptionLifecycle.getOwnerRole()).isEqualTo("logistics-operations");
        assertThat(fulfillmentExceptionLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(fulfillmentExceptionLifecycle.getSteps()).hasSize(12);
        assertThat(fulfillmentExceptionLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(8);
        assertThat(fulfillmentExceptionLifecycle.getSteps().get(10)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("CLOSED");
        });
        SkillTaskDefinition sopLifecycle = workspaceRegistry.require(
                "skill.cloudmold.supply-planning.sop-lifecycle.v1", "1.0.0");
        assertThat(sopLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(sopLifecycle.getOwnerRole()).isEqualTo("supply-planning-manager");
        assertThat(sopLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(sopLifecycle.getSteps()).hasSize(14);
        assertThat(sopLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(13);
        assertThat(sopLifecycle.getSteps().get(13)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getCapabilityId()).isEqualTo(
                    "capability.cloudmold.supplyplanning.supply-planning-query.require-replenishment-business-stage.v1");
            assertThat(step.getWaitSuccess().path("/recommendationStatus").asText())
                    .isEqualTo("CONVERTED");
            assertThat(step.getWaitSuccess().path("/projectionDocumentStatus").asText())
                    .isEqualTo("PREPARE");
        });
        SkillTaskDefinition crossborderLifecycle = workspaceRegistry.require(
                "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1", "1.0.0");
        assertThat(crossborderLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(crossborderLifecycle.getOwnerRole()).isEqualTo("crossborder-operations");
        assertThat(crossborderLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(crossborderLifecycle.getSteps()).hasSize(16);
        assertThat(crossborderLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(13);
        assertThat(crossborderLifecycle.getSteps().get(15)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getCapabilityId()).isEqualTo(
                    "capability.cloudmold.crossborder.cross-border-query.get.v1");
            assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("CLOSED");
            assertThat(step.getWaitSuccess().path("/customsStatus").asText())
                    .isEqualTo("RELEASED");
            assertThat(step.getWaitSuccess().path("/deliveryStatus").asText())
                    .isEqualTo("DELIVERED");
        });
        SkillTaskDefinition bondedCustomsLifecycle = workspaceRegistry.require(
                "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1", "1.0.0");
        assertThat(bondedCustomsLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(bondedCustomsLifecycle.getOwnerRole()).isEqualTo("bonded-customs-operations");
        assertThat(bondedCustomsLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(bondedCustomsLifecycle.getSteps()).hasSize(14);
        assertThat(bondedCustomsLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(11)
                .allSatisfy(step -> assertThat(step.getCapabilityId()).isEqualTo(
                        "capability.cloudmold.crossborder.bonded-customs-command.execute.v1"));
        assertThat(bondedCustomsLifecycle.getSteps().get(13)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getCapabilityId()).isEqualTo(
                    "capability.cloudmold.crossborder.bonded-customs-query.get.v1");
            assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("CLOSED");
            assertThat(step.getWaitSuccess().path("/tripleMatchStatus").asText())
                    .isEqualTo("TRIPLE_MATCHED");
            assertThat(step.getWaitSuccess().path("/customsStatus").asText())
                    .isEqualTo("CUSTOMS_ACCEPTED");
            assertThat(step.getWaitSuccess().path("/bondedReleaseStatus").asText())
                    .isEqualTo("BONDED_RELEASED");
            assertThat(step.getWaitSuccess().path("/deliveryStatus").asText())
                    .isEqualTo("DELIVERED");
        });
        SkillTaskDefinition partnerMarketingLifecycle = workspaceRegistry.require(
                "skill.cloudmold.partner-marketing.kol-media-operations.v1", "1.0.0");
        assertThat(partnerMarketingLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(partnerMarketingLifecycle.getOwnerRole()).isEqualTo("partner-marketing-operations");
        assertThat(partnerMarketingLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(partnerMarketingLifecycle.getSteps()).hasSize(22);
        assertThat(partnerMarketingLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(15);
        assertThat(partnerMarketingLifecycle.getSteps())
                .filteredOn(step -> "SUBMIT_CHILD".equals(step.getStepKind()))
                .hasSize(3);
        assertThat(partnerMarketingLifecycle.getSteps())
                .filteredOn(step -> "WAIT_CHILD".equals(step.getStepKind()))
                .hasSize(3);
        assertThat(partnerMarketingLifecycle.getSteps().get(21)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getCapabilityId()).isEqualTo(
                    "capability.cloudmold.partnermarketing.partner-marketing-query.get-workflow.v1");
            assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("SUCCEEDED");
            assertThat(step.getWaitSuccess().path("/currentStatus").asText()).isEqualTo("CLOSED");
            assertThat(step.getWaitSuccess().path("/terminal").asBoolean()).isTrue();
            assertThat(step.getWaitSuccess().path("/aggregateVersion").asInt()).isEqualTo(12);
        });
        SkillTaskDefinition productionReadiness = workspaceRegistry.require(
                "skill.cloudmold.mes.production-readiness.v1", "1.0.0");
        assertThat(productionReadiness.getWorkflowLevel()).isEqualTo("INTERNAL_SUBFLOW");
        assertThat(productionReadiness.getRiskLevel()).isEqualTo("R2");
        assertThat(productionReadiness.getSteps()).hasSize(9)
                .allSatisfy(step -> assertThat(step.getOperationType()).isEqualTo("WRITE"));
        SkillTaskDefinition productionLifecycle = workspaceRegistry.require(
                "skill.cloudmold.mes.production-execution-lifecycle.v1", "1.0.0");
        assertThat(productionLifecycle.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(productionLifecycle.getOwnerRole()).isEqualTo("production-supervisor");
        assertThat(productionLifecycle.getRiskLevel()).isEqualTo("R3");
        assertThat(productionLifecycle.getSteps()).hasSize(10);
        assertThat(productionLifecycle.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(7);
        assertThat(productionLifecycle.getSteps().get(9)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getCapabilityId()).isEqualTo(
                    "capability.cloudmold.integration.yudao-legacy-operations-query.get-mes-production-execution.v1");
            assertThat(step.getWaitSuccess().path("/closedLoop").asBoolean()).isTrue();
        });
        SkillTaskDefinition productToListing = workspaceRegistry.require(
                "skill.cloudmold.commerce.product-to-listing.v1", "1.1.0");
        assertThat(productToListing.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(productToListing.getOwnerRole()).isEqualTo("product-listing-operator");
        SkillTaskDefinition categoryDailyOperations = workspaceRegistry.require(
                "skill.cloudmold.commerce.category-daily-operations.v1", "1.0.0");
        assertThat(categoryDailyOperations.getWorkflowLevel()).isEqualTo("BUSINESS_ROLE");
        assertThat(categoryDailyOperations.getOwnerRole()).isEqualTo("category-operations");
        assertThat(categoryDailyOperations.getRiskLevel()).isEqualTo("R3");
        assertThat(categoryDailyOperations.getSteps()).hasSize(13);
        assertThat(categoryDailyOperations.getSteps())
                .filteredOn(step -> "WRITE".equals(step.getOperationType()))
                .hasSize(4);
        assertThat(categoryDailyOperations.getSteps())
                .filteredOn(step -> "SUBMIT_CHILD".equals(step.getStepKind()))
                .hasSize(4);
        assertThat(categoryDailyOperations.getSteps().get(12)).satisfies(step -> {
            assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
            assertThat(step.getWaitSuccess().path("/status").asText()).isEqualTo("RESOLVED");
            assertThat(step.getWaitSuccess().path("/aggregateVersion").asInt()).isEqualTo(4);
        });
        assertThat(workspaceRegistry.all()).extracting(SkillTaskDefinition::getSkillId)
                .contains("skill.cloudmold.commerce.catalog-matrix.v1",
                        "skill.cloudmold.commerce.order-cancellation-operational.v1",
                        "skill.cloudmold.commerce.product-to-listing.v1",
                        "skill.cloudmold.commerce.category-daily-operations.v1",
                        "skill.cloudmold.commerce.aftersale-saga.v1",
                        "skill.cloudmold.supply-planning.sop-lifecycle.v1",
                        "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
                        "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
                        "skill.cloudmold.partner-marketing.kol-media-operations.v1",
                        "skill.cloudmold.mes.production-readiness.v1",
                        "skill.cloudmold.mes.production-execution-lifecycle.v1",
                        "skill.cloudmold.commerce.terminal-readback.v1");
        List<String> readbackSkillIds = List.of(
                "skill.cloudmold.operations.daily-business-control.v1",
                "skill.cloudmold.operations.weekly-business-review.v1",
                "skill.cloudmold.merchant.onboarding-readback.v1",
                "skill.cloudmold.engagement.promotion-campaign-readback.v1",
                "skill.cloudmold.engagement.growth-experiment-readback.v1",
                "skill.cloudmold.commerce.order-to-cash-readback.v1",
                "skill.cloudmold.commerce.order-cancellation-readback.v1",
                "skill.cloudmold.commerce.fulfillment-exception-readback.v1",
                "skill.cloudmold.commerce.return-refund-readback.v1",
                "skill.cloudmold.customer-service.resolution-readback.v1",
                "skill.cloudmold.quality.recall-readback.v1",
                "skill.cloudmold.risk.dispute-readback.v1",
                "skill.cloudmold.payment.reconciliation-readback.v1",
                "skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                "skill.cloudmold.supplier.sourcing-decision-readback.v1",
                "skill.cloudmold.finance.close-readiness.v1",
                "skill.cloudmold.listing.lifecycle-readback.v1",
                "skill.cloudmold.warehouse.allocation-transfer-readback.v1",
                "skill.cloudmold.warehouse.inbound-readback.v1");
        assertThat(workspaceRegistry.all())
                .filteredOn(candidate -> readbackSkillIds.contains(candidate.getSkillId()))
                .hasSize(19)
                .allSatisfy(readback -> {
                    assertThat(readback.getRiskLevel()).isEqualTo("R1");
                    assertThat(readback.getSteps()).singleElement().satisfies(step -> {
                        assertThat(step.getStepKind()).isEqualTo("WAIT_CAPABILITY");
                        assertThat(step.getOperationType()).isEqualTo("READ");
                        assertThat(step.getWaitSuccess()).isNotNull();
                    });
                });
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
