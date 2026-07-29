package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RotatingBusinessScenarioInputFactoryTest {

    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();
    private final RotatingBusinessScenarioInputFactory factory =
            new RotatingBusinessScenarioInputFactory(mapper, properties);

    @Test
    void shouldCreateDailyUniqueProductToListingInputFromSuccessfulFullChainTemplate() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"oldprefix-cat","master":"oldprefix-master",
                    "projection":"oldprefix-proj","aftersale":"oldprefix-aftersale","readback":"oldprefix-readback"},
                  "catalog":{"definitions":[
                    {"styleCode":"YS-OLD8A1B2","spuCode":"YS-OLD8A1B2",
                     "skuCode":"YS-OLD8A1B2-BLACK-S","colorCode":"BLACK_OLD8A1B2",
                     "sizeGroupCode":"WOMEN_TOP_OLD8A1B2",
                     "occurredAt":"2026-01-01T00:00:00Z"}],
                    "lifecycle":[]},
                  "master":{"eligibilityAt":"2026-01-01T00:00:00Z","merchantCommands":[],
                    "identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {"operation":"CREATE_DRAFT","runId":"oldprefix-aftersale","publishStartAt":"2026-01-01T00:00:00Z",
                      "offers":[{"canonicalSkuId":"00000000-0000-0000-0000-000000000000","externalOfferId":"oldprefix-offer"}]},
                    {"operation":"SUBMIT"},{"operation":"PASS_COMPLETION"},
                    {"operation":"APPROVE_BUSINESS"},{"operation":"APPROVE_RISK"},{"operation":"PUBLISH"},
                    {"operation":"RECEIVE"}]},
                  "readback":{"listing":{"validation":{}}}
                }
                """);

        String input = factory.build(162L, RotatingBusinessScenarioInputFactory.PRODUCT_TO_LISTING_SKILL,
                "2026-07-29", "temporal-run-1").orElseThrow();
        JsonNode json = JsonUtils.parseTree(input);

        assertThat(json.path("runIds").path("catalog").asText())
                .startsWith("p260729162-").endsWith("-cat");
        JsonNode definition = json.path("catalog").path("definitions").get(0);
        String styleCode = definition.path("styleCode").asText();
        assertThat(styleCode).startsWith("YS-").doesNotContain("OLD8A1B2");
        assertThat(definition.path("spuCode").asText()).isEqualTo(styleCode);
        assertThat(definition.path("skuCode").asText()).startsWith(styleCode + "-");
        assertThat(definition.path("colorCode").asText()).doesNotContain("OLD8A1B2");
        assertThat(definition.path("sizeGroupCode").asText()).doesNotContain("OLD8A1B2");
        assertThat(json.path("catalog").path("definitions").get(0).path("occurredAt").asText())
                .isEqualTo("2026-07-29T00:00:00Z");
        assertThat(json.path("listing").path("commands")).hasSize(6);
        assertThat(json.path("listing").path("commands").get(0).path("offers")).hasSize(6);
        assertThat(json.path("listing").path("commands").get(0).path("runId").asText())
                .startsWith("p260729162-").endsWith("-aftersale");
        assertThat(json.path("master").path("merchantReference").path("merchantId").asText())
                .isEqualTo("merchant-1");
        assertThat(json.path("master").path("merchantCommands").isMissingNode()).isTrue();
        assertThat(json.path("aftersale").isMissingNode()).isTrue();
    }

    @Test
    void shouldKeepOneOccurrenceDeterministicAndRotateAnotherOccurrenceOrDay() {
        mockReadyMaster();
        String template = """
                {"runIds":{"catalog":"base-cat"},"catalog":{},
                 "master":{"identityReference":{},"warehouseReference":{}},
                 "aftersale":{"commands":[]},"readback":{}}
                """;
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn(template);

        String first = factory.build(162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL,
                "2026-07-29", "temporal-run-1").orElseThrow();
        String replay = factory.build(162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL,
                "2026-07-29", "temporal-run-1").orElseThrow();
        String sameDayNewOccurrence = factory.build(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow();
        String nextDay = factory.build(162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL,
                "2026-07-30", "temporal-run-1").orElseThrow();

        assertThat(replay).isEqualTo(first);
        assertThat(sameDayNewOccurrence).isNotEqualTo(first);
        assertThat(nextDay).isNotEqualTo(first);
        assertThat(first).contains("f260729162-").contains("-cat");
        assertThat(nextDay).contains("f260730162-").contains("-cat");
    }

    @Test
    void shouldBuildAutonomousDayWithARealConfiguredMemberPersona() {
        mockReadyMaster();
        properties.setSyntheticConsumerMemberUserId(286L);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING"},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","occurredAt":"2026-02-13T08:26:18Z",
                     "promisedDeliveryAt":"2026-02-16T08:26:18Z"},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode json = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.AUTONOMOUS_DAY_SKILL,
                "2026-07-29", "temporal-run-1").orElseThrow());

        assertThat(json.path("runIds").path("product").asText())
                .startsWith("d260729162-").endsWith("-product");
        assertThat(json.path("operatorPrincipalId").asText()).isEqualTo("principal-1");
        assertThat(json.path("operationsCommands")).hasSize(4);
        assertThat(json.path("operationsCommands").get(0).path("operation").asText())
                .isEqualTo("OPEN_ALERT");
        assertThat(json.path("operationsCommands").get(0).path("alert")
                .path("sourceType").asText()).isEqualTo("METRIC");
        assertThat(json.path("operationsCommands").get(3).path("operation").asText())
                .isEqualTo("RESOLVE_ALERT");
        assertThat(json.path("operationsCommands").get(3).path("alert")
                .path("expectedVersion").asLong()).isEqualTo(3L);
        assertThat(json.path("consumer").path("identityReference").path("sourceId").asText()).isEqualTo("286");
        assertThat(json.path("consumer").path("behaviorCommands")).hasSize(8);
        assertThat(json.path("consumer").path("commands")).hasSize(19);
        assertThat(json.path("consumer").path("commands").get(6).path("occurredAt").asText())
                .isEqualTo("2026-07-29T00:00:00Z");
        assertThat(json.path("consumer").path("commands").get(6).path("promisedDeliveryAt").asText())
                .isEqualTo("2026-08-01T00:00:00Z");
        assertThat(json.path("consumer").path("customerServiceCommands")).hasSize(8);
        assertThat(json.path("consumer").path("customerServiceCommands").get(1)
                .path("referenceSourceSystem").asText()).isEqualTo("cloudmold-order");
        assertThat(json.path("consumer").path("communityCommands")).hasSize(3);
        assertThat(json.path("consumer").path("behaviorCommands").get(0).path("sessionId").asText())
                .isNotBlank();
    }

    @Test
    void shouldBuildFreshCategoryOperationsDayAcrossFourBusinessArteries() {
        mockReadyMaster();
        properties.setSyntheticConsumerMemberUserId(286L);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{"definitions":[
                    {"styleCode":"YS-BASEABC1","spuCode":"YS-BASEABC1",
                     "occurredAt":"2026-01-01T00:00:00Z"}],"lifecycle":[]},
                  "master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {"operation":"CREATE_DRAFT","offers":[{}]},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING","items":[{}]},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","items":[{}]},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode input = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.CATEGORY_DAILY_OPERATIONS_SKILL,
                "2026-07-30", "temporal-category-1").orElseThrow());

        assertThat(input.path("runIds").path("product").asText())
                .startsWith("y260730162-").endsWith("-product");
        assertThat(input.path("runIds").path("experiment").asText())
                .endsWith("-experiment");
        assertThat(input.path("runIds").path("campaign").asText())
                .endsWith("-campaign");
        assertThat(input.path("runIds").path("consumer").asText())
                .endsWith("-consumer");
        assertThat(input.path("diagnosis").path("issueCode").asText())
                .isIn("LOW_CONVERSION", "HIGH_VIEW_LOW_CART", "CAMPAIGN_MISS",
                        "CONTENT_QUALITY");
        assertThat(input.path("diagnosis").path("modelConfidenceBasisPoints").asInt())
                .isEqualTo(9700);
        assertThat(input.path("diagnosis").path("chosenActions")).hasSize(4);
        assertThat(input.path("operationsCommands")).hasSize(4);
        assertThat(input.path("operationsCommands").get(0).path("alert")
                .path("category").asText()).isEqualTo("CATEGORY_OPERATIONS");
        assertThat(input.path("operationsCommands").get(0).path("alert")
                .path("subcategory").asText())
                .isEqualTo(input.path("diagnosis").path("issueCode").asText());
        assertThat(input.path("operationsCommands").get(3).path("operation").asText())
                .isEqualTo("RESOLVE_ALERT");
        assertThat(input.path("product").path("listing").path("commands")).hasSize(6);
        assertThat(input.path("experiment").path("conclusion").path("decision").asText())
                .isEqualTo("TREATMENT");
        assertThat(input.path("campaign").path("clickedReceiptCommand").isObject()).isTrue();
        assertThat(input.path("consumer").path("identityReference").path("sourceId").asText())
                .isEqualTo("286");
        assertThat(input.path("consumer").path("commands")).hasSize(19);
    }

    @Test
    void shouldRotateReusableChildBusinessScenariosInsteadOfLeavingDailySchedulesIdle() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{"definitions":[{"styleCode":"YS-BASEABC1","spuCode":"YS-BASEABC1",
                    "occurredAt":"2026-01-01T00:00:00Z"}],"lifecycle":[]},
                  "master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[{"idempotencyKey":"base-listing","occurredAt":"2026-01-01T00:00:00Z"}]},
                  "legacyProjection":{"plans":[{"idempotencyKey":"base-projection"}]},
                  "readback":{}
                }
                """);

        JsonNode catalog = build(RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL);
        JsonNode aftersale = build(RotatingBusinessScenarioInputFactory.AFTERSALE_SAGA_SKILL);
        JsonNode master = build(RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL);
        JsonNode projection = build(RotatingBusinessScenarioInputFactory.LEGACY_PROJECTION_SKILL);

        assertThat(catalog.path("definitions").get(0).path("styleCode").asText())
                .doesNotContain("BASEABC1");
        assertThat(catalog.path("definitions").get(0).path("occurredAt").asText())
                .isEqualTo("2026-07-29T00:00:00Z");
        assertThat(aftersale.path("commands").get(0).path("idempotencyKey").asText())
                .startsWith("a260729162-");
        assertThat(master.path("merchantReference").path("merchantId").asText())
                .isEqualTo("merchant-1");
        assertThat(master.path("eligibilityAt").asText()).isEqualTo("2026-07-29T00:00:00Z");
        assertThat(projection.path("plans").get(0).path("idempotencyKey").asText())
                .startsWith("l260729162-");
    }

    @Test
    void shouldBuildStandaloneConsumerJourneyAgainstLatestPublishedProduct() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_TO_LISTING_SKILL, "listing_create"))
                .thenReturn("""
                        {"listingId":"listing-1","offers":[{"listingOfferId":"offer-1"}]}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("""
                        {"canonicalSpuId":"spu-1","canonicalSkuId":"sku-1"}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING","items":[{}]},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","items":[{}]},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode input = build(RotatingBusinessScenarioInputFactory.CONSUMER_JOURNEY_SKILL);

        assertThat(input.path("listingId").asText()).isEqualTo("listing-1");
        assertThat(input.path("identityReference").path("sourceId").asText()).isEqualTo("286");
        assertThat(input.path("behaviorCommands").get(4).path("canonicalSpuId").asText())
                .isEqualTo("spu-1");
        assertThat(input.path("behaviorCommands").get(7).path("listingOfferId").asText())
                .isEqualTo("offer-1");
        assertThat(input.path("commands").get(1).path("items").get(0)
                .path("canonicalSkuId").asText()).isEqualTo("sku-1");
        assertThat(input.path("commands").get(6).path("sellerId").asText())
                .isEqualTo("merchant-1");
        assertThat(input.path("customerServiceCommands").get(3)
                .path("assignedAgentPrincipalId").asText()).isEqualTo("principal-1");
        assertThat(input.path("communityCommands").get(0).path("listingId").asText())
                .isEqualTo("listing-1");
        assertThat(input.path("communityCommands").get(2).path("actorPrincipalId").asText())
                .isEqualTo("principal-1");
    }

    @Test
    void shouldBuildFreshFulfillmentExceptionRoleScenarioWithFreshInTransitOrder() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_TO_LISTING_SKILL, "listing_create"))
                .thenReturn("""
                        {"listingId":"listing-1","offers":[{"listingOfferId":"offer-1"}]}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("""
                        {"canonicalSpuId":"spu-1","canonicalSkuId":"sku-1"}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING","items":[{}]},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","items":[{}]},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory.FULFILLMENT_EXCEPTION_LIFECYCLE_SKILL);
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.FULFILLMENT_EXCEPTION_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("inTransitRunId").asText()).startsWith("x260729162-")
                .endsWith("-in-transit-order");
        assertThat(first.path("inTransitRunId").asText())
                .isNotEqualTo(second.path("inTransitRunId").asText());
        assertThat(first.path("consumer").path("identityReference").path("sourceId").asText())
                .isEqualTo("286");
        assertThat(first.path("consumer").path("commands")).hasSize(19);
        assertThat(first.path("consumer").path("commands").get(10).path("operation").asText())
                .isEqualTo("IN_TRANSIT");
        assertThat(first.path("exceptionCommands")).hasSize(6);
        assertThat(first.path("exceptionCommands").get(0).path("operation").asText())
                .isEqualTo("OPEN");
        assertThat(first.path("exceptionCommands").get(1).path("action").asText())
                .isIn("CONTACT_CARRIER", "REROUTE_ADDRESS");
        assertThat(first.path("exceptionCommands").get(1).path("evidenceRef").asText())
                .matches("evidence:fulfillment-exception/sha256/[0-9a-f]{64}");
        assertThat(first.path("exceptionCommands").get(2).path("operation").asText())
                .isEqualTo("REQUEST_APPROVAL");
        assertThat(first.path("exceptionCommands").get(2).has("approvalRef")).isFalse();
        assertThat(first.path("exceptionCommands").get(3).has("approvalRef")).isFalse();
        assertThat(first.path("exceptionCommands").get(5).path("operation").asText())
                .isEqualTo("CLOSE");
    }

    @Test
    void shouldBuildFreshBoundedCrossborderComplianceScenario() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_TO_LISTING_SKILL, "listing_create"))
                .thenReturn("""
                        {"listingId":"listing-1","offers":[{"listingOfferId":"offer-1"}]}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("""
                        {"canonicalSpuId":"spu-1","canonicalSkuId":"sku-1"}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING","items":[{}]},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","items":[{}]},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory
                        .CROSSBORDER_FULFILLMENT_COMPLIANCE_LIFECYCLE_SKILL);
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory
                        .CROSSBORDER_FULFILLMENT_COMPLIANCE_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("inTransitRunId").asText()).startsWith("b260729162-")
                .endsWith("-crossborder-order");
        assertThat(first.path("inTransitRunId").asText())
                .isNotEqualTo(second.path("inTransitRunId").asText());
        assertThat(first.path("consumer").path("identityReference").path("sourceId").asText())
                .isEqualTo("286");
        assertThat(first.path("operatorPrincipalId").asText()).isEqualTo("principal-1");
        assertThat(first.path("commands")).hasSize(13);
        assertThat(first.path("commands").get(0).path("operation").asText())
                .isEqualTo("CREATE_CASE");
        assertThat(first.path("commands").get(0).path("tradeMode").asText())
                .isEqualTo("DIRECT_MAIL");
        assertThat(first.path("commands").get(1).path("assessment")
                .path("recommendation").asText())
                .isIn("CN_US_DIRECT_STANDARD", "CN_US_DIRECT_EXPRESS");
        assertThat(first.path("commands").get(1).path("assessment")
                .path("facts")).hasSize(3);
        assertThat(first.path("commands").get(1).path("assessment")
                .path("options")).hasSize(2);
        assertThat(first.path("commands").get(1).path("assessment")
                .path("evidenceRef").asText())
                .matches("evidence:crossborder/sha256/[0-9a-f]{64}");
        assertThat(first.path("commands").get(3).has("approvalRef")).isFalse();
        assertThat(first.path("commands").get(9).has("approvalRef")).isFalse();
        assertThat(first.path("commands").get(12).path("operation").asText())
                .isEqualTo("CLOSE_CASE");
    }

    @Test
    void shouldBuildFreshBondedCustomsTripleOrderScenario() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_TO_LISTING_SKILL, "listing_create"))
                .thenReturn("""
                        {"listingId":"listing-1","offers":[{"listingOfferId":"offer-1"}]}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("""
                        {"canonicalSpuId":"spu-1","canonicalSkuId":"sku-1"}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING","items":[{}]},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","items":[{}]},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory.BONDED_CUSTOMS_LIFECYCLE_SKILL);
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.BONDED_CUSTOMS_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("inTransitRunId").asText()).startsWith("t260729162-")
                .endsWith("-bonded-order");
        assertThat(first.path("inTransitRunId").asText())
                .isNotEqualTo(second.path("inTransitRunId").asText());
        assertThat(first.path("operatorPrincipalId").asText()).isEqualTo("principal-1");
        assertThat(first.path("bondedCommands")).hasSize(11);
        assertThat(first.path("bondedCommands").get(0).path("operation").asText())
                .isEqualTo("CREATE_CASE");
        JsonNode triple = first.path("bondedCommands").get(0).path("tripleOrder");
        assertThat(triple.path("orderRef").asText())
                .isNotEqualTo(second.path("bondedCommands").get(0)
                        .path("tripleOrder").path("orderRef").asText());
        assertThat(triple.path("orderAmountMinor").asLong())
                .isEqualTo(triple.path("paymentAmountMinor").asLong())
                .isEqualTo(triple.path("logisticsAmountMinor").asLong());
        assertThat(triple.path("buyerIdentityHash").asText())
                .isEqualTo(triple.path("receiverIdentityHash").asText())
                .isEqualTo(triple.path("declarantIdentityHash").asText());
        assertThat(first.path("bondedCommands").get(1).path("eligibilityAssessment")
                .path("facts")).hasSize(3);
        assertThat(first.path("bondedCommands").get(1).path("eligibilityAssessment")
                .path("recommendation").asText()).isEqualTo("BONDED_RETAIL_IMPORT");
        assertThat(first.path("bondedCommands").get(5).has("approvalRef")).isFalse();
        assertThat(first.path("bondedCommands").get(6).has("approvalRef")).isFalse();
        assertThat(first.path("bondedCommands").get(10).path("operation").asText())
                .isEqualTo("CLOSE_CASE");
        assertThat(first.path("bondedCommands").get(10).path("closeReason").asText())
                .isEqualTo("TRIPLE_MATCHED_CUSTOMS_ACCEPTED_BONDED_RELEASED_DELIVERED");
    }

    @Test
    void shouldBuildFreshPartnerMarketingRoleWithRealConsumerAttributionBindings() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[
                    {},{},{},{},{},{},
                    {"operation":"RECEIVE"},{"operation":"PLACE_FROM_LISTING","items":[{}]},
                    {"operation":"RESERVE"},{"operation":"CONFIRM_INVENTORY"},
                    {"operation":"CAPTURE"},{"operation":"CONFIRM_PAYMENT"},
                    {"operation":"CREATE","items":[{}]},{"operation":"SHIP"},
                    {"operation":"SHIP"},{"operation":"CONFIRM_SHIPMENT"},
                    {"operation":"IN_TRANSIT"},{"operation":"DELIVERED"},
                    {"operation":"COMPLETE"},{"operation":"REQUEST"},
                    {"operation":"APPROVE"},{"operation":"HANDOVER"},
                    {"operation":"TRANSIT"},{"operation":"RECEIVE"},
                    {"operation":"ACCEPT"}]},
                  "readback":{}
                }
                """);

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory
                        .PARTNER_MARKETING_KOL_MEDIA_OPERATIONS_SKILL);
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory
                        .PARTNER_MARKETING_KOL_MEDIA_OPERATIONS_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("operatorPrincipalId").asText()).isEqualTo("principal-1");
        assertThat(first.path("independentReviewerPrincipalId").asText())
                .isEqualTo("governance-user:227");
        assertThat(first.path("financePrincipalId").asText())
                .isEqualTo("finance-user:228");
        assertThat(first.path("product").isObject()).isTrue();
        assertThat(first.path("campaign").isObject()).isTrue();
        assertThat(first.path("consumer").path("identityReference")
                .path("sourceId").asText()).isEqualTo("286");
        assertThat(first.path("partnerMarketingCommands")).hasSize(12);
        assertThat(first.path("partnerMarketingCommands").get(0)
                .path("candidateCase").path("caseId").asText())
                .isNotEqualTo(second.path("partnerMarketingCommands").get(0)
                        .path("candidateCase").path("caseId").asText());
        assertThat(first.path("partnerMarketingCommands").get(3)
                .path("brief").path("campaignId").asText()).isEqualTo(
                "00000000-0000-0000-0000-000000000000");
        JsonNode attribution = first.path("partnerMarketingCommands").get(8)
                .path("attribution");
        assertThat(attribution.path("attributionSourceRef").asText())
                .startsWith("persona:o260729162-");
        assertThat(attribution.path("grossSettlementAmountMinor").asLong())
                .isEqualTo(attribution.path("platformFeeAmountMinor").asLong()
                        + attribution.path("taxWithholdingAmountMinor").asLong()
                        + attribution.path("netPayableAmountMinor").asLong());
        assertThat(first.path("partnerMarketingCommands").get(4)
                .path("actorPrincipalId").asText())
                .isNotEqualTo(first.path("partnerMarketingCommands").get(3)
                        .path("actorPrincipalId").asText());
        assertThat(first.path("partnerMarketingCommands").get(10)
                .path("actorPrincipalId").asText())
                .isNotEqualTo(first.path("partnerMarketingCommands").get(9)
                        .path("actorPrincipalId").asText());
        assertThat(first.path("communityCommands")).hasSize(3);
    }

    @Test
    void shouldBuildUniqueMerchantOnboardingLifecycleInput() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-owner\"}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);

        JsonNode first = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.MERCHANT_ONBOARDING_SKILL,
                "2026-07-29", "temporal-run-1").orElseThrow());
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.MERCHANT_ONBOARDING_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("draftCommand").path("ownerPrincipalId").asText())
                .isEqualTo("principal-owner");
        assertThat(first.path("draftCommand").path("legalName").asText())
                .startsWith("AI 模拟商家 H260729162-");
        assertThat(first.path("draftCommand").path("registrationHashToken").asText())
                .startsWith("sha256:");
        assertThat(first.path("draftCommand").path("externalShopId").asText())
                .isNotEqualTo(second.path("draftCommand").path("externalShopId").asText());
    }

    @Test
    void shouldBuildFreshRoleLevelCampaignGrowthAndSourcingInputs() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-owner\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("{\"canonicalSkuId\":\"canonical-sku-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.SUPPLIER_SOURCING_SKILL, "supplier_award"))
                .thenReturn("""
                        {"supplierId":"supplier-awarded","sourcingCaseId":"sourcing-case-awarded"}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CONSUMER_JOURNEY_SKILL, "consumer_principal"))
                .thenReturn("{\"principalId\":\"consumer-principal\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CONSUMER_JOURNEY_SKILL, "order_place"))
                .thenReturn("{\"orderId\":\"consumer-order\"}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);

        JsonNode campaign = build(RotatingBusinessScenarioInputFactory.PROMOTION_CAMPAIGN_SKILL);
        JsonNode growth = build(RotatingBusinessScenarioInputFactory.GROWTH_EXPERIMENT_SKILL);
        JsonNode sourcing = build(RotatingBusinessScenarioInputFactory.SUPPLIER_SOURCING_SKILL);
        JsonNode procurement = build(RotatingBusinessScenarioInputFactory.PROCUREMENT_ORDER_SKILL);
        JsonNode warehouse = build(RotatingBusinessScenarioInputFactory.WMS_OPERATIONS_SKILL);
        JsonNode replenishment = build(
                RotatingBusinessScenarioInputFactory.REPLENISHMENT_LIFECYCLE_SKILL);
        JsonNode promotionReplenishment = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.REPLENISHMENT_LIFECYCLE_SKILL,
                "2026-07-30", "temporal-run-promotion").orElseThrow());
        JsonNode customerService = build(
                RotatingBusinessScenarioInputFactory.CUSTOMER_SERVICE_LIFECYCLE_SKILL);

        assertThat(campaign.path("deliveryCommand").path("principalId").asText())
                .isEqualTo("principal-owner");
        assertThat(campaign.path("clickedReceiptCommand").path("receiptId").asText())
                .isNotBlank();
        assertThat(growth.path("experiment").path("variants")).hasSize(2);
        assertThat(growth.path("controlMetric").path("sampleCount").asInt()).isEqualTo(1);
        assertThat(growth.path("conclusion").path("decision").asText()).isEqualTo("TREATMENT");
        assertThat(sourcing.path("sourcingCase").path("canonicalSkuId").asText())
                .isEqualTo("canonical-sku-1");
        assertThat(sourcing.path("supplierA").path("supplierId").asText())
                .isNotEqualTo(sourcing.path("supplierB").path("supplierId").asText());
        assertThat(sourcing.path("admissionA").path("qualificationEvidenceSha256").asText())
                .matches("[0-9a-f]{64}");
        assertThat(sourcing.path("quoteA").path("unitCostMinor").asLong())
                .isLessThan(sourcing.path("quoteB").path("unitCostMinor").asLong());
        assertThat(procurement.path("purchaseOrder").path("supplierRef").asText())
                .isEqualTo("supplier-awarded");
        assertThat(procurement.path("purchaseOrder").path("canonicalWarehouseId").asText())
                .isEqualTo("warehouse-1");
        assertThat(warehouse.path("authority").path("operator").path("principalId").asText())
                .isEqualTo("principal-owner");
        assertThat(warehouse.path("item").path("skus")).hasSize(1);
        assertThat(warehouse.path("receipt").path("no").asText()).startsWith("AIRK");
        assertThat(warehouse.path("shipment").path("bizOrderNo").asText()).startsWith("AISO");
        assertThat(replenishment.path("scenarioType").asText())
                .isEqualTo("DAILY_REPLENISHMENT");
        assertThat(replenishment.path("operatorPrincipalId").asText())
                .isEqualTo("principal-owner");
        assertThat(replenishment.path("operationsCommands")).hasSize(4);
        assertThat(replenishment.path("operationsCommands").get(0).path("alert")
                .path("category").asText()).isEqualTo("SUPPLY_OPERATIONS");
        assertThat(replenishment.path("operationsCommands").get(3)
                .path("operation").asText()).isEqualTo("RESOLVE_ALERT");
        assertThat(replenishment.path("sourcing").path("sourcingCase")
                .path("targetQuantity").asInt()).isEqualTo(500);
        assertThat(replenishment.path("warehouse").path("quantities")
                .path("receipt").asInt()).isEqualTo(10);
        assertThat(promotionReplenishment.path("scenarioType").asText())
                .isEqualTo("PROMOTION_REPLENISHMENT");
        assertThat(promotionReplenishment.path("operationsCommands").get(0)
                .path("alert").path("subcategory").asText())
                .isEqualTo("PROMOTION_REPLENISHMENT");
        assertThat(promotionReplenishment.path("procurement").path("purchaseOrder")
                .path("orderedQuantity").asInt()).isEqualTo(2_000);
        assertThat(promotionReplenishment.path("warehouse").path("quantities")
                .path("receipt").asInt()).isEqualTo(30);
        assertThat(customerService.path("commands")).hasSize(8);
        assertThat(customerService.path("commands").get(0)
                .path("customerPrincipalId").asText()).isEqualTo("consumer-principal");
        assertThat(customerService.path("commands").get(1)
                .path("referenceId").asText()).isEqualTo("consumer-order");
        assertThat(customerService.path("commands").get(3)
                .path("assignedAgentPrincipalId").asText()).isEqualTo("principal-owner");
    }

    @Test
    void shouldBuildFreshFinanceCloseWithIndependentMakerAndChecker() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);

        JsonNode finance = build(RotatingBusinessScenarioInputFactory.FINANCE_CLOSE_LIFECYCLE_SKILL);

        assertThat(finance.path("makerIdentityCommand").path("sourceId").asText()).isEqualTo("228");
        assertThat(finance.path("checkerIdentityCommand").path("sourceId").asText()).isEqualTo("227");
        assertThat(finance.path("commands")).hasSize(9);
        assertThat(finance.path("commands").get(0).path("operation").asText())
                .isEqualTo("OPEN_ACCOUNTING_PERIOD");
        assertThat(finance.path("commands").get(2).path("operation").asText())
                .isEqualTo("RECONCILE_CHANNEL_STATEMENT");
        assertThat(finance.path("commands").get(3).path("differenceResolution")
                .path("adjustmentAmountMinor").asLong()).isEqualTo(10_000L);
        assertThat(finance.path("commands").get(8).path("operation").asText())
                .isEqualTo("CLOSE_ACCOUNTING_PERIOD");
        assertThat(finance.path("commands").get(1).path("statement")
                .path("evidenceSha256").asText()).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldBuildFreshSopScenarioWithRobustAlternativesAndRealWmsMapping() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-planner\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("{\"canonicalSkuId\":\"canonical-sku-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.WMS_OPERATIONS_SKILL,
                "create_source_warehouse")).thenReturn("301");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.WMS_OPERATIONS_SKILL,
                "create_target_warehouse")).thenReturn("302");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.WMS_OPERATIONS_SKILL,
                "resolve_sku")).thenReturn("{\"skuId\":401}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory.SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL);
        JsonNode nextOccurrence = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("actorPrincipalId").asText()).isEqualTo("principal-planner");
        assertThat(first.path("forecast").path("points")).hasSize(2);
        assertThat(first.path("forecastEvaluation").path("actuals").get(0)
                .path("bucketStart").asText()).isEqualTo("2026-07-22");
        assertThat(first.path("supplyPlan").path("horizonStart").asText())
                .isEqualTo("2026-07-30");
        assertThat(first.path("scenarios").path("lean").path("onHandQuantity").asInt())
                .isEqualTo(2);
        assertThat(first.path("scenarios").path("resilient").path("onHandQuantity").asInt())
                .isEqualTo(10);
        assertThat(first.path("scenarioRecommendation").path("candidateScenarioIds"))
                .hasSize(2);
        assertThat(first.path("scenarioRecommendation")
                .path("demandStressBasisPoints").asInt()).isEqualTo(12_000);
        assertThat(first.path("executionProposal").path("targetType").asText())
                .isEqualTo("TRANSFER_REQUEST");
        assertThat(first.path("executionProposal").path("sourceWarehouseId").asLong())
                .isEqualTo(301L);
        assertThat(first.path("executionProposal").path("targetWarehouseId").asLong())
                .isEqualTo(302L);
        assertThat(first.path("executionProposal").path("wmsSkuId").asLong())
                .isEqualTo(401L);
        assertThat(first.path("forecast").path("forecastId").asText())
                .isNotEqualTo(nextOccurrence.path("forecast").path("forecastId").asText());
    }

    @Test
    void shouldBuildSopScenarioFromCurrentWmsAuthorityWhenNoPriorWmsWorkflowSucceeded() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"principal-planner\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("{\"canonicalSkuId\":\"canonical-sku-1\"}");
        when(mapper.selectWmsTransferSeed(162L)).thenReturn(new WmsTransferSeedRecord()
                .setSourceWarehouseId(501L)
                .setTargetWarehouseId(502L)
                .setWmsSkuId(601L));
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory.SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL);

        assertThat(input.path("executionProposal").path("sourceWarehouseId").asLong())
                .isEqualTo(501L);
        assertThat(input.path("executionProposal").path("targetWarehouseId").asLong())
                .isEqualTo(502L);
        assertThat(input.path("executionProposal").path("wmsSkuId").asLong())
                .isEqualTo(601L);
    }

    @Test
    void shouldBuildFreshQualityInspectionRecallWithIndependentReviewAndNewLot() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "principal"))
                .thenReturn("{\"principalId\":\"11111111-1111-4111-8111-111111111111\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL, "warehouse_network"))
                .thenReturn("""
                        {"warehouseId":"22222222-2222-4222-8222-222222222222",
                         "locationId":"33333333-3333-4333-8333-333333333333"}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("{\"canonicalSkuId\":\"44444444-4444-4444-8444-444444444444\"}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory.QUALITY_INSPECTION_RECALL_LIFECYCLE_SKILL);
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.QUALITY_INSPECTION_RECALL_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("managerPrincipalId").asText())
                .isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(first.path("primaryInspectorIdentityCommand").path("sourceId").asText())
                .isEqualTo("228");
        assertThat(first.path("independentReviewerIdentityCommand").path("sourceId").asText())
                .isEqualTo("227");
        assertThat(first.path("lotCommand").path("lotCode").asText()).startsWith("AI-QLOT-J260729162-");
        assertThat(first.path("lotCommand").path("lotCode").asText())
                .isNotEqualTo(second.path("lotCommand").path("lotCode").asText());
        assertThat(first.path("stockCommand").path("quantity").asInt()).isEqualTo(12);
        assertThat(first.path("stockCommand").path("locationId").asText())
                .isEqualTo("33333333-3333-4333-8333-333333333333");
        assertThat(first.path("commands")).hasSize(16);
        assertThat(first.path("commands").get(0).path("operation").asText())
                .isEqualTo("CREATE_STANDARD");
        assertThat(first.path("commands").get(7).path("operation").asText())
                .isEqualTo("DECIDE_INSPECTION_TASK");
        assertThat(first.path("commands").get(8).path("operation").asText())
                .isEqualTo("REQUEST_RECHECK");
        assertThat(first.path("commands").get(11).path("operation").asText())
                .isEqualTo("OPEN_CAPA");
        assertThat(first.path("commands").get(13).path("operation").asText())
                .isEqualTo("OPEN_RECALL_ACTION");
        assertThat(first.path("commands").get(15).path("recallAction")
                .path("resolutionCode").asText()).isEqualTo("QUARANTINED_DESTROYED");
        assertThat(first.path("commands").get(7).path("inspectionTask")
                .path("evidenceRef").asText()).matches("sha256:[0-9a-f]{64}");
    }

    private JsonNode build(String skillId) {
        return JsonUtils.parseTree(factory.build(162L, skillId,
                "2026-07-29", "temporal-run-1").orElseThrow());
    }

    private void mockReadyMaster() {
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, "skill.cloudmold.commerce.reuse-ready-master.v1", "merchant_approve"))
                .thenReturn("{\"merchantId\":\"merchant-1\",\"shopId\":\"shop-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, "skill.cloudmold.commerce.reuse-ready-master.v1", "warehouse_network"))
                .thenReturn("{\"warehouseId\":\"warehouse-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, "skill.cloudmold.commerce.reuse-ready-master.v1", "principal"))
                .thenReturn("{\"principalId\":\"principal-1\"}");
    }
}
