package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityReference;
import cn.iocoder.yudao.module.cloudmold.procurement.api.AwardReleaseCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOperation;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.SourcingCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.SourcingOperation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
class RotatingBusinessScenarioInputFactory {

    private static final Set<String> TECHNICAL_EXECUTION_KEYS = Set.of(
            "tenantid", "operatorid", "operatortype", "approvalref", "approvalscope",
            "clientrequestkey", "skilltaskid", "skillid", "skillversion", "runid",
            "idempotencykey");

    static final String FULL_CHAIN_SKILL = "skill.cloudmold.commerce.full-chain-hsf.v1";
    static final String PRODUCT_TO_LISTING_SKILL = "skill.cloudmold.commerce.product-to-listing.v1";
    static final String AUTONOMOUS_DAY_SKILL = "skill.cloudmold.commerce.autonomous-day.v1";
    static final String CATEGORY_DAILY_OPERATIONS_SKILL =
            "skill.cloudmold.commerce.category-daily-operations.v1";
    static final String CUSTOMER_SALES_PIPELINE_LIFECYCLE_SKILL =
            "skill.cloudmold.crm.customer-sales-pipeline-lifecycle.v1";
    static final String CATALOG_MATRIX_SKILL = "skill.cloudmold.commerce.catalog-matrix.v1";
    static final String AFTERSALE_SAGA_SKILL = "skill.cloudmold.commerce.aftersale-saga.v1";
    static final String ORDER_CANCELLATION_OPERATIONS_SKILL =
            "skill.cloudmold.commerce.order-cancellation-operational.v1";
    static final String CONSUMER_JOURNEY_SKILL = "skill.cloudmold.consumer.shopping-journey.v1";
    static final String MERCHANT_ONBOARDING_SKILL = "skill.cloudmold.merchant.onboarding-lifecycle.v1";
    static final String MERCHANT_MANAGED_GROWTH_LIFECYCLE_SKILL =
            "skill.cloudmold.merchant.managed-growth-lifecycle.v1";
    static final String PROMOTION_CAMPAIGN_SKILL =
            "skill.cloudmold.engagement.promotion-campaign-operations.v1";
    static final String GROWTH_EXPERIMENT_SKILL =
            "skill.cloudmold.growth.experiment-lifecycle.v1";
    static final String PROCUREMENT_SOURCING_SKILL =
            "skill.cloudmold.procurement.sourcing-lifecycle.v1";
    static final String PROCUREMENT_ORDER_SKILL =
            "skill.cloudmold.procurement.order-lifecycle.v1";
    static final String WMS_OPERATIONS_SKILL =
            "skill.cloudmold.wms.operations.v1";
    static final String REPLENISHMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.supply.replenishment-lifecycle.v1";
    static final String SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL =
            "skill.cloudmold.supply-planning.sop-lifecycle.v1";
    static final String CUSTOMER_SERVICE_LIFECYCLE_SKILL =
            "skill.cloudmold.customer-service.resolution-lifecycle.v1";
    static final String FINANCE_CLOSE_LIFECYCLE_SKILL =
            "skill.cloudmold.finance.close-lifecycle.v1";
    static final String QUALITY_INSPECTION_RECALL_LIFECYCLE_SKILL =
            "skill.cloudmold.quality.inspection-recall-lifecycle.v1";
    static final String FULFILLMENT_EXCEPTION_LIFECYCLE_SKILL =
            "skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1";
    static final String CROSSBORDER_FULFILLMENT_COMPLIANCE_LIFECYCLE_SKILL =
            "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1";
    static final String BONDED_CUSTOMS_LIFECYCLE_SKILL =
            "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1";
    static final String PARTNER_MARKETING_KOL_MEDIA_OPERATIONS_SKILL =
            "skill.cloudmold.partner-marketing.kol-media-operations.v1";
    static final String MES_PRODUCTION_EXECUTION_LIFECYCLE_SKILL =
            "skill.cloudmold.mes.production-execution-lifecycle.v1";
    static final String ASSORTMENT_PLANNING_LIFECYCLE_SKILL =
            "skill.cloudmold.catalog.assortment-planning-lifecycle.v1";
    static final String PRODUCT_MANAGEMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.commerce.product-management-lifecycle.v1";
    static final String WAREHOUSE_ADMISSION_LIFECYCLE_SKILL =
            "skill.cloudmold.supply.warehouse-admission-lifecycle.v1";
    static final String CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL =
            "skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1";
    static final String MERCHANT_EXPERIENCE_RECTIFICATION_LIFECYCLE_SKILL =
            "skill.cloudmold.merchant-experience.rectification-lifecycle.v1";
    static final String UNFULFILLABLE_ORDER_COMPENSATION_LIFECYCLE_SKILL =
            "skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1";
    static final String LOGISTICS_SERVICE_SETTLEMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1";
    static final String MERCHANT_SERVICE_FEE_SETTLEMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1";
    static final String ADVERTISING_FEE_SETTLEMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1";
    static final String PROFIT_LOSS_IMPROVEMENT_LIFECYCLE_SKILL =
            "skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1";
    static final String RISK_DISPUTE_RESOLUTION_LIFECYCLE_SKILL =
            "skill.cloudmold.risk.dispute-resolution-lifecycle.v1";
    static final String DATA_QUALITY_RECOVERY_LIFECYCLE_SKILL =
            "skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1";
    static final String PRICING_REPRICE_LIFECYCLE_SKILL =
            "skill.cloudmold.pricing.reprice-lifecycle.v1";
    static final String READY_MASTER_SKILL = "skill.cloudmold.commerce.reuse-ready-master.v1";
    private static final String PROCUREMENT_SOURCING_INPUT_SCHEMA =
            "cloudmold.procurement-sourcing-input/v1";
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    private final AiOperationsTemporalMapper mapper;
    private final AiOperationsTemporalSeedProperties seedProperties;
    private final IdentityQueryApi identityQueries;

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
        String businessOccurredAt = date.atStartOfDay(ZoneOffset.ofHours(8))
                .toInstant().toString();
        String oldProductToken = productToken(template);
        String newProductToken = productToken(newPrefix);
        ObjectNode rotated = (ObjectNode) rotate(template, oldPrefix, newPrefix,
                oldProductToken, newProductToken, occurredAt, null);
        prepareAfterSaleDisposition(rotated, newPrefix, occurredAt, date.getDayOfYear() % 2 == 0);
        Optional<ObjectNode> reusableMaster = reusableMaster(tenantId, template, occurredAt);
        if (reusableMaster.isEmpty()) {
            return Optional.empty();
        }
        rotated.set("master", reusableMaster.get());
        JsonNode output;
        if (FULL_CHAIN_SKILL.equals(targetSkillId)) {
            ObjectNode product = productToListing(rotated, newPrefix);
            product.withObject("/runIds").put("product", newPrefix + "-product");
            rotated.set("product", product);
            output = rotated;
        } else if (CATALOG_MATRIX_SKILL.equals(targetSkillId)) {
            output = rotated.path("catalog").deepCopy();
        } else if (AFTERSALE_SAGA_SKILL.equals(targetSkillId)) {
            ObjectNode aftersale = (ObjectNode) rotated.path("aftersale").deepCopy();
            if (!hydrateAfterSaleScenario(tenantId, aftersale)) {
                return Optional.empty();
            }
            output = aftersale;
        } else if (ORDER_CANCELLATION_OPERATIONS_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = orderCancellationOperations(
                    newPrefix, occurredAt, consumer, operator.path("principalId").asText());
        } else if (PRODUCT_TO_LISTING_SKILL.equals(targetSkillId)) {
            output = productToListing(rotated, newPrefix);
        } else if (PRICING_REPRICE_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode listing = result(tenantId, PRODUCT_TO_LISTING_SKILL, "listing_publish");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (!"PUBLISHED".equals(listing.path("currentStatus").asText())
                    || listing.path("listingId").asText().isBlank()
                    || listing.path("aggregateVersion").asLong() <= 0
                    || !listing.path("offers").isArray()
                    || listing.path("offers").isEmpty()
                    || principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = pricingRepriceLifecycle(newPrefix, businessOccurredAt,
                    principal.path("principalId").asText(), listing);
        } else if (READY_MASTER_SKILL.equals(targetSkillId)) {
            output = reusableMaster.get();
        } else if (MERCHANT_ONBOARDING_SKILL.equals(targetSkillId)) {
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = merchantOnboarding(newPrefix, occurredAt, principal.path("principalId").asText());
        } else if (MERCHANT_MANAGED_GROWTH_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode onboarding = result(tenantId, MERCHANT_ONBOARDING_SKILL, "merchant_approve");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            String applicationId = onboarding.path("applicationId").asText();
            String merchantId = onboarding.path("merchantId").asText();
            if (applicationId.isBlank() || merchantId.isBlank()
                    || principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = merchantManagedGrowthLifecycle(newPrefix, occurredAt, applicationId, merchantId,
                    principal.path("principalId").asText());
        } else if (PROMOTION_CAMPAIGN_SKILL.equals(targetSkillId)) {
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            if (principal.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = promotionCampaign(newPrefix, occurredAt,
                    principal.path("principalId").asText());
        } else if (GROWTH_EXPERIMENT_SKILL.equals(targetSkillId)) {
            output = growthExperiment(newPrefix, occurredAt);
        } else if (PROCUREMENT_SOURCING_SKILL.equals(targetSkillId)) {
            Optional<ProcurementSourcingSeed> seed = procurementSourcingSeed(tenantId);
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            CatalogProcurementLine firstLine = catalogProcurementLine(tenantId, "define_1", 0);
            CatalogProcurementLine secondLine = catalogProcurementLine(tenantId, "define_2", 1);
            if (seed.isEmpty() || !seed.get().matches(firstLine, secondLine)
                    || !firstLine.usable() || !secondLine.usable()
                    || firstLine.canonicalSkuId().equals(secondLine.canonicalSkuId())
                    || warehouse.path("warehouseId").asText().isBlank()) {
                return Optional.empty();
            }
            output = procurementSourcing(newPrefix, occurredAt, firstLine, secondLine,
                    warehouse.path("warehouseId").asText(), seed.get(), 500);
        } else if (PROCUREMENT_ORDER_SKILL.equals(targetSkillId)) {
            String sourcingInputJson = mapper.selectLatestUnreleasedProcurementSourcingInput(tenantId);
            if (sourcingInputJson == null || sourcingInputJson.isBlank()) {
                // An approved award is released exactly once. There is no order candidate until
                // sourcing produces a new approved award that has not yet been consumed.
                return Optional.empty();
            }
            JsonNode sourcingInput = JsonUtils.parseTree(sourcingInputJson);
            if (sourcingInput == null || !sourcingInput.isObject()
                    || sourcingInput.path("awardId").asText().isBlank()
                    || !sourcingInput.path("awardRelease").isObject()
                    || !sourcingInput.path("purchaseOrderPlans").isArray()
                    || sourcingInput.path("purchaseOrderPlans").size() != 2) {
                return Optional.empty();
            }
            ObjectNode procurement = JsonNodeFactory.instance.objectNode();
            procurement.put("schemaVersion", PROCUREMENT_SOURCING_INPUT_SCHEMA)
                    .put("awardId", sourcingInput.path("awardId").asText());
            procurement.set("awardRelease", sourcingInput.path("awardRelease").deepCopy());
            procurement.set("purchaseOrders", sourcingInput.path("purchaseOrderPlans").deepCopy());
            output = procurement;
        } else if (WMS_OPERATIONS_SKILL.equals(targetSkillId)) {
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            WmsCatalogProjectionSeedRecord catalogProjection =
                    mapper.selectLatestWmsCatalogProjectionSeed(tenantId);
            if (merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()
                    || principal.path("principalId").asText().isBlank()
                    || warehouse.path("warehouseId").asText().isBlank()
                    || !usable(catalogProjection)) {
                return Optional.empty();
            }
            output = wmsOperations(newPrefix, occurredAt,
                    merchant.path("merchantId").asText(),
                    merchant.path("shopId").asText(),
                    principal.path("principalId").asText(),
                    warehouse.path("warehouseId").asText(),
                    catalogProjection.getSkuCode(), catalogProjection.getPrimaryBarcode(),
                    catalogProjection.getBaseUomCode());
        } else if (REPLENISHMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
            Optional<ProcurementSourcingSeed> seed = procurementSourcingSeed(tenantId);
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            CatalogProcurementLine firstLine = catalogProcurementLine(tenantId, "define_1", 0);
            CatalogProcurementLine secondLine = catalogProcurementLine(tenantId, "define_2", 1);
            WmsCatalogProjectionSeedRecord catalogProjection =
                    mapper.selectLatestWmsCatalogProjectionSeed(tenantId);
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            if (seed.isEmpty() || !seed.get().matches(firstLine, secondLine)
                    || merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()
                    || principal.path("principalId").asText().isBlank()
                    || !firstLine.usable() || !secondLine.usable()
                    || firstLine.canonicalSkuId().equals(secondLine.canonicalSkuId())
                    || warehouse.path("warehouseId").asText().isBlank()
                    || !usable(catalogProjection)) {
                return Optional.empty();
            }
            output = replenishmentLifecycle(newPrefix, occurredAt,
                    merchant.path("merchantId").asText(),
                    merchant.path("shopId").asText(),
                    principal.path("principalId").asText(),
                    firstLine, secondLine, seed.get(),
                    warehouse.path("warehouseId").asText(),
                    catalogProjection.getSkuCode(), catalogProjection.getPrimaryBarcode(),
                    catalogProjection.getBaseUomCode());
        } else if (SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode principal = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            WmsTransferSeedRecord seed = mapper.selectWmsTransferSeed(tenantId);
            if (principal.path("principalId").asText().isBlank()
                    || merchant.path("merchantId").asText().isBlank() || !usable(seed)) {
                return Optional.empty();
            }
            String mappingEvidenceSha256 = wmsTransferEvidence(seed);
            output = supplyPlanningSopLifecycle(
                    newPrefix, occurredAt,
                    seed.getCanonicalSkuId(), seed.getCanonicalWarehouseId(),
                    principal.path("principalId").asText(),
                    merchant.path("merchantId").asText(),
                    seed.getSourceCanonicalWarehouseId(), seed.getCanonicalWarehouseId(), seed.getWmsSkuId(),
                    seed.getBaseUomCode(), mappingEvidenceSha256);
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
        } else if (LOGISTICS_SERVICE_SETTLEMENT_LIFECYCLE_SKILL.equals(targetSkillId)
                || MERCHANT_SERVICE_FEE_SETTLEMENT_LIFECYCLE_SKILL.equals(targetSkillId)
                || ADVERTISING_FEE_SETTLEMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
            TemporalApprovalPolicyRecord approvalPolicy = mapper.selectApprovalPolicy(tenantId);
            Long financeMakerUserId = mapper.selectFirstEffectiveAgentRoleActor(tenantId, "finance");
            Long financeCheckerUserId = approvalPolicy == null
                    ? null : approvalPolicy.getGovernanceUserId();
            if (financeMakerUserId == null || financeMakerUserId <= 0
                    || financeCheckerUserId == null || financeCheckerUserId <= 0
                    || financeMakerUserId.equals(financeCheckerUserId)) {
                return Optional.empty();
            }
            if (LOGISTICS_SERVICE_SETTLEMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
                output = financeSettlementParentInput(newPrefix,
                        financeSettlementLifecycle(newPrefix, occurredAt,
                        financeMakerUserId, financeCheckerUserId,
                        "LOGISTICS_SERVICE", "LOGISTICS_WAREHOUSE_DISPATCH_PICKUP",
                        240_000L, 0L, 0L, 10_000L));
            } else if (MERCHANT_SERVICE_FEE_SETTLEMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
                output = financeSettlementParentInput(newPrefix,
                        financeSettlementLifecycle(newPrefix, occurredAt,
                        financeMakerUserId, financeCheckerUserId,
                        "MERCHANT_SERVICE_FEE", "MERCHANT_TECHNICAL_SERVICE",
                        900_000L, 80_000L, 45_000L, 5_000L));
            } else {
                output = financeSettlementParentInput(newPrefix,
                        financeSettlementLifecycle(newPrefix, occurredAt,
                        financeMakerUserId, financeCheckerUserId,
                        "ADVERTISING_FEE", "PLATFORM_ADVERTISING",
                        300_000L, 15_000L, 0L, 5_000L));
            }
        } else if (QUALITY_INSPECTION_RECALL_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            JsonNode manager = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
            TemporalApprovalPolicyRecord approvalPolicy = mapper.selectApprovalPolicy(tenantId);
            Long primaryInspectorUserId =
                    mapper.selectFirstEffectiveAgentRoleActor(tenantId, "finance");
            Long independentReviewerUserId = approvalPolicy == null
                    ? null : approvalPolicy.getGovernanceUserId();
            if (merchant.path("merchantId").asText().isBlank()
                    || warehouse.path("warehouseId").asText().isBlank()
                    || warehouse.path("locationId").asText().isBlank()
                    || manager.path("principalId").asText().isBlank()
                    || catalog.path("canonicalSkuId").asText().isBlank()
                    || primaryInspectorUserId == null || primaryInspectorUserId <= 0
                    || independentReviewerUserId == null || independentReviewerUserId <= 0
                    || primaryInspectorUserId.equals(independentReviewerUserId)) {
                return Optional.empty();
            }
            output = qualityInspectionRecallLifecycle(
                    newPrefix, occurredAt,
                    merchant.path("merchantId").asText(),
                    warehouse.path("warehouseId").asText(),
                    warehouse.path("locationId").asText(),
                    catalog.path("canonicalSkuId").asText(),
                    manager.path("principalId").asText(),
                    primaryInspectorUserId, independentReviewerUserId);
        } else if (FULFILLMENT_EXCEPTION_LIFECYCLE_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = fulfillmentExceptionLifecycle(newPrefix, occurredAt, consumer);
        } else if (CROSSBORDER_FULFILLMENT_COMPLIANCE_LIFECYCLE_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = crossborderFulfillmentComplianceLifecycle(
                    newPrefix, occurredAt, consumer, operator.path("principalId").asText());
        } else if (BONDED_CUSTOMS_LIFECYCLE_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = bondedCustomsLifecycle(
                    newPrefix, occurredAt, consumer, operator.path("principalId").asText());
        } else if (PARTNER_MARKETING_KOL_MEDIA_OPERATIONS_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            TemporalApprovalPolicyRecord approvalPolicy = mapper.selectApprovalPolicy(tenantId);
            Long financeActorUserId =
                    mapper.selectFirstEffectiveAgentRoleActor(tenantId, "finance");
            Long reviewerUserId = approvalPolicy == null
                    ? null : approvalPolicy.getGovernanceUserId();
            String reviewerPrincipalId = resolveSystemPrincipal(reviewerUserId);
            String financePrincipalId = resolveSystemPrincipal(financeActorUserId);
            if (operator.path("principalId").asText().isBlank()
                    || reviewerUserId == null || reviewerUserId <= 0
                    || financeActorUserId == null || financeActorUserId <= 0
                    || reviewerUserId.equals(financeActorUserId)
                    || reviewerPrincipalId == null || financePrincipalId == null
                    || reviewerPrincipalId.equals(financePrincipalId)) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            output = partnerMarketingKolMediaOperations(
                    rotated, newPrefix, occurredAt, consumer,
                    operator.path("principalId").asText(),
                    reviewerPrincipalId, financePrincipalId);
        } else if (ASSORTMENT_PLANNING_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = assortmentPlanningLifecycle(
                    newPrefix, date, occurredAt, operator.path("principalId").asText());
        } else if (PRODUCT_MANAGEMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0
                    || seedProperties.getOperatorUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
            JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
            if (operator.path("principalId").asText().isBlank()
                    || merchant.path("merchantId").asText().isBlank()
                    || warehouse.path("warehouseId").asText().isBlank()
                    || warehouse.path("locationId").asText().isBlank()
                    || catalog.path("canonicalSkuId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode mysteryPurchase = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            output = productManagementLifecycle(
                    rotated, newPrefix, date, occurredAt, mysteryPurchase,
                    operator.path("principalId").asText(),
                    merchant.path("merchantId").asText(),
                    warehouse.path("warehouseId").asText(),
                    warehouse.path("locationId").asText(),
                    catalog.path("canonicalSkuId").asText(),
                    seedProperties.getOperatorUserId());
        } else if (WAREHOUSE_ADMISSION_LIFECYCLE_SKILL.equals(targetSkillId)) {
            Optional<ProcurementSourcingSeed> seed = procurementSourcingSeed(tenantId);
            CatalogProcurementLine firstLine = catalogProcurementLine(tenantId, "define_1", 0);
            CatalogProcurementLine secondLine = catalogProcurementLine(tenantId, "define_2", 1);
            JsonNode listing = result(
                    tenantId, PRODUCT_MANAGEMENT_LIFECYCLE_SKILL, "wait_platform_store_listing");
            JsonNode quality = result(
                    tenantId, PRODUCT_MANAGEMENT_LIFECYCLE_SKILL, "wait_mystery_buyer_quality");
            String canonicalSkuId = quality.path("outputs").path("verify_quality_badge")
                    .path("canonicalSkuId").asText();
            String qualityStatus = quality.path("outputs").path("verify_quality_badge")
                    .path("status").asText();
            String merchantId = listing.path("outputs").path("wait_master").path("outputs")
                    .path("merchant_approve").path("merchantId").asText();
            String shopId = listing.path("outputs").path("wait_master").path("outputs")
                    .path("merchant_approve").path("shopId").asText();
            String principalId = listing.path("outputs").path("wait_master").path("outputs")
                    .path("principal").path("principalId").asText();
            String warehouseId = listing.path("outputs").path("wait_master").path("outputs")
                    .path("warehouse_network").path("warehouseId").asText();
            String listingId = listing.path("outputs").path("listing_create")
                    .path("listingId").asText();
            WmsCatalogProjectionSeedRecord catalogProjection =
                    mapper.selectLatestWmsCatalogProjectionSeed(tenantId);
            if (seed.isEmpty() || !seed.get().matches(firstLine, secondLine)
                    || !firstLine.usable() || !secondLine.usable()
                    || firstLine.canonicalSkuId().equals(secondLine.canonicalSkuId())
                    || !"VERIFIED".equals(qualityStatus)
                    || canonicalSkuId.isBlank()
                    || !canonicalSkuId.equals(firstLine.canonicalSkuId())
                    || merchantId.isBlank() || shopId.isBlank()
                    || principalId.isBlank() || warehouseId.isBlank() || listingId.isBlank()
                    || !usable(catalogProjection)) {
                return Optional.empty();
            }
            output = warehouseAdmissionLifecycle(
                    newPrefix, occurredAt, canonicalSkuId, listingId,
                    merchantId, shopId, principalId, warehouseId,
                    firstLine, secondLine, seed.get(),
                    catalogProjection.getSkuCode(), catalogProjection.getPrimaryBarcode(),
                    catalogProjection.getBaseUomCode());
        } else if (CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL
                .equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode agent = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            if (agent.path("principalId").asText().isBlank()
                    || merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(
                    rotated, newPrefix, businessOccurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = customerExperienceTicketResponsibilityLifecycle(
                    newPrefix, businessOccurredAt, consumer,
                    agent.path("principalId").asText(),
                    merchant.path("merchantId").asText(),
                    merchant.path("shopId").asText());
        } else if (MERCHANT_EXPERIENCE_RECTIFICATION_LIFECYCLE_SKILL
                .equals(targetSkillId)) {
            JsonNode ticket = result(
                    tenantId, CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                    "ticket_terminal_readback");
            JsonNode decision = result(
                    tenantId, CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                    "ticket_responsibility_decision");
            JsonNode compensation = result(
                    tenantId, CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                    "ticket_pay_compensation");
            JsonNode consumerJourney = result(
                    tenantId, CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL,
                    "wait_consumer_journey");
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            JsonNode agent = result(tenantId, READY_MASTER_SKILL, "principal");
            String ticketId = ticket.path("ticketId").asText();
            String customerPrincipalId = consumerJourney.path("outputs")
                    .path("consumer_principal").path("principalId").asText();
            if (!"CLOSED".equals(ticket.path("ticketStatus").asText())
                    || ticket.path("ticketVersion").asLong() != 5L
                    || decision.path("reviewId").asText().isBlank()
                    || !"PAID".equals(compensation.path("claimStatus").asText())
                    || compensation.path("compensationEntryId").asText().isBlank()
                    || ticketId.isBlank() || customerPrincipalId.isBlank()
                    || merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()
                    || agent.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = merchantExperienceRectificationLifecycle(
                    newPrefix, businessOccurredAt, ticketId, customerPrincipalId,
                    agent.path("principalId").asText(),
                    merchant.path("merchantId").asText(),
                    merchant.path("shopId").asText(),
                    decision.path("reviewId").asText(),
                    compensation.path("compensationEntryId").asText());
        } else if (UNFULFILLABLE_ORDER_COMPENSATION_LIFECYCLE_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode agent = result(tenantId, READY_MASTER_SKILL, "principal");
            if (agent.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(
                    rotated, newPrefix, businessOccurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = unfulfillableOrderCompensationLifecycle(
                    newPrefix, businessOccurredAt, consumer, agent.path("principalId").asText());
        } else if (PROFIT_LOSS_IMPROVEMENT_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = profitLossImprovementLifecycle(
                    newPrefix, businessOccurredAt, operator.path("principalId").asText());
        } else if (RISK_DISPUTE_RESOLUTION_LIFECYCLE_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode reviewer = result(tenantId, READY_MASTER_SKILL, "principal");
            if (reviewer.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(
                    rotated, newPrefix, businessOccurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            if (!hydrateConsumerJourney(tenantId, consumer)) {
                return Optional.empty();
            }
            output = riskDisputeResolutionLifecycle(
                    newPrefix, businessOccurredAt, consumer, reviewer.path("principalId").asText());
        } else if (DATA_QUALITY_RECOVERY_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            output = dataQualityRecoveryLifecycle(
                    newPrefix, businessOccurredAt, operator.path("principalId").asText());
        } else if (MES_PRODUCTION_EXECUTION_LIFECYCLE_SKILL.equals(targetSkillId)) {
            TemporalApprovalPolicyRecord approvalPolicy = mapper.selectApprovalPolicy(tenantId);
            long operatorUserId = seedProperties.getOperatorUserId();
            Long reviewerUserId = approvalPolicy == null
                    ? null : approvalPolicy.getGovernanceUserId();
            if (operatorUserId <= 0 || reviewerUserId == null || reviewerUserId <= 0
                    || reviewerUserId == operatorUserId) {
                return Optional.empty();
            }
            output = mesProductionExecutionLifecycle(
                    newPrefix, date, operatorUserId, reviewerUserId);
        } else if (CATEGORY_DAILY_OPERATIONS_SKILL.equals(targetSkillId)) {
            if (seedProperties.getSyntheticConsumerMemberUserId() <= 0) {
                return Optional.empty();
            }
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            if (operator.path("principalId").asText().isBlank()) {
                return Optional.empty();
            }
            ObjectNode consumer = consumerJourney(rotated, newPrefix, occurredAt,
                    seedProperties.getSyntheticConsumerMemberUserId());
            output = categoryDailyOperations(rotated, newPrefix, occurredAt, consumer,
                    operator.path("principalId").asText(), occurrenceKey);
        } else if (CUSTOMER_SALES_PIPELINE_LIFECYCLE_SKILL.equals(targetSkillId)) {
            JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
            JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
            WmsCatalogProjectionSeedRecord catalog = mapper.selectLatestWmsCatalogProjectionSeed(tenantId);
            if (operator.path("principalId").asText().isBlank()
                    || merchant.path("merchantId").asText().isBlank()
                    || merchant.path("shopId").asText().isBlank()
                    || !usable(catalog)) {
                return Optional.empty();
            }
            output = customerSalesPipelineLifecycle(newPrefix, date, businessOccurredAt,
                    merchant.path("merchantId").asText(), merchant.path("shopId").asText(),
                    catalog.getCanonicalSkuId(), catalog.getBaseUomCode());
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
                JsonNode operator = result(tenantId, READY_MASTER_SKILL, "principal");
                if (operator.path("principalId").asText().isBlank()) {
                    return Optional.empty();
                }
                output = autonomousDay(rotated, newPrefix, occurredAt, consumer,
                        operator.path("principalId").asText());
            }
        }
        removeTechnicalExecutionParameters(output);
        // The SOP command contract has its own domain-level runId. It is distinct from the SkillTask execution
        // metadata stripped above and must survive so every supply-planning event is causally traceable.
        if (SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL.equals(targetSkillId) && output instanceof ObjectNode objectOutput) {
            objectOutput.put("runId", stableUuid(newPrefix + ":sop-run"));
        }
        return Optional.of(JsonUtils.toJsonString(output));
    }

    private static ObjectNode customerSalesPipelineLifecycle(
            String prefix, LocalDate businessDate, String occurredAt,
            String merchantId, String shopId, String canonicalSkuId, String baseUomCode) {
        String token = prefix.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        String correlationId = stableUuid(prefix + ":crm-sales-correlation");
        String customerName = "AI 受控客户 " + token;
        String leadName = customerName + " 线索";
        String opportunityName = customerName + " 年度合作";
        String businessCode = DigestUtil.sha256Hex(prefix).substring(0, 12).toUpperCase(Locale.ROOT);
        String leadCode = "AI_LEAD_" + businessCode;
        String customerCode = "AI_CUST_" + businessCode;
        String nextContactAt = businessDate.plusDays(1) + "T09:00:00";
        String expectedCloseDate = businessDate.plusDays(14).toString();

        ArrayNode commands = JsonNodeFactory.instance.arrayNode();
        commands.add(crmCommand(prefix, correlationId, occurredAt, 1, "CREATE_LEAD")
                .set("lead", JsonNodeFactory.instance.objectNode()
                        .put("leadCode", leadCode)
                        .put("leadName", leadName)
                        .put("sourceCode", "AI_MANAGED_OUTREACH")
                        .put("status", "NEW")
                        .put("contactChannelRef", "restricted:" + DigestUtil.sha256Hex(prefix + ":lead-channel").substring(0, 32))
                        .put("maskedContact", "***" + token.substring(Math.max(0, token.length() - 4)))
                        .put("nextFollowUpAt", nextContactAt)));
        commands.add(crmCommand(prefix, correlationId, occurredAt, 2, "RECORD_FOLLOW_UP")
                .set("followUp", JsonNodeFactory.instance.objectNode()
                        .put("subjectType", "LEAD")
                        .putNull("subjectId")
                        .put("methodCode", "NOTE")
                        .put("summary", "已确认受控销售场景的需求范围与下一步")
                        .put("nextFollowUpAt", nextContactAt)));
        commands.add(crmCommand(prefix, correlationId, occurredAt, 3, "UPDATE_LEAD")
                .set("lead", JsonNodeFactory.instance.objectNode()
                        .putNull("leadId")
                        .putNull("expectedVersion")
                        .put("leadCode", leadCode)
                        .put("leadName", leadName)
                        .put("sourceCode", "AI_MANAGED_OUTREACH")
                        .put("status", "QUALIFYING")
                        .put("contactChannelRef", "restricted:" + DigestUtil.sha256Hex(prefix + ":lead-channel").substring(0, 32))
                        .put("maskedContact", "***" + token.substring(Math.max(0, token.length() - 4)))
                        .put("nextFollowUpAt", nextContactAt)));
        commands.add(crmCommand(prefix, correlationId, occurredAt, 4, "CREATE_CUSTOMER")
                .set("customer", JsonNodeFactory.instance.objectNode()
                        .put("customerCode", customerCode)
                        .put("customerName", customerName)
                        .put("levelCode", "STANDARD")
                        .put("lifecycleStatus", "ACTIVE")
                        .put("poolStatus", "OWNED")
                        .put("sourceCode", "AI_MANAGED_OUTREACH")
                        .put("industryCode", "FASHION_RETAIL")
                        .put("regionCode", "CN")
                        .put("nextFollowUpAt", nextContactAt)));
        commands.add(crmCommand(prefix, correlationId, occurredAt, 5, "UPDATE_LEAD")
                .set("lead", JsonNodeFactory.instance.objectNode()
                        .putNull("leadId")
                        .putNull("expectedVersion")
                        .put("leadCode", leadCode)
                        .put("leadName", leadName)
                        .put("sourceCode", "AI_MANAGED_OUTREACH")
                        .put("status", "CONVERTED")
                        .put("contactChannelRef", "restricted:" + DigestUtil.sha256Hex(prefix + ":lead-channel").substring(0, 32))
                        .put("maskedContact", "***" + token.substring(Math.max(0, token.length() - 4)))
                        .put("nextFollowUpAt", nextContactAt)));
        commands.add(crmCommand(prefix, correlationId, occurredAt, 6, "CREATE_CONTACT")
                .set("contact", JsonNodeFactory.instance.objectNode()
                        .putNull("customerId")
                        .put("contactName", "受控联系人 " + token)
                        .put("roleTitle", "采购负责人")
                        .put("contactChannelRef", "restricted:" + DigestUtil.sha256Hex(prefix + ":contact-channel").substring(0, 32))
                        .put("maskedContact", "***" + token.substring(Math.max(0, token.length() - 4)))
                        .put("isPrimary", true)
                        .put("status", "ACTIVE")));
        commands.add(crmOpportunityCommand(prefix, correlationId, occurredAt, 7,
                "CREATE_OPPORTUNITY", opportunityName, "QUALIFICATION", expectedCloseDate));
        commands.add(crmCommand(prefix, correlationId, occurredAt, 8, "RECORD_FOLLOW_UP")
                .set("followUp", JsonNodeFactory.instance.objectNode()
                        .put("subjectType", "OPPORTUNITY")
                        .putNull("subjectId")
                        .put("methodCode", "NOTE")
                        .put("summary", "已形成受控方案并进入商机阶段评审")
                        .put("nextFollowUpAt", nextContactAt)));
        commands.add(crmOpportunityCommand(prefix, correlationId, occurredAt, 9,
                "UPDATE_OPPORTUNITY", opportunityName, "PROPOSAL", expectedCloseDate));
        commands.add(crmOpportunityCommand(prefix, correlationId, occurredAt, 10,
                "UPDATE_OPPORTUNITY", opportunityName, "NEGOTIATION", expectedCloseDate));
        commands.add(crmOpportunityCommand(prefix, correlationId, occurredAt, 11,
                "UPDATE_OPPORTUNITY", opportunityName, "CLOSED_WON", expectedCloseDate));
        String contractCode = "AI_CONTRACT_" + businessCode;
        ArrayNode salesContractCommands = JsonNodeFactory.instance.arrayNode();
        salesContractCommands.add(JsonNodeFactory.instance.objectNode()
                .put("operation", "CREATE_DRAFT")
                .put("idempotencyKey", "pending-sales-contract-command")
                .put("runId", prefix + "-crm-sales")
                .put("correlationId", correlationId)
                .put("causationId", stableUuid(prefix + ":crm-sales:12"))
                .put("occurredAt", occurredAt)
                .put("reasonCode", "AI_MANAGED_SALES_PIPELINE")
                .put("contractCode", contractCode)
                .put("contractName", customerName + " 销售合同")
                .putNull("customerId")
                .put("sellerMerchantId", merchantId)
                .put("sellerShopId", shopId)
                .put("currencyCode", "CNY")
                .put("effectiveDate", businessDate.toString())
                .put("expiresOn", businessDate.plusYears(1).toString())
                .set("items", JsonNodeFactory.instance.arrayNode().add(
                        JsonNodeFactory.instance.objectNode()
                                .put("canonicalSkuId", canonicalSkuId)
                                .put("itemName", "AI 受控销售商品 " + token)
                                .put("uomCode", baseUomCode)
                                .put("quantity", "1")
                                .put("unitPriceMinor", 3_980_000L)
                                .put("lineAmountMinor", 3_980_000L))));
        salesContractCommands.add(JsonNodeFactory.instance.objectNode()
                .put("operation", "SUBMIT_APPROVAL")
                .put("idempotencyKey", "pending-sales-contract-command")
                .put("runId", prefix + "-crm-sales")
                .put("correlationId", correlationId)
                .put("causationId", stableUuid(prefix + ":crm-sales:13"))
                .put("occurredAt", occurredAt)
                .put("reasonCode", "AI_MANAGED_SALES_PIPELINE")
                .putNull("salesContractId")
                .putNull("expectedVersion"));

        ArrayNode receivablesCommands = JsonNodeFactory.instance.arrayNode();
        receivablesCommands.add(receivablesCommandEnvelope(prefix, correlationId, occurredAt, 14)
                .put("planCode", "AI_PLAN_" + businessCode)
                .putNull("customerId")
                .putNull("salesContractId")
                .put("plannedAmountMinor", 3_980_000L)
                .put("currencyCode", "CNY")
                .put("dueDate", businessDate.plusDays(30).toString())
                .put("reasonCode", "AI_MANAGED_SALES_PIPELINE"));
        receivablesCommands.add(receivablesCommandEnvelope(prefix, correlationId, occurredAt, 15)
                .put("receiptCode", "AI_RECEIPT_" + businessCode)
                .putNull("customerId")
                .putNull("salesContractId")
                .put("receiptAmountMinor", 3_980_000L)
                .put("currencyCode", "CNY")
                .put("receiptDate", businessDate.toString())
                .put("externalReference", "ai-managed:" + businessCode)
                .put("reasonCode", "AI_MANAGED_SALES_PIPELINE"));
        receivablesCommands.add(receivablesCommandEnvelope(prefix, correlationId, occurredAt, 16)
                .putNull("receiptId")
                .putNull("expectedVersion")
                .put("reasonCode", "AI_MANAGED_SALES_PIPELINE")
                .set("allocations", JsonNodeFactory.instance.arrayNode().add(
                        JsonNodeFactory.instance.objectNode()
                                .putNull("receivablePlanId")
                                .putNull("expectedPlanVersion")
                                .put("amountMinor", 3_980_000L))));
        ObjectNode output = JsonNodeFactory.instance.objectNode()
                .put("scenarioVersion", "cloudmold.crm-customer-sales-receivables/v2")
                .put("classification", "LOCAL_TEST");
        output.set("commands", commands);
        output.set("salesContractCommands", salesContractCommands);
        output.set("receivablesCommands", receivablesCommands);
        return output;
    }

    private static ObjectNode receivablesCommandEnvelope(
            String prefix, String correlationId, String occurredAt, int sequence) {
        return JsonNodeFactory.instance.objectNode().set("envelope",
                JsonNodeFactory.instance.objectNode()
                        .put("correlationId", correlationId)
                        .put("causationId", stableUuid(prefix + ":crm-sales:" + sequence))
                        .put("runId", prefix + "-crm-sales")
                        .put("idempotencyKey", "pending-receivables-command")
                        .put("occurredAt", occurredAt));
    }

    private static ObjectNode crmCommand(
            String prefix, String correlationId, String occurredAt, int sequence, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", "pending-crm-command")
                .put("runId", prefix + "-crm-sales")
                .put("correlationId", correlationId)
                .put("causationId", stableUuid(prefix + ":crm-sales:" + sequence))
                .put("occurredAt", occurredAt)
                .put("reasonCode", "AI_MANAGED_SALES_PIPELINE");
    }

    private static ObjectNode crmOpportunityCommand(
            String prefix, String correlationId, String occurredAt, int sequence,
            String operation, String opportunityName, String stage, String expectedCloseDate) {
        return crmCommand(prefix, correlationId, occurredAt, sequence, operation)
                .set("opportunity", JsonNodeFactory.instance.objectNode()
                        .putNull("opportunityId")
                        .putNull("customerId")
                        .putNull("expectedVersion")
                        .put("opportunityCode", "AI_OPP_" + DigestUtil.sha256Hex(prefix).substring(0, 12).toUpperCase(Locale.ROOT))
                        .put("opportunityName", opportunityName)
                        .put("stage", stage)
                        .put("expectedAmountMinor", 3980000L)
                        .put("currencyCode", "CNY")
                        .put("expectedCloseDate", expectedCloseDate));
    }

    private static void removeTechnicalExecutionParameters(JsonNode value) {
        if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (TECHNICAL_EXECUTION_KEYS.contains(normalized)) {
                    fields.remove();
                } else {
                    removeTechnicalExecutionParameters(field.getValue());
                }
            }
        } else if (value.isArray()) {
            value.forEach(RotatingBusinessScenarioInputFactory::removeTechnicalExecutionParameters);
        }
    }

    private static String scenarioCode(String skillId) {
        return switch (skillId) {
            case FULL_CHAIN_SKILL -> "f";
            case PRODUCT_TO_LISTING_SKILL -> "p";
            case PRICING_REPRICE_LIFECYCLE_SKILL -> "pr";
            case AUTONOMOUS_DAY_SKILL -> "d";
            case CATEGORY_DAILY_OPERATIONS_SKILL -> "y";
            case CUSTOMER_SALES_PIPELINE_LIFECYCLE_SKILL -> "cs";
            case CATALOG_MATRIX_SKILL -> "c";
            case AFTERSALE_SAGA_SKILL -> "a";
            case ORDER_CANCELLATION_OPERATIONS_SKILL -> "i";
            case CONSUMER_JOURNEY_SKILL -> "u";
            case MERCHANT_ONBOARDING_SKILL -> "h";
            case MERCHANT_MANAGED_GROWTH_LIFECYCLE_SKILL -> "mg";
            case PROMOTION_CAMPAIGN_SKILL -> "e";
            case GROWTH_EXPERIMENT_SKILL -> "g";
            case PROCUREMENT_SOURCING_SKILL -> "s";
            case PROCUREMENT_ORDER_SKILL -> "q";
            case WMS_OPERATIONS_SKILL -> "w";
            case REPLENISHMENT_LIFECYCLE_SKILL -> "r";
            case SUPPLY_PLANNING_SOP_LIFECYCLE_SKILL -> "v";
            case CUSTOMER_SERVICE_LIFECYCLE_SKILL -> "k";
            case FINANCE_CLOSE_LIFECYCLE_SKILL -> "n";
            case QUALITY_INSPECTION_RECALL_LIFECYCLE_SKILL -> "j";
            case FULFILLMENT_EXCEPTION_LIFECYCLE_SKILL -> "x";
            case CROSSBORDER_FULFILLMENT_COMPLIANCE_LIFECYCLE_SKILL -> "b";
            case BONDED_CUSTOMS_LIFECYCLE_SKILL -> "t";
            case PARTNER_MARKETING_KOL_MEDIA_OPERATIONS_SKILL -> "o";
            case MES_PRODUCTION_EXECUTION_LIFECYCLE_SKILL -> "z";
            case ASSORTMENT_PLANNING_LIFECYCLE_SKILL -> "aa";
            case PRODUCT_MANAGEMENT_LIFECYCLE_SKILL -> "pm";
            case WAREHOUSE_ADMISSION_LIFECYCLE_SKILL -> "wa";
            case CUSTOMER_EXPERIENCE_TICKET_RESPONSIBILITY_LIFECYCLE_SKILL -> "cx";
            case MERCHANT_EXPERIENCE_RECTIFICATION_LIFECYCLE_SKILL -> "mr";
            case UNFULFILLABLE_ORDER_COMPENSATION_LIFECYCLE_SKILL -> "uc";
            case LOGISTICS_SERVICE_SETTLEMENT_LIFECYCLE_SKILL -> "ls";
            case MERCHANT_SERVICE_FEE_SETTLEMENT_LIFECYCLE_SKILL -> "ms";
            case ADVERTISING_FEE_SETTLEMENT_LIFECYCLE_SKILL -> "as";
            case PROFIT_LOSS_IMPROVEMENT_LIFECYCLE_SKILL -> "pl";
            case RISK_DISPUTE_RESOLUTION_LIFECYCLE_SKILL -> "rd";
            case DATA_QUALITY_RECOVERY_LIFECYCLE_SKILL -> "dq";
            case READY_MASTER_SKILL -> "m";
            default -> throw new IllegalArgumentException("Unsupported rotating scenario Skill: " + skillId);
        };
    }

    private static ObjectNode assortmentPlanningLifecycle(
            String prefix, LocalDate date, String occurredAt, String actorPrincipalId) {
        String token = prefix.replace("-", "").toUpperCase(Locale.ROOT);
        String waveId = stableUuid(prefix + ":assortment-wave");
        String correlationId = stableUuid(prefix + ":assortment-correlation");
        String runId = prefix + "-assortment";
        String season = switch (date.getMonthValue()) {
            case 3, 4, 5 -> "SPRING";
            case 6, 7, 8 -> "SUMMER";
            case 9, 10, 11 -> "AUTUMN";
            default -> "WINTER";
        };
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("waveId", waveId);
        ArrayNode commands = root.putArray("commands");

        ObjectNode create = assortmentCommand(
                commands, "CREATE_WAVE", correlationId, runId, occurredAt, actorPrincipalId);
        create.putObject("wave")
                .put("waveId", waveId)
                .put("waveCode", "ASW" + token)
                .put("planningYear", date.getYear())
                .put("seasonCode", season)
                .put("categoryCode", "APPAREL")
                .put("trendBrief", "结合站内搜索、社媒趋势与消费者研究生成的 occurrence 级新品波段")
                .put("targetAudience", "重视通勤舒适、设计感与性价比的核心服饰消费者")
                .put("targetStyleCount", 2)
                .put("targetPriceFloorMinor", 10000)
                .put("targetPriceCeilingMinor", 50000)
                .put("targetGrossMarginBps", 4500)
                .put("maxReturnRateBps", 1800)
                .put("launchStartDate", date.plusDays(15).toString())
                .put("launchEndDate", date.plusDays(45).toString())
                .put("currencyCode", "CNY")
                .put("reasonCode", "DAILY_ASSORTMENT_WAVE");

        String entryId = stableUuid(prefix + ":assortment-entry");
        String coreId = stableUuid(prefix + ":assortment-core");
        String premiumId = stableUuid(prefix + ":assortment-premium");
        addAssortmentCandidate(commands, correlationId, runId, occurredAt, actorPrincipalId,
                waveId, 1L, entryId, "ASE" + token, "轻量通勤针织上衣",
                "SEARCH_TREND", "trend:search:" + prefix, "ENTRY", 19900, 9000);
        addAssortmentEvaluation(commands, correlationId, runId, occurredAt, actorPrincipalId,
                waveId, 2L, entryId, 86, 83, 88, 24, 1200,
                "搜索增长稳定、受众匹配高，供应风险可控");
        addAssortmentCandidate(commands, correlationId, runId, occurredAt, actorPrincipalId,
                waveId, 3L, coreId, "ASC" + token, "结构感通勤连衣裙",
                "SOCIAL_TREND", "trend:social:" + prefix, "CORE", 29900, 14000);
        addAssortmentEvaluation(commands, correlationId, runId, occurredAt, actorPrincipalId,
                waveId, 4L, coreId, 91, 89, 90, 28, 1400,
                "社媒内容增长与站内需求共振，适合作为核心价格带主推款");
        addAssortmentCandidate(commands, correlationId, runId, occurredAt, actorPrincipalId,
                waveId, 5L, premiumId, "ASP" + token, "高端羊毛混纺外套",
                "COMPETITOR", "trend:competitor:" + prefix, "PREMIUM", 49900, 30000);
        addAssortmentEvaluation(commands, correlationId, runId, occurredAt, actorPrincipalId,
                waveId, 6L, premiumId, 94, 78, 82, 55, 2500,
                "趋势热度高，但成本、供给风险和预测退货率不满足本波段约束");

        ObjectNode select = assortmentCommand(
                commands, "SELECT_PORTFOLIO", correlationId, runId, occurredAt, actorPrincipalId);
        select.putObject("portfolio")
                .put("waveId", waveId)
                .put("expectedWaveVersion", 7)
                .put("decisionPolicyVersion", "assortment-policy:v1")
                .put("decisionEvidenceSha256", DigestUtil.sha256Hex(prefix + ":portfolio"))
                .put("reasonCode", "AI_CONSTRAINED_PORTFOLIO");

        ObjectNode approve = assortmentCommand(
                commands, "APPROVE_WAVE", correlationId, runId, occurredAt, actorPrincipalId);
        approve.putObject("approval")
                .put("waveId", waveId)
                .put("expectedWaveVersion", 8)
                .put("approvalNote", "独立审批确认组合符合毛利、退货率和价格带策略")
                .put("reasonCode", "ASSORTMENT_APPROVED");

        ObjectNode publish = assortmentCommand(
                commands, "PUBLISH_WAVE", correlationId, runId, occurredAt, actorPrincipalId);
        publish.putObject("publication")
                .put("waveId", waveId)
                .put("expectedWaveVersion", 9)
                .put("launchCalendarRef", "calendar:assortment:" + prefix)
                .put("downstreamHandoffRef", "handoff:product-development:" + prefix)
                .put("publicationEvidenceSha256", DigestUtil.sha256Hex(prefix + ":publication"))
                .put("reasonCode", "ASSORTMENT_PUBLISHED");
        return root;
    }

    private static ObjectNode assortmentCommand(
            ArrayNode commands, String operation, String correlationId, String runId,
            String occurredAt, String actorPrincipalId) {
        ObjectNode command = commands.addObject();
        command.put("operation", operation)
                .put("correlationId", correlationId)
                .put("runId", runId)
                .put("idempotencyKey", "pending-assortment-" + commands.size())
                .put("occurredAt", occurredAt)
                .put("actorPrincipalId", actorPrincipalId);
        return command;
    }

    private static void addAssortmentCandidate(
            ArrayNode commands, String correlationId, String runId, String occurredAt,
            String actorPrincipalId, String waveId, long expectedVersion, String candidateId,
            String candidateCode, String concept, String signalType, String signalRef,
            String priceBand, long priceMinor, long costMinor) {
        assortmentCommand(commands, "ADD_CANDIDATE", correlationId, runId, occurredAt, actorPrincipalId)
                .putObject("candidate")
                .put("waveId", waveId)
                .put("expectedWaveVersion", expectedVersion)
                .put("candidateId", candidateId)
                .put("candidateCode", candidateCode)
                .put("productConcept", concept)
                .put("sourceSignalType", signalType)
                .put("sourceSignalRef", signalRef)
                .put("priceBandCode", priceBand)
                .put("targetPriceMinor", priceMinor)
                .put("expectedUnitCostMinor", costMinor)
                .put("reasonCode", "CANDIDATE_DISCOVERED");
    }

    private static void addAssortmentEvaluation(
            ArrayNode commands, String correlationId, String runId, String occurredAt,
            String actorPrincipalId, String waveId, long expectedVersion, String candidateId,
            int trendScore, int demandScore, int audienceFitScore, int supplyRiskScore,
            int predictedReturnRateBps, String rationale) {
        assortmentCommand(commands, "EVALUATE_CANDIDATE", correlationId, runId,
                occurredAt, actorPrincipalId)
                .putObject("evaluation")
                .put("waveId", waveId)
                .put("expectedWaveVersion", expectedVersion)
                .put("candidateId", candidateId)
                .put("trendScore", trendScore)
                .put("demandScore", demandScore)
                .put("audienceFitScore", audienceFitScore)
                .put("supplyRiskScore", supplyRiskScore)
                .put("predictedReturnRateBps", predictedReturnRateBps)
                .put("evidenceSha256", DigestUtil.sha256Hex(candidateId + ":" + rationale))
                .put("rationale", rationale)
                .put("reasonCode", "AI_CANDIDATE_EVALUATED");
    }

    private static ObjectNode mesProductionExecutionLifecycle(
            String prefix, LocalDate date, long operatorUserId, long reviewerUserId) {
        String codeToken = prefix.replace("-", "").toUpperCase(Locale.ROOT);
        String businessStart = date + "T09:00:00";
        String businessEnd = date + "T17:00:00";
        String expireDate = date.plusYears(1) + "T23:59:59";
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putObject("runIds").put("readiness", prefix + "-production-readiness");
        root.put("operatorUserId", operatorUserId);
        root.put("reviewerUserId", reviewerUserId);

        ObjectNode readiness = root.putObject("readiness");
        readiness.putObject("unitMeasure")
                .put("idempotencyKey", "pending-mes-unit")
                .put("code", "CMU" + codeToken)
                .put("name", "件")
                .put("primaryFlag", true)
                .putNull("primaryId")
                .putNull("changeRate")
                .put("status", 0)
                .put("remark", "CloudMold AI 新品试产计量单位 " + prefix);
        readiness.putObject("itemType")
                .put("idempotencyKey", "pending-mes-item-type")
                .put("parentId", 0)
                .put("code", "CMT" + codeToken)
                .put("name", "AI 新品试产产成品 " + codeToken)
                .put("itemOrProduct", "PRODUCT")
                .put("sort", 1)
                .put("status", 0)
                .put("remark", "CloudMold AI 新品试产分类 " + prefix);
        readiness.putObject("item")
                .put("idempotencyKey", "pending-mes-item")
                .put("code", "CMI" + codeToken)
                .put("name", "CloudMold AI 新品 " + codeToken)
                .put("specification", "每日 occurrence 新建并完成试产")
                .put("unitMeasureId", 0)
                .put("itemTypeId", 0)
                .put("safeStockFlag", true)
                .put("minStock", 6)
                .put("maxStock", 120)
                .put("highValue", false)
                // V1 models fresh-product trial production without raw-material or
                // batch-attribute setup. Batch tracing is introduced with the BOM
                // and material-issue slice instead of creating an invalid partial
                // batch configuration here.
                .put("batchFlag", false)
                .put("remark", "由生产主管 Temporal 工作流创建");
        readiness.putObject("workshop")
                .put("idempotencyKey", "pending-mes-workshop")
                .put("code", "CMW" + codeToken)
                .put("name", "AI 新品试产车间 " + codeToken)
                .put("area", 120)
                .put("chargeUserId", operatorUserId)
                .put("status", 0)
                .put("remark", "试产车间 " + prefix);
        readiness.putObject("process")
                .put("idempotencyKey", "pending-mes-process")
                .put("code", "CMP" + codeToken)
                .put("name", "新品成型与终检 " + codeToken)
                .put("attention", "按排产数量生产，报工合格数量必须与产出入库一致")
                .put("status", 0)
                .put("remark", "关键工序 " + prefix);
        readiness.putObject("workstation")
                .put("idempotencyKey", "pending-mes-workstation")
                .put("code", "CMS" + codeToken)
                .put("name", "AI 试产工作站 " + codeToken)
                .put("address", "CloudMold TEST / " + prefix)
                .put("workshopId", 0)
                .put("processId", 0)
                .putNull("warehouseId")
                .putNull("locationId")
                .putNull("areaId")
                .put("status", 0)
                .put("remark", "生产任务执行站");
        readiness.putObject("route")
                .put("idempotencyKey", "pending-mes-route")
                .put("code", "CMR" + codeToken)
                .put("name", "AI 新品试产路线 " + codeToken)
                .put("description", "单一关键工序，完成合格品产出与入库")
                .put("remark", "生产主管试产路线");
        readiness.putObject("routeProcess")
                .put("idempotencyKey", "pending-mes-route-process")
                .put("routeId", 0)
                .put("processId", 0)
                .put("sort", 1)
                .put("linkType", 0)
                .put("prepareTime", 30)
                .put("waitTime", 5)
                .put("colorCode", "#1677FF")
                .put("keyFlag", true)
                .put("checkFlag", false)
                .put("remark", "关键工序记录合格/不合格并触发产成品入库");
        readiness.putObject("routeStatus")
                .put("idempotencyKey", "pending-mes-route-status")
                .put("routeId", 0)
                .put("status", 0);

        root.putObject("workOrder")
                .put("idempotencyKey", "pending-mes-work-order")
                .put("code", "CMO" + codeToken)
                .put("name", "CloudMold AI 新品试产工单 " + codeToken)
                .put("type", 1)
                .put("orderSourceType", 2)
                .put("orderSourceCode", "AI-STOCK-" + codeToken)
                .put("productId", 0)
                .put("quantity", 12)
                .putNull("clientId")
                .putNull("vendorId")
                .put("batchCode", "CMB" + codeToken)
                .put("requestDate", businessEnd)
                .put("parentId", 0)
                .put("remark", "每日 occurrence 新品试产工单");
        root.putObject("documentAction")
                .put("idempotencyKey", "pending-mes-document-action")
                .put("documentId", 0);
        root.putObject("task")
                .put("idempotencyKey", "pending-mes-task")
                .put("workOrderId", 0)
                .put("workstationId", 0)
                .put("routeId", 0)
                .put("processId", 0)
                .put("itemId", 0)
                .put("quantity", 12)
                .put("startTime", businessStart)
                .put("duration", 1)
                .put("endTime", businessEnd)
                .put("colorCode", "#1677FF")
                .put("remark", "生产主管派工任务");
        root.putObject("feedback")
                .put("idempotencyKey", "pending-mes-feedback")
                .put("code", "CMF" + codeToken)
                .put("type", 1)
                .put("workstationId", 0)
                .put("routeId", 0)
                .put("processId", 0)
                .put("workOrderId", 0)
                .put("taskId", 0)
                .put("itemId", 0)
                .put("expireDate", expireDate)
                .put("lotNumber", "CML" + codeToken)
                .put("scheduledQuantity", 12)
                .put("feedbackQuantity", 12)
                .put("qualifiedQuantity", 12)
                .put("unqualifiedQuantity", 0)
                .put("uncheckQuantity", 0)
                .put("laborScrapQuantity", 0)
                .put("materialScrapQuantity", 0)
                .put("otherScrapQuantity", 0)
                .put("feedbackUserId", operatorUserId)
                .put("feedbackTime", businessEnd)
                .put("approveUserId", reviewerUserId)
                .put("remark", "AI 生产主管报工；合格品审批后入库");
        return root;
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
        ObjectNode favoriteCommand = objectAt(consumer.path("favoriteCommand"), -1);
        favoriteCommand.put("canonicalSpuId", canonicalSpuId);
        JsonNode identityReference = consumer.path("identityReference");
        ConsumerFavoriteSeedRecord existingFavorite = mapper.selectConsumerFavorite(
                tenantId,
                identityReference.path("sourceSystem").asText(),
                identityReference.path("sourceType").asText(),
                identityReference.path("sourceId").asText(),
                canonicalSpuId);
        if (existingFavorite != null) {
            favoriteCommand.put("favoriteId", existingFavorite.getFavoriteId())
                    .put("expectedVersion", existingFavorite.getVersion())
                    .put("desiredStatus", "ACTIVE".equals(existingFavorite.getStatus()) ? "REMOVED" : "ACTIVE");
        }
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

    private String resolveSystemPrincipal(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        try {
            return identityQueries.resolveActiveSource(new SourceIdentityReference(
                    "SYSTEM", "SYSTEM_ADMIN_USER", Long.toString(userId))).getPrincipalId();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean hydrateAfterSaleScenario(Long tenantId, ObjectNode aftersale) {
        JsonNode listing = result(tenantId, PRODUCT_TO_LISTING_SKILL, "listing_create");
        JsonNode catalog = result(tenantId, CATALOG_MATRIX_SKILL, "define_1");
        JsonNode merchant = result(tenantId, READY_MASTER_SKILL, "merchant_approve");
        JsonNode warehouse = result(tenantId, READY_MASTER_SKILL, "warehouse_network");
        String listingId = listing.path("listingId").asText();
        String listingOfferId = listing.path("offers").path(0).path("listingOfferId").asText();
        String canonicalSpuId = catalog.path("canonicalSpuId").asText();
        String canonicalSkuId = catalog.path("canonicalSkuId").asText();
        String merchantId = merchant.path("merchantId").asText();
        String shopId = merchant.path("shopId").asText();
        String warehouseId = warehouse.path("warehouseId").asText();
        if (listingId.isBlank() || listingOfferId.isBlank()
                || canonicalSpuId.isBlank() || canonicalSkuId.isBlank()
                || merchantId.isBlank() || shopId.isBlank()
                || warehouseId.isBlank()) {
            return false;
        }

        JsonNode commands = aftersale.path("commands");
        if (!commands.isArray() || commands.size() < 26) {
            return false;
        }

        ObjectNode receive = objectAt(commands, 6);
        receive.put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId);
        objectAt(objectAt(commands, 7).path("items"), 0)
                .put("canonicalSkuId", canonicalSkuId)
                .put("listingId", listingId)
                .put("listingOfferId", listingOfferId);
        ObjectNode reserve = objectAt(commands, 8);
        reserve.put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId);
        ObjectNode fulfillment = objectAt(commands, 12);
        fulfillment.put("sellerId", merchantId).put("warehouseId", warehouseId);
        objectAt(fulfillment.path("items"), 0).put("canonicalSkuId", canonicalSkuId);
        ObjectNode ship = objectAt(commands, 13);
        ship.put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId);
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

    private static void prepareAfterSaleDisposition(ObjectNode fullChain, String prefix, String occurredAt,
                                                    boolean scrapScenario) {
        JsonNode commandsNode = fullChain.path("aftersale").path("commands");
        if (!(commandsNode instanceof ArrayNode commands) || commands.size() < 25
                || !(commands.get(24) instanceof ObjectNode inspection)) {
            return;
        }
        String runId = inspection.path("runId").asText(prefix + "-aftersale");
        String correlationId = inspection.path("correlationId")
                .asText(stableUuid(prefix + ":aftersale-correlation"));
        ObjectNode assessment = JsonNodeFactory.instance.objectNode()
                .put("operation", "ASSESS_DISPOSITION")
                .put("idempotencyKey", prefix + "-disposition-assess")
                .put("runId", runId)
                .put("afterSaleId", ZERO_UUID)
                .put("expectedVersion", 2)
                .put("assessorId", "ai-return-assessor:" + prefix)
                .put("packagingScore", scrapScenario ? 40 : 96)
                .put("appearanceScore", scrapScenario ? 30 : 95)
                .put("functionScore", scrapScenario ? 20 : 98)
                .put("safetyRisk", scrapScenario)
                .put("counterfeitRisk", false)
                .put("estimatedResaleValueMinor", scrapScenario ? 12000 : 39800)
                .put("estimatedRecoveryCostMinor", scrapScenario ? 9000 : 1000)
                .put("inspectionEvidenceRef", "restricted:return-inspection:" + prefix)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        String causationId = inspection.path("causationId").asText();
        if (!causationId.isBlank()) {
            assessment.put("causationId", causationId);
        }
        commands.insert(24, assessment);
        inspection.put("inspectorId", "warehouse-inspector:" + prefix);
        inspection.put("dispositionAssessmentId", ZERO_UUID);
        inspection.put("dispositionCode", scrapScenario ? "SCRAP" : "RESTOCK");
        inspection.put("qualityStatus", scrapScenario ? "DAMAGED" : "QUALIFIED");
    }

    private JsonNode result(Long tenantId, String stepCode) {
        return result(tenantId, READY_MASTER_SKILL, stepCode);
    }

    private JsonNode result(Long tenantId, String skillId, String stepCode) {
        String result = mapper.selectLatestSuccessfulSkillTaskStepResult(tenantId, skillId, stepCode);
        return result == null || result.isBlank()
                ? JsonNodeFactory.instance.missingNode() : JsonUtils.parseTree(result);
    }

    private CatalogProcurementLine catalogProcurementLine(Long tenantId, String stepCode, int definitionIndex) {
        JsonNode result = result(tenantId, CATALOG_MATRIX_SKILL, stepCode);
        String catalogInputJson = mapper.selectLatestSuccessfulSkillTaskInput(tenantId, CATALOG_MATRIX_SKILL);
        JsonNode catalogInput = catalogInputJson == null || catalogInputJson.isBlank()
                ? JsonNodeFactory.instance.missingNode() : JsonUtils.parseTree(catalogInputJson);
        JsonNode definition = catalogInput.isMissingNode()
                ? JsonNodeFactory.instance.missingNode()
                : catalogInput.path("definitions").path(definitionIndex);
        return new CatalogProcurementLine(
                result.path("canonicalSkuId").asText(), definition.path("baseUomCode").asText());
    }

    private Optional<ProcurementSourcingSeed> procurementSourcingSeed(Long tenantId) {
        String inputJson = mapper.selectLatestSuccessfulSkillTaskInput(
                tenantId, PROCUREMENT_SOURCING_SKILL);
        JsonNode input = inputJson == null || inputJson.isBlank()
                ? JsonNodeFactory.instance.missingNode() : JsonUtils.parseTree(inputJson);
        if (!input.isObject()
                || !PROCUREMENT_SOURCING_INPUT_SCHEMA.equals(input.path("schemaVersion").asText())
                || !input.path("supplierCandidates").isArray()
                || input.path("supplierCandidates").size() != 2
                || !input.path("valuationPolicies").isArray()
                || input.path("valuationPolicies").size() != 2) {
            return Optional.empty();
        }
        JsonNode actors = input.path("actors");
        ProcurementSourcingSeed seed = new ProcurementSourcingSeed(
                input.path("legalEntityId").asText(),
                input.path("supplierCandidates").get(0).path("supplierId").asText(),
                input.path("supplierCandidates").get(1).path("supplierId").asText(),
                valuationPolicy(input.path("valuationPolicies").get(0)),
                valuationPolicy(input.path("valuationPolicies").get(1)),
                actors.path("creatorPrincipalId").asText(),
                actors.path("reviewerAPrincipalId").asText(),
                actors.path("reviewerBPrincipalId").asText(),
                actors.path("approverPrincipalId").asText());
        return seed.usable() ? Optional.of(seed) : Optional.empty();
    }

    private static ValuationPolicySeed valuationPolicy(JsonNode input) {
        return new ValuationPolicySeed(
                input.path("canonicalSkuId").asText(),
                input.path("valuationPolicyId").asText(),
                input.path("valuationPolicyVersion").asText(),
                input.path("valuationPolicyHash").asText());
    }

    private static boolean usable(WmsCatalogProjectionSeedRecord projection) {
        return projection != null && projection.getSkuCode() != null
                && !projection.getSkuCode().isBlank() && projection.getPrimaryBarcode() != null
                && !projection.getPrimaryBarcode().isBlank() && projection.getBaseUomCode() != null
                && !projection.getBaseUomCode().isBlank();
    }

    private static boolean usable(WmsTransferSeedRecord seed) {
        return seed != null && seed.getSourceWarehouseId() != null && seed.getSourceWarehouseId() > 0
                && seed.getTargetWarehouseId() != null && seed.getTargetWarehouseId() > 0
                && !seed.getSourceWarehouseId().equals(seed.getTargetWarehouseId())
                && seed.getWmsSkuId() != null && seed.getWmsSkuId() > 0
                && seed.getItemId() != null && seed.getItemId() > 0
                && text(seed.getWmsSkuCode()) && text(seed.getWmsBarcode())
                && text(seed.getItemUnit()) && text(seed.getSourceWarehouseMappingId())
                && text(seed.getSourceCanonicalWarehouseId()) && text(seed.getTargetWarehouseMappingId())
                && text(seed.getCanonicalWarehouseId()) && text(seed.getCanonicalSkuId())
                && !seed.getSourceCanonicalWarehouseId().equals(seed.getCanonicalWarehouseId())
                && text(seed.getCatalogSkuCode()) && text(seed.getCatalogBarcode())
                && text(seed.getBaseUomCode());
    }

    private static String wmsTransferEvidence(WmsTransferSeedRecord seed) {
        return DigestUtil.sha256Hex(String.join("\u001f", List.of(
                "TRANSFER_REQUEST", seed.getCanonicalSkuId(), seed.getSourceCanonicalWarehouseId(),
                seed.getCanonicalWarehouseId(),
                seed.getCatalogSkuCode(), seed.getCatalogBarcode(), unitCode(seed.getBaseUomCode()),
                String.valueOf(seed.getWmsSkuId()), String.valueOf(seed.getItemId()),
                seed.getWmsSkuCode(), seed.getWmsBarcode(), unitCode(seed.getItemUnit()),
                String.valueOf(seed.getSourceWarehouseId()), String.valueOf(seed.getTargetWarehouseId()),
                seed.getSourceWarehouseMappingId(), seed.getTargetWarehouseMappingId())));
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static String unitCode(String value) {
        return value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
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
            String publishStartAt = createCommand.path("publishStartAt").asText();
            if (!publishStartAt.isBlank()) {
                createCommand.put("publishStartAt", LocalDate.parse(publishStartAt.substring(0, 10))
                        .atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toString());
            }
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
        output.putObject("readback").put("listingId", ZERO_UUID);
        return output;
    }

    private static ObjectNode pricingRepriceLifecycle(String prefix, String occurredAt, String principalId,
                                                       JsonNode publishedListing) {
        String listingId = publishedListing.path("listingId").asText();
        long expectedVersion = publishedListing.path("aggregateVersion").asLong();
        String correlationId = stableUuid(prefix + ":pricing-reprice");
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        ObjectNode command = output.putObject("reprice")
                .put("operation", "REPRICE")
                .put("idempotencyKey", prefix + "-reprice")
                .put("runId", prefix + "-pricing")
                .put("listingId", listingId)
                .put("expectedVersion", expectedVersion)
                .put("publisherRef", principalId)
                .put("reason", "daily controlled pricing adjustment with immutable listing revision")
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        ArrayNode offers = command.putArray("offers");
        int index = 0;
        for (JsonNode offer : publishedListing.path("offers")) {
            offers.addObject().put("canonicalSkuId", offer.path("canonicalSkuId").asText())
                    .put("priceMinor", offer.path("priceMinor").asLong() + (index++ == 0 ? 100L : 0L))
                    .put("currencyCode", offer.path("currencyCode").asText("CNY"))
                    .put("enabled", true);
        }
        output.putObject("receipt").put("idempotencyKey", prefix + "-reprice-receipt")
                .put("listingId", listingId).put("expectedVersion", expectedVersion + 1)
                .put("outcome", "CONFIRMED_PUBLISHED").put("channelListingId", "internal-channel:" + prefix)
                .put("channelStatus", "ONLINE").put("confirmedAt", occurredAt)
                .put("evidenceRef", "synthetic:yshopping-internal:reprice:" + prefix)
                .put("correlationId", correlationId).put("occurredAt", occurredAt);
        output.putObject("readback").put("listingId", listingId);
        return output;
    }

    private static ObjectNode autonomousDay(ObjectNode fullChain, String prefix, String occurredAt,
                                            ObjectNode consumer, String operatorPrincipalId) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        ObjectNode runIds = output.putObject("runIds");
        runIds.put("product", prefix + "-product");
        runIds.put("consumer", prefix + "-consumer");
        output.put("operatorPrincipalId", operatorPrincipalId);
        addRoleOperationsCase(output, prefix, occurredAt, operatorPrincipalId,
                "AI_COMMERCE_DAY_", "commerce-day:", "commerce_day_",
                "COMMERCE_OPERATIONS", "DAILY_FULL_CYCLE",
                "DAILY_PLAN_DISPATCHED", "OPERATIONS_CONTROL_ASSIGNED",
                "FULL_CYCLE_VERIFIED");
        output.set("product", productToListing(fullChain, prefix));
        output.set("consumer", consumer);
        return output;
    }

    private static ObjectNode orderCancellationOperations(
            String prefix, String occurredAt, ObjectNode consumer, String operatorPrincipalId) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("runIds")
                .put("orderScenario", prefix + "-paid-unshipped-order");
        output.put("operatorPrincipalId", operatorPrincipalId);
        output.set("orderScenario", consumer);
        addRoleOperationsCase(output, prefix, occurredAt, operatorPrincipalId,
                "AI_ORDER_CANCEL_", "order-cancellation:", "order_cancellation_",
                "ORDER_EXCEPTION", "PAID_UNSHIPPED_CANCELLATION",
                "CANCELLATION_REQUEST_RECEIVED", "ORDER_EXCEPTION_OPERATOR_ASSIGNED",
                "CANCELLATION_COMPENSATION_VERIFIED");
        output.putObject("cancellation")
                .put("operation", "START")
                .put("cancellationMode", "PAID_UNSHIPPED")
                .put("responsibilityParty", "BUYER")
                .put("responsibilityCode", "BUYER_CHANGED_MIND")
                .put("reason", "AI 模拟消费者付款后、发货前申请取消")
                .put("idempotencyKey", "pending-order-cancellation")
                .put("runId", prefix + "-order-cancellation")
                .put("orderId", ZERO_UUID)
                .put("correlationId", stableUuid(prefix + ":order-cancellation"))
                .put("occurredAt", occurredAt);
        return output;
    }

    private static ObjectNode categoryDailyOperations(
            ObjectNode fullChain, String prefix, String occurredAt, ObjectNode consumer,
            String operatorPrincipalId, String occurrenceKey) {
        String[] issueCodes = {
                "LOW_CONVERSION",
                "HIGH_VIEW_LOW_CART",
                "CAMPAIGN_MISS",
                "CONTENT_QUALITY"
        };
        String issueCode = issueCodes[Math.floorMod(occurrenceKey.hashCode(), issueCodes.length)];
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("runIds")
                .put("product", prefix + "-product")
                .put("experiment", prefix + "-experiment")
                .put("campaign", prefix + "-campaign")
                .put("consumer", prefix + "-consumer");
        output.put("operatorPrincipalId", operatorPrincipalId);
        output.putObject("diagnosis")
                .put("issueCode", issueCode)
                .put("scopeType", "CATEGORY")
                .put("scopeRef", "category:daily:" + prefix)
                .put("modelConfidenceBasisPoints", 9700)
                .putArray("chosenActions")
                .add("LAUNCH_NEW_ASSORTMENT")
                .add("RUN_CONVERSION_EXPERIMENT")
                .add("ACTIVATE_TARGETED_CAMPAIGN")
                .add("VERIFY_CONSUMER_JOURNEY");
        addRoleOperationsCase(output, prefix, occurredAt, operatorPrincipalId,
                "AI_CATEGORY_DAY_", "category-operations:", "category_operations_",
                "CATEGORY_OPERATIONS", issueCode,
                "CATEGORY_DIAGNOSIS_READY", "CATEGORY_OPERATOR_ASSIGNED",
                "CATEGORY_ACTIONS_VERIFIED");
        output.set("product", productToListing(fullChain, prefix));
        output.set("experiment", growthExperiment(prefix, occurredAt));
        output.set("campaign", promotionCampaign(prefix, occurredAt, operatorPrincipalId));
        output.set("consumer", consumer);
        return output;
    }

    private static ObjectNode productManagementLifecycle(
            ObjectNode fullChain, String prefix, LocalDate date, String occurredAt,
            ObjectNode mysteryPurchase, String operatorPrincipalId, String merchantId,
            String warehouseId, String locationId, String canonicalSkuId,
            long inspectorUserId) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("runIds")
                .put("assortment", prefix + "-assortment")
                .put("product", prefix + "-product")
                .put("mysteryPurchase", prefix + "-mystery-purchase")
                .put("quality", prefix + "-mystery-quality")
                .put("campaign", prefix + "-new-product-campaign");
        output.put("operatorPrincipalId", operatorPrincipalId);
        output.putObject("managementDecision")
                .put("supplyPoolScope", "PLATFORM_ALL_ELIGIBLE_SUPPLY")
                .put("targetStoreType", "PLATFORM_FLAGSHIP")
                .put("qualityGate", "MYSTERY_BUYER_VERIFIED")
                .put("incubationStrategy", "QUALITY_FIRST_NEW_PRODUCT")
                .putArray("qualityDimensions")
                .add("PATTERN_REVIEW")
                .add("PRICE_VERIFICATION")
                .add("CONTENT_SHOOTING")
                .add("MODEL_FITTING");
        addRoleOperationsCase(output, prefix, occurredAt, operatorPrincipalId,
                "AI_PRODUCT_MANAGEMENT_", "product-management:", "product_management_",
                "PRODUCT_MANAGEMENT", "NEW_PRODUCT_END_TO_END",
                "PRODUCT_PLAN_DISPATCHED", "PRODUCT_OPERATOR_ASSIGNED",
                "PRODUCT_LAUNCH_AND_QUALITY_VERIFIED");
        output.set("assortment",
                assortmentPlanningLifecycle(prefix + "-assortment", date, occurredAt,
                        operatorPrincipalId));
        output.set("product", productToListing(fullChain, prefix));
        output.set("mysteryPurchase", mysteryPurchase);
        output.set("quality", mysteryBuyerSampleVerification(
                prefix, occurredAt, merchantId, warehouseId, locationId,
                canonicalSkuId, operatorPrincipalId, inspectorUserId));
        ObjectNode campaign = promotionCampaign(
                prefix + "-new-product", occurredAt, operatorPrincipalId);
        campaign.withObject("/campaignCommand")
                .put("campaignName", "质检通过新品首发与爆款培育 " + prefix.toUpperCase())
                .put("sourceType", "QUALITY_VERIFIED_NEW_PRODUCT")
                .put("sourceId", "product-management:" + prefix);
        output.set("campaign", campaign);
        return output;
    }

    private static ObjectNode warehouseAdmissionLifecycle(
            String prefix, String occurredAt, String canonicalSkuId, String listingId,
            String merchantId, String shopId, String principalId, String warehouseId,
            CatalogProcurementLine firstLine, CatalogProcurementLine secondLine,
            ProcurementSourcingSeed sourcingSeed,
            String canonicalSkuCode, String canonicalBarcode,
            String canonicalBaseUomCode) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.put("canonicalSkuId", canonicalSkuId);
        output.putObject("runIds")
                .put("merchant", prefix + "-bd-merchant")
                .put("replenishment", prefix + "-platform-warehouse")
                .put("traffic", prefix + "-quality-traffic");
        output.putObject("admissionDecision")
                .put("decision", "APPROVE")
                .put("qualityPolicy", "VERIFIED_ONLY")
                .put("canonicalSkuId", canonicalSkuId)
                .put("listingId", listingId)
                .put("targetWarehouseId", warehouseId)
                .put("trafficPolicy", "QUALITY_VERIFIED_PLATFORM_WAREHOUSE");
        addRoleOperationsCase(output, prefix, occurredAt, principalId,
                "AI_WAREHOUSE_ADMISSION_", "warehouse-admission:", "warehouse_admission_",
                "WAREHOUSE_ADMISSION", "QUALITY_FIRST_INBOUND",
                "WAREHOUSE_ADMISSION_READY", "SUPPLY_CHAIN_OPERATOR_ASSIGNED",
                "INBOUND_FULFILLMENT_AND_TRAFFIC_VERIFIED");
        output.set("merchant",
                merchantOnboarding(prefix + "-bd", occurredAt, principalId));
        output.set("replenishment", replenishmentLifecycle(
                prefix + "-warehouse", occurredAt,
                merchantId, shopId, principalId, firstLine, secondLine, sourcingSeed, warehouseId,
                canonicalSkuCode, canonicalBarcode, canonicalBaseUomCode));
        ObjectNode traffic = promotionCampaign(
                prefix + "-quality-traffic", occurredAt, principalId);
        traffic.withObject("/campaignCommand")
                .put("campaignName", "质检通过平台仓商品站内流量承接 " + prefix.toUpperCase())
                .put("sourceType", "QUALITY_VERIFIED_WAREHOUSE_ADMISSION")
                .put("sourceId", "listing:" + listingId);
        output.set("traffic", traffic);
        return output;
    }

    private static ObjectNode customerExperienceTicketResponsibilityLifecycle(
            String prefix, String occurredAt, ObjectNode consumer,
            String agentPrincipalId, String merchantId, String shopId) {
        String runId = stableUuid(prefix + ":experience-ticket-run");
        String correlationId = stableUuid(prefix + ":experience-ticket-correlation");
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("runIds").put("consumer", prefix + "-consumer-experience");
        output.putObject("responsibilityDecision")
                .put("responsibleParty", "MERCHANT")
                .put("outcomeCode", "MERCHANT_RESPONSIBLE")
                .put("reasonCode", "PRODUCT_DESCRIPTION_MISMATCH")
                .put("merchantId", merchantId)
                .put("shopId", shopId)
                .put("compensationPolicy", "SERVICE_COMPENSATION");
        addRoleOperationsCase(output, prefix, occurredAt, agentPrincipalId,
                "AI_CUSTOMER_EXPERIENCE_", "customer-experience:", "customer_experience_",
                "CUSTOMER_EXPERIENCE", "TICKET_RESPONSIBILITY",
                "EXPERIENCE_COMPLAINT_RECEIVED", "EXPERIENCE_OPERATOR_ASSIGNED",
                "RESPONSIBILITY_AND_COMPENSATION_VERIFIED");
        output.set("consumer", consumer);

        ArrayNode commands = output.putArray("ticketCommands");
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CREATE_TICKET")
                .put("ticketNo", "AI-CX-" + prefix.toUpperCase(Locale.ROOT))
                .put("customerPrincipalId", "pending-consumer:" + prefix)
                .put("channelCode", "APP")
                .put("priority", "HIGH")
                .put("categoryCode", "EXPERIENCE_COMPLAINT")
                .put("slaPolicyCode", "CONSUMER_EXPERIENCE_PRIORITY")
                .put("slaPolicyVersion", 1)
                .put("resolutionDeadlineAt",
                        Instant.parse(occurredAt).plus(24, ChronoUnit.HOURS).toString())
                .put("fcrWindowHours", 72));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "LINK_REFERENCE")
                .put("ticketId", ZERO_UUID)
                .put("referenceSourceSystem", "cloudmold-order")
                .put("referenceType", "ORDER")
                .put("referenceId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RECORD_MESSAGE")
                .put("ticketId", ZERO_UUID)
                .put("direction", "INBOUND")
                .put("senderType", "CUSTOMER")
                .put("senderPrincipalId", "pending-consumer:" + prefix)
                .put("messageType", "TEXT")
                .put("contentToken", "restricted:experience_complaint_" + prefix));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "ASSIGN_AGENT")
                .put("ticketId", ZERO_UUID)
                .put("assignedAgentPrincipalId", agentPrincipalId));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "START_PROCESSING")
                .put("ticketId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RESOLVE_TICKET")
                .put("ticketId", ZERO_UUID));
        commands.add(customerServiceCommand(
                        prefix, runId, occurredAt, correlationId, "RECORD_QUALITY_REVIEW")
                .put("ticketId", ZERO_UUID)
                .put("reviewerPrincipalId", agentPrincipalId)
                .put("scoreBasisPoints", 7200)
                .put("outcomeCode", "MERCHANT_RESPONSIBLE")
                .put("reasonCode", "PRODUCT_DESCRIPTION_MISMATCH"));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "REQUEST_CLAIM")
                .put("ticketId", ZERO_UUID)
                .put("claimCode", "CX-" + prefix.toUpperCase(Locale.ROOT))
                .put("claimType", "SERVICE_COMPENSATION")
                .put("requestedAmountMinor", 5000)
                .put("currencyCode", "CNY")
                .put("reasonCode", "MERCHANT_EXPERIENCE_GAP"));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "APPROVE_CLAIM")
                .put("claimId", ZERO_UUID)
                .put("approvedAmountMinor", 3000));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "PAY_COMPENSATION")
                .put("claimId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CLOSE_TICKET")
                .put("ticketId", ZERO_UUID));
        return output;
    }

    private static ObjectNode merchantExperienceRectificationLifecycle(
            String prefix, String occurredAt, String ticketId, String customerPrincipalId,
            String agentPrincipalId, String merchantId, String shopId,
            String responsibilityReviewId, String compensationEntryId) {
        String runId = stableUuid(prefix + ":merchant-rectification-run");
        String correlationId = stableUuid(prefix + ":merchant-rectification-correlation");
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.put("ticketId", ticketId);
        output.putObject("merchantReference")
                .put("merchantId", merchantId)
                .put("shopId", shopId);
        output.putObject("trigger")
                .put("triggerType", "MERCHANT_RESPONSIBILITY_EFFECTIVE")
                .put("responsibilityReviewId", responsibilityReviewId)
                .put("compensationEntryId", compensationEntryId)
                .put("merchantId", merchantId)
                .put("ticketId", ticketId);
        addRoleOperationsCase(output, prefix, occurredAt, agentPrincipalId,
                "AI_MERCHANT_RECTIFICATION_",
                "merchant-experience:" + merchantId + ":",
                "merchant_rectification_",
                "MERCHANT_EXPERIENCE", "RESPONSIBILITY_RECTIFICATION",
                "MERCHANT_RECTIFICATION_TRIGGERED", "MERCHANT_EXPERIENCE_OPERATOR_ASSIGNED",
                "CONSUMER_EXPERIENCE_RECOVERY_VERIFIED");

        ArrayNode commands = output.putArray("ticketCommands");
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "REOPEN_TICKET")
                .put("ticketId", ticketId));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RECORD_MESSAGE")
                .put("ticketId", ticketId)
                .put("direction", "OUTBOUND")
                .put("senderType", "SYSTEM")
                .put("messageType", "TEXT")
                .put("contentToken", "restricted:merchant_rectification_progress_" + prefix));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RESOLVE_TICKET")
                .put("ticketId", ticketId));
        commands.add(customerServiceCommand(
                        prefix, runId, occurredAt, correlationId, "RECORD_QUALITY_REVIEW")
                .put("ticketId", ticketId)
                .put("reviewerPrincipalId", agentPrincipalId)
                .put("scoreBasisPoints", 9600)
                .put("outcomeCode", "RECTIFICATION_VERIFIED")
                .put("reasonCode", "MERCHANT_ACTION_COMPLETED"));
        commands.add(customerServiceCommand(
                        prefix, runId, occurredAt, correlationId, "RECORD_BUYER_FEEDBACK")
                .put("ticketId", ticketId)
                .put("customerPrincipalId", customerPrincipalId)
                .put("touchpointCode", "TICKET_RESOLUTION")
                .put("sentimentCode", "SATISFIED")
                .put("commentToken", "restricted:experience_recovery_satisfied_" + prefix));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CLOSE_TICKET")
                .put("ticketId", ticketId));
        return output;
    }

    private static ObjectNode unfulfillableOrderCompensationLifecycle(
            String prefix, String occurredAt, ObjectNode consumer, String agentPrincipalId) {
        String runId = stableUuid(prefix + ":unfulfillable-compensation-run");
        String correlationId = stableUuid(prefix + ":unfulfillable-compensation-correlation");
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("runIds")
                .put("orderCancellation", prefix + "-unfulfillable-cancellation");
        output.putObject("compensationPolicy")
                .put("trigger", "PAID_UNSHIPPED_ORDER_CANNOT_FULFILL")
                .put("refundPolicy", "INSTANT_FULL_REFUND_AND_SHIPMENT_INTERCEPT")
                .put("compensationType", "SERVICE_COMPENSATION")
                .put("requestedAmountMinor", 5_000L)
                .put("approvedAmountMinor", 5_000L)
                .put("currencyCode", "CNY");
        output.set("orderCancellation",
                orderCancellationOperations(prefix + "-loss", occurredAt, consumer, agentPrincipalId));
        addRoleOperationsCase(output, prefix, occurredAt, agentPrincipalId,
                "AI_UNFULFILLABLE_COMPENSATION_", "unfulfillable-order:",
                "unfulfillable_compensation_", "CUSTOMER_EXPERIENCE",
                "UNFULFILLABLE_ORDER_COMPENSATION",
                "UNFULFILLABLE_ORDER_CONFIRMED", "CUSTOMER_COMPENSATION_OPERATOR_ASSIGNED",
                "REFUND_INTERCEPT_AND_COMPENSATION_VERIFIED");

        ArrayNode commands = output.putArray("ticketCommands");
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CREATE_TICKET")
                .put("ticketNo", "AI-UC-" + prefix.toUpperCase(Locale.ROOT))
                .put("customerPrincipalId", "pending-consumer:" + prefix)
                .put("channelCode", "INTERNAL")
                .put("priority", "URGENT")
                .put("categoryCode", "UNFULFILLABLE_ORDER")
                .put("slaPolicyCode", "UNFULFILLABLE_ORDER_PRIORITY")
                .put("slaPolicyVersion", 1)
                .put("resolutionDeadlineAt",
                        Instant.parse(occurredAt).plus(2, ChronoUnit.HOURS).toString())
                .put("fcrWindowHours", 24));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "LINK_REFERENCE")
                .put("ticketId", ZERO_UUID)
                .put("referenceSourceSystem", "cloudmold-order")
                .put("referenceType", "ORDER")
                .put("referenceId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RECORD_MESSAGE")
                .put("ticketId", ZERO_UUID)
                .put("direction", "OUTBOUND")
                .put("senderType", "SYSTEM")
                .put("senderPrincipalId", agentPrincipalId)
                .put("messageType", "TEXT")
                .put("contentToken", "restricted:unfulfillable_refund_notice_" + prefix));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "ASSIGN_AGENT")
                .put("ticketId", ZERO_UUID)
                .put("assignedAgentPrincipalId", agentPrincipalId));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "START_PROCESSING")
                .put("ticketId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "RESOLVE_TICKET")
                .put("ticketId", ZERO_UUID));
        commands.add(customerServiceCommand(
                        prefix, runId, occurredAt, correlationId, "RECORD_QUALITY_REVIEW")
                .put("ticketId", ZERO_UUID)
                .put("reviewerPrincipalId", agentPrincipalId)
                .put("scoreBasisPoints", 10_000)
                .put("outcomeCode", "PLATFORM_FULFILLMENT_RESPONSIBLE")
                .put("reasonCode", "ORDER_CANNOT_BE_FULFILLED"));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "REQUEST_CLAIM")
                .put("ticketId", ZERO_UUID)
                .put("claimCode", "UC-" + prefix.toUpperCase(Locale.ROOT))
                .put("claimType", "SERVICE_COMPENSATION")
                .put("requestedAmountMinor", 5_000L)
                .put("currencyCode", "CNY")
                .put("reasonCode", "UNFULFILLABLE_ORDER"));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "APPROVE_CLAIM")
                .put("claimId", ZERO_UUID)
                .put("approvedAmountMinor", 5_000L));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "PAY_COMPENSATION")
                .put("claimId", ZERO_UUID));
        commands.add(customerServiceCommand(prefix, runId, occurredAt, correlationId, "CLOSE_TICKET")
                .put("ticketId", ZERO_UUID));
        return output;
    }

    private static ObjectNode profitLossImprovementLifecycle(
            String prefix, String occurredAt, String operatorPrincipalId) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("improvementTargets")
                .put("problemOrderRateDirection", "DOWN")
                .put("returnRateDirection", "DOWN")
                .put("confirmedRevenueDirection", "UP")
                .put("problemOrderActions",
                        "前置库存与履约可用性校验、异常订单分层拦截、工单判责和商家整改")
                .put("returnRateActions",
                        "按款色码复盘尺码版型与素材偏差、神秘买手质检、退货原因聚类和选品淘汰");
        addRoleOperationsCase(output, prefix, occurredAt, operatorPrincipalId,
                "AI_PROFIT_LOSS_IMPROVEMENT_", "profit-loss:",
                "profit_loss_improvement_", "FINANCE_OPERATIONS",
                "PROBLEM_ORDER_AND_RETURN_LOSS",
                "WEEKLY_PNL_REVIEW_READY", "PROFIT_LOSS_OPERATOR_ASSIGNED",
                "LOSS_REDUCTION_ACTIONS_VERIFIED");
        return output;
    }

    private static ObjectNode riskDisputeResolutionLifecycle(
            String prefix, String occurredAt, ObjectNode consumer, String reviewerPrincipalId) {
        String runId = stableUuid(prefix + ":risk-dispute-run");
        String correlationId = stableUuid(prefix + ":risk-dispute-correlation");
        String token = prefix.toUpperCase(Locale.ROOT).replace('-', '_');
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putObject("runIds").put("consumer", prefix + "-risk-consumer");
        output.set("consumer", consumer);
        addRoleOperationsCase(output, prefix, occurredAt, reviewerPrincipalId,
                "AI_PAYMENT_DISPUTE_", "risk-dispute:", "risk_dispute_",
                "RISK_OPERATIONS", "PAYMENT_CHARGEBACK",
                "PAYMENT_DISPUTE_IDENTIFIED", "RISK_OPERATOR_ASSIGNED",
                "RISK_DISPUTE_AND_LOSS_RECONCILED");

        ArrayNode commands = output.putArray("riskCommands");
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "CREATE_CLUSTER")
                .put("clusterCode", "PAYMENT_CHARGEBACK_" + token)
                .put("riskLevel", "HIGH"));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "OPEN_REVIEW")
                .put("clusterId", ZERO_UUID)
                .put("reviewerPrincipalId", reviewerPrincipalId));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "LINK_ORDER_REVIEW_CASE")
                .put("caseId", ZERO_UUID)
                .put("orderId", ZERO_UUID)
                .put("paymentId", ZERO_UUID)
                .put("riskType", "FRAUD")
                .put("reasonCode", "SUSPICIOUS_CAPTURE"));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "OPEN_PAYMENT_DISPUTE")
                .put("caseId", ZERO_UUID)
                .put("orderId", ZERO_UUID)
                .put("paymentId", ZERO_UUID)
                .put("disputeType", "CHARGEBACK")
                .put("reasonCode", "CARDHOLDER_DISPUTE")
                .put("amountMinor", 39_800L)
                .put("currencyCode", "CNY")
                .put("externalRef", "chargeback-" + prefix));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "START_REVIEW")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 1L));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "DECIDE_REVIEW")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 2L)
                .put("decisionType", "CONFIRM_RISK")
                .put("reasonCode", "CONFIRMED_CHARGEBACK")
                .put("decidedByPrincipalId", reviewerPrincipalId));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "RESOLVE_PAYMENT_DISPUTE")
                .put("disputeId", ZERO_UUID)
                .put("expectedVersion", 1L)
                .put("disputeStatus", "LOST")
                .put("decisionId", ZERO_UUID)
                .put("reasonCode", "CHARGEBACK_LOST"));
        commands.add(riskCommand(prefix, runId, occurredAt, correlationId, "POST_LOSS_ENTRY")
                .put("orderId", ZERO_UUID)
                .put("paymentId", ZERO_UUID)
                .put("disputeId", ZERO_UUID)
                .put("lossEntryType", "CHARGEBACK_LOSS")
                .put("signedAmountMinor", 39_800L)
                .put("currencyCode", "CNY")
                .put("externalRef", "chargeback-" + prefix));
        return output;
    }

    private static ObjectNode riskCommand(
            String prefix, String runId, String occurredAt, String correlationId, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", prefix + "-risk-" + operation.toLowerCase(Locale.ROOT))
                .put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    /**
     * Builds a fresh, INTERNAL_TEST metadata incident rather than pretending to repair a live warehouse.
     * The durable metadata definitions and append-only DQC evidence are nevertheless created through the
     * normal metadata authority, so the workflow can be used as an operational acceptance exercise.
     */
    private static ObjectNode dataQualityRecoveryLifecycle(
            String prefix, String occurredAt, String operatorPrincipalId) {
        String token = stableUuid(prefix + ":data-quality").replace("-", "").substring(0, 16);
        String runTraceId = stableUuid(prefix + ":data-quality-run");
        String correlationId = stableUuid(prefix + ":data-quality-correlation");
        String sourceId = "data-source-" + token;
        String rawDatasetId = "dataset-raw-" + token;
        String metricDatasetId = "dataset-metric-" + token;
        String lineageId = "lineage-dqc-" + token;
        String taskId = "task-dqc-" + token;
        String ruleId = "rule-dqc-" + token;
        String taskRunId = "task-run-dqc-" + token;
        ObjectNode output = JsonNodeFactory.instance.objectNode();

        addRoleOperationsCase(output, prefix, occurredAt, operatorPrincipalId,
                "AI_DATA_QUALITY_", "data-quality:", "data_quality_",
                "DATA_AI_OPERATIONS", "DATA_QUALITY",
                "DQC_FAILURE_IDENTIFIED", "DATA_AI_OPERATOR_ASSIGNED", "DQC_RECOVERY_VERIFIED");
        ArrayNode commands = output.putArray("metadataCommands");
        commands.add(metadataPublish(prefix, runTraceId, occurredAt, correlationId,
                        "PUBLISH_DATA_SOURCE", sourceId, "DATA_SOURCE_DQC_" + token.toUpperCase(Locale.ROOT),
                        "数据质量测试数据源 " + token, operatorPrincipalId)
                .put("sourceType", "STARROCKS")
                .put("environment", "TEST")
                .put("endpointRef", "restricted:starrocks-" + token)
                .put("credentialRef", "restricted:credential-" + token)
                .put("namespaceRef", "restricted:analytics-" + token));
        commands.add(metadataDatasetPublish(prefix, runTraceId, occurredAt, correlationId,
                rawDatasetId, "DATASET_RAW_DQC_" + token.toUpperCase(Locale.ROOT),
                "数据质量原始订单数据集 " + token, operatorPrincipalId, "ODS", "ORDER_DAY", token));
        commands.add(metadataDatasetPublish(prefix, runTraceId, occurredAt, correlationId,
                metricDatasetId, "DATASET_METRIC_DQC_" + token.toUpperCase(Locale.ROOT),
                "数据质量指标数据集 " + token, operatorPrincipalId, "ADS", "METRIC_DAY", token));
        commands.add(metadataPublish(prefix, runTraceId, occurredAt, correlationId,
                        "PUBLISH_LINEAGE", lineageId, "LINEAGE_DQC_" + token.toUpperCase(Locale.ROOT),
                        "订单指标血缘 " + token, operatorPrincipalId)
                .put("sourceDatasetId", ZERO_UUID).put("sourceDatasetVersion", 1L)
                .put("targetDatasetId", ZERO_UUID).put("targetDatasetVersion", 1L)
                .put("lineageDirection", "SOURCE_TO_TARGET")
                .put("transformSha256", DigestUtil.sha256Hex("dqc-transform:" + token))
                .put("transformationRef", "restricted:transform-" + token));
        ObjectNode task = metadataPublish(prefix, runTraceId, occurredAt, correlationId,
                "PUBLISH_TASK", taskId, "TASK_DQC_" + token.toUpperCase(Locale.ROOT),
                "数据质量修复任务 " + token, operatorPrincipalId);
        task.put("taskType", "QUALITY")
                .put("executableArtifactRef", "restricted:dqc-task-" + token)
                .put("codeSha256", DigestUtil.sha256Hex("dqc-task-code:" + token))
                .put("scheduleSha256", DigestUtil.sha256Hex("dqc-task-schedule:" + token))
                .put("resourceGroupRef", "restricted:dqc-resource-" + token);
        task.putObject("sla").put("serviceLevelCode", "DQC_DAILY")
                .put("deadlineMinuteUtc", 120).put("maximumDurationMillis", 60_000L)
                .put("maximumFreshnessMillis", 86_400_000L)
                .put("approvedByPrincipalId", operatorPrincipalId);
        commands.add(task);
        commands.add(metadataPublish(prefix, runTraceId, occurredAt, correlationId,
                        "PUBLISH_DQC_RULE", ruleId, "RULE_DQC_" + token.toUpperCase(Locale.ROOT),
                        "订单指标行数质量规则 " + token, operatorPrincipalId)
                .put("targetDatasetId", ZERO_UUID).put("targetDatasetVersion", 1L)
                .put("targetDatasetField", "orderCount").put("ruleType", "ROW_COUNT")
                .put("severity", "CRITICAL")
                .put("expressionSha256", DigestUtil.sha256Hex("dqc-rule:" + token))
                .put("thresholdValue", 100).put("thresholdComparator", "GTE"));

        Instant scheduledAt = Instant.parse(occurredAt);
        commands.add(metadataObservation(prefix, runTraceId, occurredAt, correlationId, taskRunId,
                1, 0L, "SCHEDULED", scheduledAt, null, null, null, token));
        commands.add(metadataObservation(prefix, runTraceId, occurredAt, correlationId, taskRunId,
                1, 1L, "RUNNING", scheduledAt, scheduledAt.plusSeconds(60), null, null, token));
        commands.add(metadataObservation(prefix, runTraceId, occurredAt, correlationId, taskRunId,
                1, 2L, "FAILED", scheduledAt, scheduledAt.plusSeconds(60),
                scheduledAt.plusSeconds(120), "restricted:dqc-failure-" + token, token));
        commands.add(metadataDqcResult(prefix, runTraceId, occurredAt, correlationId,
                "dqc-result-fail-" + token, taskRunId, 3L, "FAIL", 0, 1_000L, token));
        commands.add(metadataObservation(prefix, runTraceId, occurredAt, correlationId, taskRunId,
                2, 3L, "SCHEDULED", scheduledAt.plusSeconds(180), null, null, null, token));
        commands.add(metadataObservation(prefix, runTraceId, occurredAt, correlationId, taskRunId,
                2, 4L, "RUNNING", scheduledAt.plusSeconds(180), scheduledAt.plusSeconds(240), null, null, token));
        commands.add(metadataObservation(prefix, runTraceId, occurredAt, correlationId, taskRunId,
                2, 5L, "SUCCEEDED", scheduledAt.plusSeconds(180), scheduledAt.plusSeconds(240),
                scheduledAt.plusSeconds(300), null, token));
        commands.add(metadataDqcResult(prefix, runTraceId, occurredAt, correlationId,
                "dqc-result-pass-" + token, taskRunId, 6L, "PASS", 120, 0L, token));
        return output;
    }

    private static ObjectNode metadataPublish(String prefix, String runTraceId, String occurredAt,
                                              String correlationId, String operation, String definitionId,
                                              String definitionCode, String displayName,
                                              String ownerPrincipalId) {
        return metadataCommand(prefix, runTraceId, occurredAt, correlationId, operation)
                .put("definitionId", definitionId).put("definitionCode", definitionCode)
                .put("displayName", displayName).put("expectedVersion", 0L)
                .put("ownerPrincipalId", ownerPrincipalId)
                .put("specificationSha256", DigestUtil.sha256Hex(operation + ":" + definitionId))
                .put("artifactRef", "restricted:metadata-" + definitionId);
    }

    private static ObjectNode metadataDatasetPublish(
            String prefix, String runTraceId, String occurredAt, String correlationId, String definitionId,
            String definitionCode, String displayName, String ownerPrincipalId, String layerCode,
            String grainCode, String token) {
        ObjectNode dataset = metadataPublish(prefix, runTraceId, occurredAt, correlationId,
                "PUBLISH_DATASET", definitionId, definitionCode, displayName, ownerPrincipalId)
                .put("dataSourceId", ZERO_UUID).put("dataSourceVersion", 1L)
                .put("datasetType", "TABLE").put("qualifiedName", "analytics.dqc_" + token)
                .put("layerCode", layerCode).put("grainCode", grainCode)
                .put("schemaSha256", DigestUtil.sha256Hex("dqc-schema:" + definitionId))
                .put("storageLocationRef", "restricted:storage-" + definitionId)
                .put("retentionDays", 90);
        ArrayNode fields = dataset.putArray("fields");
        fields.addObject().put("fieldCode", "metricDate").put("dataType", "DATE")
                .put("nullable", false).put("primaryKeyPart", true)
                .put("semanticType", "BUSINESS_DATE").put("classification", "INTERNAL");
        fields.addObject().put("fieldCode", "orderCount").put("dataType", "BIGINT")
                .put("nullable", false).put("primaryKeyPart", false)
                .put("semanticType", "MEASURE").put("classification", "INTERNAL");
        return dataset;
    }

    private static ObjectNode metadataObservation(
            String prefix, String runTraceId, String occurredAt, String correlationId, String taskRunId,
            int attempt, long expectedSequence, String status, Instant scheduledAt, Instant startedAt,
            Instant finishedAt, String errorRef, String token) {
        ObjectNode observation = metadataCommand(prefix, runTraceId, occurredAt, correlationId,
                "OBSERVE_TASK_RUN")
                .put("taskId", ZERO_UUID).put("taskVersion", 1L).put("taskRunId", taskRunId)
                .put("attempt", attempt).put("expectedObservationSequence", expectedSequence)
                .put("runStatus", status).put("scheduledAt", scheduledAt.toString())
                .put("durationMillis", finishedAt == null ? 0L : 60_000L)
                .put("computeCostMinor", 0L).put("costCurrency", "CNY")
                .put("resourceMillis", 0L).put("rowsRead", 1_000L).put("rowsWritten", 1_000L);
        if (startedAt != null) {
            observation.put("startedAt", startedAt.toString());
        }
        if (finishedAt != null) {
            observation.put("finishedAt", finishedAt.toString());
        }
        if ("SUCCEEDED".equals(status)) {
            observation.put("outputSnapshotRef", "restricted:dqc-output-" + token);
        }
        if (errorRef != null) {
            observation.put("errorRef", errorRef);
        }
        return observation;
    }

    private static ObjectNode metadataDqcResult(
            String prefix, String runTraceId, String occurredAt, String correlationId, String resultId,
            String taskRunId, long observationSequence, String status, int actualValue,
            long violations, String token) {
        return metadataCommand(prefix, runTraceId, occurredAt, correlationId, "RECORD_DQC_RESULT")
                .put("dqcResultId", resultId).put("dqcRuleId", ZERO_UUID).put("dqcRuleVersion", 1L)
                .put("targetDatasetId", ZERO_UUID).put("targetDatasetVersion", 1L)
                .put("taskRunId", taskRunId).put("taskRunObservationSequence", observationSequence)
                .put("dqcResultStatus", status).put("expectedValue", 100).put("actualValue", actualValue)
                .put("evaluatedRows", 1_000L).put("violationCount", violations)
                .put("evidenceRef", "restricted:dqc-evidence-" + token + "-" + status.toLowerCase(Locale.ROOT));
    }

    private static ObjectNode metadataCommand(
            String prefix, String runTraceId, String occurredAt, String correlationId, String operation) {
        return JsonNodeFactory.instance.objectNode().put("operation", operation)
                .put("idempotencyKey", prefix + "-metadata-" + operation.toLowerCase(Locale.ROOT))
                .put("runTraceId", runTraceId).put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static void addRoleOperationsCase(
            ObjectNode output, String prefix, String occurredAt, String operatorPrincipalId,
            String alertCodePrefix, String sourceRefPrefix, String evidenceTokenPrefix,
            String category, String subcategory, String noticeReasonCode,
            String claimReasonCode, String resolveReasonCode) {
        String alertId = stableUuid(prefix + ":commerce-operations-day");
        String correlationId = stableUuid(prefix + ":commerce-operations-day-correlation");
        String safeCodeSuffix = prefix.toUpperCase().replace('-', '_');
        ArrayNode commands = output.putArray("operationsCommands");
        commands.addObject()
                .put("operation", "OPEN_ALERT")
                .put("idempotencyKey", prefix + "-open-day")
                .put("runId", prefix + "-operations-day")
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt)
                .putObject("alert")
                .put("alertId", alertId)
                .put("alertCode", alertCodePrefix + safeCodeSuffix)
                .put("sourceType", "METRIC")
                .put("sourceRef", sourceRefPrefix + prefix)
                .put("severity", "MEDIUM")
                .put("category", category)
                .put("subcategory", subcategory)
                .put("evidenceRef", "restricted:" + evidenceTokenPrefix + safeCodeSuffix)
                .put("titleSha256", DigestUtil.sha256Hex(category + ":" + prefix))
                .put("actorPrincipalId", operatorPrincipalId);
        addAlertTransition(commands, "NOTICE_ALERT", prefix + "-notice-day", alertId,
                1L, operatorPrincipalId, noticeReasonCode, correlationId, occurredAt);
        addAlertTransition(commands, "CLAIM_ALERT", prefix + "-claim-day", alertId,
                2L, operatorPrincipalId, claimReasonCode, correlationId, occurredAt);
        addAlertTransition(commands, "RESOLVE_ALERT", prefix + "-resolve-day", alertId,
                3L, operatorPrincipalId, resolveReasonCode, correlationId, occurredAt);
    }

    private static void addAlertTransition(ArrayNode commands, String operation, String idempotencyKey,
                                           String alertId, long expectedVersion, String actorPrincipalId,
                                           String reasonCode, String correlationId, String occurredAt) {
        commands.addObject()
                .put("operation", operation)
                .put("idempotencyKey", idempotencyKey)
                .put("runId", idempotencyKey)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt)
                .putObject("alert")
                .put("alertId", alertId)
                .put("expectedVersion", expectedVersion)
                .put("actorPrincipalId", actorPrincipalId)
                .put("reasonCode", reasonCode);
    }

    private static ObjectNode merchantOnboarding(String prefix, String occurredAt, String ownerPrincipalId) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        String correlationId = stableUuid(prefix + ":merchant-onboarding");
        input.put("correlationId", correlationId);
        input.put("occurredAt", occurredAt);
        input.putObject("draftCommand")
                .put("legalName", "AI 模拟商家 " + prefix.toUpperCase())
                .put("registrationHashToken", "sha256:" + stableUuid(prefix + ":registration").replace("-", ""))
                .put("businessLicenseToken", "sha256:"
                        + DigestUtil.sha256Hex(prefix + ":merchant-license"))
                .put("ownerPrincipalId", ownerPrincipalId)
                .put("channelCode", "YSHOPPING_INTERNAL")
                .put("externalShopId", "ai-shop-" + prefix)
                .put("sourceSystem", "CLOUDMOLD_AI")
                .put("traceId", correlationId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
        return input;
    }

    /**
     * Builds a Local Dev/Test-only managed-admission case from an already approved
     * canonical onboarding application. The resulting payload still goes through
     * Temporal, Agent Control approval, SkillTask and Merchant commands; it never
     * writes tables directly or represents a real merchant decision.
     */
    private static ObjectNode merchantManagedGrowthLifecycle(String prefix, String occurredAt,
                                                              String applicationId, String merchantId,
                                                              String actorPrincipalId) {
        String correlationId = stableUuid(prefix + ":managed-growth");
        String evidenceBase = "evidence:local-test/merchant-managed-growth/" + prefix;
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.putObject("localTest")
                .put("classification", "LOCAL_TEST")
                .put("purpose", "synthetic managed-merchant admission workflow verification")
                .put("sourceApplicationId", applicationId)
                .put("sourceMerchantId", merchantId);
        managedLifecycleCommand(input, "admissionOpenCommand", correlationId, occurredAt)
                .put("applicationId", applicationId);
        managedLifecycleCommand(input, "attributionCommand", correlationId, occurredAt)
                .put("attributionChannelCode", "LOCAL_TEST_AUTOMATION")
                .put("attributionReference", "local-test:" + prefix)
                .put("attributionEvidenceRef", evidenceBase + "/attribution")
                .putObject("sourceReference")
                .put("sourceSystem", "CLOUDMOLD_AI_LOCAL_TEST")
                .put("sourceType", "SYNTHETIC_RUN")
                .put("sourceId", prefix);
        ObjectNode evidence = managedLifecycleCommand(input, "evidencePackageCommand", correlationId, occurredAt)
                .put("evidencePackageRef", evidenceBase + "/package");
        ArrayNode evidenceItems = evidence.putArray("evidenceItems");
        managedEvidenceItem(evidenceItems, "BUSINESS_LICENSE", "本地测试营业资质引用",
                evidenceBase + "/business-license", prefix);
        managedEvidenceItem(evidenceItems, "FACTORY_PROFILE", "本地测试工厂能力引用",
                evidenceBase + "/factory-profile", prefix);
        managedLifecycleCommand(input, "aiDiagnosticProposalCommand", correlationId, occurredAt)
                .put("recommendationCode", "FACTORY_INSPECTION_REQUIRED")
                .put("recommendationSummary", "LOCAL_TEST 合成证据完整，进入受控验厂链路验证")
                .put("diagnosticEvidenceRef", evidenceBase + "/ai-diagnostic");
        managedLifecycleCommand(input, "aiDiagnosticAcceptCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("reviewNote", "LOCAL_TEST 自动审批：仅验证受控工作流与审计证据");
        managedLifecycleCommand(input, "inspectionCreateCommand", correlationId, occurredAt)
                .put("reviewNote", "LOCAL_TEST 创建受控验厂任务");
        managedLifecycleCommand(input, "inspectionClaimCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("reviewNote", "LOCAL_TEST 验厂小二认领");
        managedLifecycleCommand(input, "inspectionScheduleCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("scheduledAt", Instant.parse(occurredAt).plus(1, ChronoUnit.HOURS).toString())
                .put("reviewNote", "LOCAL_TEST 预约验厂");
        managedLifecycleCommand(input, "inspectionSubmitCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("inspectionOutcomeEvidenceRef", evidenceBase + "/inspection")
                .put("reviewNote", "LOCAL_TEST 现场验厂证据已受控引用");
        managedLifecycleCommand(input, "inspectionFirstReviewCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("inspectionOutcomeEvidenceRef", evidenceBase + "/qa-first-review")
                .put("reviewNote", "LOCAL_TEST QA 一审通过");
        managedLifecycleCommand(input, "inspectionFinalReviewCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("inspectionOutcomeEvidenceRef", evidenceBase + "/qa-final-review")
                .put("reviewNote", "LOCAL_TEST QA 终审待完成");
        managedLifecycleCommand(input, "inspectionCompleteCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("inspectionOutcomeEvidenceRef", evidenceBase + "/inspection-complete")
                .put("reviewNote", "LOCAL_TEST 验厂任务完成");
        managedLifecycleCommand(input, "managedFinalReviewCommand", correlationId, occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("reviewDecision", "APPROVED")
                .put("finalReviewEvidenceRef", evidenceBase + "/managed-final-review")
                .put("reviewNote", "LOCAL_TEST 自动审批：托管准入测试结论通过");
        return input;
    }

    private static ObjectNode managedLifecycleCommand(ObjectNode input, String field,
                                                      String correlationId, String occurredAt) {
        return input.putObject(field)
                .put("sourceSystem", "CLOUDMOLD_AI_LOCAL_TEST")
                .put("traceId", correlationId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static void managedEvidenceItem(ArrayNode items, String itemCode, String itemLabel,
                                            String evidenceRef, String sourceId) {
        items.addObject()
                .put("itemCode", itemCode)
                .put("itemLabel", itemLabel)
                .put("evidenceRef", evidenceRef)
                .put("sourceSystem", "CLOUDMOLD_AI_LOCAL_TEST")
                .put("sourceType", "SYNTHETIC_RUN")
                .put("sourceId", sourceId)
                .put("note", "LOCAL_TEST synthetic evidence reference only");
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
                .put("startsAt", runningAt)
                .put("endsAt", base.plus(1, ChronoUnit.DAYS).toString());
        ObjectNode experiment = input.putObject("experiment")
                .put("experimentId", experimentId)
                .put("experimentCode", "AI-EXP-" + prefix.toUpperCase())
                .put("campaignId", campaignId)
                .put("name", "AI 商详到支付转化实验 " + prefix.toUpperCase())
                .put("hypothesis", "个性化卖点排序能够提高支付转化率")
                .put("primaryMetricCode", "PAYMENT_CONVERSION")
                .put("minimumSampleSizePerVariant", 30)
                .put("startsAt", runningAt)
                .put("endsAt", concludedAt);
        ArrayNode variants = experiment.putArray("variants");
        variants.addObject().put("variantCode", "CONTROL")
                .put("variantKind", "CONTROL").put("allocationBasisPoints", 5000);
        variants.addObject().put("variantCode", "TREATMENT")
                .put("variantKind", "TREATMENT").put("allocationBasisPoints", 5000);
        for (int sample = 1; sample <= 30; sample++) {
            String suffix = sample == 1 ? "" : String.format("%02d", sample);
            growthExposure(input, "controlExposure" + suffix,
                    prefix, experimentId, "CONTROL", sample);
            growthExposure(input, "treatmentExposure" + suffix,
                    prefix, experimentId, "TREATMENT", sample);
        }
        growthMetric(input, "controlMetric", prefix, experimentId, "CONTROL",
                Instant.parse(runningAt), snapshotAt, concludedAt, 420_000L);
        growthMetric(input, "treatmentMetric", prefix, experimentId, "TREATMENT",
                Instant.parse(runningAt), snapshotAt, concludedAt, 610_000L);
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
                                       String experimentId, String variantCode, int sample) {
        String suffix = variantCode.toLowerCase();
        input.putObject(field)
                .put("exposureId", stableUuid(prefix + ":growth-exposure:" + suffix + ":" + sample))
                .put("exposureKey", "exposure:" + prefix + ":" + suffix + ":" + sample)
                .put("experimentId", experimentId)
                .put("variantCode", variantCode)
                .put("principalId", "synthetic-buyer:" + prefix + ":" + suffix + ":" + sample)
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
                .put("sampleCount", 30)
                .put("metricValueMicros", valueMicros)
                .put("dataFreshUntil", freshUntil)
                .put("evidenceRef", "evidence://growth/" + prefix + "/" + suffix);
    }

    private static ObjectNode procurementSourcing(
            String prefix, String occurredAt, CatalogProcurementLine firstCatalogLine,
            CatalogProcurementLine secondCatalogLine, String warehouseId,
            ProcurementSourcingSeed seed, int totalQuantity) {
        Instant instant = Instant.parse(occurredAt);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        String runId = stableUuid(prefix + ":procurement-sourcing-run");
        String correlationId = stableUuid(prefix + ":procurement-sourcing-correlation");
        String requisitionId = stableUuid(prefix + ":purchase-requisition");
        String eventId = stableUuid(prefix + ":sourcing-event");
        String awardId = stableUuid(prefix + ":award");
        String policyId = stableUuid(prefix + ":evaluation-policy");
        String line1 = stableUuid(prefix + ":requisition-line:1");
        String line2 = stableUuid(prefix + ":requisition-line:2");
        String schedule11 = stableUuid(prefix + ":requisition-line:1:schedule:1");
        String schedule12 = stableUuid(prefix + ":requisition-line:1:schedule:2");
        String schedule21 = stableUuid(prefix + ":requisition-line:2:schedule:1");
        String schedule22 = stableUuid(prefix + ":requisition-line:2:schedule:2");
        String sourcingLine1 = stableUuid(prefix + ":sourcing-line:1");
        String sourcingLine2 = stableUuid(prefix + ":sourcing-line:2");
        String sourcingSchedule11 = stableUuid(prefix + ":sourcing-line:1:schedule:1");
        String sourcingSchedule12 = stableUuid(prefix + ":sourcing-line:1:schedule:2");
        String sourcingSchedule21 = stableUuid(prefix + ":sourcing-line:2:schedule:1");
        String sourcingSchedule22 = stableUuid(prefix + ":sourcing-line:2:schedule:2");
        BigDecimal quantity1 = BigDecimal.valueOf(Math.multiplyExact(totalQuantity, 3L) / 5L);
        BigDecimal quantity2 = BigDecimal.valueOf(totalQuantity).subtract(quantity1);
        BigDecimal quantity11 = quantity1.multiply(new BigDecimal("0.4"));
        BigDecimal quantity12 = quantity1.subtract(quantity11);
        BigDecimal quantity21 = quantity2.multiply(new BigDecimal("0.4"));
        BigDecimal quantity22 = quantity2.subtract(quantity21);

        PurchaseRequisitionCommand requisition = PurchaseRequisitionCommand.builder()
                .idempotencyKey(prefix + ":purchase-requisition:create-approved")
                .runId(runId).correlationId(correlationId).occurredAt(instant)
                .requisitionId(requisitionId).requisitionCode(code("AI_PR_", prefix))
                .sourceBusinessType("AI_SUPPLY_PLAN").sourceBusinessRef(prefix)
                .legalEntityId(seed.legalEntityId())
                .taxCalculationPolicyCode("STANDARD_V1")
                .roundingPolicyCode("HALF_UP")
                .reasonCode("AI_SOURCING_REQUIRED")
                .remark("AI 采购寻源的已审批多行、多交期采购申请")
                .lines(List.of(
                        requisitionLine(line1, 1, firstCatalogLine,
                                seed.valuationPolicyFor(firstCatalogLine.canonicalSkuId()), quantity1,
                                requisitionSchedule(schedule11, 1, warehouseId, date.plusDays(21), quantity11),
                                requisitionSchedule(schedule12, 2, warehouseId, date.plusDays(35), quantity12)),
                        requisitionLine(line2, 2, secondCatalogLine,
                                seed.valuationPolicyFor(secondCatalogLine.canonicalSkuId()), quantity2,
                                requisitionSchedule(schedule21, 1, warehouseId, date.plusDays(21), quantity21),
                                requisitionSchedule(schedule22, 2, warehouseId, date.plusDays(35), quantity22))))
                .build();

        SourcingCommand.EventDefinition event = SourcingCommand.EventDefinition.builder()
                .eventId(eventId).eventCode(code("AI_RFQ_", prefix)).requisitionId(requisitionId)
                .title("AI 多供应商、多商品、多交期寻源 " + prefix.toUpperCase(Locale.ROOT))
                .quotationDeadline(date.plusDays(7).atTime(23, 59, 59))
                .lines(List.of(
                        sourcingLine(sourcingLine1, 1, line1,
                                sourcingSchedule(sourcingSchedule11, 1, schedule11),
                                sourcingSchedule(sourcingSchedule12, 2, schedule12)),
                        sourcingLine(sourcingLine2, 2, line2,
                                sourcingSchedule(sourcingSchedule21, 1, schedule21),
                                sourcingSchedule(sourcingSchedule22, 2, schedule22))))
                .build();

        QuotationIds quoteA = quotationIds(prefix, "a");
        QuotationIds quoteB = quotationIds(prefix, "b");
        SourcingCommand.QuotationRevisionDefinition revisionA = quotationRevision(
                quoteA, eventId, seed.supplierAId(), 5L, firstCatalogLine, secondCatalogLine,
                sourcingLine1, sourcingLine2, sourcingSchedule11, sourcingSchedule12,
                sourcingSchedule21, sourcingSchedule22, quantity1, quantity2,
                quantity11, quantity12, quantity21, quantity22, date, 2100, 3200);
        SourcingCommand.QuotationRevisionDefinition revisionB = quotationRevision(
                quoteB, eventId, seed.supplierBId(), 6L, firstCatalogLine, secondCatalogLine,
                sourcingLine1, sourcingLine2, sourcingSchedule11, sourcingSchedule12,
                sourcingSchedule21, sourcingSchedule22, quantity1, quantity2,
                quantity11, quantity12, quantity21, quantity22, date, 2200, 3100);

        SourcingCommand.EvaluationDimensionDefinition costDimension = evaluationDimension(
                prefix, "cost", "TOTAL_COST", "总成本", 4000);
        SourcingCommand.EvaluationDimensionDefinition qualityDimension = evaluationDimension(
                prefix, "quality", "QUALITY_ASSURANCE", "质量保证", 3500);
        SourcingCommand.EvaluationDimensionDefinition deliveryDimension = evaluationDimension(
                prefix, "delivery", "DELIVERY_RELIABILITY", "交付可靠性", 2500);
        SourcingCommand.EvaluationPolicyDefinition policy = SourcingCommand.EvaluationPolicyDefinition.builder()
                .policyId(policyId).policyCode(code("AI_EVAL_", prefix)).policyVersion(1)
                .eventId(eventId).expectedEventVersion(8L)
                .dimensions(List.of(costDimension, qualityDimension, deliveryDimension)).build();

        List<SourcingCommand.AwardLineDefinition> awardLines = List.of(
                awardLine(prefix, 1, sourcingLine1, sourcingSchedule11, quoteA.line1(),
                        quoteA.schedule11(), seed.supplierAId(), quantity11),
                awardLine(prefix, 2, sourcingLine1, sourcingSchedule12, quoteB.line1(),
                        quoteB.schedule12(), seed.supplierBId(), quantity12),
                awardLine(prefix, 3, sourcingLine2, sourcingSchedule21, quoteA.line2(),
                        quoteA.schedule21(), seed.supplierAId(), quantity21),
                awardLine(prefix, 4, sourcingLine2, sourcingSchedule22, quoteB.line2(),
                        quoteB.schedule22(), seed.supplierBId(), quantity22));

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("schemaVersion", PROCUREMENT_SOURCING_INPUT_SCHEMA)
                .put("awardId", awardId)
                .put("legalEntityId", seed.legalEntityId());
        ArrayNode supplierCandidates = input.putArray("supplierCandidates");
        supplierCandidates.addObject().put("supplierId", seed.supplierAId());
        supplierCandidates.addObject().put("supplierId", seed.supplierBId());
        input.putObject("actors")
                .put("creatorPrincipalId", seed.creatorPrincipalId())
                .put("reviewerAPrincipalId", seed.reviewerAPrincipalId())
                .put("reviewerBPrincipalId", seed.reviewerBPrincipalId())
                .put("approverPrincipalId", seed.approverPrincipalId());
        ArrayNode valuationPolicies = input.putArray("valuationPolicies");
        valuationPolicies.add(valuationPolicyNode(seed.firstValuationPolicy()));
        valuationPolicies.add(valuationPolicyNode(seed.secondValuationPolicy()));
        input.set("purchaseRequisition", commandEnvelope("purchase_requisition", seed.creatorPrincipalId(), requisition));

        ArrayNode commands = input.putArray("sourcingCommands");
        commands.add(commandEnvelope("event_create", seed.creatorPrincipalId(), sourcingCommand(prefix, instant,
                runId, correlationId, SourcingOperation.CREATE_SOURCING_EVENT, event, null)));
        commands.add(commandEnvelope("event_publish", seed.creatorPrincipalId(), sourcingTransitionCommand(prefix,
                instant, runId, correlationId, SourcingOperation.PUBLISH_SOURCING_EVENT, eventId, 1L, null)));
        commands.add(commandEnvelope("invite_supplier_a", seed.creatorPrincipalId(), invitationCommand(prefix,
                instant, runId, correlationId, eventId, seed.supplierAId(), 2L, "a")));
        commands.add(commandEnvelope("invite_supplier_b", seed.creatorPrincipalId(), invitationCommand(prefix,
                instant, runId, correlationId, eventId, seed.supplierBId(), 3L, "b")));
        commands.add(commandEnvelope("open_quoting", seed.creatorPrincipalId(), sourcingTransitionCommand(prefix,
                instant, runId, correlationId, SourcingOperation.OPEN_QUOTING, eventId, 4L, null)));
        commands.add(commandEnvelope("quotation_a_submit", seed.creatorPrincipalId(), quotationCommand(prefix,
                instant, runId, correlationId, revisionA, "a")));
        commands.add(commandEnvelope("quotation_b_submit", seed.creatorPrincipalId(), quotationCommand(prefix,
                instant, runId, correlationId, revisionB, "b")));
        commands.add(commandEnvelope("close_quoting", seed.creatorPrincipalId(), sourcingTransitionCommand(prefix,
                instant, runId, correlationId, SourcingOperation.CLOSE_QUOTING, eventId, 7L, null)));
        commands.add(commandEnvelope("evaluation_policy_create", seed.creatorPrincipalId(), evaluationPolicyCommand(
                prefix, instant, runId, correlationId, policy)));
        commands.add(commandEnvelope("evaluation_a_record", seed.reviewerAPrincipalId(), evaluationScoreCommand(
                prefix, instant, runId, correlationId, eventId, policyId, quoteA.revision(),
                costDimension, qualityDimension, deliveryDimension, 9L, "a", 96, 93, 90)));
        commands.add(commandEnvelope("evaluation_b_record", seed.reviewerBPrincipalId(), evaluationScoreCommand(
                prefix, instant, runId, correlationId, eventId, policyId, quoteB.revision(),
                costDimension, qualityDimension, deliveryDimension, 10L, "b", 90, 95, 94)));
        commands.add(commandEnvelope("award_draft_create", seed.creatorPrincipalId(), awardCommand(prefix,
                instant, runId, correlationId, awardId, eventId, policyId, 11L, awardLines)));
        commands.add(commandEnvelope("award_submit", seed.creatorPrincipalId(), awardTransitionCommand(prefix,
                instant, runId, correlationId, SourcingOperation.SUBMIT_AWARD, awardId, 1L, 12L, null)));
        commands.add(commandEnvelope("award_approve", seed.approverPrincipalId(), awardTransitionCommand(prefix,
                instant, runId, correlationId, SourcingOperation.APPROVE_AWARD, awardId, 2L, 13L,
                "DUAL_SOURCE_APPROVED")));
        commands.add(commandEnvelope("event_close", seed.creatorPrincipalId(), sourcingTransitionCommand(prefix,
                instant, runId, correlationId, SourcingOperation.CLOSE_SOURCING_EVENT, eventId, 14L,
                "SOURCING_COMPLETED")));

        input.set("awardRelease", commandEnvelope("award_release", seed.approverPrincipalId(),
                AwardReleaseCommand.builder()
                        .idempotencyKey(prefix + ":award-release")
                        .runId(runId).correlationId(correlationId).occurredAt(instant)
                        .awardId(awardId).expectedAwardVersion(3L).build()));
        ArrayNode plans = input.putArray("purchaseOrderPlans");
        plans.add(purchaseOrderPlan(prefix, "a", instant, runId, correlationId,
                seed.creatorPrincipalId(), seed.approverPrincipalId()));
        plans.add(purchaseOrderPlan(prefix, "b", instant, runId, correlationId,
                seed.creatorPrincipalId(), seed.approverPrincipalId()));
        return input;
    }

    private static PurchaseRequisitionCommand.LineDefinition requisitionLine(
            String lineId, int lineNumber, CatalogProcurementLine catalogLine,
            ValuationPolicySeed valuationPolicy, BigDecimal quantity,
            PurchaseRequisitionCommand.DeliveryScheduleDefinition... schedules) {
        return PurchaseRequisitionCommand.LineDefinition.builder()
                .lineId(lineId).lineNumber(lineNumber).canonicalSkuId(catalogLine.canonicalSkuId())
                .requestedQuantity(quantity).uomCode(catalogLine.baseUomCode())
                .valuationPolicyId(valuationPolicy.valuationPolicyId())
                .valuationPolicyVersion(valuationPolicy.valuationPolicyVersion())
                .valuationPolicyHash(valuationPolicy.valuationPolicyHash())
                .schedules(List.of(schedules)).build();
    }

    private static ObjectNode valuationPolicyNode(ValuationPolicySeed policy) {
        return JsonNodeFactory.instance.objectNode()
                .put("canonicalSkuId", policy.canonicalSkuId())
                .put("valuationPolicyId", policy.valuationPolicyId())
                .put("valuationPolicyVersion", policy.valuationPolicyVersion())
                .put("valuationPolicyHash", policy.valuationPolicyHash());
    }

    private static PurchaseRequisitionCommand.DeliveryScheduleDefinition requisitionSchedule(
            String scheduleId, int scheduleNumber, String warehouseId, LocalDate date,
            BigDecimal quantity) {
        return PurchaseRequisitionCommand.DeliveryScheduleDefinition.builder()
                .scheduleId(scheduleId).scheduleNumber(scheduleNumber)
                .canonicalWarehouseId(warehouseId).requiredDeliveryDate(date)
                .scheduledQuantity(quantity).build();
    }

    private static SourcingCommand.EventLineDefinition sourcingLine(
            String sourcingLineId, int lineNumber, String requisitionLineId,
            SourcingCommand.EventScheduleDefinition... schedules) {
        return SourcingCommand.EventLineDefinition.builder()
                .sourcingLineId(sourcingLineId).lineNumber(lineNumber)
                .requisitionLineId(requisitionLineId).schedules(List.of(schedules)).build();
    }

    private static SourcingCommand.EventScheduleDefinition sourcingSchedule(
            String sourcingScheduleId, int scheduleNumber, String requisitionScheduleId) {
        return SourcingCommand.EventScheduleDefinition.builder()
                .sourcingScheduleId(sourcingScheduleId).scheduleNumber(scheduleNumber)
                .requisitionScheduleId(requisitionScheduleId).build();
    }

    private static QuotationIds quotationIds(String prefix, String supplierSuffix) {
        return new QuotationIds(
                stableUuid(prefix + ":quotation:" + supplierSuffix),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":revision:1"),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":line:1"),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":line:2"),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":line:1:schedule:1"),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":line:1:schedule:2"),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":line:2:schedule:1"),
                stableUuid(prefix + ":quotation:" + supplierSuffix + ":line:2:schedule:2"),
                supplierSuffix);
    }

    private static SourcingCommand.QuotationRevisionDefinition quotationRevision(
            QuotationIds ids, String eventId, String supplierId, long expectedEventVersion,
            CatalogProcurementLine firstCatalogLine, CatalogProcurementLine secondCatalogLine,
            String sourcingLine1, String sourcingLine2, String sourcingSchedule11,
            String sourcingSchedule12, String sourcingSchedule21, String sourcingSchedule22,
            BigDecimal quantity1, BigDecimal quantity2, BigDecimal quantity11,
            BigDecimal quantity12, BigDecimal quantity21, BigDecimal quantity22,
            LocalDate date, long firstPrice, long secondPrice) {
        return SourcingCommand.QuotationRevisionDefinition.builder()
                .quotationId(ids.quotation()).quotationCode(code("AI_Q_" + ids.suffix() + "_", eventId))
                .revisionId(ids.revision()).revisionNumber(1).eventId(eventId)
                .supplierId(supplierId).currencyCode("CNY").expectedEventVersion(expectedEventVersion)
                .lines(List.of(
                        quotationLine(ids.line1(), 1, sourcingLine1, quantity1,
                                firstCatalogLine.baseUomCode(), firstPrice,
                                quotationSchedule(ids.schedule11(), 1, sourcingSchedule11,
                                        quantity11, date.plusDays(21)),
                                quotationSchedule(ids.schedule12(), 2, sourcingSchedule12,
                                        quantity12, date.plusDays(35))),
                        quotationLine(ids.line2(), 2, sourcingLine2, quantity2,
                                secondCatalogLine.baseUomCode(), secondPrice,
                                quotationSchedule(ids.schedule21(), 1, sourcingSchedule21,
                                        quantity21, date.plusDays(21)),
                                quotationSchedule(ids.schedule22(), 2, sourcingSchedule22,
                                        quantity22, date.plusDays(35)))))
                .build();
    }

    private static SourcingCommand.QuotationRevisionLineDefinition quotationLine(
            String revisionLineId, int lineNumber, String sourcingLineId, BigDecimal quantity,
            String uomCode, long priceMinor,
            SourcingCommand.QuotationRevisionScheduleDefinition... schedules) {
        return SourcingCommand.QuotationRevisionLineDefinition.builder()
                .revisionLineId(revisionLineId).lineNumber(lineNumber).sourcingLineId(sourcingLineId)
                .offeredQuantity(quantity).uomCode(uomCode)
                .unitNetPriceMinor(BigDecimal.valueOf(priceMinor)).taxCode("VAT13").taxRateBps(1300)
                .schedules(List.of(schedules)).build();
    }

    private static SourcingCommand.QuotationRevisionScheduleDefinition quotationSchedule(
            String revisionScheduleId, int scheduleNumber, String sourcingScheduleId,
            BigDecimal quantity, LocalDate promisedDate) {
        return SourcingCommand.QuotationRevisionScheduleDefinition.builder()
                .revisionScheduleId(revisionScheduleId).scheduleNumber(scheduleNumber)
                .sourcingScheduleId(sourcingScheduleId).offeredQuantity(quantity)
                .promisedDeliveryDate(promisedDate).build();
    }

    private static SourcingCommand.EvaluationDimensionDefinition evaluationDimension(
            String prefix, String suffix, String code, String name, int weightBps) {
        return SourcingCommand.EvaluationDimensionDefinition.builder()
                .dimensionId(stableUuid(prefix + ":evaluation-dimension:" + suffix))
                .dimensionCode(code).dimensionName(name).weightBps(weightBps).maximumScore(100).build();
    }

    private static SourcingCommand.AwardLineDefinition awardLine(
            String prefix, int lineNumber, String sourcingLineId, String sourcingScheduleId,
            String quotationRevisionLineId, String quotationRevisionScheduleId,
            String supplierId, BigDecimal quantity) {
        return SourcingCommand.AwardLineDefinition.builder()
                .awardLineId(stableUuid(prefix + ":award-line:" + lineNumber))
                .lineNumber(lineNumber).sourcingLineId(sourcingLineId)
                .sourcingScheduleId(sourcingScheduleId)
                .quotationRevisionLineId(quotationRevisionLineId)
                .quotationRevisionScheduleId(quotationRevisionScheduleId)
                .supplierId(supplierId).awardedQuantity(quantity).build();
    }

    private static SourcingCommand sourcingCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            SourcingOperation operation, SourcingCommand.EventDefinition event,
            String causationId) {
        return SourcingCommand.builder().operation(operation)
                .idempotencyKey(prefix + ":" + operation.name().toLowerCase(Locale.ROOT))
                .runId(runId).correlationId(correlationId).causationId(causationId)
                .occurredAt(occurredAt).event(event).build();
    }

    private static SourcingCommand sourcingTransitionCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            SourcingOperation operation, String eventId, long expectedVersion, String reasonCode) {
        return SourcingCommand.builder().operation(operation)
                .idempotencyKey(prefix + ":" + operation.name().toLowerCase(Locale.ROOT))
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .eventTransition(SourcingCommand.EventTransitionDefinition.builder()
                        .eventId(eventId).expectedVersion(expectedVersion).reasonCode(reasonCode).build())
                .build();
    }

    private static SourcingCommand invitationCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            String eventId, String supplierId, long expectedVersion, String suffix) {
        return SourcingCommand.builder().operation(SourcingOperation.INVITE_SUPPLIER)
                .idempotencyKey(prefix + ":invite-supplier:" + suffix)
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .invitation(SourcingCommand.InvitationDefinition.builder()
                        .invitationId(stableUuid(prefix + ":invitation:" + suffix))
                        .eventId(eventId).supplierId(supplierId)
                        .expectedEventVersion(expectedVersion).build())
                .build();
    }

    private static SourcingCommand quotationCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            SourcingCommand.QuotationRevisionDefinition revision, String suffix) {
        return SourcingCommand.builder().operation(SourcingOperation.SUBMIT_QUOTATION_REVISION)
                .idempotencyKey(prefix + ":quotation-submit:" + suffix)
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .quotationRevision(revision).build();
    }

    private static SourcingCommand evaluationPolicyCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            SourcingCommand.EvaluationPolicyDefinition policy) {
        return SourcingCommand.builder().operation(SourcingOperation.CREATE_EVALUATION_POLICY)
                .idempotencyKey(prefix + ":evaluation-policy:create")
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .evaluationPolicy(policy).build();
    }

    private static SourcingCommand evaluationScoreCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            String eventId, String policyId, String revisionId,
            SourcingCommand.EvaluationDimensionDefinition cost,
            SourcingCommand.EvaluationDimensionDefinition quality,
            SourcingCommand.EvaluationDimensionDefinition delivery,
            long expectedVersion, String suffix, int costScore, int qualityScore,
            int deliveryScore) {
        return SourcingCommand.builder().operation(SourcingOperation.RECORD_EVALUATION_SCORE)
                .idempotencyKey(prefix + ":evaluation-score:" + suffix)
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .evaluationScore(SourcingCommand.EvaluationScoreDefinition.builder()
                        .scoreId(stableUuid(prefix + ":evaluation-score:" + suffix))
                        .eventId(eventId).policyId(policyId).policyVersion(1)
                        .quotationRevisionId(revisionId)
                        .reviewerEvidenceSha256(DigestUtil.sha256Hex(prefix + ":review-evidence:" + suffix))
                        .expectedEventVersion(expectedVersion)
                        .dimensions(List.of(
                                dimensionScore(cost.getDimensionId(), costScore, prefix, suffix, "cost"),
                                dimensionScore(quality.getDimensionId(), qualityScore, prefix, suffix, "quality"),
                                dimensionScore(delivery.getDimensionId(), deliveryScore, prefix, suffix, "delivery")))
                        .build()).build();
    }

    private static SourcingCommand.DimensionScoreDefinition dimensionScore(
            String dimensionId, int score, String prefix, String supplierSuffix, String dimension) {
        return SourcingCommand.DimensionScoreDefinition.builder().dimensionId(dimensionId).score(score)
                .evidenceReference("evidence://procurement/" + prefix + "/" + supplierSuffix + "/" + dimension)
                .build();
    }

    private static SourcingCommand awardCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            String awardId, String eventId, String policyId, long expectedVersion,
            List<SourcingCommand.AwardLineDefinition> lines) {
        return SourcingCommand.builder().operation(SourcingOperation.CREATE_AWARD_DRAFT)
                .idempotencyKey(prefix + ":award:create-draft")
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .award(SourcingCommand.AwardDefinition.builder()
                        .awardId(awardId).awardCode(code("AI_AWARD_", prefix))
                        .eventId(eventId).policyId(policyId).policyVersion(1)
                        .decisionReasonCode("DUAL_SOURCE_RESILIENCE")
                        .expectedEventVersion(expectedVersion).lines(lines).build())
                .build();
    }

    private static SourcingCommand awardTransitionCommand(
            String prefix, Instant occurredAt, String runId, String correlationId,
            SourcingOperation operation, String awardId, long expectedAwardVersion,
            long expectedEventVersion, String reasonCode) {
        return SourcingCommand.builder().operation(operation)
                .idempotencyKey(prefix + ":" + operation.name().toLowerCase(Locale.ROOT))
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .awardTransition(SourcingCommand.AwardTransitionDefinition.builder()
                        .awardId(awardId).expectedAwardVersion(expectedAwardVersion)
                        .expectedEventVersion(expectedEventVersion).reasonCode(reasonCode).build())
                .build();
    }

    private static ObjectNode purchaseOrderPlan(
            String prefix, String suffix, Instant occurredAt, String runId, String correlationId,
            String creatorPrincipalId, String approverPrincipalId) {
        String authoritativeOrderId = "__AWARD_RELEASE_RESULT__";
        ArrayNode commands = JsonNodeFactory.instance.arrayNode();
        commands.add(commandEnvelope("purchase_order_submit", creatorPrincipalId,
                procurementTransitionCommand(prefix, suffix, occurredAt, runId, correlationId,
                        ProcurementOperation.SUBMIT_PURCHASE_ORDER, authoritativeOrderId, 1L,
                        "AI_PO_SUBMITTED")));
        commands.add(commandEnvelope("purchase_order_approve", approverPrincipalId,
                procurementTransitionCommand(prefix, suffix, occurredAt, runId, correlationId,
                        ProcurementOperation.APPROVE_PURCHASE_ORDER, authoritativeOrderId, 2L,
                        "AI_PO_APPROVED")));
        commands.add(commandEnvelope("purchase_order_release", approverPrincipalId,
                procurementTransitionCommand(prefix, suffix, occurredAt, runId, correlationId,
                        ProcurementOperation.RELEASE_PURCHASE_ORDER, authoritativeOrderId, 3L,
                        "AI_PO_RELEASED")));
        commands.add(commandEnvelope("purchase_order_dispatch", creatorPrincipalId,
                procurementTransitionCommand(prefix, suffix, occurredAt, runId, correlationId,
                        ProcurementOperation.DISPATCH_PURCHASE_ORDER, authoritativeOrderId, 4L,
                        "AI_PO_DISPATCHED")));
        commands.add(commandEnvelope("supplier_confirm", creatorPrincipalId,
                procurementTransitionCommand(prefix, suffix, occurredAt, runId, correlationId,
                        ProcurementOperation.SUPPLIER_CONFIRM_PURCHASE_ORDER, authoritativeOrderId, 5L,
                        "SUPPLIER_CONFIRMED")));
        ObjectNode plan = JsonNodeFactory.instance.objectNode();
        plan.set("commands", commands);
        return plan;
    }

    private static ProcurementCommand procurementTransitionCommand(
            String prefix, String suffix, Instant occurredAt, String runId, String correlationId,
            ProcurementOperation operation, String orderId, long expectedVersion, String reasonCode) {
        return ProcurementCommand.builder().operation(operation)
                .idempotencyKey(prefix + ":purchase-order:" + suffix + ":"
                        + operation.name().toLowerCase(Locale.ROOT))
                .runId(runId).correlationId(correlationId).occurredAt(occurredAt)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId(orderId).expectedVersion(expectedVersion).reasonCode(reasonCode).build())
                .build();
    }

    private static ObjectNode commandEnvelope(String stepCode, String actorPrincipalId, Object command) {
        ObjectNode envelope = JsonNodeFactory.instance.objectNode();
        envelope.put("stepCode", stepCode).put("actorPrincipalId", actorPrincipalId);
        envelope.set("command", JsonUtils.parseTree(JsonUtils.toJsonString(command)));
        return envelope;
    }

    private static String code(String prefix, String value) {
        String normalized = (prefix + value).replace('-', '_').replace(':', '_')
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static ObjectNode wmsOperations(String prefix, String occurredAt,
                                            String merchantId, String shopId,
                                            String principalId, String canonicalWarehouseId,
                                            String canonicalSkuCode, String canonicalBarcode,
                                            String canonicalBaseUomCode) {
        String compact = prefix.replace("-", "").toUpperCase();
        String code = compact.length() > 16 ? compact.substring(0, 16) : compact;
        String date = occurredAt.substring(0, 10);
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("occurredAt", occurredAt)
                .put("correlationId", stableUuid(prefix + ":wms-correlation"))
                .put("canonicalWarehouseId", canonicalWarehouseId);
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
                .put("name", "AI仓储品类-" + code)
                .put("sort", 1)
                .put("status", 0);
        ObjectNode item = input.putObject("item")
                .put("code", "AII" + code)
                .put("name", "AI 仓储全链路商品 " + prefix.toUpperCase())
                .put("unit", canonicalBaseUomCode)
                .put("remark", "每日唯一收货、调拨、出库与盘点商品");
        item.putArray("skus").addObject()
                .put("name", "标准款")
                .put("barCode", canonicalBarcode)
                .put("code", canonicalSkuCode)
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
                                                     String principalId,
                                                     CatalogProcurementLine firstCatalogLine,
                                                     CatalogProcurementLine secondCatalogLine,
                                                     ProcurementSourcingSeed sourcingSeed,
                                                     String warehouseId, String canonicalSkuCode,
                                                     String canonicalBarcode,
                                                     String canonicalBaseUomCode) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        boolean promotion = date.getDayOfMonth() % 5 == 0;
        int orderedQuantity = promotion ? 2_000 : 500;
        ObjectNode sourcing = procurementSourcing(prefix + "-src", occurredAt,
                firstCatalogLine, secondCatalogLine, warehouseId, sourcingSeed, orderedQuantity);
        ObjectNode procurement = JsonNodeFactory.instance.objectNode();
        procurement.put("schemaVersion", PROCUREMENT_SOURCING_INPUT_SCHEMA)
                .put("awardId", sourcing.path("awardId").asText());
        procurement.set("awardRelease", sourcing.path("awardRelease").deepCopy());
        procurement.set("purchaseOrders", sourcing.path("purchaseOrderPlans").deepCopy());
        ObjectNode physical = wmsOperations(prefix + "-wms", occurredAt,
                merchantId, shopId, principalId, warehouseId, canonicalSkuCode,
                canonicalBarcode, canonicalBaseUomCode);
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
        input.put("operatorPrincipalId", principalId);
        addRoleOperationsCase(input, prefix, occurredAt, principalId,
                "AI_REPLENISHMENT_", "replenishment-day:", "replenishment_",
                "SUPPLY_OPERATIONS", promotion
                        ? "PROMOTION_REPLENISHMENT" : "DAILY_REPLENISHMENT",
                "REPLENISHMENT_PLAN_DISPATCHED", "REPLENISHMENT_OPERATOR_ASSIGNED",
                "REPLENISHMENT_FLOW_VERIFIED");
        input.putObject("runIds")
                .put("sourcing", prefix + "-sourcing")
                .put("procurement", prefix + "-procurement")
                .put("warehouse", prefix + "-warehouse");
        input.set("sourcing", sourcing);
        input.set("procurement", procurement);
        input.set("warehouse", physical);
        return input;
    }

    private static ObjectNode supplyPlanningSopLifecycle(
            String prefix, String occurredAt, String canonicalSkuId, String warehouseId,
            String actorPrincipalId, String ownerId, String sourceWarehouseId, String targetWarehouseId,
            long wmsSkuId, String baseUomCode, String mappingEvidenceSha256) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String runId = stableUuid(prefix + ":sop-run");
        String correlationId = stableUuid(prefix + ":sop-correlation");
        String forecastId = stableUuid(prefix + ":forecast");
        String planId = stableUuid(prefix + ":supply-plan");
        String leanScenarioId = stableUuid(prefix + ":scenario-lean");
        String resilientScenarioId = stableUuid(prefix + ":scenario-resilient");

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt)
                .put("actorPrincipalId", actorPrincipalId)
                .put("conversionId", stableUuid(prefix + ":replenishment-conversion"));

        ObjectNode forecast = input.putObject("forecast");
        forecast.put("forecastId", forecastId)
                .put("forecastCode", "AI-FC-" + prefix.toUpperCase())
                .put("horizonStart", date.minusDays(7).toString())
                .put("horizonEnd", date.plusDays(30).toString())
                .put("bucketType", "DAY")
                .put("modelRef", "AI_DEMAND_SENSING_V1")
                .put("baselineSha256",
                        DigestUtil.sha256Hex(prefix + ":forecast-baseline"));
        ArrayNode points = forecast.putArray("points");
        points.addObject()
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId)
                .put("bucketStart", date.minusDays(7).toString())
                .put("forecastQuantity", 18)
                .put("lowerQuantity", 15)
                .put("upperQuantity", 24)
                .put("uomCode", baseUomCode);
        points.addObject()
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId)
                .put("bucketStart", date.plusDays(1).toString())
                .put("forecastQuantity", 20)
                .put("lowerQuantity", 16)
                .put("upperQuantity", 30)
                .put("uomCode", baseUomCode);

        ObjectNode forecastEvaluation = input.putObject("forecastEvaluation");
        forecastEvaluation.put("evaluationId",
                        stableUuid(prefix + ":forecast-evaluation"))
                .put("forecastId", forecastId)
                .put("actualsSha256",
                        DigestUtil.sha256Hex(prefix + ":forecast-actuals"));
        forecastEvaluation.putArray("actuals").addObject()
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId)
                .put("bucketStart", date.minusDays(7).toString())
                .put("actualQuantity", 20)
                .put("uomCode", baseUomCode);

        input.putObject("supplyPlan")
                .put("planId", planId)
                .put("planCode", "AI-SOP-" + prefix.toUpperCase())
                .put("demandForecastId", forecastId)
                .put("horizonStart", date.plusDays(1).toString())
                .put("horizonEnd", date.plusDays(30).toString())
                .put("targetServiceLevelBasisPoints", 9_500)
                .put("budgetAmountMinor", 100_000)
                .put("currencyCode", "CNY")
                .put("constraintsSha256",
                        DigestUtil.sha256Hex(prefix + ":sop-constraints"));

        ObjectNode scenarios = input.putObject("scenarios");
        scenarios.set("lean", supplyPlanScenario(
                leanScenarioId, planId, "LEAN", canonicalSkuId, warehouseId,
                20, 5, 2, 0, 10, prefix + ":lean", baseUomCode));
        scenarios.set("resilient", supplyPlanScenario(
                resilientScenarioId, planId, "RESILIENT", canonicalSkuId, warehouseId,
                20, 5, 10, 10, 20, prefix + ":resilient", baseUomCode));

        ObjectNode recommendation = input.putObject("scenarioRecommendation");
        recommendation.put("recommendationId",
                        stableUuid(prefix + ":scenario-recommendation"))
                .put("planId", planId)
                .put("expectedPlanVersion", 1)
                .put("targetServiceLevelFloorBasisPoints", 9_500)
                .put("maxProjectedCostMinor", 50_000)
                .put("demandStressBasisPoints", 12_000)
                .put("supplyAvailabilityBasisPoints", 8_000)
                .put("policySha256",
                        DigestUtil.sha256Hex(prefix + ":robust-scenario-policy"));
        recommendation.putArray("candidateScenarioIds")
                .add(leanScenarioId)
                .add(resilientScenarioId);

        input.putObject("executionProposal")
                .put("proposalId", stableUuid(prefix + ":execution-proposal"))
                .put("recommendationId", ZERO_UUID)
                .put("expectedRecommendationVersion", 2)
                .put("targetType", "TRANSFER_REQUEST")
                .put("mappingEvidenceSha256", mappingEvidenceSha256)
                .put("sourceWarehouseId", sourceWarehouseId)
                .put("targetWarehouseId", targetWarehouseId)
                .put("wmsSkuId", wmsSkuId)
                .put("ownerType", "MERCHANT")
                .put("ownerId", ownerId)
                .put("unitCostMinor", 1_000)
                .put("proposedByPrincipalId", actorPrincipalId)
                .put("policyCode", "SOP_TRANSFER_V1")
                .put("policySha256",
                        DigestUtil.sha256Hex(prefix + ":sop-transfer-policy"));
        return input;
    }

    private static ObjectNode supplyPlanScenario(
            String scenarioId, String planId, String scenarioCode,
            String canonicalSkuId, String warehouseId, int forecastQuantity,
            int safetyStockQuantity, int onHandQuantity, int inboundQuantity,
            int capacityQuantity, String evidenceSeed, String baseUomCode) {
        return JsonNodeFactory.instance.objectNode()
                .put("scenarioId", scenarioId)
                .put("planId", planId)
                .put("scenarioCode", scenarioCode)
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId)
                .put("forecastQuantity", forecastQuantity)
                .put("safetyStockQuantity", safetyStockQuantity)
                .put("onHandQuantity", onHandQuantity)
                .put("inboundQuantity", inboundQuantity)
                .put("capacityQuantity", capacityQuantity)
                .put("minimumOrderQuantity", 1)
                .put("unitCostMinor", 1_000)
                .put("uomCode", baseUomCode)
                .put("parametersSha256", DigestUtil.sha256Hex(evidenceSeed));
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
        return financeSettlementLifecycle(prefix, occurredAt, makerUserId, checkerUserId,
                "DAILY_CLOSE", "YSHOPPING_INTERNAL",
                1_200_000L, 100_000L, 50_000L, 10_000L);
    }

    private static ObjectNode financeSettlementParentInput(
            String prefix, ObjectNode settlementInput) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.putObject("runIds").put("settlement", prefix + "-finance-settlement");
        input.set("settlement", settlementInput);
        return input;
    }

    private static ObjectNode financeSettlementLifecycle(
            String prefix, String occurredAt, long makerUserId, long checkerUserId,
            String scenarioCode, String channelCode, long grossAmountMinor,
            long refundAmountMinor, long feeAmountMinor, long differenceAmountMinor) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String scenarioToken = scenarioCode.toLowerCase(Locale.ROOT);
        String runId = prefix + "-" + scenarioToken;
        String correlationId = stableUuid(prefix + ":" + scenarioToken + "-correlation");
        String periodId = stableUuid(prefix + ":" + scenarioToken + "-accounting-period");
        String statementId = stableUuid(prefix + ":" + scenarioToken + "-statement");
        String settlementId = stableUuid(prefix + ":" + scenarioToken + "-settlement");
        String journalId = stableUuid(prefix + ":" + scenarioToken + "-journal");
        long netAmountMinor = grossAmountMinor - refundAmountMinor - feeAmountMinor;

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.putObject("settlementContext")
                .put("scenarioCode", scenarioCode)
                .put("channelCode", channelCode)
                .put("grossAmountMinor", grossAmountMinor)
                .put("refundAmountMinor", refundAmountMinor)
                .put("feeAmountMinor", feeAmountMinor)
                .put("netSettlementAmountMinor", netAmountMinor)
                .put("currencyCode", "CNY");
        input.set("makerIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, makerUserId));
        input.set("checkerIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, checkerUserId));
        ArrayNode commands = input.putArray("commands");

        commands.add(financeCommand(runId, correlationId, occurredAt, "OPEN_ACCOUNTING_PERIOD")
                .set("period", JsonNodeFactory.instance.objectNode()
                        .put("periodId", periodId)
                        .put("periodCode", "AI-" + scenarioCode + "-" + date + "-"
                                + DigestUtil.sha256Hex(prefix).substring(0, 8))
                        .put("periodStart", date.toString())
                        .put("periodEnd", date.toString())
                        .put("currencyCode", "CNY")
                        .put("reasonCode", "AI_" + scenarioCode)));
        commands.add(financeCommand(runId, correlationId, occurredAt, "IMPORT_CHANNEL_STATEMENT")
                .set("statement", JsonNodeFactory.instance.objectNode()
                        .put("statementId", statementId)
                        .put("statementCode", "AI-" + scenarioCode + "-ST-" + prefix.toUpperCase())
                        .put("periodId", periodId)
                        .put("channelCode", channelCode)
                        .put("statementDate", date.toString())
                        .put("currencyCode", "CNY")
                        .put("grossAmountMinor", grossAmountMinor)
                        .put("refundAmountMinor", refundAmountMinor)
                        .put("feeAmountMinor", feeAmountMinor)
                        .put("netSettlementAmountMinor", netAmountMinor)
                        .put("expectedBusinessNetAmountMinor",
                                netAmountMinor - differenceAmountMinor)
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":" + scenarioToken + "-statement-evidence"))
                        .put("reasonCode", "AI_" + scenarioCode + "_STATEMENT_IMPORT")));
        commands.add(financeCommand(runId, correlationId, occurredAt,
                        "RECONCILE_CHANNEL_STATEMENT")
                .set("statement", JsonNodeFactory.instance.objectNode()
                        .put("statementId", statementId)
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_" + scenarioCode + "_RECONCILIATION")));
        commands.add(financeCommand(runId, correlationId, occurredAt,
                        "RESOLVE_RECONCILIATION_DIFFERENCE")
                .set("differenceResolution", JsonNodeFactory.instance.objectNode()
                        .put("differenceId", ZERO_UUID)
                        .put("adjustmentAmountMinor", differenceAmountMinor)
                        .put("resolutionType", "CHANNEL_ADJUSTMENT")
                        .put("resolutionEvidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":" + scenarioToken + "-difference-evidence"))
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_" + scenarioCode + "_ADJUSTMENT")));
        commands.add(financeCommand(runId, correlationId, occurredAt,
                        "CREATE_SETTLEMENT_BATCH")
                .set("settlement", JsonNodeFactory.instance.objectNode()
                        .put("settlementBatchId", settlementId)
                        .put("settlementCode", "AI-" + scenarioCode + "-SE-" + prefix.toUpperCase())
                        .put("periodId", periodId)
                        .put("statementId", statementId)
                        .put("expectedAmountMinor", netAmountMinor)
                        .put("reasonCode", "AI_" + scenarioCode + "_SETTLEMENT_PREPARE")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "CONFIRM_SETTLEMENT")
                .set("settlement", JsonNodeFactory.instance.objectNode()
                        .put("settlementBatchId", settlementId)
                        .put("settledAmountMinor", netAmountMinor)
                        .put("bankReference", "AI-" + scenarioCode + "-BANK-" + prefix.toUpperCase())
                        .put("settlementEvidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":" + scenarioToken + "-settlement-evidence"))
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_" + scenarioCode + "_RECEIPT_VERIFIED")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "PREPARE_JOURNAL_ENTRY")
                .set("journalEntry", JsonNodeFactory.instance.objectNode()
                        .put("journalEntryId", journalId)
                        .put("journalCode", "AI-" + scenarioCode + "-JE-" + prefix.toUpperCase())
                        .put("periodId", periodId)
                        .put("sourceType", "SETTLEMENT_BATCH")
                        .put("sourceId", settlementId)
                        .put("debitTotalMinor", netAmountMinor)
                        .put("creditTotalMinor", netAmountMinor)
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":" + scenarioToken + "-journal-evidence"))
                        .put("reasonCode", "AI_" + scenarioCode + "_JOURNAL_PREPARE")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "POST_JOURNAL_ENTRY")
                .set("journalEntry", JsonNodeFactory.instance.objectNode()
                        .put("journalEntryId", journalId)
                        .put("expectedVersion", 1)
                        .put("reasonCode", "AI_" + scenarioCode + "_JOURNAL_POST")));
        commands.add(financeCommand(runId, correlationId, occurredAt, "CLOSE_ACCOUNTING_PERIOD")
                .set("period", JsonNodeFactory.instance.objectNode()
                        .put("periodId", periodId)
                        .put("expectedVersion", 1)
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":" + scenarioToken + "-close-evidence"))
                        .put("reasonCode", "AI_" + scenarioCode + "_CLOSE_COMPLETE")));
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

    private static ObjectNode qualityInspectionRecallLifecycle(
            String prefix, String occurredAt, String merchantId, String warehouseId,
            String locationId, String canonicalSkuId, String managerPrincipalId,
            long primaryInspectorUserId, long independentReviewerUserId) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String runId = prefix + "-quality-recall";
        String correlationId = stableUuid(prefix + ":quality-recall-correlation");
        String inspectionTaskId = stableUuid(prefix + ":inspection-task");
        String standardId = stableUuid(prefix + ":quality-standard");
        String capaId = stableUuid(prefix + ":quality-capa");
        String recallActionId = stableUuid(prefix + ":quality-recall");

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("managerPrincipalId", managerPrincipalId);
        input.set("primaryInspectorIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, primaryInspectorUserId));
        input.set("independentReviewerIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, independentReviewerUserId));
        input.set("lotCommand", JsonNodeFactory.instance.objectNode()
                .put("operation", "REGISTER")
                .put("idempotencyKey", "pending-quality-lot-command")
                .put("runId", runId)
                .put("ownerType", "MERCHANT")
                .put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("lotCode", "AI-QLOT-" + prefix.toUpperCase())
                .put("manufacturedOn", date.minusDays(30).toString())
                .put("expiresOn", date.plusDays(365).toString())
                .put("receivedAt", occurredAt)
                .put("reasonCode", "AI_QUALITY_INSPECTION")
                .put("evidenceRef", "evidence:quality/" + prefix)
                .put("traceId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt));
        input.set("stockCommand", JsonNodeFactory.instance.objectNode()
                .put("operation", "RECEIVE")
                .put("idempotencyKey", "pending-quality-stock-command")
                .put("ownerType", "MERCHANT")
                .put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId)
                .put("locationId", locationId)
                .put("lotId", ZERO_UUID)
                .put("stockStatus", "SELLABLE")
                .put("qualityStatus", "QUALIFIED")
                .put("baseUomCode", "EA")
                .put("quantity", 12)
                .put("businessType", "QUALITY_INSPECTION")
                .put("businessId", inspectionTaskId)
                .put("businessItemId", recallActionId)
                .put("businessNo", "AI-QC-" + prefix.toUpperCase())
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt));

        ArrayNode commands = input.putArray("commands");
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CREATE_STANDARD")
                .set("standard", JsonNodeFactory.instance.objectNode()
                        .put("standardId", standardId)
                        .put("standardCode", "AI-QS-" + prefix.toUpperCase())
                        .put("categoryCode", "APPAREL")
                        .put("brandCode", "CLOUDMOLD")
                        .put("applicableSkuId", canonicalSkuId)
                        .put("contentSha256",
                                DigestUtil.sha256Hex(prefix + ":quality-standard-content"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "PUBLISH_STANDARD")
                .set("standard", JsonNodeFactory.instance.objectNode()
                        .put("standardId", standardId)
                        .put("expectedVersion", 1)
                        .put("approverPrincipalId", ZERO_UUID)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CERTIFY_AUTHENTICATOR")
                .set("certification", JsonNodeFactory.instance.objectNode()
                        .put("certificationId", stableUuid(prefix + ":primary-certification"))
                        .put("authenticatorPrincipalId", ZERO_UUID)
                        .put("standardId", standardId)
                        .put("certificationLevel", "EXPERT")
                        .put("effectiveFrom", date.minusDays(1).toString())
                        .put("effectiveTo", date.plusDays(365).toString())
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":primary-certification-evidence"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CERTIFY_AUTHENTICATOR")
                .set("certification", JsonNodeFactory.instance.objectNode()
                        .put("certificationId", stableUuid(prefix + ":reviewer-certification"))
                        .put("authenticatorPrincipalId", ZERO_UUID)
                        .put("standardId", standardId)
                        .put("certificationLevel", "SENIOR")
                        .put("effectiveFrom", date.minusDays(1).toString())
                        .put("effectiveTo", date.plusDays(365).toString())
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":reviewer-certification-evidence"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CREATE_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("standardId", standardId)
                        .put("subjectType", "INBOUND_ITEM")
                        .put("subjectRef", "AI-INBOUND-" + prefix.toUpperCase())
                        .put("canonicalSkuId", canonicalSkuId)
                        .put("lotId", ZERO_UUID)
                        .put("warehouseId", warehouseId)
                        .put("priority", "HIGH")));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "ASSIGN_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 1)
                        .put("authenticatorPrincipalId", ZERO_UUID)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "START_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 2)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "DECIDE_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 3)
                        .put("decision", "FAIL")
                        .put("defectCode", "MATERIAL_DEFECT")
                        .put("evidenceRef", qualityEvidence(prefix, "primary-inspection"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "REQUEST_RECHECK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 4)
                        .put("recheckReasonCode", "AI_INDEPENDENT_RECHECK")
                        .put("secondaryAuthenticatorPrincipalId", ZERO_UUID)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "SUBMIT_RECHECK_DECISION")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 5)
                        .put("decision", "FAIL")
                        .put("defectCode", "MATERIAL_DEFECT")
                        .put("evidenceRef", qualityEvidence(prefix, "independent-recheck"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "COMPLETE_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 6)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "OPEN_CAPA")
                .set("capa", JsonNodeFactory.instance.objectNode()
                        .put("capaId", capaId)
                        .put("inspectionTaskId", inspectionTaskId)
                        .put("rootCauseCode", "SUPPLIER_MATERIAL")
                        .put("ownerPrincipalId", managerPrincipalId)
                        .put("dueDate", date.plusDays(7).toString())));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "RESOLVE_CAPA")
                .set("capa", JsonNodeFactory.instance.objectNode()
                        .put("capaId", capaId)
                        .put("expectedVersion", 1)
                        .put("effectivenessEvidenceRef",
                                qualityEvidence(prefix, "capa-effectiveness"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "OPEN_RECALL_ACTION")
                .set("recallAction", JsonNodeFactory.instance.objectNode()
                        .put("recallActionId", recallActionId)
                        .put("inspectionTaskId", inspectionTaskId)
                        .put("reasonCode", "MATERIAL_DEFECT")
                        .put("ownerPrincipalId", managerPrincipalId)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "ACKNOWLEDGE_RECALL_ACTION")
                .set("recallAction", JsonNodeFactory.instance.objectNode()
                        .put("recallActionId", recallActionId)
                        .put("ownerPrincipalId", managerPrincipalId)
                        .put("expectedVersion", 1)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "RESOLVE_RECALL_ACTION")
                .set("recallAction", JsonNodeFactory.instance.objectNode()
                        .put("recallActionId", recallActionId)
                        .put("ownerPrincipalId", managerPrincipalId)
                        .put("expectedVersion", 2)
                        .put("resolutionCode", "QUARANTINED_DESTROYED")));
        return input;
    }

    private static ObjectNode mysteryBuyerSampleVerification(
            String prefix, String occurredAt, String merchantId, String warehouseId,
            String locationId, String canonicalSkuId, String managerPrincipalId,
            long inspectorUserId) {
        LocalDate date = Instant.parse(occurredAt).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        String runId = prefix + "-mystery-buyer-quality";
        String correlationId = stableUuid(prefix + ":mystery-buyer-quality-correlation");
        String inspectionTaskId = stableUuid(prefix + ":mystery-buyer-inspection");
        String standardId = stableUuid(prefix + ":mystery-buyer-standard");

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("canonicalSkuId", canonicalSkuId);
        input.put("traceId", stableUuid(prefix + ":mystery-buyer-quality-run"));
        input.put("managerPrincipalId", managerPrincipalId);
        input.set("inspectorIdentityCommand", financeIdentityCommand(
                runId, correlationId, occurredAt, inspectorUserId));
        input.set("lotCommand", JsonNodeFactory.instance.objectNode()
                .put("operation", "REGISTER")
                .put("idempotencyKey", "pending-mystery-buyer-lot")
                .put("runId", runId)
                .put("ownerType", "MERCHANT")
                .put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("lotCode", "AI-MB-" + prefix.toUpperCase())
                .put("manufacturedOn", date.minusDays(30).toString())
                .put("expiresOn", date.plusDays(365).toString())
                .put("receivedAt", occurredAt)
                .put("reasonCode", "MYSTERY_BUYER_PURCHASED_SAMPLE")
                .put("evidenceRef", "evidence:mystery-buyer-order/" + prefix)
                .put("traceId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt));
        input.set("stockCommand", JsonNodeFactory.instance.objectNode()
                .put("operation", "RECEIVE")
                .put("idempotencyKey", "pending-mystery-buyer-stock")
                .put("ownerType", "MERCHANT")
                .put("ownerId", merchantId)
                .put("canonicalSkuId", canonicalSkuId)
                .put("warehouseId", warehouseId)
                .put("locationId", locationId)
                .put("lotId", ZERO_UUID)
                .put("stockStatus", "NON_SELLABLE")
                .put("qualityStatus", "PENDING_QC")
                .put("baseUomCode", "EA")
                .put("quantity", 1)
                .put("businessType", "MYSTERY_BUYER_SAMPLE")
                .put("businessId", inspectionTaskId)
                .put("businessItemId", inspectionTaskId)
                .put("businessNo", "AI-MBQC-" + prefix.toUpperCase())
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt));

        ArrayNode commands = input.putArray("commands");
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CREATE_STANDARD")
                .set("standard", JsonNodeFactory.instance.objectNode()
                        .put("standardId", standardId)
                        .put("standardCode", "AI-MBQS-" + prefix.toUpperCase())
                        .put("categoryCode", "APPAREL")
                        .put("brandCode", "CLOUDMOLD")
                        .put("applicableSkuId", canonicalSkuId)
                        .put("contentSha256", DigestUtil.sha256Hex(
                                prefix + ":pattern:price:content-shooting:model-fitting"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "PUBLISH_STANDARD")
                .set("standard", JsonNodeFactory.instance.objectNode()
                        .put("standardId", standardId)
                        .put("expectedVersion", 1)
                        .put("approverPrincipalId", managerPrincipalId)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CERTIFY_AUTHENTICATOR")
                .set("certification", JsonNodeFactory.instance.objectNode()
                        .put("certificationId", stableUuid(prefix + ":mystery-buyer-certification"))
                        .put("authenticatorPrincipalId", ZERO_UUID)
                        .put("standardId", standardId)
                        .put("certificationLevel", "EXPERT")
                        .put("effectiveFrom", date.minusDays(1).toString())
                        .put("effectiveTo", date.plusDays(365).toString())
                        .put("evidenceSha256",
                                DigestUtil.sha256Hex(prefix + ":mystery-buyer-certification"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "CREATE_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("standardId", standardId)
                        .put("subjectType", "LISTING_SAMPLE")
                        .put("subjectRef", "mystery-buyer-order:" + prefix)
                        .put("canonicalSkuId", canonicalSkuId)
                        .put("lotId", ZERO_UUID)
                        .put("warehouseId", warehouseId)
                        .put("priority", "HIGH")));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "ASSIGN_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 1)
                        .put("authenticatorPrincipalId", ZERO_UUID)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "START_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 2)));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "DECIDE_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 3)
                        .put("decision", "PASS")
                        .put("evidenceRef", qualityEvidence(
                                prefix, "pattern-price-content-fitting-pass"))));
        commands.add(qualityCommand(runId, correlationId, occurredAt, "COMPLETE_INSPECTION_TASK")
                .set("inspectionTask", JsonNodeFactory.instance.objectNode()
                        .put("taskId", inspectionTaskId)
                        .put("expectedVersion", 4)));
        return input;
    }

    private static ObjectNode qualityCommand(String runId, String correlationId,
                                             String occurredAt, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", "pending-quality-command")
                .put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static String qualityEvidence(String prefix, String purpose) {
        return "sha256:" + DigestUtil.sha256Hex(prefix + ":" + purpose);
    }

    private static ObjectNode fulfillmentExceptionLifecycle(
            String prefix, String occurredAt, ObjectNode consumer) {
        boolean addressChange = Math.floorMod(prefix.hashCode(), 2) == 0;
        String runId = prefix + "-fulfillment-exception";
        String correlationId = stableUuid(prefix + ":fulfillment-exception-correlation");
        String exceptionType = addressChange ? "ADDRESS_CHANGE" : "DELAY";
        String action = addressChange ? "REROUTE_ADDRESS" : "CONTACT_CARRIER";
        String reason = addressChange
                ? "AI识别客户在签收前提出地址更正，需要重新规划末端配送路线"
                : "AI识别在途节点超过承诺时效阈值，需要承运商优先干预";
        String actionDescription = addressChange
                ? "核验新地址、重算配送路线、通知承运商改派并同步新的预计送达时间"
                : "联系承运商定位车辆与滞留原因，升级优先级并同步新的预计送达时间";
        String resolutionSummary = addressChange
                ? "承运商已确认改派路线和新地址，客户已获知新的预计送达时间"
                : "承运商已解除滞留并恢复运输，客户已获知新的预计送达时间";

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("inTransitRunId", prefix + "-in-transit-order");
        input.set("consumer", consumer);
        ArrayNode commands = input.putArray("exceptionCommands");
        commands.add(fulfillmentExceptionCommand(runId, correlationId, occurredAt, "OPEN")
                .put("exceptionType", exceptionType)
                .put("reason", reason));
        commands.add(fulfillmentExceptionCommand(runId, correlationId, occurredAt, "PLAN")
                .put("exceptionId", ZERO_UUID)
                .put("expectedVersion", 1)
                .put("action", action)
                .put("actionDescription", actionDescription)
                .put("evidenceRef", fulfillmentExceptionEvidence(prefix, "impact-and-response-plan")));
        commands.add(fulfillmentExceptionCommand(runId, correlationId, occurredAt,
                        "REQUEST_APPROVAL")
                .put("exceptionId", ZERO_UUID)
                .put("expectedVersion", 2));
        commands.add(fulfillmentExceptionCommand(runId, correlationId, occurredAt,
                        "START_EXECUTION")
                .put("exceptionId", ZERO_UUID)
                .put("expectedVersion", 3));
        commands.add(fulfillmentExceptionCommand(runId, correlationId, occurredAt, "RESOLVE")
                .put("exceptionId", ZERO_UUID)
                .put("expectedVersion", 4)
                .put("evidenceRef", fulfillmentExceptionEvidence(prefix, "carrier-confirmation"))
                .put("resolutionSummary", resolutionSummary));
        commands.add(fulfillmentExceptionCommand(runId, correlationId, occurredAt, "CLOSE")
                .put("exceptionId", ZERO_UUID)
                .put("expectedVersion", 5));
        return input;
    }

    private static ObjectNode fulfillmentExceptionCommand(
            String runId, String correlationId, String occurredAt, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", "pending-fulfillment-exception-command")
                .put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static String fulfillmentExceptionEvidence(String prefix, String purpose) {
        return "evidence:fulfillment-exception/sha256/"
                + DigestUtil.sha256Hex(prefix + ":" + purpose);
    }

    private static ObjectNode crossborderFulfillmentComplianceLifecycle(
            String prefix, String occurredAt, ObjectNode consumer, String operatorPrincipalId) {
        boolean express = Math.floorMod(prefix.hashCode(), 2) == 0;
        String runId = prefix + "-crossborder-direct-mail";
        String correlationId = stableUuid(prefix + ":crossborder-correlation");
        String caseId = stableUuid(prefix + ":crossborder-case");
        String routeCode = express ? "CN_US_DIRECT_EXPRESS" : "CN_US_DIRECT_STANDARD";
        String carrierCode = express ? "TEST_INTL_EXPRESS" : "TEST_POSTAL_PACKET";
        String serviceLevel = express ? "EXPRESS_5D" : "STANDARD_10D";
        int slaDays = express ? 5 : 10;
        String declarationId = stableUuid(prefix + ":customs-declaration");
        String bookingRef = "booking:" + prefix;
        String trackingNumber = "CMUS" + DigestUtil.sha256Hex(prefix).substring(0, 12).toUpperCase();

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("inTransitRunId", prefix + "-crossborder-order");
        input.put("operatorPrincipalId", operatorPrincipalId);
        input.set("consumer", consumer);
        ArrayNode commands = input.putArray("commands");

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "CREATE_CASE")
                .put("caseId", caseId)
                .put("orderId", ZERO_UUID)
                .put("fulfillmentId", ZERO_UUID)
                .put("tradeMode", "DIRECT_MAIL")
                .put("originCountry", "CN")
                .put("destinationCountry", "US"));

        ObjectNode assessment = JsonNodeFactory.instance.objectNode();
        assessment.putArray("facts")
                .add("订单已支付且已生成规范履约单")
                .add("货物为测试服饰商品，数量与申报金额可追溯")
                .add("目的地为美国，采用中国直邮测试通道");
        assessment.putArray("options")
                .add("CN_US_DIRECT_STANDARD")
                .add("CN_US_DIRECT_EXPRESS");
        assessment.put("recommendation", routeCode);
        assessment.putArray("risks")
                .add(express ? "EXPRESS_COST_PREMIUM" : "CUSTOMS_INSPECTION_DELAY")
                .add("EXTERNAL_RULE_SOURCE_NOT_CONNECTED");
        assessment.put("confidence", express ? 0.93 : 0.89);
        assessment.putArray("missingFacts");
        assessment.put("evidenceRef", crossborderEvidence(prefix, "bounded-compliance-assessment"));
        commands.add(crossborderCommand(runId, correlationId, occurredAt,
                        "RECORD_COMPLIANCE_ASSESSMENT")
                .put("caseId", caseId)
                .put("expectedVersion", 1)
                .set("assessment", assessment));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "SELECT_ROUTE")
                .put("caseId", caseId)
                .put("expectedVersion", 2)
                .set("route", JsonNodeFactory.instance.objectNode()
                        .put("routeCode", routeCode)
                        .put("carrierCode", carrierCode)
                        .put("serviceLevel", serviceLevel)
                        .put("slaDays", slaDays)));

        commands.add(crossborderCommand(runId, correlationId, occurredAt,
                        "APPROVE_COMPLIANCE")
                .put("caseId", caseId)
                .put("expectedVersion", 3));

        commands.add(crossborderCommand(runId, correlationId, occurredAt,
                        "ASSEMBLE_DECLARATION")
                .put("caseId", caseId)
                .put("expectedVersion", 4)
                .set("declaration", JsonNodeFactory.instance.objectNode()
                        .put("declarationId", declarationId)
                        .put("hsCode", "610910")
                        .put("goodsDescription", "KNITTED COTTON T-SHIRT")
                        .put("quantity", 1)
                        .put("declaredAmountMinor", 39_800)
                        .put("currency", "CNY")
                        .put("evidenceRef", crossborderEvidence(prefix, "declaration-packet"))));

        commands.add(crossborderCommand(runId, correlationId, occurredAt,
                        "VALIDATE_THREE_DOCUMENTS")
                .put("caseId", caseId)
                .put("expectedVersion", 5)
                .set("documents", JsonNodeFactory.instance.objectNode()
                        .put("orderRef", crossborderEvidence(prefix, "order-document"))
                        .put("paymentRef", crossborderEvidence(prefix, "payment-document"))
                        .put("logisticsRef", crossborderEvidence(prefix, "logistics-document"))
                        .put("validationEvidenceRef",
                                crossborderEvidence(prefix, "three-document-validation"))));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "BOOK_CARRIER")
                .put("caseId", caseId)
                .put("expectedVersion", 6)
                .set("booking", JsonNodeFactory.instance.objectNode()
                        .put("bookingRef", bookingRef)
                        .put("carrierCode", carrierCode)
                        .put("serviceLevel", serviceLevel)));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "RECORD_LABEL")
                .put("caseId", caseId)
                .put("expectedVersion", 7)
                .set("label", JsonNodeFactory.instance.objectNode()
                        .put("labelRef", crossborderEvidence(prefix, "carrier-label"))
                        .put("trackingNumber", trackingNumber)));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "HANDOVER")
                .put("caseId", caseId)
                .put("expectedVersion", 8)
                .put("handoverRef", crossborderEvidence(prefix, "export-handover")));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "SUBMIT_CUSTOMS")
                .put("caseId", caseId)
                .put("expectedVersion", 9)
                .put("customsDeclarationRef",
                        crossborderEvidence(prefix, "customs-submission-receipt")));

        commands.add(crossborderCommand(runId, correlationId, occurredAt,
                        "RECORD_CUSTOMS_RELEASE")
                .put("caseId", caseId)
                .put("expectedVersion", 10)
                .put("customsReleaseRef",
                        crossborderEvidence(prefix, express
                                ? "customs-direct-release" : "customs-inspection-release")));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "RECORD_DELIVERY")
                .put("caseId", caseId)
                .put("expectedVersion", 11)
                .put("deliveryEvidenceRef", crossborderEvidence(prefix, "last-mile-delivery")));

        commands.add(crossborderCommand(runId, correlationId, occurredAt, "CLOSE_CASE")
                .put("caseId", caseId)
                .put("expectedVersion", 12)
                .put("closeReason", "DELIVERED_AND_CUSTOMS_RELEASED"));
        return input;
    }

    private static ObjectNode crossborderCommand(
            String runId, String correlationId, String occurredAt, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", "pending-crossborder-command")
                .put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static String crossborderEvidence(String prefix, String purpose) {
        return "evidence:crossborder/sha256/"
                + DigestUtil.sha256Hex(prefix + ":" + purpose);
    }

    private static ObjectNode bondedCustomsLifecycle(
            String prefix, String occurredAt, ObjectNode consumer, String operatorPrincipalId) {
        String runId = prefix + "-bonded-customs";
        String correlationId = stableUuid(prefix + ":bonded-customs-correlation");
        String caseId = stableUuid(prefix + ":bonded-customs-case");
        String identityHash = DigestUtil.sha256Hex(prefix + ":bonded-buyer");
        long goodsAmountMinor = 39_800L;

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("inTransitRunId", prefix + "-bonded-order");
        input.put("operatorPrincipalId", operatorPrincipalId);
        input.set("consumer", consumer);
        ArrayNode commands = input.putArray("bondedCommands");

        ObjectNode tripleOrder = JsonNodeFactory.instance.objectNode()
                .put("orderRef", "bonded-order:" + prefix)
                .put("paymentRef", "bonded-payment:" + prefix)
                .put("logisticsRef", "bonded-logistics:" + prefix)
                .put("orderAmountMinor", goodsAmountMinor)
                .put("paymentAmountMinor", goodsAmountMinor)
                .put("logisticsAmountMinor", goodsAmountMinor)
                .put("currency", "CNY")
                .put("buyerIdentityHash", identityHash)
                .put("receiverIdentityHash", identityHash)
                .put("declarantIdentityHash", identityHash)
                .put("orderSnapshotRef", bondedCustomsEvidence(prefix, "order-snapshot"))
                .put("paymentSnapshotRef", bondedCustomsEvidence(prefix, "payment-snapshot"))
                .put("logisticsSnapshotRef", bondedCustomsEvidence(prefix, "logistics-snapshot"));
        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt, "CREATE_CASE")
                .put("caseId", caseId)
                .put("canonicalOrderId", ZERO_UUID)
                .set("tripleOrder", tripleOrder));

        ObjectNode assessment = JsonNodeFactory.instance.objectNode();
        assessment.putArray("facts")
                .add("规范订单已支付并形成境内在途履约事实")
                .add("订单、支付和物流三单均具备可审计快照")
                .add("商品按受控测试正面清单与保税仓模式申报");
        assessment.putArray("options")
                .add("BONDED_RETAIL_IMPORT")
                .add("HOLD_FOR_MANUAL_CLASSIFICATION");
        assessment.put("recommendation", "BONDED_RETAIL_IMPORT");
        assessment.putArray("risks")
                .add("CUSTOMS_RULE_SOURCE_NOT_CONNECTED")
                .add("HS_CLASSIFICATION_REQUIRES_PRODUCTION_AUTHORITY");
        assessment.put("confidence", 0.91);
        assessment.putArray("missingFacts");
        assessment.put("evidenceRef",
                bondedCustomsEvidence(prefix, "bounded-eligibility-assessment"));
        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "ASSESS_ELIGIBILITY")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 1)
                .set("eligibilityAssessment", assessment));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "CLASSIFY_GOODS")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 2)
                .set("goodsClassification", JsonNodeFactory.instance.objectNode()
                        .put("hsCode", "610910")
                        .put("positiveListCode", "POSITIVE_LIST_TEST")
                        .put("goodsName", "KNITTED COTTON T-SHIRT")
                        .put("evidenceRef",
                                bondedCustomsEvidence(prefix, "goods-classification"))));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "MATCH_TRIPLE_ORDERS")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 3)
                .set("tripleOrder", tripleOrder.deepCopy()));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "CALCULATE_TAX")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 4)
                .set("taxCalculation", JsonNodeFactory.instance.objectNode()
                        .put("dutiableAmountMinor", goodsAmountMinor)
                        .put("consumptionTaxMinor", 0)
                        .put("valueAddedTaxMinor", 3_582)
                        .put("totalTaxMinor", 3_582)
                        .put("currency", "CNY")
                        .put("evidenceRef",
                                bondedCustomsEvidence(prefix, "test-tax-calculation"))));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "APPROVE_DECLARATION")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 5));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "SUBMIT_DECLARATION")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 6)
                .put("declarationRef",
                        bondedCustomsEvidence(prefix, "customs-declaration-submission")));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "ACCEPT_CUSTOMS")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 7)
                .put("customsAcceptanceRef",
                        bondedCustomsEvidence(prefix, "customs-acceptance")));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "RELEASE_BONDED_STOCK")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 8)
                .put("bondedReleaseRef",
                        bondedCustomsEvidence(prefix, "bonded-stock-release")));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "CONFIRM_DELIVERY")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 9)
                .put("deliveryConfirmationRef",
                        bondedCustomsEvidence(prefix, "domestic-delivery")));

        commands.add(bondedCustomsCommand(runId, correlationId, occurredAt,
                        "CLOSE_CASE")
                .put("caseId", ZERO_UUID)
                .put("expectedVersion", 10)
                .put("closeReason",
                        "TRIPLE_MATCHED_CUSTOMS_ACCEPTED_BONDED_RELEASED_DELIVERED"));
        return input;
    }

    private static ObjectNode bondedCustomsCommand(
            String runId, String correlationId, String occurredAt, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", "pending-bonded-customs-command")
                .put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt);
    }

    private static String bondedCustomsEvidence(String prefix, String purpose) {
        return "evidence:bonded-customs/sha256/"
                + DigestUtil.sha256Hex(prefix + ":" + purpose);
    }

    private static ObjectNode partnerMarketingKolMediaOperations(
            ObjectNode fullChain, String prefix, String occurredAt, ObjectNode consumer,
            String operatorPrincipalId, String reviewerPrincipalId,
            String financePrincipalId) {
        String correlationId = stableUuid(prefix + ":partner-marketing-correlation");
        String caseId = stableUuid(prefix + ":partner-marketing-case");
        String runId = prefix + "-partner-marketing";
        String attributionSourceRef = "persona:" + prefix;

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("operatorPrincipalId", operatorPrincipalId);
        input.put("independentReviewerPrincipalId", reviewerPrincipalId);
        input.put("financePrincipalId", financePrincipalId);
        input.putObject("runIds")
                .put("product", prefix + "-product")
                .put("campaign", prefix + "-campaign")
                .put("consumer", prefix + "-consumer")
                .put("partnerMarketing", runId);
        input.set("product", productToListing(fullChain, prefix));
        input.set("campaign", promotionCampaign(prefix, occurredAt, operatorPrincipalId));
        input.set("consumer", consumer);

        ArrayNode commands = input.putArray("partnerMarketingCommands");
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "OPEN_CANDIDATE_CASE")
                .set("candidateCase", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("caseCode", "AI-KOL-" + prefix.toUpperCase(Locale.ROOT))
                        .put("creatorPrincipalId", operatorPrincipalId)
                        .put("candidateHandle", "@cloudmold_creator_" + prefix)
                        .put("platformCode", "INSTAGRAM")
                        .put("regionCode", "US")
                        .put("categoryCode", "APPAREL")
                        .put("reasonCode", "AI_DAILY_CREATOR_DISCOVERY")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "QUALIFY_CANDIDATE")
                .set("qualification", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 1)
                        .put("riskLevel", "LOW")
                        .put("riskDecision", "PASS")
                        .put("riskEvidenceSha256",
                                partnerMarketingEvidence(prefix, "candidate-risk-screen"))
                        .put("qualificationNote",
                                "历史内容、受众地区、品牌安全和广告披露能力均通过受控检查")
                        .put("reasonCode", "AI_BRAND_AND_COMPLIANCE_SCREEN")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "START_OUTREACH")
                .set("outreach", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 2)
                        .put("outreachChannelCode", "INSTAGRAM_DM")
                        .put("outreachExternalRef", "outreach:" + prefix)
                        .put("reasonCode", "AI_CREATOR_OUTREACH")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "SUBMIT_BRIEF")
                .set("brief", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 3)
                        .put("campaignId", ZERO_UUID)
                        .put("listingId", ZERO_UUID)
                        .put("cooperationModel", "FIXED_FEE")
                        .put("budgetAmountMinor", 10_000)
                        .put("currencyCode", "CNY")
                        .put("briefSummary",
                                "新品开箱、尺码体验、本地化表达、广告披露与站内追踪链接")
                        .put("briefEvidenceSha256",
                                partnerMarketingEvidence(prefix, "cooperation-brief"))
                        .put("reasonCode", "AI_CREATOR_BRIEF_SUBMIT")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        reviewerPrincipalId, "APPROVE_BRIEF")
                .set("brief", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 4)
                        .put("approvalEvidenceSha256",
                                partnerMarketingEvidence(prefix, "brief-approval"))
                        .put("reasonCode", "AI_BRIEF_APPROVED")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "SUBMIT_CONTENT")
                .set("content", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 5)
                        .put("contentSummary",
                                "本地化新品体验内容，包含材质、尺码、穿搭和付费合作披露")
                        .put("contentEvidenceSha256",
                                partnerMarketingEvidence(prefix, "creator-content"))
                        .put("reasonCode", "AI_CREATOR_CONTENT_SUBMIT")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        reviewerPrincipalId, "APPROVE_CONTENT")
                .set("content", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 6)
                        .put("approvalEvidenceSha256",
                                partnerMarketingEvidence(prefix, "content-approval"))
                        .put("reasonCode", "AI_CONTENT_BRAND_COMPLIANCE_APPROVED")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        reviewerPrincipalId, "VERIFY_PUBLICATION_DISCLOSURE")
                .set("publication", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 7)
                        .put("externalPublishRef", "publication:" + prefix)
                        .put("externalPublishUrl",
                                "https://creator.example/cloudmold/" + prefix)
                        .put("disclosureLabel", "PAID_PARTNERSHIP")
                        .put("publishEvidenceSha256",
                                partnerMarketingEvidence(prefix, "publication-disclosure"))
                        .put("reasonCode", "AI_DISCLOSURE_VERIFIED")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "RECONCILE_ATTRIBUTION")
                .set("attribution", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 8)
                        .put("attributedOrderCount", 1)
                        .put("attributedOrderId", ZERO_UUID)
                        .put("attributedPaymentId", ZERO_UUID)
                        .put("attributionSourceRef", attributionSourceRef)
                        .put("grossSettlementAmountMinor", 10_000)
                        .put("platformFeeAmountMinor", 0)
                        .put("taxWithholdingAmountMinor", 1_000)
                        .put("netPayableAmountMinor", 9_000)
                        .put("attributionEvidenceSha256",
                                partnerMarketingEvidence(prefix, "paid-order-attribution"))
                        .put("reasonCode", "AI_ATTRIBUTION_RECONCILED")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        reviewerPrincipalId, "APPROVE_SETTLEMENT")
                .set("settlementApproval", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 9)
                        .put("approvedNetPayableAmountMinor", 9_000)
                        .put("approvalEvidenceSha256",
                                partnerMarketingEvidence(prefix, "settlement-approval"))
                        .put("reasonCode", "AI_SETTLEMENT_APPROVED")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        financePrincipalId, "MARK_SETTLEMENT_PAID")
                .set("settlementPayment", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 10)
                        .put("paidNetPayableAmountMinor", 9_000)
                        .put("settlementReference", "settlement:" + prefix)
                        .put("paymentEvidenceSha256",
                                partnerMarketingEvidence(prefix, "settlement-payment"))
                        .put("reasonCode", "AI_SETTLEMENT_PAID")));
        commands.add(partnerMarketingCommand(runId, correlationId, occurredAt,
                        operatorPrincipalId, "CLOSE_CASE")
                .set("caseClose", JsonNodeFactory.instance.objectNode()
                        .put("caseId", caseId)
                        .put("expectedVersion", 11)
                        .put("closureEvidenceSha256",
                                partnerMarketingEvidence(prefix, "case-closure"))
                        .put("reasonCode", "AI_PARTNER_MARKETING_CLOSED")));

        ArrayNode community = input.putArray("communityCommands");
        ArrayNode iv = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < 12; index++) iv.add(index + 1);
        ArrayNode ciphertext = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < 24; index++) ciphertext.add(index + 11);
        ObjectNode communityDraft = commonCommand(
                prefix + "-partner-content-create", runId, occurredAt, correlationId)
                .put("contentId", stableUuid(prefix + ":partner-content"))
                .put("authorPrincipalId", operatorPrincipalId)
                .put("contentType", "POST")
                .put("bodyRef", "restricted:partner_content_" + prefix)
                .put("bodyKeyId", "synthetic-key-v1")
                .put("bodyDigestSha256",
                        partnerMarketingEvidence(prefix, "community-content"))
                .put("canonicalSpuId", ZERO_UUID)
                .put("canonicalSkuId", ZERO_UUID)
                .put("listingId", ZERO_UUID)
                .put("listingOfferId", ZERO_UUID)
                .put("desiredStatus", "DRAFT");
        communityDraft.set("bodyIv", iv);
        communityDraft.set("bodyCiphertext", ciphertext);
        community.add(communityDraft);
        community.add(commonCommand(
                        prefix + "-partner-content-submit", runId, occurredAt, correlationId)
                .put("contentId", ZERO_UUID)
                .put("actorPrincipalId", operatorPrincipalId)
                .put("desiredStatus", "PENDING_MODERATION")
                .put("reasonCode", "BRAND_COMPLIANCE_REVIEW"));
        community.add(commonCommand(
                        prefix + "-partner-content-publish", runId, occurredAt, correlationId)
                .put("contentId", ZERO_UUID)
                .put("actorPrincipalId", reviewerPrincipalId)
                .put("desiredStatus", "PUBLISHED")
                .put("reasonCode", "AI_MODERATION_PASSED"));
        return input;
    }

    private static ObjectNode partnerMarketingCommand(
            String runId, String correlationId, String occurredAt,
            String actorPrincipalId, String operation) {
        return JsonNodeFactory.instance.objectNode()
                .put("operation", operation)
                .put("idempotencyKey", "pending-partner-marketing-command")
                .put("runId", runId)
                .put("correlationId", correlationId)
                .put("occurredAt", occurredAt)
                .put("actorPrincipalId", actorPrincipalId);
    }

    private static String partnerMarketingEvidence(String prefix, String purpose) {
        return DigestUtil.sha256Hex(prefix + ":partner-marketing:" + purpose);
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
        consumer.put("traceId", runId);
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

    private record CatalogProcurementLine(String canonicalSkuId, String baseUomCode) {
        private boolean usable() {
            return canonicalSkuId != null && !canonicalSkuId.isBlank()
                    && baseUomCode != null && baseUomCode.matches("[A-Z][A-Z0-9_-]{0,63}");
        }
    }

    private record ProcurementSourcingSeed(
            String legalEntityId, String supplierAId, String supplierBId,
            ValuationPolicySeed firstValuationPolicy,
            ValuationPolicySeed secondValuationPolicy, String creatorPrincipalId,
            String reviewerAPrincipalId, String reviewerBPrincipalId,
            String approverPrincipalId) {
        private boolean usable() {
            return legalEntityId != null && !legalEntityId.isBlank()
                    && distinctNonBlank(supplierAId, supplierBId)
                    && firstValuationPolicy != null && firstValuationPolicy.usable()
                    && secondValuationPolicy != null && secondValuationPolicy.usable()
                    && !firstValuationPolicy.canonicalSkuId()
                    .equals(secondValuationPolicy.canonicalSkuId())
                    && distinctNonBlank(creatorPrincipalId, reviewerAPrincipalId,
                    reviewerBPrincipalId, approverPrincipalId);
        }

        private boolean matches(CatalogProcurementLine firstLine,
                                CatalogProcurementLine secondLine) {
            return firstLine != null && secondLine != null
                    && valuationPolicyFor(firstLine.canonicalSkuId()) != null
                    && valuationPolicyFor(secondLine.canonicalSkuId()) != null;
        }

        private ValuationPolicySeed valuationPolicyFor(String canonicalSkuId) {
            if (firstValuationPolicy.canonicalSkuId().equals(canonicalSkuId)) {
                return firstValuationPolicy;
            }
            return secondValuationPolicy.canonicalSkuId().equals(canonicalSkuId)
                    ? secondValuationPolicy : null;
        }
    }

    private record ValuationPolicySeed(
            String canonicalSkuId, String valuationPolicyId,
            String valuationPolicyVersion, String valuationPolicyHash) {
        private boolean usable() {
            return java.util.stream.Stream.of(
                    canonicalSkuId, valuationPolicyId, valuationPolicyVersion)
                    .allMatch(value -> value != null && !value.isBlank())
                    && valuationPolicyHash != null
                    && valuationPolicyHash.matches("[0-9a-f]{64}");
        }
    }

    private record QuotationIds(
            String quotation, String revision, String line1, String line2,
            String schedule11, String schedule12, String schedule21, String schedule22,
            String suffix) {
    }

    private static boolean distinctNonBlank(String... values) {
        return values != null && values.length > 0
                && java.util.Arrays.stream(values).allMatch(value -> value != null && !value.isBlank())
                && java.util.Arrays.stream(values).distinct().count() == values.length;
    }
}
