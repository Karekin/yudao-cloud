package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 测试环境业务场景轮转器。
 *
 * <p>它不直接写任何业务表，只把最近一次成功的全链路输入旋转为当日唯一场景，
 * 再交给 Temporal + SkillTask 按正常审批、幂等和领域命令执行。这样每天会产生
 * 新款号、新 SPU/SKU、新刊登和新订单，而不是反复重放同一个对象。</p>
 */
@Component
@RequiredArgsConstructor
class RotatingBusinessScenarioInputFactory {

    static final String FULL_CHAIN_SKILL = "skill.cloudmold.commerce.full-chain-hsf.v1";
    static final String PRODUCT_TO_LISTING_SKILL = "skill.cloudmold.commerce.product-to-listing.v1";
    static final String AUTONOMOUS_DAY_SKILL = "skill.cloudmold.commerce.autonomous-day.v1";
    static final String CATALOG_MATRIX_SKILL = "skill.cloudmold.commerce.catalog-matrix.v1";
    static final String AFTERSALE_SAGA_SKILL = "skill.cloudmold.commerce.aftersale-saga.v1";
    static final String CONSUMER_JOURNEY_SKILL = "skill.cloudmold.consumer.shopping-journey.v1";
    static final String MERCHANT_ONBOARDING_SKILL = "skill.cloudmold.merchant.onboarding-lifecycle.v1";
    static final String PROMOTION_CAMPAIGN_SKILL =
            "skill.cloudmold.engagement.promotion-campaign-operations.v1";
    static final String GROWTH_EXPERIMENT_SKILL =
            "skill.cloudmold.growth.experiment-lifecycle.v1";
    static final String SUPPLIER_SOURCING_SKILL =
            "skill.cloudmold.supplier.sourcing-lifecycle.v1";
    static final String PROCUREMENT_ORDER_SKILL =
            "skill.cloudmold.procurement.order-lifecycle.v1";
    static final String WMS_OPERATIONS_SKILL =
            "skill.cloudmold.wms.operations.v1";
    static final String REPLENISHMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.supply.replenishment-lifecycle.v1";
    static final String CUSTOMER_SERVICE_LIFECYCLE_SKILL =
            "skill.cloudmold.customer-service.resolution-lifecycle.v1";
    static final String FINANCE_CLOSE_LIFECYCLE_SKILL =
            "skill.cloudmold.finance.close-lifecycle.v1";
    static final String READY_MASTER_SKILL = "skill.cloudmold.commerce.reuse-ready-master.v1";
    static final String LEGACY_PROJECTION_SKILL = "skill.cloudmold.commerce.legacy-projection-plan.v1";
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    private final AiOperationsTemporalMapper mapper;
    private final AiOperationsTemporalSeedProperties seedProperties;

