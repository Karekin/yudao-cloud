package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityReference;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RotatingBusinessScenarioInputFactoryTest {

    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final IdentityQueryApi identityQueries = mock(IdentityQueryApi.class);
    private final AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();
    private final RotatingBusinessScenarioInputFactory factory =
            new RotatingBusinessScenarioInputFactory(mapper, properties, identityQueries);

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
        assertThat(json.path("listing").path("commands").get(0).has("runId")).isFalse();
        assertThat(json.path("listing").path("commands").get(0).path("publishStartAt").asText())
                .isEqualTo("2026-07-28T16:00:00Z");
        assertThat(json.path("readback").path("listingId").asText())
                .isEqualTo("00000000-0000-0000-0000-000000000000");
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
        assertThat(json.path("consumer").path("commands")).hasSize(20);
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
        assertThat(input.path("consumer").path("commands")).hasSize(20);
    }

    @Test
    void shouldRotateReusableChildBusinessScenariosInsteadOfLeavingDailySchedulesIdle() {
        mockReadyMaster();
        ObjectNode template = completeAfterSaleTemplate();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(template));

        JsonNode catalog = build(RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL);
        JsonNode aftersale = build(RotatingBusinessScenarioInputFactory.AFTERSALE_SAGA_SKILL);
        JsonNode master = build(RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL);

        assertThat(catalog.path("definitions").get(0).path("styleCode").asText())
                .doesNotContain("BASEABC1");
        assertThat(catalog.path("definitions").get(0).path("occurredAt").asText())
                .isEqualTo("2026-07-29T00:00:00Z");
        assertThat(aftersale.path("commands").get(0).has("idempotencyKey")).isFalse();
        assertThat(master.path("merchantReference").path("merchantId").asText())
                .isEqualTo("merchant-1");
        assertThat(master.path("eligibilityAt").asText()).isEqualTo("2026-07-29T00:00:00Z");
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
        when(mapper.selectConsumerFavorite(162L, "MEMBER", "MEMBER_USER", "286", "spu-1"))
                .thenReturn(new ConsumerFavoriteSeedRecord()
                        .setFavoriteId("favorite-existing").setStatus("ACTIVE").setVersion(4L));

        JsonNode input = build(RotatingBusinessScenarioInputFactory.CONSUMER_JOURNEY_SKILL);

        assertThat(input.path("listingId").asText()).isEqualTo("listing-1");
        assertThat(input.path("identityReference").path("sourceId").asText()).isEqualTo("286");
        assertThat(input.path("behaviorCommands").get(4).path("canonicalSpuId").asText())
                .isEqualTo("spu-1");
        assertThat(input.path("behaviorCommands").get(7).path("listingOfferId").asText())
                .isEqualTo("offer-1");
        assertThat(input.path("favoriteCommand").path("favoriteId").asText())
                .isEqualTo("favorite-existing");
        assertThat(input.path("favoriteCommand").path("expectedVersion").asLong()).isEqualTo(4L);
        assertThat(input.path("favoriteCommand").path("desiredStatus").asText()).isEqualTo("REMOVED");
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
        assertThat(first.path("consumer").path("commands")).hasSize(20);
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
        assertThat(first.path("commands").get(0).path("caseId").asText())
                .isNotEqualTo("00000000-0000-0000-0000-000000000000")
                .isNotEqualTo(second.path("commands").get(0).path("caseId").asText());
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
        assertThat(first.path("bondedCommands").get(0).path("caseId").asText())
                .isNotEqualTo("00000000-0000-0000-0000-000000000000")
                .isNotEqualTo(second.path("bondedCommands").get(0).path("caseId").asText());
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
        assertThat(first.path("bondedCommands").get(2).path("goodsClassification")
                .path("positiveListCode").asText()).isEqualTo("POSITIVE_LIST_TEST");
        assertThat(first.path("bondedCommands").get(3).path("tripleOrder")
                .path("orderAmountMinor").asLong()).isEqualTo(triple.path("orderAmountMinor").asLong());
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
        when(identityQueries.resolveActiveSource(
                new SourceIdentityReference("SYSTEM", "SYSTEM_ADMIN_USER", "227")))
                .thenReturn(SourceIdentityView.builder()
                        .principalId("1d9634aa-f54c-4c81-96dc-d3b61d5486d7").build());
        when(identityQueries.resolveActiveSource(
                new SourceIdentityReference("SYSTEM", "SYSTEM_ADMIN_USER", "228")))
                .thenReturn(SourceIdentityView.builder()
                        .principalId("38fb8735-2be3-429e-877f-17d19715fedb").build());
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
                .isEqualTo("1d9634aa-f54c-4c81-96dc-d3b61d5486d7");
        assertThat(first.path("financePrincipalId").asText())
                .isEqualTo("38fb8735-2be3-429e-877f-17d19715fedb");
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
        assertThat(first.path("draftCommand").path("businessLicenseToken").asText())
                .matches("sha256:[0-9a-f]{64}");
        assertThat(first.path("draftCommand").path("externalShopId").asText())
                .isNotEqualTo(second.path("draftCommand").path("externalShopId").asText());
    }

    @Test
    void shouldBuildLocalTestManagedMerchantAdmissionOnlyAfterCanonicalOnboardingApproval() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {
                  "runIds":{"catalog":"base-cat"},
                  "catalog":{},"master":{"identityReference":{},"warehouseReference":{}},
                  "aftersale":{"commands":[]},"readback":{}
                }
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.MERCHANT_ONBOARDING_SKILL, "merchant_approve"))
                .thenReturn("""
                        {"applicationId":"application-approved-1","merchantId":"merchant-approved-1"}
                        """);

        JsonNode input = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.MERCHANT_MANAGED_GROWTH_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-1").orElseThrow());

        assertThat(input.path("localTest").path("classification").asText()).isEqualTo("LOCAL_TEST");
        assertThat(input.path("admissionOpenCommand").path("applicationId").asText())
                .isEqualTo("application-approved-1");
        assertThat(input.path("attributionCommand").path("sourceReference")
                .path("sourceSystem").asText()).isEqualTo("CLOUDMOLD_AI_LOCAL_TEST");
        assertThat(input.path("evidencePackageCommand").path("evidenceItems")).hasSize(2);
        assertThat(input.path("aiDiagnosticProposalCommand").path("recommendationCode").asText())
                .isEqualTo("FACTORY_INSPECTION_REQUIRED");
        assertThat(input.path("inspectionScheduleCommand").path("scheduledAt").asText())
                .isEqualTo("2026-07-29T01:00:00Z");
        assertThat(input.path("managedFinalReviewCommand").path("reviewDecision").asText())
                .isEqualTo("APPROVED");
    }

    @Test
    void shouldNotBuildManagedMerchantAdmissionWithoutCanonicalOnboardingApproval() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL)).thenReturn("""
                {"runIds":{"catalog":"base-cat"},"catalog":{},
                 "master":{"identityReference":{},"warehouseReference":{}},
                 "aftersale":{"commands":[]},"readback":{}}
                """);

        assertThat(factory.build(162L,
                RotatingBusinessScenarioInputFactory.MERCHANT_MANAGED_GROWTH_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-1")).isEmpty();
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
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn(procurementSourcingSeed("canonical-sku-1"));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL, "award_approve"))
                .thenReturn("""
                        {"aggregateId":"award-approved","aggregateVersion":3,"status":"APPROVED"}
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
        JsonNode sourcing = build(RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn(JsonUtils.toJsonString(sourcing));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL, "award_approve"))
                .thenReturn("{\"aggregateId\":\"" + sourcing.path("awardId").asText()
                        + "\",\"aggregateVersion\":3,\"status\":\"APPROVED\"}");
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
        assertThat(growth.path("experiment").path("startsAt").asText())
                .isEqualTo(growth.path("runningAt").asText());
        assertThat(growth.path("controlMetric").path("sampleCount").asInt()).isEqualTo(30);
        assertThat(growth.path("controlMetric").path("measuredFrom").asText())
                .isEqualTo(growth.path("runningAt").asText());
        assertThat(growth.path("controlExposure30").path("variantCode").asText())
                .isEqualTo("CONTROL");
        assertThat(growth.path("treatmentExposure30").path("variantCode").asText())
                .isEqualTo("TREATMENT");
        assertThat(growth.path("conclusion").path("decision").asText()).isEqualTo("TREATMENT");
        assertThat(sourcing.path("schemaVersion").asText())
                .isEqualTo("cloudmold.procurement-sourcing-input/v1");
        assertThat(sourcing.path("supplierCandidates")).hasSize(2);
        assertThat(sourcing.path("legalEntityId").asText()).isEqualTo("legal-entity-01");
        assertThat(sourcing.path("purchaseRequisition").path("command").path("lines")).hasSize(2);
        assertThat(sourcing.path("purchaseRequisition").path("command")
                .path("legalEntityId").asText()).isEqualTo("legal-entity-01");
        assertThat(sourcing.path("purchaseRequisition").path("command")
                .path("taxCalculationPolicyCode").asText()).isEqualTo("STANDARD_V1");
        assertThat(sourcing.path("purchaseRequisition").path("command").path("lines").get(0)
                .path("valuationPolicyId").asText()).isEqualTo("policy-1");
        assertThat(sourcing.path("purchaseRequisition").path("command").path("lines").get(0)
                .path("schedules")).hasSize(2);
        assertThat(sourcing.path("sourcingCommands")).hasSize(15);
        assertThat(sourcing.path("sourcingCommands").get(5).path("command")
                .path("quotationRevision").path("lines")).hasSize(2);
        assertThat(sourcing.path("sourcingCommands").get(5).path("command")
                .path("quotationRevision").path("lines").get(0).path("schedules")).hasSize(2);
        assertThat(sourcing.path("purchaseOrderPlans")).hasSize(2);
        assertThat(sourcing.path("awardRelease").path("command").path("awardId").asText())
                .isEqualTo(sourcing.path("awardId").asText());
        assertThat(sourcing.path("awardRelease").path("command")
                .path("expectedAwardVersion").asLong()).isEqualTo(3L);
        assertThat(sourcing.path("purchaseOrderPlans").get(0).path("commands").get(0)
                .path("command").path("operation").asText())
                .isEqualTo("SUBMIT_PURCHASE_ORDER");
        assertThat(sourcing.path("purchaseOrderPlans").get(0).path("commands").get(0)
                .path("command").path("purchaseOrder").path("orderId").asText())
                .isEqualTo("__AWARD_RELEASE_RESULT__");
        assertThat(sourcing.path("purchaseOrderPlans").get(1).path("commands").get(0)
                .path("command").path("purchaseOrder").has("lines")).isFalse();
        assertThat(procurement.path("purchaseOrders")).hasSize(2);
        assertThat(procurement.path("awardRelease").path("command")
                .path("expectedAwardVersion").asLong()).isEqualTo(3L);
        assertThat(warehouse.path("authority").path("operator").path("principalId").asText())
                .isEqualTo("principal-owner");
        assertThat(warehouse.path("canonicalWarehouseId").asText()).isEqualTo("warehouse-1");
        assertThat(warehouse.path("correlationId").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(warehouse.path("item").path("skus")).hasSize(1);
        assertThat(warehouse.path("item").path("unit").asText()).isEqualTo("PCS");
        assertThat(warehouse.path("item").path("skus").get(0).path("code").asText())
                .isEqualTo("YS-BASE-BLACK-S");
        assertThat(warehouse.path("item").path("skus").get(0).path("barCode").asText())
                .isEqualTo("CM-BASE-BLACK-S");
        assertThat(warehouse.path("category").path("name").asText()).hasSizeLessThanOrEqualTo(30);
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
        assertThat(replenishment.path("sourcing").path("purchaseRequisition")
                .path("command").path("lines").get(0).path("requestedQuantity").decimalValue())
                .isEqualByComparingTo("300");
        assertThat(replenishment.path("warehouse").path("quantities")
                .path("receipt").asInt()).isEqualTo(10);
        assertThat(promotionReplenishment.path("scenarioType").asText())
                .isEqualTo("PROMOTION_REPLENISHMENT");
        assertThat(promotionReplenishment.path("operationsCommands").get(0)
                .path("alert").path("subcategory").asText())
                .isEqualTo("PROMOTION_REPLENISHMENT");
        assertThat(promotionReplenishment.path("procurement").path("purchaseOrders")).hasSize(2);
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
    void shouldFailClosedWithoutTwoExplicitSuppliersAndIndependentActors() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn("""
                        {"schemaVersion":"cloudmold.procurement-sourcing-input/v1",
                         "legalEntityId":"legal-entity-01",
                         "supplierCandidates":[{"supplierId":"supplier-a"}],
                         "valuationPolicies":[
                           {"canonicalSkuId":"sku-1","valuationPolicyId":"policy-1",
                            "valuationPolicyVersion":"2026.1","valuationPolicyHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                           {"canonicalSkuId":"sku-2","valuationPolicyId":"policy-2",
                            "valuationPolicyVersion":"2026.1","valuationPolicyHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}],
                         "actors":{"creatorPrincipalId":"principal-creator",
                                   "reviewerAPrincipalId":"principal-reviewer-a",
                                   "reviewerBPrincipalId":"principal-reviewer-b",
                                   "approverPrincipalId":"principal-approver"}}
                        """);

        assertThat(factory.build(162L,
                RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL,
                "2026-07-29", "temporal-missing-supplier")).isEmpty();

        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn("""
                        {"schemaVersion":"cloudmold.procurement-sourcing-input/v1",
                         "legalEntityId":"legal-entity-01",
                         "supplierCandidates":[{"supplierId":"supplier-a"},
                                               {"supplierId":"supplier-a"}],
                         "valuationPolicies":[
                           {"canonicalSkuId":"sku-1","valuationPolicyId":"policy-1",
                            "valuationPolicyVersion":"2026.1","valuationPolicyHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                           {"canonicalSkuId":"sku-2","valuationPolicyId":"policy-2",
                            "valuationPolicyVersion":"2026.1","valuationPolicyHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}],
                         "actors":{"creatorPrincipalId":"principal-shared",
                                   "reviewerAPrincipalId":"principal-shared",
                                   "reviewerBPrincipalId":"principal-reviewer-b",
                                   "approverPrincipalId":"principal-approver"}}
                        """);

        assertThat(factory.build(162L,
                RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL,
                "2026-07-29", "temporal-duplicate-authority")).isEmpty();

        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn("""
                        {"schemaVersion":"cloudmold.procurement-sourcing-input/v1",
                         "supplierCandidates":[{"supplierId":"supplier-a"},
                                               {"supplierId":"supplier-b"}],
                         "valuationPolicies":[
                           {"canonicalSkuId":"sku-1","valuationPolicyId":"policy-1",
                            "valuationPolicyVersion":"2026.1","valuationPolicyHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                           {"canonicalSkuId":"sku-2","valuationPolicyId":"policy-2",
                            "valuationPolicyVersion":"2026.1","valuationPolicyHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}],
                         "actors":{"creatorPrincipalId":"principal-creator",
                                   "reviewerAPrincipalId":"principal-reviewer-a",
                                   "reviewerBPrincipalId":"principal-reviewer-b",
                                   "approverPrincipalId":"principal-approver"}}
                        """);

        assertThat(factory.build(162L,
                RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL,
                "2026-07-29", "temporal-missing-legal-entity")).isEmpty();

        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn(procurementSourcingSeed("sku-1")
                        .replace("a".repeat(64), "UNVERIFIED"));

        assertThat(factory.build(162L,
                RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL,
                "2026-07-29", "temporal-invalid-valuation-policy")).isEmpty();
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
        assertThat(finance.path("commands").get(0).path("period").path("periodCode").asText())
                .matches("AI-DAILY_CLOSE-\\d{4}-\\d{2}-\\d{2}-[0-9a-f]{8}");
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
                .setWmsSkuId(601L)
                .setItemId(701L)
                .setWmsSkuCode("YS-BASE-BLACK-S")
                .setWmsBarcode("CM-BASE-BLACK-S")
                .setItemUnit("PCS")
                .setTargetWarehouseMappingId("mapping-502")
                .setCanonicalWarehouseId("warehouse-1")
                .setCanonicalSkuId("sku-1")
                .setCatalogSkuCode("YS-BASE-BLACK-S")
                .setCatalogBarcode("CM-BASE-BLACK-S")
                .setBaseUomCode("PCS"));
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

    @Test
    void shouldAlternateRestockAndScrapInputsWithMakerCheckerSeparation() {
        mockReadyMaster();
        ObjectNode template = completeAfterSaleTemplate();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(template));

        JsonNode scrapDay = build(RotatingBusinessScenarioInputFactory.AFTERSALE_SAGA_SKILL);
        JsonNode restockDay = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.AFTERSALE_SAGA_SKILL,
                "2026-07-30", "temporal-run-1").orElseThrow());

        assertThat(scrapDay.path("commands")).hasSize(26);
        assertThat(scrapDay.path("commands").get(24).path("operation").asText())
                .isEqualTo("ASSESS_DISPOSITION");
        assertThat(scrapDay.path("commands").get(24).path("assessorId").asText())
                .startsWith("ai-return-assessor:");
        assertThat(scrapDay.path("commands").get(25).path("inspectorId").asText())
                .startsWith("warehouse-inspector:")
                .isNotEqualTo(scrapDay.path("commands").get(24).path("assessorId").asText());
        assertThat(scrapDay.path("commands").get(25).path("dispositionCode").asText())
                .isEqualTo("SCRAP");
        assertThat(scrapDay.path("commands").get(24).path("safetyRisk").asBoolean()).isTrue();
        assertThat(restockDay.path("commands").get(25).path("dispositionCode").asText())
                .isEqualTo("RESTOCK");
    }

    @Test
    void shouldHydrateStandaloneAfterSaleWithActiveCatalogAndMasterReferences() {
        mockReadyMaster();
        ObjectNode template = completeAfterSaleTemplate();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(template));

        JsonNode input = build(RotatingBusinessScenarioInputFactory.AFTERSALE_SAGA_SKILL);

        JsonNode orderItem = input.path("commands").get(7).path("items").get(0);
        assertThat(orderItem.path("listingId").asText()).isEqualTo("listing-1");
        assertThat(orderItem.path("listingOfferId").asText()).isEqualTo("offer-1");
        assertThat(orderItem.path("canonicalSkuId").asText()).isEqualTo("sku-1");
        JsonNode inventoryReceive = input.path("commands").get(6);
        assertThat(inventoryReceive.path("ownerId").asText()).isEqualTo("merchant-1");
        assertThat(inventoryReceive.path("canonicalSkuId").asText()).isEqualTo("sku-1");
        assertThat(inventoryReceive.path("warehouseId").asText()).isEqualTo("warehouse-1");
        assertThat(input.path("commands").get(12).path("sellerId").asText())
                .isEqualTo("merchant-1");
        assertThat(input.path("commands").get(13).path("warehouseId").asText())
                .isEqualTo("warehouse-1");
    }

    @Test
    void shouldBuildFreshPaidUnshippedOrderCancellationOperations() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
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
                RotatingBusinessScenarioInputFactory.ORDER_CANCELLATION_OPERATIONS_SKILL);
        JsonNode second = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.ORDER_CANCELLATION_OPERATIONS_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(first.path("runIds").path("orderScenario").asText())
                .startsWith("i260729162-").endsWith("-paid-unshipped-order");
        assertThat(first.path("runIds").path("orderScenario").asText())
                .isNotEqualTo(second.path("runIds").path("orderScenario").asText());
        assertThat(first.path("orderScenario").path("identityReference").path("sourceId").asText())
                .isEqualTo("286");
        assertThat(first.path("orderScenario").path("listingId").asText())
                .isEqualTo("listing-1");
        assertThat(first.path("orderScenario").path("commands").get(6)
                .path("operation").asText()).isEqualTo("CREATE");
        assertThat(first.path("operationsCommands")).hasSize(4);
        assertThat(first.path("operationsCommands").get(0)
                .path("operation").asText()).isEqualTo("OPEN_ALERT");
        assertThat(first.path("operationsCommands").get(0).path("alert")
                .path("category").asText()).isEqualTo("ORDER_EXCEPTION");
        assertThat(first.path("cancellation").path("operation").asText()).isEqualTo("START");
        assertThat(first.path("cancellation").path("cancellationMode").asText())
                .isEqualTo("PAID_UNSHIPPED");
        assertThat(first.path("cancellation").path("responsibilityCode").asText())
                .isEqualTo("BUYER_CHANGED_MIND");
    }

    @Test
    void shouldBuildOccurrenceFreshMesProductionExecutionWithIndependentReview() {
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

        JsonNode first = build(
                RotatingBusinessScenarioInputFactory.MES_PRODUCTION_EXECUTION_LIFECYCLE_SKILL);
        JsonNode replay = build(
                RotatingBusinessScenarioInputFactory.MES_PRODUCTION_EXECUTION_LIFECYCLE_SKILL);
        JsonNode nextOccurrence = JsonUtils.parseTree(factory.build(162L,
                RotatingBusinessScenarioInputFactory.MES_PRODUCTION_EXECUTION_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-2").orElseThrow());

        assertThat(replay).isEqualTo(first);
        assertThat(first).isNotEqualTo(nextOccurrence);
        assertThat(first.path("runIds").path("readiness").asText())
                .startsWith("z260729162-").endsWith("-production-readiness");
        assertThat(first.path("operatorUserId").asLong()).isEqualTo(1L);
        assertThat(first.path("reviewerUserId").asLong()).isEqualTo(227L);
        assertThat(first.path("readiness").path("item").path("code").asText())
                .startsWith("CMIZ260729162").isNotEqualTo(
                        nextOccurrence.path("readiness").path("item").path("code").asText());
        assertThat(first.path("readiness").path("item").path("batchFlag").asBoolean()).isFalse();
        assertThat(first.path("readiness").path("routeProcess").path("keyFlag").asBoolean()).isTrue();
        assertThat(first.path("readiness").path("routeProcess").path("checkFlag").asBoolean()).isFalse();
        assertThat(first.path("workOrder").path("quantity").asInt()).isEqualTo(12);
        assertThat(first.path("task").path("quantity").asInt()).isEqualTo(12);
        assertThat(first.path("feedback").path("feedbackQuantity").asInt()).isEqualTo(12);
        assertThat(first.path("feedback").path("qualifiedQuantity").asInt()).isEqualTo(12);
        assertThat(first.path("feedback").path("unqualifiedQuantity").asInt()).isZero();
        assertThat(first.path("feedback").path("feedbackUserId").asLong()).isEqualTo(1L);
        assertThat(first.path("feedback").path("approveUserId").asLong()).isEqualTo(227L);
    }

    @Test
    void shouldBuildApprovalSafeAssortmentPlanningInput() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory.ASSORTMENT_PLANNING_LIFECYCLE_SKILL);

        assertThat(input.path("commands")).hasSize(10);
        assertThat(input.path("commands").get(0).path("actorPrincipalId").asText())
                .isEqualTo("principal-1");
        assertThat(input.toString()).doesNotContain(
                "\"runId\":", "\"idempotencyKey\":", "\"approvalRef\":");
    }

    @Test
    void shouldBuildProductManagementFromAssortmentThroughVerifiedMysteryBuyerQuality() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.READY_MASTER_SKILL,
                "warehouse_network"))
                .thenReturn("{\"warehouseId\":\"warehouse-1\",\"locationId\":\"location-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory.PRODUCT_MANAGEMENT_LIFECYCLE_SKILL);

        assertThat(input.path("runIds").path("product").asText())
                .startsWith("pm260729162-").endsWith("-product");
        assertThat(input.path("managementDecision").path("supplyPoolScope").asText())
                .isEqualTo("PLATFORM_ALL_ELIGIBLE_SUPPLY");
        JsonNode qualityDimensions =
                input.path("managementDecision").path("qualityDimensions");
        assertThat(qualityDimensions).hasSize(4);
        assertThat(qualityDimensions.get(0).asText()).isEqualTo("PATTERN_REVIEW");
        assertThat(qualityDimensions.get(1).asText()).isEqualTo("PRICE_VERIFICATION");
        assertThat(qualityDimensions.get(2).asText()).isEqualTo("CONTENT_SHOOTING");
        assertThat(qualityDimensions.get(3).asText()).isEqualTo("MODEL_FITTING");
        assertThat(input.path("operationsCommands")).hasSize(4);
        assertThat(input.path("assortment").path("commands")).hasSize(10);
        assertThat(input.path("product").path("listing").path("commands")).hasSize(6);
        assertThat(input.path("mysteryPurchase").path("identityReference")
                .path("sourceId").asText()).isEqualTo("286");
        assertThat(input.path("mysteryPurchase").path("traceId").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(input.path("quality").path("commands")).hasSize(8);
        assertThat(input.path("quality").path("lotCommand").path("evidenceRef").asText())
                .startsWith("evidence:mystery-buyer-order/");
        assertThat(input.path("quality").path("traceId").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(input.path("quality").path("commands").get(6)
                .path("inspectionTask").path("decision").asText()).isEqualTo("PASS");
        assertThat(input.path("quality").path("commands").get(6)
                .path("inspectionTask").path("evidenceRef").asText())
                .startsWith("sha256:");
        assertThat(input.path("campaign").path("campaignCommand")
                .path("sourceType").asText()).isEqualTo("QUALITY_VERIFIED_NEW_PRODUCT");
        assertThat(input.toString()).doesNotContain(
                "\"runId\":", "\"idempotencyKey\":", "\"approvalRef\":");
    }

    @Test
    void shouldBuildWarehouseAdmissionOnlyFromLatestVerifiedProductManagementResult() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("{\"canonicalSpuId\":\"spu-product\",\"canonicalSkuId\":\"sku-product\"}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn(procurementSourcingSeed("sku-product"));
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_MANAGEMENT_LIFECYCLE_SKILL,
                "wait_platform_store_listing")).thenReturn("""
                {"outputs":{
                  "wait_master":{"outputs":{
                    "merchant_approve":{"merchantId":"merchant-product","shopId":"shop-product"},
                    "principal":{"principalId":"principal-product"},
                    "warehouse_network":{"warehouseId":"warehouse-product"}}},
                  "listing_create":{"listingId":"listing-product"}}}
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_MANAGEMENT_LIFECYCLE_SKILL,
                "wait_mystery_buyer_quality")).thenReturn("""
                {"outputs":{"verify_quality_badge":{
                  "canonicalSkuId":"sku-product","status":"VERIFIED",
                  "inspectionStatus":"COMPLETED","decision":"PASS"}}}
                """);

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory.WAREHOUSE_ADMISSION_LIFECYCLE_SKILL);

        assertThat(input.path("canonicalSkuId").asText()).isEqualTo("sku-product");
        assertThat(input.path("admissionDecision").path("decision").asText())
                .isEqualTo("APPROVE");
        assertThat(input.path("admissionDecision").path("qualityPolicy").asText())
                .isEqualTo("VERIFIED_ONLY");
        assertThat(input.path("merchant").path("draftCommand")
                .path("ownerPrincipalId").asText()).isEqualTo("principal-product");
        assertThat(input.path("replenishment").path("procurement").path("awardRelease")
                .path("command").path("expectedAwardVersion").asLong()).isEqualTo(3L);
        assertThat(input.path("replenishment").path("procurement").path("purchaseOrders")
                .get(0).path("commands").get(0).path("command").path("purchaseOrder")
                .has("lines")).isFalse();
        assertThat(input.path("traffic").path("campaignCommand")
                .path("sourceType").asText())
                .isEqualTo("QUALITY_VERIFIED_WAREHOUSE_ADMISSION");
        assertThat(input.path("traffic").path("campaignCommand")
                .path("sourceId").asText()).isEqualTo("listing:listing-product");
        assertThat(input.path("operationsCommands")).hasSize(4);
    }

    @Test
    void shouldNotBuildWarehouseAdmissionWhenQualityIsNotVerified() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_MANAGEMENT_LIFECYCLE_SKILL,
                "wait_platform_store_listing")).thenReturn("""
                {"outputs":{"listing_create":{"listingId":"listing-product"}}}
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_MANAGEMENT_LIFECYCLE_SKILL,
                "wait_mystery_buyer_quality")).thenReturn("""
                {"outputs":{"verify_quality_badge":{
                  "canonicalSkuId":"sku-product","status":"REJECTED"}}}
                """);

        assertThat(factory.build(162L,
                RotatingBusinessScenarioInputFactory.WAREHOUSE_ADMISSION_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-1")).isEmpty();
    }

    @Test
    void shouldBuildFreshConsumerExperienceResponsibilityCaseWithCompensation() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL);

        assertThat(input.path("runIds").path("consumer").asText())
                .startsWith("cx260729162-").endsWith("-consumer-experience");
        assertThat(input.path("consumer").path("identityReference")
                .path("sourceId").asText()).isEqualTo("286");
        assertThat(input.path("responsibilityDecision").path("responsibleParty").asText())
                .isEqualTo("MERCHANT");
        assertThat(input.path("responsibilityDecision").path("outcomeCode").asText())
                .isEqualTo("MERCHANT_RESPONSIBLE");
        assertThat(input.path("responsibilityDecision").path("reasonCode").asText())
                .isEqualTo("PRODUCT_DESCRIPTION_MISMATCH");
        assertThat(input.path("operationsCommands")).hasSize(4);
        assertThat(input.path("ticketCommands")).hasSize(11);
        assertThat(input.path("ticketCommands").get(6).path("operation").asText())
                .isEqualTo("RECORD_QUALITY_REVIEW");
        assertThat(input.path("ticketCommands").get(6).path("outcomeCode").asText())
                .isEqualTo("MERCHANT_RESPONSIBLE");
        assertThat(input.path("ticketCommands").get(7).path("operation").asText())
                .isEqualTo("REQUEST_CLAIM");
        assertThat(input.path("ticketCommands").get(9).path("operation").asText())
                .isEqualTo("PAY_COMPENSATION");
    }

    @Test
    void shouldBuildUnfulfillableOrderRefundInterceptAndCompensation() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory
                        .UNFULFILLABLE_ORDER_COMPENSATION_LIFECYCLE_SKILL);

        assertThat(input.path("runIds").path("orderCancellation").asText())
                .startsWith("uc260729162-").endsWith("-unfulfillable-cancellation");
        assertThat(input.path("orderCancellation").path("cancellation")
                .path("cancellationMode").asText()).isEqualTo("PAID_UNSHIPPED");
        assertThat(input.path("compensationPolicy").path("refundPolicy").asText())
                .isEqualTo("INSTANT_FULL_REFUND_AND_SHIPMENT_INTERCEPT");
        assertThat(input.path("ticketCommands")).hasSize(11);
        assertThat(input.path("ticketCommands").get(0).path("channelCode").asText())
                .isEqualTo("INTERNAL");
        assertThat(input.path("ticketCommands").get(6).path("outcomeCode").asText())
                .isEqualTo("PLATFORM_FULFILLMENT_RESPONSIBLE");
        assertThat(input.path("ticketCommands").get(7).path("requestedAmountMinor").asLong())
                .isEqualTo(5_000L);
        assertThat(input.path("ticketCommands").get(0).path("occurredAt").asText())
                .isEqualTo("2026-07-28T16:00:00Z");
        assertThat(input.path("operationsCommands")).hasSize(4);
    }

    @Test
    void shouldBuildRiskDisputeResolutionFromFreshPaidConsumerJourney() {
        properties.setSyntheticConsumerMemberUserId(286L);
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory.RISK_DISPUTE_RESOLUTION_LIFECYCLE_SKILL);

        assertThat(input.path("runIds").path("consumer").asText())
                .startsWith("rd260729162-").endsWith("-risk-consumer");
        assertThat(input.path("consumer").path("identityReference")
                .path("sourceId").asText()).isEqualTo("286");
        assertThat(input.path("operationsCommands")).hasSize(4);
        assertThat(input.path("operationsCommands").get(0).path("alert")
                .path("category").asText()).isEqualTo("RISK_OPERATIONS");
        assertThat(input.path("riskCommands")).hasSize(8);
        assertThat(input.path("riskCommands").get(0).path("operation").asText())
                .isEqualTo("CREATE_CLUSTER");
        assertThat(input.path("riskCommands").get(3).path("operation").asText())
                .isEqualTo("OPEN_PAYMENT_DISPUTE");
        assertThat(input.path("riskCommands").get(3).path("amountMinor").asLong())
                .isEqualTo(39_800L);
        assertThat(input.path("riskCommands").get(3).path("externalRef").asText())
                .matches("[A-Za-z0-9][A-Za-z0-9_-]{1,127}");
        assertThat(input.path("riskCommands").get(6).path("disputeStatus").asText())
                .isEqualTo("LOST");
        assertThat(input.path("riskCommands").get(7).path("lossEntryType").asText())
                .isEqualTo("CHARGEBACK_LOSS");
        assertThat(input.path("riskCommands").get(7).path("externalRef").asText())
                .matches("[A-Za-z0-9][A-Za-z0-9_-]{1,127}");
    }

    @Test
    void shouldBuildFreshDataQualityRecoveryEvidenceChain() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory.DATA_QUALITY_RECOVERY_LIFECYCLE_SKILL);

        assertThat(input.path("operationsCommands")).hasSize(4);
        assertThat(input.path("operationsCommands").get(0).path("alert")
                .path("category").asText()).isEqualTo("DATA_AI_OPERATIONS");
        assertThat(input.path("metadataCommands")).hasSize(14);
        assertThat(input.path("metadataCommands").get(0).path("operation").asText())
                .isEqualTo("PUBLISH_DATA_SOURCE");
        assertThat(input.path("metadataCommands").get(3).path("operation").asText())
                .isEqualTo("PUBLISH_LINEAGE");
        assertThat(input.path("metadataCommands").get(8).path("runStatus").asText())
                .isEqualTo("FAILED");
        assertThat(input.path("metadataCommands").get(9).path("dqcResultStatus").asText())
                .isEqualTo("FAIL");
        assertThat(input.path("metadataCommands").get(12).path("runStatus").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(input.path("metadataCommands").get(13).path("dqcResultStatus").asText())
                .isEqualTo("PASS");
        assertThat(input.path("metadataCommands").get(13).path("taskRunObservationSequence").asLong())
                .isEqualTo(6L);
    }

    @Test
    void shouldBuildThreeIndependentFinanceSettlementScenariosAndProfitLossCase() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));
        when(mapper.selectApprovalPolicy(162L)).thenReturn(new TemporalApprovalPolicyRecord()
                .setTenantId(162L).setGovernanceUserId(227L).setStatus("ACTIVE"));
        when(mapper.selectFirstEffectiveAgentRoleActor(162L, "finance")).thenReturn(228L);

        JsonNode logistics = build(
                RotatingBusinessScenarioInputFactory.LOGISTICS_SERVICE_SETTLEMENT_LIFECYCLE_SKILL);
        JsonNode merchant = build(
                RotatingBusinessScenarioInputFactory.MERCHANT_SERVICE_FEE_SETTLEMENT_LIFECYCLE_SKILL);
        JsonNode advertising = build(
                RotatingBusinessScenarioInputFactory.ADVERTISING_FEE_SETTLEMENT_LIFECYCLE_SKILL);
        JsonNode profitLoss = build(
                RotatingBusinessScenarioInputFactory.PROFIT_LOSS_IMPROVEMENT_LIFECYCLE_SKILL);

        assertThat(logistics.path("settlement").path("settlementContext")
                .path("channelCode").asText()).isEqualTo("LOGISTICS_WAREHOUSE_DISPATCH_PICKUP");
        assertThat(merchant.path("settlement").path("settlementContext")
                .path("feeAmountMinor").asLong()).isEqualTo(45_000L);
        assertThat(advertising.path("settlement").path("settlementContext")
                .path("channelCode").asText()).isEqualTo("PLATFORM_ADVERTISING");
        assertThat(logistics.path("settlement").path("commands")).hasSize(9);
        assertThat(merchant.path("settlement").path("makerIdentityCommand")
                .path("sourceId").asText()).isEqualTo("228");
        assertThat(advertising.path("settlement").path("checkerIdentityCommand")
                .path("sourceId").asText()).isEqualTo("227");
        assertThat(profitLoss.path("improvementTargets")
                .path("problemOrderRateDirection").asText()).isEqualTo("DOWN");
        assertThat(profitLoss.path("improvementTargets")
                .path("confirmedRevenueDirection").asText()).isEqualTo("UP");
        assertThat(profitLoss.path("operationsCommands")).hasSize(4);
        assertThat(profitLoss.path("operationsCommands").get(0)
                .path("occurredAt").asText()).isEqualTo("2026-07-28T16:00:00Z");
        assertThat(profitLoss.path("operationsCommands").get(0)
                .path("alert").path("sourceRef").asText()).startsWith("profit-loss:");
    }

    @Test
    void shouldBuildMerchantRectificationOnlyFromEffectiveResponsibilityAndPaidCompensation() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "ticket_terminal_readback")).thenReturn("""
                {"ticketId":"ticket-cx-1","ticketStatus":"CLOSED","ticketVersion":5}
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "ticket_responsibility_decision")).thenReturn("""
                {"reviewId":"review-cx-1","ticketId":"ticket-cx-1","ticketStatus":"RESOLVED"}
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "ticket_pay_compensation")).thenReturn("""
                {"claimStatus":"PAID","compensationEntryId":"compensation-cx-1"}
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "wait_consumer_journey")).thenReturn("""
                {"outputs":{
                  "consumer_principal":{"principalId":"principal-customer-1"}
                }}
                """);

        JsonNode input = build(
                RotatingBusinessScenarioInputFactory
                        .MERCHANT_EXPERIENCE_RECTIFICATION_LIFECYCLE_SKILL);

        assertThat(input.path("ticketId").asText()).isEqualTo("ticket-cx-1");
        assertThat(input.path("merchantReference").path("merchantId").asText())
                .isEqualTo("merchant-1");
        assertThat(input.path("trigger").path("triggerType").asText())
                .isEqualTo("MERCHANT_RESPONSIBILITY_EFFECTIVE");
        assertThat(input.path("trigger").path("responsibilityReviewId").asText())
                .isEqualTo("review-cx-1");
        assertThat(input.path("trigger").path("compensationEntryId").asText())
                .isEqualTo("compensation-cx-1");
        assertThat(input.path("operationsCommands").get(0).path("alert")
                .path("sourceRef").asText()).contains("merchant-1");
        assertThat(input.path("ticketCommands")).hasSize(6);
        assertThat(input.path("ticketCommands").get(0).path("operation").asText())
                .isEqualTo("REOPEN_TICKET");
        assertThat(input.path("ticketCommands").get(3).path("outcomeCode").asText())
                .isEqualTo("RECTIFICATION_VERIFIED");
        assertThat(input.path("ticketCommands").get(4).path("customerPrincipalId").asText())
                .isEqualTo("principal-customer-1");
    }

    @Test
    void shouldNotBuildMerchantRectificationBeforeResponsibilityCompensationIsPaid() {
        mockReadyMaster();
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.FULL_CHAIN_SKILL))
                .thenReturn(JsonUtils.toJsonString(completeAfterSaleTemplate()));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "ticket_terminal_readback")).thenReturn("""
                {"ticketId":"ticket-cx-1","ticketStatus":"CLOSED","ticketVersion":5}
                """);
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "ticket_responsibility_decision")).thenReturn("{\"reviewId\":\"review-cx-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L,
                RotatingBusinessScenarioInputFactory
                        .CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                "ticket_pay_compensation")).thenReturn("{\"claimStatus\":\"APPROVED\"}");

        assertThat(factory.build(
                162L,
                RotatingBusinessScenarioInputFactory
                        .MERCHANT_EXPERIENCE_RECTIFICATION_LIFECYCLE_SKILL,
                "2026-07-29", "temporal-run-1")).isEmpty();
    }

    private JsonNode build(String skillId) {
        return JsonUtils.parseTree(factory.build(162L, skillId,
                "2026-07-29", "temporal-run-1").orElseThrow());
    }

    private static ObjectNode completeAfterSaleTemplate() {
        ObjectNode template = JsonNodeFactory.instance.objectNode();
        template.putObject("runIds").put("catalog", "base-cat");
        template.putObject("catalog").putArray("definitions").addObject()
                .put("styleCode", "YS-BASEABC1").put("spuCode", "YS-BASEABC1")
                .put("occurredAt", "2026-01-01T00:00:00Z");
        ((ObjectNode) template.path("catalog")).putArray("lifecycle");
        template.putObject("master").putObject("identityReference");
        ((ObjectNode) template.path("master")).putObject("warehouseReference");
        ArrayNode commands = template.putObject("aftersale").putArray("commands");
        for (int index = 0; index < 25; index++) {
            ObjectNode command = commands.addObject()
                    .put("operation", index == 24 ? "ACCEPT_INSPECTION" : "TEST")
                    .put("idempotencyKey", "base-command-" + index)
                    .put("runId", "base-aftersale")
                    .put("correlationId", "70000000-0000-4000-8000-000000000001")
                    .put("occurredAt", "2026-01-01T00:00:00Z");
            if (index == 0) {
                command.putArray("offers").addObject();
            } else if (index == 7 || index == 12) {
                command.putArray("items").addObject();
            }
        }
        template.putObject("readback");
        return template;
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
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_1"))
                .thenReturn("{\"canonicalSpuId\":\"spu-1\",\"canonicalSkuId\":\"sku-1\"}");
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL, "define_2"))
                .thenReturn("{\"canonicalSpuId\":\"spu-2\",\"canonicalSkuId\":\"sku-2\"}");
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.CATALOG_MATRIX_SKILL))
                .thenReturn("""
                        {"definitions":[{"skuCode":"YS-BASE-BLACK-S",
                        "barcode":"CM-BASE-BLACK-S","baseUomCode":"PCS"},
                        {"skuCode":"YS-BASE-WHITE-M",
                        "barcode":"CM-BASE-WHITE-M","baseUomCode":"PCS"}]}
                        """);
        when(mapper.selectLatestSuccessfulSkillTaskInput(
                162L, RotatingBusinessScenarioInputFactory.PROCUREMENT_SOURCING_SKILL))
                .thenReturn(procurementSourcingSeed("sku-1"));
        when(mapper.selectLatestWmsCatalogProjectionSeed(162L))
                .thenReturn(new WmsCatalogProjectionSeedRecord()
                        .setCanonicalSkuId("sku-1")
                        .setSkuCode("YS-BASE-BLACK-S")
                        .setPrimaryBarcode("CM-BASE-BLACK-S")
                        .setBaseUomCode("PCS"));
        when(mapper.selectWmsTransferSeed(162L)).thenReturn(new WmsTransferSeedRecord()
                .setSourceWarehouseId(301L)
                .setTargetWarehouseId(302L)
                .setWmsSkuId(401L)
                .setItemId(501L)
                .setWmsSkuCode("YS-BASE-BLACK-S")
                .setWmsBarcode("CM-BASE-BLACK-S")
                .setItemUnit("PCS")
                .setTargetWarehouseMappingId("mapping-302")
                .setCanonicalWarehouseId("warehouse-1")
                .setCanonicalSkuId("sku-1")
                .setCatalogSkuCode("YS-BASE-BLACK-S")
                .setCatalogBarcode("CM-BASE-BLACK-S")
                .setBaseUomCode("PCS"));
        when(mapper.selectLatestSuccessfulSkillTaskStepResult(
                162L, RotatingBusinessScenarioInputFactory.PRODUCT_TO_LISTING_SKILL, "listing_create"))
                .thenReturn("""
                        {"listingId":"listing-1","offers":[{"listingOfferId":"offer-1"}]}
                        """);
    }

    private static String procurementSourcingSeed(String firstCanonicalSkuId) {
        return """
                {"schemaVersion":"cloudmold.procurement-sourcing-input/v1",
                 "legalEntityId":"legal-entity-01",
                 "supplierCandidates":[{"supplierId":"supplier-a"},{"supplierId":"supplier-b"}],
                 "valuationPolicies":[
                   {"canonicalSkuId":"%s","valuationPolicyId":"policy-1",
                    "valuationPolicyVersion":"2026.1","valuationPolicyHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                   {"canonicalSkuId":"sku-2","valuationPolicyId":"policy-2",
                    "valuationPolicyVersion":"2026.1","valuationPolicyHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}],
                 "actors":{"creatorPrincipalId":"principal-creator",
                           "reviewerAPrincipalId":"principal-reviewer-a",
                           "reviewerBPrincipalId":"principal-reviewer-b",
                           "approverPrincipalId":"principal-approver"}}
                """.formatted(firstCanonicalSkuId);
    }
}
