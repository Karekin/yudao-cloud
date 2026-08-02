package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import cn.hutool.crypto.digest.DigestUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps canonical domain events to safe, read-only managed workflow inputs.
 *
 * <p>Write workflows are intentionally absent. An Outbox fact may automatically
 * start diagnosis or terminal verification, but it must never fabricate a write
 * command, approval reference, commercial amount, or authority binding.</p>
 */
final class ManagedWorkflowOutboxRouteCatalog {

    private static final Map<String, Set<String>> ROUTES = Map.ofEntries(
            Map.entry("skill.cloudmold.catalog.inspect-active-sku.v1",
                    Set.of("catalog.entity.status_changed")),
            Map.entry("skill.cloudmold.inventory.stockout-diagnosis.v1",
                    Set.of("catalog.entity.status_changed")),
            Map.entry("skill.cloudmold.catalog.assortment-wave-readiness.v1",
                    Set.of("catalog.sku.defined", "catalog.sku.upserted")),
            Map.entry("skill.cloudmold.commerce.order-to-cash-readback.v1",
                    Set.of("order.status.changed")),
            Map.entry("skill.cloudmold.commerce.order-cancellation-readback.v1",
                    Set.of("order.cancellation_saga.status_changed")),
            Map.entry("skill.cloudmold.commerce.fulfillment-exception-readback.v1",
                    Set.of("fulfillment.exception.status.changed")),
            Map.entry("skill.cloudmold.commerce.return-refund-readback.v1",
                    Set.of("after_sale.status.changed")),
            Map.entry("skill.cloudmold.customer-service.resolution-readback.v1",
                    Set.of("customer_service.ticket.status_changed")),
            Map.entry("skill.cloudmold.quality.recall-readback.v1",
                    Set.of("quality.recall_action.opened",
                            "quality.recall_action.acknowledged",
                            "quality.recall_action.resolved")),
            Map.entry("skill.cloudmold.risk.dispute-readback.v1",
                    Set.of("risk.payment_dispute.status_changed")),
            Map.entry("skill.cloudmold.payment.reconciliation-readback.v1",
                    Set.of("payment.status.changed")),
            Map.entry("skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                    Set.of("procurement.order.released")),
            Map.entry("skill.cloudmold.procurement.sourcing-decision-readback.v1",
                    Set.of("procurement.award.approved")),
            Map.entry("skill.cloudmold.finance.close-readiness.v1",
                    Set.of("finance.accounting_period.opened")),
            Map.entry("skill.cloudmold.engagement.growth-experiment-readback.v1",
                    Set.of("promotion.growth_experiment.created")),
            Map.entry("skill.cloudmold.warehouse.inbound-readback.v1",
                    Set.of("procurement.order.supplier_confirmed",
                            "supply_planning.replenishment.converted")),
            Map.entry("skill.cloudmold.warehouse.allocation-transfer-readback.v1",
                    Set.of("supply_planning.replenishment.converted")),
            Map.entry("skill.cloudmold.merchant.onboarding-readback.v1",
                    Set.of("merchant.onboarding.status_changed")),
            Map.entry("skill.cloudmold.listing.lifecycle-readback.v1",
                    Set.of("listing.status.changed",
                            "listing.channel_publish.confirmed",
                            "listing.channel_publish.failed",
                            "listing.sales_eligibility_enforcement.status_changed")),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-readback.v1",
                    Set.of("engagement.notification.campaign_status_changed",
                            "engagement.notification.delivery_status_changed",
                            "engagement.notification.delivery_receipt_recorded"))
    );

    private ManagedWorkflowOutboxRouteCatalog() {
    }

    static List<String> eventTypes(String skillId) {
        Set<String> values = ROUTES.get(skillId);
        return values == null ? List.of() : values.stream().sorted().toList();
    }

    static String consumerId(String skillId, String skillVersion) {
        String readable = skillId.replace("skill.cloudmold.", "").replace(".v1", "");
        String routeVersion = routeVersion(skillId);
        String suffix = DigestUtil.sha256Hex(skillId + ":" + skillVersion + ":" + routeVersion)
                .substring(0, 12);
        String value = "aiops/" + readable + "/" + routeVersion + "/" + suffix;
        return value.length() <= 128 ? value : "aiops/" + routeVersion + "/" + suffix;
    }