    Optional<String> build(Long tenantId, String targetSkillId, String businessDate,
                           String occurrenceKey) {
        if (!ManagedWorkflowDailyAutomationCatalog.isRotatingBusinessScenario(targetSkillId)) {
            return Optional.empty();
        }
        String templateJson = mapper.selectLatestSuccessfulSkillTaskInput(tenantId, FULL_CHAIN_SKILL);
        if (templateJson == null || templateJson.isBlank()) {
            return Optional.empty();
        }
        JsonNode parsed = JsonUtils.parseTree(templateJson);
        if (!(parsed instanceof ObjectNode template)) {
            return Optional.empty();
        }
        String catalogRunId = template.path("runIds").path("catalog").asText();
        if (!catalogRunId.endsWith("-cat")) {
            return Optional.empty();
        }
        LocalDate date = LocalDate.parse(businessDate);
        String oldPrefix = catalogRunId.substring(0, catalogRunId.length() - 4);
        String scenarioCode = scenarioCode(targetSkillId);
        String occurrenceToken = stableUuid(occurrenceKey).replace("-", "").substring(0, 8);
        String newPrefix = scenarioCode + date.format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%03d", Math.floorMod(tenantId, 1000)) + "-" + occurrenceToken;
        String occurredAt = date + "T00:00:00Z";
        String oldProductToken = productToken(template);
        String newProductToken = productToken(newPrefix);
        ObjectNode rotated = (ObjectNode) rotate(template, oldPrefix, newPrefix,
                oldProductToken, newProductToken, occurredAt, null);
        Optional<ObjectNode> reusableMaster = reusableMaster(tenantId, template, occurredAt);
        if (reusableMaster.isEmpty()) {
            return Optional.empty();
        }
        rotated.set("master", reusableMaster.get());
        JsonNode output;
        if (FULL_CHAIN_SKILL.equals(targetSkillId)) {
            output = rotated;
        } else if (CATALOG_MATRIX_SKILL.equals(targetSkillId)) {
            output = rotated.path("catalog").deepCopy();
        } else if (AFTERSALE_SAGA_SKILL.equals(targetSkillId)) {
            output = rotated.path("aftersale").deepCopy();
        } else if (PRODUCT_TO_LISTING_SKILL.equals(targetSkillId)) {
            output = productToListing(rotated, newPrefix);
        } else if (READY_MASTER_SKILL.equals(targetSkillId)) {
            output = reusableMaster.get();
        } else if (LEGACY_PROJECTION_SKILL.equals(targetSkillId)) {
            output = rotated.path("legacyProjection").deepCopy();
        } else if (MERCHANT_ONBOARDING_SKILL.equals(targetSkillId)) {
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = merchantOnboarding(newPrefix, occurredAt, principal.path("principalId").asText());
        } else if (PROMOTION_CAMPAIGN_SKILL.equals(targetSkillId)) {
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = promotionCampaign(newPrefix, occurredAt,
                    principal.path("principalId").asText());
        } else if (GROWTH_EXPERIMENT_SKILL.equals(targetSkillId)) {
            output = growthExperiment(newPrefix, occurredAt);
        } else if (SUPPLIER_SOURCING_SKILL.equals(targetSkillId)) {
            JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (catalog.path("canonicalSkuId").asText().isBlank()
                    || principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = supplierSourcing(newPrefix, occurredAt,
                    catalog.path("canonicalSkuId").asText(),
                    principal.path("principalId").asText());
        } else if (PROCUREMENT_ORDER_SKILL.equals(targetSkillId)) {
            JsonNode award = result(tenantId, SUPPLIER_SOURCING_SKILL, "supplier_award");
            JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (award.path("supplierId").asText().isBlank()
                    || catalog.path("canonicalSkuId").asText().isBlank()
                    || warehouse.path("warehouseId").asText().isBlank()
                    || principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = procurementOrder(newPrefix, occurredAt,
                    award.path("supplierId").asText(),
                    award.path("sourcingCaseId").asText(),
                    catalog.path("canonicalSkuId").asText(),
                    warehouse.path("warehouseId").asText(),
                    principal.path("principalId").asText());
        } else if (WMS_OPERATIONS_SKILL.equals(targetSkillId)) {
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()
                    || principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = wmsOperations(newPrefix, occurredAt,
                    merchant.path("merchantId").asText(),
                    merchant.path("shopId").asText(),
                    principal.path("principalId").asText());
        } else if (REPLENISHMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            if (merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()
                    || principal.path("principalId").asText().isBlank()
                    || catalog.path("canonicalSkuId").asText().isBlank()
                    || warehouse.path("warehouseId").asText().isBlank()) {
                return Optional.empty();
            }
            output = replenishmentLifecycle(newPrefix, occurredAt,
                    merchant.path("merchantId").asText(),
                    merchant.path("shopId").asText(),
                    principal.path("principalId").asText(),
                    catalog.path("canonicalSkuId").asText(),
                    warehouse.path("warehouseId").asText());
        } else if (CUSTOMER_SERVICE_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode customer = result(tenantId, CONSUMER_JOURNEY_SKILL, "consumer_principal");
            JsonNode order = result(tenantId, CONSUMER_JOURNEY_SKILL, "order_place");
            JsonNode agent = result(tenantId, READY_MASTER_SKILL, "principal");
            if (customer.path("principalId").asText().isBlank()
                    || order.path("orderId").asText().isBlank()
                    || agent.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = customerServiceLifecycle(newPrefix, occurredAt,
                    customer.path("principalId").asText(),
                    agent.path("principalId").asText(),
                    order.path("orderId").asText());
        } else if (FINANCE_CLOSE_LIFECYCLE_SKILL.equals(targetSkillId)) {
            TemporalApprovalPolicyRecord approvalPolicy = mapper.selectApprovalPolicy(tenantId);
            Long financeMakerUserId = mapper.selectFirstEffectiveAgentRoleActor(tenantId, "finance");
            Long financeCheckerUserId = approvalPolicy == null
                    ? null : approvalPolicy.getGovernanceUserId();
            if (financeMakerUserId == null || financeMakerUserId <= 0
                    || financeCheckerUserId == null || financeCheckerUserId <= 0
                    || financeMakerUserId.equals(financeCheckerUserId)) {
                return Optional.empty();
            }
            output = financeCloseLifecycle(newPrefix, occurredAt,
                    financeMakerUserId, financeCheckerUserId);
        } else {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (CONSUMER_JOURNEY_SKILL.equals(targetSkillId)) {
                if (!hydrateConsumerJourney(tenantId, consumer)) {
                    return Optional.empty();
                }
                output = consumer;
            } else {
                output = autonomousDay(rotated, newPrefix, consumer);
            }
        }
        return Optional.of(JsonUtils.toJsonString(output));
    }

    private static String scenarioCode(String skillId) {
        return switch (skillId) {
            case FULL_CHAIN_SKILL -> "f";
            case PRODUCT_TO_LISTING_SKILL -> "p";
            case AUTONOMOUS_DAY_SKILL -> "d";
            case CATALOG_MATRIX_SKILL -> "c";
            case AFTERSALE_SAGA_SKILL -> "a";
            case CONSUMER_JOURNEY_SKILL -> "u";
            case MERCHANT_ONBOARDING_SKILL -> "h";
            case PROMOTION_CAMPAIGN_SKILL -> "e";
            case GROWTH_EXPERIMENT_SKILL -> "g";
            case SUPPLIER_SOURCING_SKILL -> "s";
            case PROCUREMENT_ORDER_SKILL -> "q";
            case WMS_OPERATIONS_SKILL -> "w";
            case REPLENISHMENT_LIFECYCLE_SKILL -> "r";
            case CUSTOMER_SERVICE_LIFECYCLE_SKILL -> "k";
            case FINANCE_CLOSE_LIFECYCLE_SKILL -> "n";
            case READY_MASTER_SKILL -> "m";
            case LEGACY_PROJECTION_SKILL -> "l";
            default -> throw new IllegalArgumentException("Unsupported rotating scenario Skill: " + skillId);
        };
    }

    private boolean hydrateConsumerJourney(Long tenantId, ObjectNode consumer) {
        JsonNode listing = result(tenantId, PRODUCT_TO_LISTING_SKILL, "listing_create");
        JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
        JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
        JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
        JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
        String listingId = listing.path("listingId").asText();
        String listingOfferId = listing.path("offers").path(0).path("listingOfferId").asText();
        String canonicalSpuId = catalog.path("canonicalSpuId").asText();
        String canonicalSkuId = catalog.path("canonicalSkuId").asText();
        String merchantId = merchant.path("merchantId").asText();
        String warehouseId = warehouse.path("warehouseId").asText();
        String principalId = principal.path("principalId").asText();
        if (listingId.isBlank() || listingOfferId.isBlank() || canonicalSpuId.isBlank()
                || canonicalSkuId.isBlank() || merchantId.isBlank() || warehouseId.isBlank()
                || principalId.isBlank()) {
            return false;
        }
        consumer.put("listingId", listingId);
        for (int index = 4; index <= 7; index++) {
            ObjectNode behavior = objectAt(consumer.path("behaviorCommands"), index);
            behavior.put("canonicalSpuId", canonicalSpuId)
                    .put("skuId", canonicalSkuId)
                    .put("listingId", listingId)
                    .put("listingOfferId", listingOfferId);
        }
        objectAt(consumer.path("favoriteCommand"), -1).put("canonicalSpuId", canonicalSpuId);
        ObjectNode receive = objectAt(consumer.path("commands"), 0);
        receive.put("ownerId", merchantId).put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId);
        ObjectNode orderItem = objectAt(objectAt(consumer.path("commands"), 1).path("items"), 0);
        orderItem.put("canonicalSkuId", canonicalSkuId).put("listingId", listingId)
                .put("listingOfferId", listingOfferId);
        ObjectNode reserve = objectAt(consumer.path("commands"), 2);
        reserve.put("ownerId", merchantId).put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId);
        ObjectNode fulfillment = objectAt(consumer.path("commands"), 6);
        fulfillment.put("sellerId", merchantId).put("warehouseId", warehouseId);
        objectAt(fulfillment.path("items"), 0).put("canonicalSkuId", canonicalSkuId);
        ObjectNode ship = objectAt(consumer.path("commands"), 7);
        ship.put("ownerId", merchantId).put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId);
        objectAt(consumer.path("customerServiceCommands"), 3)
                .put("assignedAgentPrincipalId", principalId);
        ObjectNode community = objectAt(consumer.path("communityCommands"), 0);
        community.put("canonicalSpuId", canonicalSpuId).put("canonicalSkuId", canonicalSkuId)
                .put("listingId", listingId).put("listingOfferId", listingOfferId);
        objectAt(consumer.path("communityCommands"), 2).put("actorPrincipalId", principalId);
        return true;
    }

    private static ObjectNode objectAt(JsonNode value, int index) {
        JsonNode selected = index < 0 ? value : value.path(index);
        if (!(selected instanceof ObjectNode object)) {
            throw new IllegalStateException("Synthetic business scenario template is incomplete");
        }
        return object;
    }

