package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedWorkflowOutboxRouteCatalogTest {

    @Test
    void mapsCanonicalEventsToExactReadOnlyWorkflowInputs() {
        AutomationOutboxEventRecord order = event(
                "order.status.changed", "order", "order-1",
                """
                {"order_id":"order-1","current_status":"COMPLETED"}
                """);
        AutomationOutboxEventRecord payment = event(
                "payment.status.changed", "payment", "payment-1",
                """
                {"order_id":"order-1","payment_id":"payment-1","current_status":"CAPTURED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-payment");
        AutomationOutboxEventRecord delivery = event(
                "engagement.notification.delivery_status_changed",
                "notification_delivery", "delivery-1",
                """
                {"campaign_id":"campaign-1","delivery_id":"delivery-1","current_status":"DELIVERED"}
                """);

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.commerce.order-to-cash-readback.v1", order).inputJson())
                .isEqualTo("{\"orderId\":\"order-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.payment.reconciliation-readback.v1", payment).inputJson())
                .isEqualTo("{\"orderId\":\"order-1\",\"paymentId\":\"payment-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.engagement.promotion-campaign-readback.v1", delivery).inputJson())
                .isEqualTo("{\"campaignId\":\"campaign-1\",\"deliveryId\":\"delivery-1\"}");
    }

    @Test
    void neverRoutesOutboxFactsIntoWriteWorkflows() {
        assertThat(ManagedWorkflowOutboxRouteCatalog.eventTypes(
                "skill.cloudmold.commerce.product-to-listing.v1")).isEmpty();
        assertThat(ManagedWorkflowOutboxRouteCatalog.eventTypes(
                "skill.cloudmold.supply-planning.prepare.v1")).isEmpty();
        assertThat(ManagedWorkflowOutboxRouteCatalog.eventTypes(
                "skill.cloudmold.commerce.aftersale-saga.v1")).isEmpty();
    }

    @Test
    void skipsEventsThatCannotProduceRequiredBusinessKeys() {
        AutomationOutboxEventRecord exception = event(
                "fulfillment.exception.status.changed", "fulfillment_exception",
                "exception-1", "{\"current_status\":\"OPEN\"}");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.commerce.fulfillment-exception-readback.v1", exception))
                .isNull();
    }

    @Test
    void routesCustomerResolutionOnlyFromCanonicalCustomerServiceFacts() {
        AutomationOutboxEventRecord closed = event(
                "customer_service.ticket.status_changed", "customer_service_ticket", "ticket-1",
                """
                {"ticket_id":"ticket-1","current_status":"CLOSED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-customer-service");
        AutomationOutboxEventRecord forged = event(
                "customer_service.ticket.status_changed", "customer_service_ticket", "ticket-2",
                """
                {"ticket_id":"ticket-2","current_status":"CLOSED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-crm");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.customer-service.resolution-readback.v1", closed))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("ticket-1", "{\"ticketId\":\"ticket-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.customer-service.resolution-readback.v1", forged)).isNull();
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.customer-service.resolution-readback.v1")).isEqualTo("route-v2");
    }

    @Test
    void rejectsNonCanonicalPaymentAndRiskFacts() {
        AutomationOutboxEventRecord payment = event(
                "payment.status.changed", "payment", "payment-1",
                """
                {"order_id":"order-1","payment_id":"payment-1"}
                """)
                .setSchemaVersion(3)
                .setSourceSystem("cloudmold-payment");
        AutomationOutboxEventRecord dispute = event(
                "risk.payment_dispute.status_changed", "risk_payment_dispute", "dispute-1",
                """
                {"dispute_id":"dispute-1","current_status":"RESOLVED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-risk");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.payment.reconciliation-readback.v1", payment)).isNull();
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.risk.dispute-readback.v1", dispute)).isNull();
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.payment.reconciliation-readback.v1")).isEqualTo("route-v2");
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.risk.dispute-readback.v1")).isEqualTo("route-v2");
    }

    @Test
    void onlyRoutesCatalogSkuAfterItBecomesActive() {
        AutomationOutboxEventRecord active = event(
                "catalog.entity.status_changed", "catalog_entity", "sku-1",
                "{\"entity_type\":\"SKU\",\"entity_id\":\"sku-1\",\"current_status\":\"ACTIVE\"}")
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-catalog");
        AutomationOutboxEventRecord draft = event(
                "catalog.entity.status_changed", "catalog_entity", "sku-1",
                "{\"entity_type\":\"SKU\",\"entity_id\":\"sku-1\",\"current_status\":\"DRAFT\"}")
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-catalog");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.catalog.inspect-active-sku.v1", active).inputJson())
                .isEqualTo("{\"skuId\":\"sku-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.catalog.inspect-active-sku.v1", draft)).isNull();
    }

    @Test
    void routesStockoutDiagnosisOnlyAfterCanonicalSpuActivation() {
        AutomationOutboxEventRecord active = event(
                "catalog.entity.status_changed", "catalog_entity", "spu-1",
                "{\"entity_type\":\"SPU\",\"entity_id\":\"spu-1\",\"current_status\":\"ACTIVE\"}")
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-catalog");
        AutomationOutboxEventRecord draft = event(
                "catalog.entity.status_changed", "catalog_entity", "spu-1",
                "{\"entity_type\":\"SPU\",\"entity_id\":\"spu-1\",\"current_status\":\"DRAFT\"}")
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-catalog");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.inventory.stockout-diagnosis.v1", active))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("spu-1",
                        "{\"canonicalSpuId\":\"spu-1\",\"lowStockThreshold\":null}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.inventory.stockout-diagnosis.v1", draft)).isNull();
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.inventory.stockout-diagnosis.v1")).isEqualTo("route-v2");
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.listing.lifecycle-readback.v1")).isEqualTo("route-v2");
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.merchant.onboarding-readback.v1")).isEqualTo("route-v2");
    }

    @Test
    void materializesOneAssortmentReadinessObjectivePerWave() {
        AutomationOutboxEventRecord sku = event(
                "catalog.sku.defined", "catalog_sku", "sku-1",
                """
                {"planning_year":2026,"season_code":"AW","wave_code":"W1"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-catalog");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.catalog.assortment-wave-readiness.v1", sku))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("2026/AW/W1",
                        "{\"planningYear\":2026,\"seasonCode\":\"AW\",\"waveCode\":\"W1\"}");
    }

    @Test
    void routesCanonicalListingAndEligibilityLifecycleEvents() {
        AutomationOutboxEventRecord listing = event(
                "listing.channel_publish.confirmed", "listing", "listing-1",
                "{\"listing_id\":\"listing-1\",\"outcome\":\"CONFIRMED_PUBLISHED\"}")
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-listing");
        AutomationOutboxEventRecord enforcement = event(
                "listing.sales_eligibility_enforcement.status_changed",
                "listing_sales_eligibility_enforcement", "saga-1",
                "{\"saga_id\":\"saga-1\",\"current_status\":\"COMPLETED\"}")
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-listing");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.listing.lifecycle-readback.v1", listing))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("LISTING/listing-1",
                        "{\"entityType\":\"LISTING\",\"businessId\":\"listing-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.listing.lifecycle-readback.v1", enforcement))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("ELIGIBILITY_ENFORCEMENT/saga-1",
                        "{\"entityType\":\"ELIGIBILITY_ENFORCEMENT\","
                                + "\"businessId\":\"saga-1\"}");
    }

    @Test
    void routesOnlyGovernedTransferConversionsToMovementReadback() {
        AutomationOutboxEventRecord transfer = event(
                "supply_planning.replenishment.converted",
                "replenishment_recommendation", "recommendation-1",
                """
                {"recommendation_id":"recommendation-1","target_type":"TRANSFER_REQUEST",
                 "business_object_type":"MOVEMENT_ORDER","business_object_id":"991"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-supply-planning");
        AutomationOutboxEventRecord purchase = event(
                "supply_planning.replenishment.converted",
                "replenishment_recommendation", "recommendation-2",
                """
                {"recommendation_id":"recommendation-2","target_type":"PURCHASE_REQUEST",
                 "business_object_type":"procurement_order","business_object_id":"order-1"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-supply-planning");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.allocation-transfer-readback.v1", transfer))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("MOVEMENT_ORDER/991", "{\"movementOrderId\":991}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.allocation-transfer-readback.v1", purchase))
                .isNull();
    }

    @Test
    void routesOnlyGovernedPurchaseConversionsToInboundReadback() {
        AutomationOutboxEventRecord purchase = event(
                "supply_planning.replenishment.converted",
                "replenishment_recommendation", "recommendation-1",
                """
                {"recommendation_id":"recommendation-1","target_type":"PURCHASE_REQUEST",
                 "business_object_type":"procurement_order","business_object_id":"order-1"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-supply-planning");
        AutomationOutboxEventRecord transfer = event(
                "supply_planning.replenishment.converted",
                "replenishment_recommendation", "recommendation-2",
                """
                {"recommendation_id":"recommendation-2","target_type":"TRANSFER_REQUEST",
                 "business_object_type":"MOVEMENT_ORDER","business_object_id":"991"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-supply-planning");
        AutomationOutboxEventRecord spoofed = event(
                "supply_planning.replenishment.converted",
                "replenishment_recommendation", "recommendation-3",
                """
                {"recommendation_id":"recommendation-3","target_type":"PURCHASE_REQUEST",
                 "business_object_type":"procurement_order","business_object_id":"order-3"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("untrusted-source");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.inbound-readback.v1", purchase))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("recommendation-1", "{\"recommendationId\":\"recommendation-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.inbound-readback.v1", transfer))
                .isNull();
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.inbound-readback.v1", spoofed))
                .isNull();
    }

    @Test
    void routesConfirmedReplenishmentProcurementToInboundReadback() {
        AutomationOutboxEventRecord confirmed = event(
                "procurement.order.supplier_confirmed",
                "procurement_order", "order-1",
                """
                {"order_id":"order-1","source_business_type":"REPLENISHMENT",
                 "source_business_ref":"recommendation-1",
                 "current_status":"SUPPLIER_CONFIRMED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-procurement");
        AutomationOutboxEventRecord unrelated = event(
                "procurement.order.supplier_confirmed",
                "procurement_order", "order-2",
                """
                {"order_id":"order-2","source_business_type":"MANUAL",
                 "source_business_ref":"manual-1",
                 "current_status":"SUPPLIER_CONFIRMED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-procurement");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.inbound-readback.v1", confirmed))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("recommendation-1", "{\"recommendationId\":\"recommendation-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.warehouse.inbound-readback.v1", unrelated))
                .isNull();
    }

    @Test
    void routesCanonicalProcurementCreationToSupplierConfirmationReadback() {
        AutomationOutboxEventRecord created = event(
                "procurement.order.created", "procurement_order", "procurement-1",
                """
                {"order_id":"procurement-1","supplier_ref":"ERP_SUPPLIER:3",
                 "current_status":"CREATED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-procurement");
        AutomationOutboxEventRecord missingSupplier = event(
                "procurement.order.created", "procurement_order", "procurement-2",
                """
                {"order_id":"procurement-2","current_status":"CREATED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-procurement");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.procurement.supplier-confirmation-readback.v1", created))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("procurement-1",
                        "{\"procurementOrderId\":\"procurement-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.procurement.supplier-confirmation-readback.v1",
                missingSupplier)).isNull();
    }

    @Test
    void routesCanonicalRfqCreationToSupplierSourcingDecisionReadback() {
        AutomationOutboxEventRecord created = event(
                "supplier.sourcing_case.created", "supplier_sourcing_case", "sourcing-1",
                """
                {"sourcing_case_id":"sourcing-1","rfq_code":"RFQ-2026-001",
                 "request_ref":"assortment-wave-1","current_status":"OPEN"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-supplier");
        AutomationOutboxEventRecord wrongSource = event(
                "supplier.sourcing_case.created", "supplier_sourcing_case", "sourcing-2",
                """
                {"sourcing_case_id":"sourcing-2","rfq_code":"RFQ-2026-002",
                 "request_ref":"assortment-wave-2","current_status":"OPEN"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-erp");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.supplier.sourcing-decision-readback.v1", created))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("sourcing-1", "{\"sourcingCaseId\":\"sourcing-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.supplier.sourcing-decision-readback.v1", wrongSource)).isNull();
    }

    @Test
    void routesCanonicalAccountingPeriodToFinanceCloseReadback() {
        AutomationOutboxEventRecord opened = event(
                "finance.accounting_period.opened", "finance_accounting_period", "period-2026-07",
                """
                {"period_id":"period-2026-07","period_code":"2026-07",
                 "currency_code":"CNY","current_status":"OPEN"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-finance");
        AutomationOutboxEventRecord wrongSource = event(
                "finance.accounting_period.opened", "finance_accounting_period", "period-2026-08",
                """
                {"period_id":"period-2026-08","period_code":"2026-08",
                 "currency_code":"CNY","current_status":"OPEN"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-erp");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.finance.close-readiness.v1", opened))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("period-2026-07", "{\"closeKey\":\"period-2026-07\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.finance.close-readiness.v1", wrongSource)).isNull();
    }

    @Test
    void routesCanonicalGrowthExperimentToDailyReadback() {
        AutomationOutboxEventRecord created = event(
                "promotion.growth_experiment.created", "promotion_growth_experiment", "experiment-1",
                """
                {"experiment_id":"experiment-1","experiment_code":"EXP-1",
                 "campaign_id":"campaign-1","status":"DRAFT"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-promotion");
        AutomationOutboxEventRecord wrongSource = event(
                "promotion.growth_experiment.created", "promotion_growth_experiment", "experiment-2",
                """
                {"experiment_id":"experiment-2","experiment_code":"EXP-2"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-cdp");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.engagement.growth-experiment-readback.v1", created))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("experiment-1", "{\"experimentId\":\"experiment-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.engagement.growth-experiment-readback.v1", wrongSource)).isNull();
    }

    @Test
    void routesOnlyCanonicalMerchantOnboardingEvents() {
        AutomationOutboxEventRecord approved = event(
                "merchant.onboarding.status_changed", "MERCHANT_ONBOARDING", "application-1",
                """
                {"application_id":"application-1","current_status":"APPROVED",
                 "merchant_id":"merchant-1","shop_id":"shop-1"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-merchant");
        AutomationOutboxEventRecord wrongSource = event(
                "merchant.onboarding.status_changed", "MERCHANT_ONBOARDING", "application-2",
                """
                {"application_id":"application-2","current_status":"APPROVED"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-merchant");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.merchant.onboarding-readback.v1", approved))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("application-1", "{\"applicationId\":\"application-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.merchant.onboarding-readback.v1", wrongSource)).isNull();
    }

    @Test
    void routesOnlyCanonicalQualityRecallEvents() {
        AutomationOutboxEventRecord opened = event(
                "quality.recall_action.opened", "quality_recall_action", "recall-1",
                """
                {"recall_action_id":"recall-1","current_status":"OPEN"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("cloudmold-quality");
        AutomationOutboxEventRecord wrongSource = event(
                "quality.recall_action.opened", "quality_recall_action", "recall-2",
                """
                {"recall_action_id":"recall-2","current_status":"OPEN"}
                """)
                .setSchemaVersion(1)
                .setSourceSystem("legacy-qms");

        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.quality.recall-readback.v1", opened))
                .extracting(ManagedWorkflowOutboxRouteCatalog.MaterializedInput::businessKey,
                        ManagedWorkflowOutboxRouteCatalog.MaterializedInput::inputJson)
                .containsExactly("recall-1", "{\"recallActionId\":\"recall-1\"}");
        assertThat(ManagedWorkflowOutboxRouteCatalog.materialize(
                "skill.cloudmold.quality.recall-readback.v1", wrongSource)).isNull();
        assertThat(ManagedWorkflowOutboxRouteCatalog.routeVersion(
                "skill.cloudmold.quality.recall-readback.v1")).isEqualTo("route-v2");
    }

    private static AutomationOutboxEventRecord event(
            String eventType, String aggregateType, String aggregateId, String payload) {
        return new AutomationOutboxEventRecord()
                .setTenantId(162L)
                .setEventId("event-1")
                .setEventType(eventType)
                .setAggregateType(aggregateType)
                .setAggregateId(aggregateId)
                .setPayload(payload);
    }
}