    static String routeVersion(String skillId) {
        return switch (skillId) {
            case "skill.cloudmold.inventory.stockout-diagnosis.v1",
                 "skill.cloudmold.merchant.onboarding-readback.v1",
                 "skill.cloudmold.customer-service.resolution-readback.v1",
                 "skill.cloudmold.quality.recall-readback.v1",
                 "skill.cloudmold.risk.dispute-readback.v1",
                 "skill.cloudmold.payment.reconciliation-readback.v1",
                 "skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                 "skill.cloudmold.procurement.sourcing-decision-readback.v1",
                 "skill.cloudmold.warehouse.inbound-readback.v1",
                 "skill.cloudmold.listing.lifecycle-readback.v1" -> "route-v2";
            default -> "route-v1";
        };
    }

    static MaterializedInput materialize(String skillId, AutomationOutboxEventRecord event) {
        if (!eventTypes(skillId).contains(event.getEventType())) {
            return null;
        }
        JsonNode payload = JsonUtils.parseTree(event.getPayload());
        if (payload == null) {
            return null;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        String businessKey;
        switch (skillId) {
            case "skill.cloudmold.catalog.inspect-active-sku.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-catalog".equals(event.getSourceSystem())
                        || !"catalog_entity".equals(event.getAggregateType())
                        || !"SKU".equals(firstText(payload, "entity_type", null))
                        || !"ACTIVE".equals(firstText(payload, "current_status", null))) {
                    return null;
                }
                String skuId = firstText(payload, "entity_id", event.getAggregateId());
                if (blank(skuId)) {
                    return null;
                }
                input.put("skuId", skuId);
                businessKey = skuId;
            }
            case "skill.cloudmold.catalog.assortment-wave-readiness.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-catalog".equals(event.getSourceSystem())
                        || !"catalog_sku".equals(event.getAggregateType())) {
                    return null;
                }
                Integer planningYear = integerValue(payload, "planning_year");
                String seasonCode = firstText(payload, "season_code", null);
                String waveCode = firstText(payload, "wave_code", null);
                if (planningYear == null || blank(seasonCode) || blank(waveCode)) {
                    return null;
                }
                input.put("planningYear", planningYear);
                input.put("seasonCode", seasonCode);
                input.put("waveCode", waveCode);
                businessKey = planningYear + "/" + seasonCode + "/" + waveCode;
            }
            case "skill.cloudmold.inventory.stockout-diagnosis.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-catalog".equals(event.getSourceSystem())
                        || !"catalog_entity".equals(event.getAggregateType())
                        || !"SPU".equals(firstText(payload, "entity_type", null))
                        || !"ACTIVE".equals(firstText(payload, "current_status", null))) {
                    return null;
                }
                String canonicalSpuId = firstText(payload, "entity_id", event.getAggregateId());
                if (blank(canonicalSpuId)) {
                    return null;
                }
                input.put("canonicalSpuId", canonicalSpuId);
                // Keep the optional field present for the SkillTask template while
                // delegating the default threshold policy to Inventory.
                input.put("lowStockThreshold", NullNode.getInstance());
                businessKey = canonicalSpuId;
            }
            case "skill.cloudmold.commerce.order-to-cash-readback.v1" -> {
                String orderId = firstText(payload, "order_id", event.getAggregateId());
                if (blank(orderId)) {
                    return null;
                }
                input.put("orderId", orderId);
                businessKey = orderId;
            }
            case "skill.cloudmold.commerce.order-cancellation-readback.v1" -> {
                String sagaId = firstText(payload, "cancellation_saga_id", event.getAggregateId());
                if (blank(sagaId)) {
                    return null;
                }
                input.put("cancellationSagaId", sagaId);
                businessKey = sagaId;
            }
            case "skill.cloudmold.commerce.fulfillment-exception-readback.v1" -> {
                String orderId = firstText(payload, "order_id", null);
                if (blank(orderId)) {
                    return null;
                }
                input.put("orderId", orderId);
                businessKey = orderId;
            }
            case "skill.cloudmold.commerce.return-refund-readback.v1" -> {
                String afterSaleId = firstText(payload, "after_sale_id", event.getAggregateId());
                if (blank(afterSaleId)) {
                    return null;
                }
                input.put("afterSaleId", afterSaleId);
                businessKey = afterSaleId;
            }
            case "skill.cloudmold.customer-service.resolution-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-customer-service".equals(event.getSourceSystem())
                        || !"customer_service_ticket".equals(event.getAggregateType())) {
                    return null;
                }
                String ticketId = firstText(payload, "ticket_id", event.getAggregateId());
                if (blank(ticketId)) {
                    return null;
                }
                input.put("ticketId", ticketId);
                businessKey = ticketId;
            }
            case "skill.cloudmold.quality.recall-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-quality".equals(event.getSourceSystem())
                        || !"quality_recall_action".equals(event.getAggregateType())) {
                    return null;
                }
                String recallActionId = firstText(payload, "recall_action_id", event.getAggregateId());
                if (blank(recallActionId)) {
                    return null;
                }
                input.put("recallActionId", recallActionId);
                businessKey = recallActionId;
            }
            case "skill.cloudmold.risk.dispute-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-risk".equals(event.getSourceSystem())
                        || !"risk_payment_dispute".equals(event.getAggregateType())) {
                    return null;
                }
                String disputeId = firstText(payload, "dispute_id", event.getAggregateId());
                if (blank(disputeId)) {
                    return null;
                }
                input.put("disputeId", disputeId);
                businessKey = disputeId;
            }
            case "skill.cloudmold.payment.reconciliation-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-payment".equals(event.getSourceSystem())
                        || !"payment".equals(event.getAggregateType())) {
                    return null;
                }
                String orderId = firstText(payload, "order_id", null);
                String paymentId = firstText(payload, "payment_id", event.getAggregateId());
                if (blank(orderId) || blank(paymentId)) {
                    return null;
                }
                input.put("orderId", orderId);
                input.put("paymentId", paymentId);
                businessKey = paymentId;
            }
            case "skill.cloudmold.procurement.supplier-confirmation-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 2
                        || !"cloudmold-procurement".equals(event.getSourceSystem())
                        || !"procurement_order".equals(event.getAggregateType())
                        || event.getAggregateVersion() == null || event.getAggregateVersion() != 4L
                        || !"RELEASED".equals(firstText(payload, "current_status", null))
                        || !"SOURCING_AWARD".equals(firstText(payload, "source_business_type", null))
                        || !payload.path("items").isArray() || payload.path("items").isEmpty()) {
                    return null;
                }
                String procurementOrderId = firstText(payload, "order_id", event.getAggregateId());
                String supplierId = firstText(payload, "supplier_id", null);
                String awardId = firstText(payload, "source_business_ref", null);
                if (blank(procurementOrderId) || blank(supplierId) || blank(awardId)) {
                    return null;
                }
                input.put("procurementOrderId", procurementOrderId);
                businessKey = procurementOrderId;
            }
            case "skill.cloudmold.procurement.sourcing-decision-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 2
                        || !"cloudmold-procurement".equals(event.getSourceSystem())
                        || !"procurement_award".equals(event.getAggregateType())
                        || event.getAggregateVersion() == null || event.getAggregateVersion() != 3L) {
                    return null;
                }
                String awardId = event.getAggregateId();
                String eventId = firstText(payload, "event_id", null);
                String snapshotId = firstText(payload, "snapshot_id", null);
                if (blank(awardId) || blank(eventId)
                        || !(awardId + ":v3").equals(snapshotId)) {
                    return null;
                }
                input.put("awardId", awardId);
                businessKey = awardId;
            }
            case "skill.cloudmold.finance.close-readiness.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-finance".equals(event.getSourceSystem())
                        || !"finance_accounting_period".equals(event.getAggregateType())) {
                    return null;
                }
                String periodId = firstText(payload, "period_id", event.getAggregateId());
                String periodCode = firstText(payload, "period_code", null);
                if (blank(periodId) || blank(periodCode)) {
                    return null;
                }
                input.put("closeKey", periodId);
                businessKey = periodId;
            }
            case "skill.cloudmold.engagement.growth-experiment-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-promotion".equals(event.getSourceSystem())
                        || !"promotion_growth_experiment".equals(event.getAggregateType())) {
                    return null;
                }
                String experimentId = firstText(payload, "experiment_id", event.getAggregateId());
                String experimentCode = firstText(payload, "experiment_code", null);
                if (blank(experimentId) || blank(experimentCode)) {
                    return null;
                }
                input.put("experimentId", experimentId);
                businessKey = experimentId;
            }
            case "skill.cloudmold.warehouse.inbound-readback.v1" -> {
                String recommendationId;
                if ("supply_planning.replenishment.converted".equals(event.getEventType())) {
                    if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                            || !"cloudmold-supply-planning".equals(event.getSourceSystem())
                            || !"replenishment_recommendation".equals(event.getAggregateType())
                            || !"PURCHASE_REQUEST".equals(firstText(payload, "target_type", null))
                            || !"procurement_order".equals(firstText(payload, "business_object_type", null))) {
                        return null;
                    }
                    recommendationId = firstText(payload, "recommendation_id", null);
                } else {
                    if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                            || !"cloudmold-procurement".equals(event.getSourceSystem())
                            || !"procurement_order".equals(event.getAggregateType())
                            || !"REPLENISHMENT".equals(firstText(payload, "source_business_type", null))
                            || !"SUPPLIER_CONFIRMED".equals(firstText(payload, "current_status", null))) {
                        return null;
                    }
                    recommendationId = firstText(payload, "source_business_ref", null);
                }
                if (blank(recommendationId)) {
                    return null;
                }
                input.put("recommendationId", recommendationId);
                businessKey = recommendationId;
            }
            case "skill.cloudmold.warehouse.allocation-transfer-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-supply-planning".equals(event.getSourceSystem())
                        || !"replenishment_recommendation".equals(event.getAggregateType())
                        || !"TRANSFER_REQUEST".equals(firstText(payload, "target_type", null))
                        || !"MOVEMENT_ORDER".equals(firstText(payload, "business_object_type", null))) {
                    return null;
                }
                Long movementOrderId = positiveLong(payload, "business_object_id");
                if (movementOrderId == null) {
                    return null;
                }
                input.put("movementOrderId", movementOrderId);
                businessKey = "MOVEMENT_ORDER/" + movementOrderId;
            }
            case "skill.cloudmold.merchant.onboarding-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-merchant".equals(event.getSourceSystem())
                        || !"MERCHANT_ONBOARDING".equals(event.getAggregateType())) {
                    return null;
                }
                String applicationId = firstText(payload, "application_id", event.getAggregateId());
                if (blank(applicationId)) {
                    return null;
                }
                input.put("applicationId", applicationId);
                businessKey = applicationId;
            }
            case "skill.cloudmold.listing.lifecycle-readback.v1" -> {
                if (event.getSchemaVersion() == null || event.getSchemaVersion() != 1
                        || !"cloudmold-listing".equals(event.getSourceSystem())) {
                    return null;
                }
                boolean enforcement =
                        "listing.sales_eligibility_enforcement.status_changed"
                                .equals(event.getEventType());
                String expectedAggregateType = enforcement
                        ? "listing_sales_eligibility_enforcement" : "listing";
                if (!expectedAggregateType.equals(event.getAggregateType())) {
                    return null;
                }
                String entityType = enforcement
                        ? "ELIGIBILITY_ENFORCEMENT" : "LISTING";
                String businessId = firstText(payload,
                        enforcement ? "saga_id" : "listing_id",
                        event.getAggregateId());
                if (blank(businessId)) {
                    return null;
                }
                input.put("entityType", entityType);
                input.put("businessId", businessId);
                businessKey = entityType + "/" + businessId;
            }
            case "skill.cloudmold.engagement.promotion-campaign-readback.v1" -> {
                String campaignId = firstText(payload, "campaign_id",
                        "notification_campaign".equals(event.getAggregateType())
                                ? event.getAggregateId() : null);
                if (blank(campaignId)) {
                    return null;
                }
                input.put("campaignId", campaignId);
                String deliveryId = firstText(payload, "delivery_id",
                        "notification_delivery".equals(event.getAggregateType())
                                ? event.getAggregateId() : null);
                if (!blank(deliveryId)) {
                    input.put("deliveryId", deliveryId);
                }
                businessKey = blank(deliveryId) ? campaignId : campaignId + "/" + deliveryId;
            }
            default -> {
                return null;
            }
        }
        return new MaterializedInput(businessKey, JsonUtils.toJsonString(input));
    }

    private static String firstText(JsonNode payload, String field, String defaultValue) {
        JsonNode value = payload.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText() : defaultValue;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Integer integerValue(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value != null && value.canConvertToInt() ? value.intValue() : null;
    }

    private static Long positiveLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            long parsed = value.isIntegralNumber()
                    ? value.longValue() : Long.parseLong(value.asText());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record MaterializedInput(String businessKey, String inputJson) {
    }
}