    private Optional<ObjectNode> reusableMaster(Long tenantId, ObjectNode template, String eligibilityAt) {
        JsonNode merchant = result(tenantId, "merchant_approve");
        JsonNode warehouse = result(tenantId, "warehouse_network");
        JsonNode identityReference = template.path("master").path("identityReference");
        JsonNode warehouseReference = template.path("master").path("warehouseReference");
        if (!merchant.isObject() || !warehouse.isObject()
                || !identityReference.isObject() || !warehouseReference.isObject()
                || merchant.path("merchantId").asText().isBlank()
                || merchant.path("shopId").asText().isBlank()
                || warehouse.path("warehouseId").asText().isBlank()) {
            return Optional.empty();
        }
        ObjectNode master = JsonNodeFactory.instance.objectNode();
        master.put("eligibilityAt", eligibilityAt);
        master.set("identityReference", identityReference.deepCopy());
        master.putObject("merchantReference")
                .put("merchantId", merchant.path("merchantId").asText())
                .put("shopId", merchant.path("shopId").asText());
        master.set("warehouseReference", warehouseReference.deepCopy());
        return Optional.of(master);
    }

    private JsonNode result(Long tenantId, String stepCode) {
        return result(tenantId, READY_MASTER_SKILL, stepCode);
    }

    private JsonNode result(Long tenantId, String skillId, String stepCode) {
        String result = mapper.selectLatestSuccessfulSkillTaskStepResult(tenantId, skillId, stepCode);
        return result == null || result.isBlank()
                ? JsonNodeFactory.instance.missingNode() : JsonUtils.parseTree(result);
    }

    private static ObjectNode productToListing(ObjectNode fullChain, String prefix) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        ObjectNode runIds = output.putObject("runIds");
        runIds.put("catalog", prefix + "-cat");
        runIds.put("master", prefix + "-master");
        output.set("catalog", fullChain.path("catalog").deepCopy());
        output.set("master", fullChain.path("master").deepCopy());
        ArrayNode listingCommands = output.putObject("listing").putArray("commands");
        JsonNode sourceCommands = fullChain.path("aftersale").path("commands");
        for (int index = 0; index < 6 && index < sourceCommands.size(); index++) {
            listingCommands.add(sourceCommands.get(index).deepCopy());
        }
        if (!listingCommands.isEmpty() && listingCommands.get(0) instanceof ObjectNode createCommand) {
            JsonNode sourceOffer = createCommand.path("offers").path(0);
            if (sourceOffer.isObject()) {
                ArrayNode offers = JsonNodeFactory.instance.arrayNode();
                for (int index = 0; index < 6; index++) {
                    ObjectNode offer = (ObjectNode) sourceOffer.deepCopy();
                    offer.put("externalOfferId", "internal-offer:" + prefix + ":" + (index + 1));
                    offers.add(offer);
                }
                createCommand.set("offers", offers);
            }
        }
        output.putObject("listingReceipt")
                .put("idempotencyKey", prefix + "-channel-receipt")
                .put("listingId", ZERO_UUID)
                .put("expectedVersion", 6)
                .put("outcome", "CONFIRMED_PUBLISHED")
                .put("channelListingId", "internal-channel:" + prefix)
                .put("channelStatus", "ONLINE")
                .put("confirmedAt", fullChain.path("catalog").path("definitions").path(0)
                        .path("occurredAt").asText())
                .put("evidenceRef", "synthetic:yshopping-internal:" + prefix)
                .put("correlationId", stableUuid(prefix + ":channel-confirmation"))
                .put("occurredAt", fullChain.path("catalog").path("definitions").path(0)
                        .path("occurredAt").asText());
        output.set("readback", JsonNodeFactory.instance.objectNode());
        return output;
    }

    private static ObjectNode autonomousDay(ObjectNode fullChain, String prefix, ObjectNode consumer) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        ObjectNode runIds = output.putObject("runIds");
        runIds.put("product", prefix + "-product");
        runIds.put("consumer", prefix + "-consumer");
        output.set("product", productToListing(fullChain, prefix));
        output.set("consumer", consumer);
        return output;
    }

    private static ObjectNode merchantOnboarding(String prefix, String occurredAt, String ownerPrincipalId) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        String correlationId = stableUuid(prefix + ":merchant-onboarding");
        input.put("correlationId", correlationId);
        input.put("occurredAt", occurredAt);
        input.putObject("draftCommand")
                .put("legalName", "AI 模拟商家 " + prefix.toUpperCase())
                .put("registrationHashToken", "sha256:" + stableUuid(prefix + ":registration").replace("-", ""))
                .put("businessLicenseToken", "restricted:merchant-license:" + prefix)
                .put("ownerPrincipalId", ownerPrincipalId)
                .put("channelCode", "YSHOPPING_INTERNAL")
                .put("externalShopId", "ai-shop-" + prefix)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        return input;
    }

    private static ObjectNode promotionCampaign(String prefix, String occurredAt,
                                                String principalId) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        String campaignId = stableUuid(prefix + ":notification-campaign");
        String deliveryId = stableUuid(prefix + ":notification-delivery");
        String correlationId = stableUuid(prefix + ":promotion-correlation");
        input.putObject("campaignCommand")
                .put("campaignId", campaignId)
                .put("campaignCode", "AI-CMP-" + prefix.toUpperCase())
                .put("campaignName", "AI 每日用户召回活动 " + prefix.toUpperCase())
                .put("channel", "APP_PUSH")
                .put("sourceSystem", "CLOUDMOLD_AI")
                .put("sourceType", "DAILY_CAMPAIGN")
                .put("sourceId", "campaign:" + prefix)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        input.putObject("deliveryCommand")
                .put("deliveryId", deliveryId)
                .put("deliveryKey", "AI-DELIVERY-" + prefix.toUpperCase())
                .put("campaignId", campaignId)
                .put("principalId", principalId)
                .put("channel", "APP_PUSH")
                .put("destinationToken", "restricted:synthetic-device:" + prefix)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        input.putObject("attemptCommand")
                .put("attemptId", stableUuid(prefix + ":notification-attempt"))
                .put("deliveryId", deliveryId)
                .put("attemptNo", 1)
                .put("providerCode", "CLOUDMOLD_SIMULATOR")
                .put("providerReference", "provider:" + prefix)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        notificationReceipt(input, "deliveredReceiptCommand", prefix, deliveryId,
                correlationId, occurredAt, "delivered");
        notificationReceipt(input, "openedReceiptCommand", prefix, deliveryId,
                correlationId, occurredAt, "opened");
        notificationReceipt(input, "clickedReceiptCommand", prefix, deliveryId,
                correlationId, occurredAt, "clicked");
        return input;
    }

    private static void notificationReceipt(ObjectNode input, String field, String prefix,
                                            String deliveryId, String correlationId,
                                            String occurredAt, String status) {
        input.putObject(field)
                .put("receiptId", stableUuid(prefix + ":notification-receipt:" + status))
                .put("deliveryId", deliveryId)
                .put("externalReceiptId", "receipt:" + prefix + ":" + status)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static ObjectNode growthExperiment(String prefix, String occurredAt) {
        Instant base = Instant.parse(occurredAt);
        String runningAt = base.plus(10, ChronoUnit.MINUTES).toString();
        String snapshotAt = base.plus(19, ChronoUnit.MINUTES).toString();
        String concludedAt = base.plus(20, ChronoUnit.MINUTES).toString();
        String experimentId = stableUuid(prefix + ":growth-experiment");
        String campaignId = stableUuid(prefix + ":growth-campaign");
        String correlationId = stableUuid(prefix + ":growth-correlation");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("correlationId", correlationId)
                .put("runningAt", runningAt)
                .put("snapshotAt", snapshotAt)
                .put("concludedAt", concludedAt);
        input.putObject("campaign")
                .put("campaignId", campaignId)
                .put("campaignCode", "AI-GROWTH-" + prefix.toUpperCase())
                .put("campaignKind", "GENERAL")
                .put("name", "AI 每日转化优化实验 " + prefix.toUpperCase())
                .put("startsAt", base.toString())
                .put("endsAt", base.plus(1, ChronoUnit.DAYS).toString());
        ObjectNode experiment = input.putObject("experiment")
                .put("experimentId", experimentId)
                .put("experimentCode", "AI-EXP-" + prefix.toUpperCase())
                .put("campaignId", campaignId)
                .put("name", "AI 商详到支付转化实验 " + prefix.toUpperCase())
                .put("hypothesis", "个性化卖点排序能够提高支付转化率")
                .put("primaryMetricCode", "PAYMENT_CONVERSION")
                .put("minimumSampleSizePerVariant", 1)
                .put("startsAt", base.toString())
                .put("endsAt", concludedAt);
        ArrayNode variants = experiment.putArray("variants");
        variants.addObject().put("variantCode", "CONTROL")
                .put("variantKind", "CONTROL").put("allocationBasisPoints", 5000);
        variants.addObject().put("variantCode", "TREATMENT")
                .put("variantKind", "TREATMENT").put("allocationBasisPoints", 5000);
        growthExposure(input, "controlExposure", prefix, experimentId, "CONTROL");
        growthExposure(input, "treatmentExposure", prefix, experimentId, "TREATMENT");
        growthMetric(input, "controlMetric", prefix, experimentId, "CONTROL",
                base, snapshotAt, concludedAt, 420_000L);
        growthMetric(input, "treatmentMetric", prefix, experimentId, "TREATMENT",
                base, snapshotAt, concludedAt, 610_000L);
        input.putObject("conclusion")
                .put("experimentId", experimentId)
                .put("decision", "TREATMENT")
                .put("confidenceBasisPoints", 9700)
                .put("guardrailStatus", "PASSED")
                .put("evidenceRef", "evidence://growth/" + prefix + "/conclusion")
                .put("reason", "治疗组达到主指标门槛且护栏全部通过");
        return input;
    }

    private static void growthExposure(ObjectNode input, String field, String prefix,
                                       String experimentId, String variantCode) {
        String suffix = variantCode.toLowerCase();
        input.putObject(field)
                .put("exposureId", stableUuid(prefix + ":growth-exposure:" + suffix))
                .put("exposureKey", "exposure:" + prefix + ":" + suffix)
                .put("experimentId", experimentId)
                .put("variantCode", variantCode)
                .put("principalId", "synthetic-buyer:" + prefix + ":" + suffix)
                .put("assignmentVersion", "v1");
    }

    private static void growthMetric(ObjectNode input, String field, String prefix,
                                     String experimentId, String variantCode, Instant measuredFrom,
                                     String measuredTo, String freshUntil, long valueMicros) {
        String suffix = variantCode.toLowerCase();
        input.putObject(field)
                .put("snapshotId", stableUuid(prefix + ":growth-metric:" + suffix))
                .put("snapshotKey", "snapshot:" + prefix + ":" + suffix)
                .put("experimentId", experimentId)
                .put("variantCode", variantCode)
                .put("metricCode", "PAYMENT_CONVERSION")
                .put("measuredFrom", measuredFrom.toString())
                .put("measuredTo", measuredTo)
                .put("sampleCount", 1)
                .put("metricValueMicros", valueMicros)
                .put("dataFreshUntil", freshUntil)
                .put("evidenceRef", "evidence://growth/" + prefix + "/" + suffix);
    }

    private static ObjectNode supplierSourcing(String prefix, String occurredAt,
                                               String canonicalSkuId, String actorPrincipalId) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String caseId = stableUuid(prefix + ":sourcing-case");
        String supplierA = stableUuid(prefix + ":supplier:a");
        String supplierB = stableUuid(prefix + ":supplier:b");
        String quoteA = stableUuid(prefix + ":quote:a");
        String quoteB = stableUuid(prefix + ":quote:b");
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("correlationId", stableUuid(prefix + ":supplier-correlation"))
                .put("occurredAt", occurredAt)
                .put("actorPrincipalId", actorPrincipalId);
        supplier(input, "supplierA", supplierA, "A", prefix, "LOW");
        supplier(input, "supplierB", supplierB, "B", prefix, "MEDIUM");
        admission(input, "admissionA", supplierA,
                DigestUtil.sha256Hex(prefix + ":qualification:a"),
                DigestUtil.sha256Hex(prefix + ":risk:a"));
        admission(input, "admissionB", supplierB,
                DigestUtil.sha256Hex(prefix + ":qualification:b"),
                DigestUtil.sha256Hex(prefix + ":risk:b"));
        input.putObject("sourcingCase")
                .put("sourcingCaseId", caseId)
                .put("rfqCode", "AI-RFQ-" + prefix.toUpperCase())
                .put("requestRef", "assortment:" + prefix)
                .put("canonicalSkuId", canonicalSkuId)
                .put("targetQuantity", 500)
                .put("uomCode", "EA")
                .put("currencyCode", "CNY")
                .put("maxUnitCostMinor", 2500)
                .put("requiredDeliveryDate", date.plusDays(21).toString())
                .put("requirements", "质量、交期、成本和产能须同时达到 AI 选品计划门槛");
        quote(input, "quoteA", quoteA, caseId, supplierA,
                date.plusDays(14), 2100, 14, 800);
        quote(input, "quoteB", quoteB, caseId, supplierB,
                date.plusDays(14), 2350, 12, 700);
        sample(input, "sampleA", prefix, "a", caseId, supplierA, quoteA,
                93, 91, 88, 90, "PASS");
        sample(input, "sampleB", prefix, "b", caseId, supplierB, quoteB,
                86, 84, 92, 82, "PASS");
        input.putObject("award")
                .put("sourcingCaseId", caseId)
                .put("supplierId", supplierA)
                .put("quoteId", quoteA)
                .put("decisionRationale", "比较两家合格供应商的成本、样品、交期和产能后选择综合得分最高者");
        return input;
    }

    private static void supplier(ObjectNode input, String field, String supplierId,
                                 String suffix, String prefix, String riskLevel) {
        input.putObject(field)
                .put("supplierId", supplierId)
                .put("supplierCode", "AI_SUP_" + suffix + "_" + prefix.replace("-", "").toUpperCase())
                .put("supplierName", "AI 模拟供应商 " + suffix + " " + prefix.toUpperCase())
                .put("countryCode", "CN")
                .put("capabilitySummary", "服装柔性供应、快速打样、稳定交付与可审计质量体系")
                .put("riskLevel", riskLevel)
                .put("reasonCode", "AI_DAILY_SOURCING");
    }

    private static void admission(ObjectNode input, String field, String supplierId,
                                  String qualificationHash, String riskHash) {
        input.putObject(field)
                .put("supplierId", supplierId)
                .put("qualificationEvidenceSha256", qualificationHash)
                .put("riskEvidenceSha256", riskHash)
                .put("reasonCode", "AI_EVIDENCE_VERIFIED");
    }

    private static void quote(ObjectNode input, String field, String quoteId, String caseId,
                              String supplierId, LocalDate validUntil, long unitCostMinor,
                              int leadTimeDays, int capacity) {
        input.putObject(field)
                .put("quoteId", quoteId)
                .put("sourcingCaseId", caseId)
                .put("supplierId", supplierId)
                .put("quoteVersion", 1)
                .put("unitCostMinor", unitCostMinor)
                .put("moq", 100)
                .put("leadTimeDays", leadTimeDays)
                .put("capacityQuantity", capacity)
                .put("validUntil", validUntil.toString())
                .put("termsSummary", "含税到仓，样品通过后按采购订单交付");
    }

    private static void sample(ObjectNode input, String field, String prefix, String suffix,
                               String caseId, String supplierId, String quoteId,
                               int quality, int fit, int delivery, int risk, String result) {
        input.putObject(field)
                .put("evaluationId", stableUuid(prefix + ":sample:" + suffix))
                .put("sourcingCaseId", caseId)
                .put("supplierId", supplierId)
                .put("quoteId", quoteId)
                .put("qualityScore", quality)
                .put("fitScore", fit)
                .put("deliveryScore", delivery)
                .put("riskScore", risk)
                .put("result", result)
                .put("notes", "AI 样品评估覆盖质量、版型、交期与供应风险");
    }

    private static ObjectNode procurementOrder(String prefix, String occurredAt,
                                               String supplierId, String sourcingCaseId,
                                               String canonicalSkuId, String warehouseId,
                                               String actorPrincipalId) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("correlationId", stableUuid(prefix + ":procurement-correlation"))
                .put("occurredAt", occurredAt)
                .put("actorPrincipalId", actorPrincipalId);
        input.putObject("purchaseOrder")
                .put("orderId", stableUuid(prefix + ":purchase-order"))
                .put("orderCode", "AI-PO-" + prefix.toUpperCase())
                .put("sourceBusinessType", "SUPPLIER_SOURCING")
                .put("sourceBusinessRef", sourcingCaseId)
                .put("supplierRef", supplierId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("canonicalWarehouseId", warehouseId)
                .put("orderedQuantity", 500)
                .put("uomCode", "EA")
                .put("unitCostMinor", 2100)
                .put("totalAmountMinor", 1_050_000)
                .put("currencyCode", "CNY")
                .put("leadTimeDays", 14)
                .put("requiredDeliveryDate", date.plusDays(21).toString())
                .put("remark", "AI 根据定标结果创建并向供应商下发采购订单")
                .put("reasonCode", "AI_SOURCING_AWARD");
        return input;
    }

    private static ObjectNode wmsOperations(String prefix, String occurredAt,
                                            String merchantId, String shopId,
                                            String principalId) {
        String compact = prefix.replace("-", "").toUpperCase();
        String code = compact.length() > 16 ? compact.substring(0, 16) : compact;
        String date = occurredAt.substring(0, 10);
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.putObject("authority").putObject("reference")
                .put("merchantId", merchantId)
                .put("shopId", shopId);
        input.withObject("/authority/operator")
                .put("merchantId", merchantId)
                .put("shopId", shopId)
                .put("principalId", principalId)
                .put("roleCode", "OWNER");
        input.putObject("merchant")
                .put("code", "AIM" + code)
                .put("name", "AI 每日仓储商家 " + prefix.toUpperCase())
                .put("type", 3)
                .put("level", "A")
                .put("address", "Shanghai")
                .put("mobile", "13800000001")
                .put("contact", "AI Warehouse Operator")
                .put("email", "warehouse-" + code.toLowerCase() + "@cloudmold.test")
                .put("remark", "Temporal 每日仓储运营造数");
        input.putObject("sourceWarehouse")
                .put("code", "AIS" + code)
                .put("name", "AI 每日仓储源仓 " + prefix.toUpperCase())
                .put("sort", 1)
                .put("remark", "每日收货源仓");
        input.putObject("targetWarehouse")
                .put("code", "AIT" + code)
                .put("name", "AI 每日仓储目标仓 " + prefix.toUpperCase())
                .put("sort", 2)
                .put("remark", "每日调拨与发货目标仓");
        input.putObject("category")
                .put("parentId", 0)
                .put("code", "AIC" + code)
                .put("name", "AI 每日仓储商品")
                .put("sort", 1)
                .put("status", 0);
        ObjectNode item = input.putObject("item")
                .put("code", "AII" + code)
                .put("name", "AI 仓储全链路商品 " + prefix.toUpperCase())
                .put("unit", "件")
                .put("remark", "每日唯一收货、调拨、出库与盘点商品");
        item.putArray("skus").addObject()
                .put("name", "标准款")
                .put("barCode", "697" + String.format("%010d",
                        Math.floorMod(prefix.hashCode(), 10_000_000_000L)))
                .put("code", "AIK" + code)
                .put("length", 10)
                .put("width", 8)
                .put("height", 2)
                .put("grossWeight", 0.5)
                .put("netWeight", 0.45)
                .put("costPrice", 39.8)
                .put("sellingPrice", 199);
        input.putObject("receipt")
                .put("no", "AIRK" + code)
                .put("orderTime", date + "T02:00:00")
                .put("bizOrderNo", "AIPO" + code)
                .put("remark", "AI 每日采购收货");
        input.putObject("movement")
                .put("no", "AIDB" + code)
                .put("orderTime", date + "T02:10:00")
                .put("remark", "AI 每日仓间调拨");
        input.putObject("shipment")
                .put("no", "AICK" + code)
                .put("orderTime", date + "T02:20:00")
                .put("bizOrderNo", "AISO" + code)
                .put("remark", "AI 每日销售出库");
        input.putObject("check")
                .put("no", "AIPD" + code)
                .put("orderTime", date + "T02:30:00")
                .put("receiptTime", date + "T02:00:00")
                .put("remark", "AI 每日零差异盘点");
        input.putObject("quantities")
                .put("receipt", 10)
                .put("receiptTotalPrice", 398)
                .put("movement", 3)
                .put("movementTotalPrice", 119.4)
                .put("shipment", 2)
                .put("shipmentTotalPrice", 79.6)
                .put("check", 1);
        return input;
    }

    private static ObjectNode replenishmentLifecycle(String prefix, String occurredAt,
                                                     String merchantId, String shopId,
                                                     String principalId, String canonicalSkuId,
                                                     String warehouseId) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        boolean promotion = date.getDayOfMonth() % 5 == 0;
        int orderedQuantity = promotion ? 2_000 : 500;
        ObjectNode sourcing = supplierSourcing(prefix + "-src", occurredAt,
                canonicalSkuId, principalId);
        sourcing.withObject("/sourcingCase").put("targetQuantity", orderedQuantity);
        String sourcingCaseId = sourcing.path("sourcingCase").path("sourcingCaseId").asText();
        String supplierId = sourcing.path("supplierA").path("supplierId").asText();
        ObjectNode procurement = procurementOrder(prefix + "-po", occurredAt,
                supplierId, sourcingCaseId, canonicalSkuId, warehouseId, principalId);
        procurement.withObject("/purchaseOrder")
                .put("orderedQuantity", orderedQuantity)
                .put("totalAmountMinor", orderedQuantity * 2_100L)
                .put("remark", promotion
                        ? "AI 根据大促销量预测与安全库存缺口下发加急补货"
                        : "AI 根据日销、在途与安全库存缺口下发日常补货");
        ObjectNode physical = wmsOperations(prefix + "-wms", occurredAt,
                merchantId, shopId, principalId);
        if (promotion) {
            physical.withObject("/quantities")
                    .put("receipt", 30)
                    .put("receiptTotalPrice", 1_194)
                    .put("movement", 10)
                    .put("movementTotalPrice", 398)
                    .put("shipment", 5)
                    .put("shipmentTotalPrice", 199)
                    .put("check", 5);
        }
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("scenarioType", promotion ? "PROMOTION_REPLENISHMENT" : "DAILY_REPLENISHMENT");
        input.put("replenishmentReceiptRemark", promotion
                ? "AI 大促补货采购到仓"
                : "AI 日常补货采购到仓");
        input.putObject("runIds")
                .put("sourcing", prefix + "-sourcing")
                .put("procurement", prefix + "-procurement")
                .put("warehouse", prefix + "-warehouse");
        input.set("sourcing", sourcing);
        input.set("procurement", procurement);
        input.set("warehouse", physical);
        return input;
    }

    private static ObjectNode customerServiceLifecycle(String prefix, String occurredAt,
                                                       String customerPrincipalId,
                                                       String agentPrincipalId,
                                                       String orderId) {
        String runId = stableUuid(prefix + ":customer-service-run");
        String correlationId = stableUuid(prefix + ":customer-service-correlation");
        ArrayNode commands = JsonNodeFactory.instance.arrayNode();
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CREATE_TICKET")
                .put("ticketNo", "AI-CS-" + prefix.toUpperCase())
                .put("customerPrincipalId", customerPrincipalId)
                .put("channelCode", "APP")
                .put("priority", "NORMAL")
                .put("categoryCode", "ORDER_CONSULTATION"));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "LINK_REFERENCE")
                .put("ticketId", ZERO_UUID)
                .put("referenceSourceSystem", "cloudmold-order")
                .put("referenceType", "ORDER")
                .put("referenceId", orderId));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RECORD_MESSAGE")
                .put("ticketId", ZERO_UUID)
                .put("direction", "INBOUND")
                .put("senderType", "CUSTOMER")
                .put("senderPrincipalId", customerPrincipalId)
                .put("messageType", "TEXT")
                .put("contentToken", "restricted:daily_delivery_question_" + prefix));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "ASSIGN_AGENT")
                .put("ticketId", ZERO_UUID)
                .put("assignedAgentPrincipalId", agentPrincipalId));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "START_PROCESSING")
                .put("ticketId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RESOLVE_TICKET")
                .put("ticketId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId,
                        "RECORD_BUYER_FEEDBACK")
                .put("ticketId", ZERO_UUID)
                .put("customerPrincipalId", customerPrincipalId)
                .put("touchpointCode", "TICKET_RESOLUTION")
                .put("sentimentCode", "SATISFIED")
                .put("commentToken", "restricted:daily_resolution_satisfied_" + prefix));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CLOSE_TICKET")
                .put("ticketId", ZERO_UUID));
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.set("commands", commands);
        return input;
    }

    private static ObjectNode financeCloseLifecycle(String prefix, String occurredAt,
                                                    long makerUserId, long checkerUserId) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String runId = prefix + "-finance-close";
        String correlationId = stableUuid(prefix + ":finance-close-correlation");
        String periodId = stableUuid(prefix + ":accounting-period");
        String statementId = stableUuid(prefix + ":channel-statement");
        String settlementId = stableUuid(prefix + ":settlement-batch");
        String journalId = stableUuid(prefix + ":journal-entry");
        long grossAmountMinor = 1_200_000L;
        long refundAmountMinor = 100_000L;
        long feeAmountMinor = 50_000L;
        long netAmountMinor = grossAmountMinor - refundAmountMinor - feeAmountMinor;
        long differenceAmountMinor = 10_000L;

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.set("makerIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, makerUserId));
        input.set("checkerIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, checkerUserId));
        ArrayNode commands = input.putArray("commands");

        commands.add(financeCommand(runId, correlationId, occurredAt, "OPEN_ACCOUNTING_PERIOD")
                .set("period", JsonNodeFactory.instance.objectNode()
                        .put("periodId", periodId)
                        .put("periodCode", "AI-" + date.toString())
                        .put("periodStart", date.toString())
                        .put("periodEnd", date.toString())
                        .put("currencyCode", "CNY")
                        .put("reasonCode", "AI_DAILY_CLOSE")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "IMPORT_CHANNEL_STATEMENT")
                .set("statement", JsonNodeFactory.instance.objectNode()
                        .put("statementId", statementId)
                        .put("statementCode", "AI-ST-" + prefix.toUpperCase())
                        .put("periodId", periodId)
                        .put("channelCode", "YSHOPPING_INTERNAL")
                        .put("statementDate", date.toString())
                        .put("currencyCode", "CNY")
                        .put("grossAmountMinor", grossAmountMinor)
                        .put("refundAmountMinor", refundAmountMinor)
                        .put("feeAmountMinor", feeAmountMinor)
                        .put("netSettlementAmountMinor", netAmountMinor)
                        .put("expectedBusinessNetAmountMinor",
                                netAmountMinor - differenceAmountMinor)
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":statement-evidence"))
                        .put("reasonCode", "AI_CHANNEL_STATEMENT_IMPORT")));
        commands.add(financeCommand(runId, correlationId, occurredAt,
                        "RECONCILE_CHANNEL_STATEMENT")
                .set("statement", JsonNodeFactory.instance.objectNode()
                        .put("statementId", statementId)
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_RECONCILIATION")));
        commands.add(financeCommand(runId, correlationId, occurredAt,
                        "RESOLVE_RECONCILIATION_DIFFERENCE")
                .set("differenceResolution", JsonNodeFactory.instance.objectNode()
                        .put("differenceId", ZERO_UUID)
                        .put("adjustmentAmountMinor", differenceAmountMinor)
                        .put("resolutionType", "CHANNEL_ADJUSTMENT")
                        .put("resolutionEvidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":difference-evidence"))
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_CHANNEL_ADJUSTMENT")));
        commands.add(financeCommand(runId, correlationId, occurredAt,
                        "CREATE_SETTLEMENT_BATCH")
                .set("settlement", JsonNodeFactory.instance.objectNode()
                        .put("settlementBatchId", settlementId)
                        .put("settlementCode", "AI-SE-" + prefix.toUpperCase())
                        .put("periodId", periodId)
                        .put("statementId", statementId)
                        .put("expectedAmountMinor", netAmountMinor)
                        .put("reasonCode", "AI_SETTLEMENT_PREPARE")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "CONFIRM_SETTLEMENT")
                .set("settlement", JsonNodeFactory.instance.objectNode()
                        .put("settlementBatchId", settlementId)
                        .put("settledAmountMinor", netAmountMinor)
                        .put("bankReference", "AI-BANK-" + prefix.toUpperCase())
                        .put("settlementEvidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":settlement-evidence"))
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_BANK_RECEIPT_VERIFIED")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "PREPARE_JOURNAL_ENTRY")
                .set("journalEntry", JsonNodeFactory.instance.objectNode()
                        .put("journalEntryId", journalId)
                        .put("journalCode", "AI-JE-" + prefix.toUpperCase())
                        .put("periodId", periodId)
                        .put("sourceType", "SETTLEMENT_BATCH")
                        .put("sourceId", settlementId)
                        .put("debitTotalMinor", netAmountMinor)
                        .put("creditTotalMinor", netAmountMinor)
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":journal-evidence"))
                        .put("reasonCode", "AI_JOURNAL_PREPARE")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "POST_JOURNAL_ENTRY")
                .set("journalEntry", JsonNodeFactory.instance.objectNode()
                        .put("journalEntryId", journalId)
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_JOURNAL_POST")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "CLOSE_ACCOUNTING_PERIOD")
                .set("period", JsonNodeFactory.instance.objectNode()
                        .put("periodId", periodId)
                        .put("expectedVersion", 1)
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":close-evidence"))
                        .put("reasonCode", "AI_DAILY_CLOSE_COMPLETE")));
        return input;
    }

    private static ObjectNode financeIdentityCommand(String runId, String correlationId,
                                                     String occurredAt, long userId) {
        return JsonNodeFactory.instance.objectNode()
                .put("idempotencyKey", "pending-identity-link")
                .put("runId", runId)
                .put("principalType", "PLATFORM_OPERATOR")
                .put("sourceSystem", "SYSTEM")
                .put("sourceType", "SYSTEM_ADMIN_USER")
                .put("sourceId", Long.toString(userId))
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static ObjectNode financeCommand(String runId, String correlationId,
                                             String occurredAt, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("correlationId", correlationId)
                .put("runId", runId)
                .put("idempotencyKey", "pending-finance-command")
                .put("occurredAt", occurredAt);
    }

    private static ObjectNode consumerJourney(ObjectNode fullChain, String prefix, String occurredAt,
                                              long memberUserId) {
        ObjectNode consumer = JsonNodeFactory.instance.objectNode();
        consumer.put("listingId", ZERO_UUID);
        consumer.putObject("identityReference")
                .put("sourceSystem", "MEMBER")
                .put("sourceType", "MEMBER_USER")
                .put("sourceId", Long.toString(memberUserId));

        String runId = stableUuid(prefix + ":consumer-run");
        String sessionId = stableUuid(prefix + ":session");
        String correlationId = stableUuid(prefix + ":correlation");
        String sourceId = "persona:" + prefix;
        String searchToken = "search:" + prefix;
        String resultSetToken = "results:" + prefix;
        String checkoutToken = "checkout:" + prefix;

        ArrayNode behavior = consumer.putArray("behaviorCommands");
        behavior.add(commonCommand(prefix + "-session", runId, occurredAt, correlationId)
                .put("sessionId", sessionId).put("channelCode", "YSHOPPING_INTERNAL")
                .put("entrypointCode", "COMMUNITY_FEED").put("sourceSystem", "CLOUDMOLD_AI")
                .put("sourceType", "SYNTHETIC_PERSONA").put("sourceId", sourceId));
        behavior.add(commonCommand(prefix + "-link", runId, occurredAt, correlationId)
                .put("sessionId", sessionId).put("sourceSystem", "CLOUDMOLD_AI")
                .put("sourceType", "SYNTHETIC_PERSONA").put("sourceId", sourceId));
        behavior.add(behaviorCommand(prefix, "search-requested", "SEARCH_REQUESTED", runId, sessionId,
                occurredAt, correlationId, sourceId).put("searchToken", searchToken));
        behavior.add(behaviorCommand(prefix, "search-exposed", "SEARCH_RESULT_EXPOSED", runId, sessionId,
                occurredAt, correlationId, sourceId).put("searchToken", searchToken)
                .put("resultSetToken", resultSetToken));
        behavior.add(withListing(behaviorCommand(prefix, "search-clicked", "SEARCH_RESULT_CLICKED", runId,
                sessionId, occurredAt, correlationId, sourceId).put("searchToken", searchToken)
                .put("resultSetToken", resultSetToken).put("resultPosition", 0)));
        behavior.add(withListing(behaviorCommand(prefix, "pdp-viewed", "PDP_VIEWED", runId, sessionId,
                occurredAt, correlationId, sourceId)));
        behavior.add(withListing(behaviorCommand(prefix, "cart-added", "CART_ADDED", runId, sessionId,
                occurredAt, correlationId, sourceId).put("quantity", 2)));
        behavior.add(withListing(behaviorCommand(prefix, "checkout-started", "CHECKOUT_STARTED", runId,
                sessionId, occurredAt, correlationId, sourceId).put("checkoutToken", checkoutToken)));

        consumer.set("favoriteCommand", commonCommand(prefix + "-favorite", runId, occurredAt, correlationId)
                .put("favoriteId", stableUuid(prefix + ":favorite")).put("canonicalSpuId", ZERO_UUID)
                .put("desiredStatus", "ACTIVE").put("sourceSystem", "CLOUDMOLD_AI")
                .put("sourceType", "SYNTHETIC_PERSONA").put("sourceId", sourceId));
        consumer.set("attributionCommand", commonCommand(prefix + "-attribution", runId, occurredAt, correlationId)
                .put("attributionId", stableUuid(prefix + ":attribution")).put("sessionId", sessionId)
                .put("checkoutToken", checkoutToken).put("orderId", ZERO_UUID).put("paymentId", ZERO_UUID)
                .put("sourceSystem", "CLOUDMOLD_AI").put("sourceType", "SYNTHETIC_PERSONA")
                .put("sourceId", sourceId));

        ArrayNode commands = consumer.putArray("commands");
        JsonNode sourceCommands = fullChain.path("aftersale").path("commands");
        for (int index = 6; index < sourceCommands.size(); index++) {
            commands.add(sourceCommands.get(index).deepCopy());
        }

        ArrayNode customerService = consumer.putArray("customerServiceCommands");
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CREATE_TICKET")
                .put("ticketNo", "AI-TKT-" + prefix.toUpperCase()).put("customerPrincipalId", ZERO_UUID)
                .put("channelCode", "APP").put("priority", "NORMAL").put("categoryCode", "ORDER_CONSULTATION"));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "LINK_REFERENCE")
                .put("ticketId", ZERO_UUID).put("referenceSourceSystem", "cloudmold-order")
                .put("referenceType", "ORDER").put("referenceId", ZERO_UUID));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RECORD_MESSAGE")
                .put("ticketId", ZERO_UUID).put("direction", "INBOUND").put("senderType", "CUSTOMER")
                .put("senderPrincipalId", ZERO_UUID).put("messageType", "TEXT")
                .put("contentToken", "restricted:delivery_question_" + prefix));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "ASSIGN_AGENT")
                .put("ticketId", ZERO_UUID).put("assignedAgentPrincipalId", ZERO_UUID));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "START_PROCESSING")
                .put("ticketId", ZERO_UUID));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RESOLVE_TICKET")
                .put("ticketId", ZERO_UUID));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RECORD_BUYER_FEEDBACK")
                .put("ticketId", ZERO_UUID).put("customerPrincipalId", ZERO_UUID)
                .put("touchpointCode", "TICKET_RESOLUTION").put("sentimentCode", "SATISFIED")
                .put("commentToken", "restricted:satisfied_" + prefix));
        customerService.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CLOSE_TICKET")
                .put("ticketId", ZERO_UUID));

        ArrayNode community = consumer.putArray("communityCommands");
        ArrayNode iv = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < 12; index++) iv.add(index + 1);
        ArrayNode ciphertext = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < 24; index++) ciphertext.add(index + 11);
        community.add(commonCommand(prefix + "-community-create", runId, occurredAt, correlationId)
                .put("contentId", stableUuid(prefix + ":community-content")).put("authorPrincipalId", ZERO_UUID)
                .put("contentType", "POST").put("bodyRef", "restricted:community_post_" + prefix)
                .put("bodyKeyId", "synthetic-key-v1").set("bodyIv", iv));
        ((ObjectNode) community.get(0)).set("bodyCiphertext", ciphertext);
        ((ObjectNode) community.get(0)).put("bodyDigestSha256",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .put("canonicalSpuId", ZERO_UUID).put("canonicalSkuId", ZERO_UUID)
                .put("listingId", ZERO_UUID).put("listingOfferId", ZERO_UUID).put("desiredStatus", "DRAFT");
        community.add(commonCommand(prefix + "-community-submit", runId, occurredAt, correlationId)
                .put("contentId", ZERO_UUID).put("actorPrincipalId", ZERO_UUID)
                .put("desiredStatus", "PENDING_MODERATION").put("reasonCode", "CONSUMER_REVIEW"));
        community.add(commonCommand(prefix + "-community-publish", runId, occurredAt, correlationId)
                .put("contentId", ZERO_UUID).put("actorPrincipalId", ZERO_UUID)
                .put("desiredStatus", "PUBLISHED").put("reasonCode", "AI_MODERATION_PASSED"));
        return consumer;
    }

    private static ObjectNode behaviorCommand(String prefix, String code, String type, String runId,
                                              String sessionId, String occurredAt, String correlationId,
                                              String sourceId) {
        return commonCommand(prefix + "-" + code, runId, occurredAt, correlationId)
                .put("behaviorId", stableUuid(prefix + ":" + code)).put("sessionId", sessionId)
                .put("behaviorType", type).put("sourceSystem", "CLOUDMOLD_AI")
                .put("sourceType", "SYNTHETIC_PERSONA").put("sourceId", sourceId);
    }

    private static ObjectNode withListing(ObjectNode command) {
        return command.put("canonicalSpuId", ZERO_UUID).put("skuId", ZERO_UUID)
                .put("listingId", ZERO_UUID).put("listingOfferId", ZERO_UUID)
                .put("expectedPriceMinor", 19900).put("currencyCode", "CNY");
    }

    private static ObjectNode customerServiceCommand(String prefix, String runId, String occurredAt,
                                                     String correlationId, String operation) {
        return commonCommand(prefix + "-cs-" + operation.toLowerCase(), runId, occurredAt, correlationId)
                .put("operation", operation);
    }

    private static ObjectNode commonCommand(String idempotencyKey, String runId, String occurredAt,
                                            String correlationId) {
        return JsonNodeFactory.instance.objectNode().put("idempotencyKey", idempotencyKey)
                .put("runId", runId).put("occurredAt", occurredAt).put("correlationId", correlationId);
    }

    private static String stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String productToken(ObjectNode template) {
        String styleCode = template.path("catalog").path("definitions").path(0)
                .path("styleCode").asText();
        int separator = styleCode.lastIndexOf('-');
        return separator >= 0 && separator < styleCode.length() - 1
                ? styleCode.substring(separator + 1) : "";
    }

    private static String productToken(String newPrefix) {
        return stableUuid(newPrefix + ":product-code")
                .replace("-", "").substring(0, 8).toUpperCase();
    }

    private static JsonNode rotate(JsonNode node, String oldPrefix, String newPrefix,
                                   String oldProductToken, String newProductToken,
                                   String occurredAt, String fieldName) {
        if (node.isTextual()) {
            if ("occurredAt".equals(fieldName) || "eligibilityAt".equals(fieldName)
                    || "publishStartAt".equals(fieldName)) {
                return TextNode.valueOf(occurredAt);
            }
            if ("promisedDeliveryAt".equals(fieldName)) {
                return TextNode.valueOf(Instant.parse(occurredAt)
                        .plus(3, ChronoUnit.DAYS).toString());
            }
            String value = node.asText().replace(oldPrefix, newPrefix);
            if (!oldProductToken.isBlank()) {
                value = value.replace(oldProductToken, newProductToken);
            }
            return TextNode.valueOf(value);
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                result.add(rotate(item, oldPrefix, newPrefix, oldProductToken,
                        newProductToken, occurredAt, fieldName));
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.set(field.getKey(), rotate(field.getValue(), oldPrefix, newPrefix,
                        oldProductToken, newProductToken, occurredAt, field.getKey()));
            }
            return result;
        }
        return node.deepCopy();
    }
}
