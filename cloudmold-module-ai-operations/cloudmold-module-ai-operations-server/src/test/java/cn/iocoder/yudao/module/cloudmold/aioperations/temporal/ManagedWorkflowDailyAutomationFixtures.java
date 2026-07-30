package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class ManagedWorkflowDailyAutomationFixtures {

    static final List<String> MANAGED_SKILL_IDS = List.of(
            "skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
            "skill.cloudmold.catalog.inspect-active-sku.v1",
            "skill.cloudmold.catalog.assortment-wave-readiness.v1",
            "skill.cloudmold.commerce.aftersale-saga.v1",
            "skill.cloudmold.commerce.autonomous-day.v1",
            "skill.cloudmold.commerce.category-daily-operations.v1",
            "skill.cloudmold.commerce.catalog-matrix.v1",
            "skill.cloudmold.commerce.fulfillment-exception-readback.v1",
            "skill.cloudmold.commerce.full-chain-hsf.v1",
            "skill.cloudmold.commerce.legacy-projection-plan.v1",
            "skill.cloudmold.commerce.order-cancellation-operational.v1",
            "skill.cloudmold.commerce.order-cancellation-readback.v1",
            "skill.cloudmold.commerce.order-to-cash-readback.v1",
            "skill.cloudmold.commerce.product-to-listing.v1",
            "skill.cloudmold.commerce.return-refund-readback.v1",
            "skill.cloudmold.commerce.reuse-ready-master.v1",
            "skill.cloudmold.commerce.terminal-readback.v1",
            "skill.cloudmold.customer-service.resolution-readback.v1",
            "skill.cloudmold.consumer.in-transit-order-scenario.v1",
            "skill.cloudmold.consumer.paid-unshipped-order-scenario.v1",
            "skill.cloudmold.consumer.shopping-journey.v1",
            "skill.cloudmold.engagement.promotion-campaign-operations.v1",
            "skill.cloudmold.engagement.growth-experiment-readback.v1",
            "skill.cloudmold.engagement.promotion-campaign-readback.v1",
            "skill.cloudmold.finance.close-readiness.v1",
            "skill.cloudmold.finance.close-lifecycle.v1",
            "skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1",
            "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
            "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
            "skill.cloudmold.partner-marketing.kol-media-operations.v1",
            "skill.cloudmold.mes.production-readiness.v1",
            "skill.cloudmold.mes.production-execution-lifecycle.v1",
            "skill.cloudmold.inventory.stockout-diagnosis.v1",
            "skill.cloudmold.listing.lifecycle-readback.v1",
            "skill.cloudmold.merchant.onboarding-lifecycle.v1",
            "skill.cloudmold.merchant.onboarding-readback.v1",
            "skill.cloudmold.growth.experiment-lifecycle.v1",
            "skill.cloudmold.operations.daily-business-control.v1",
            "skill.cloudmold.operations.weekly-business-review.v1",
            "skill.cloudmold.payment.reconciliation-readback.v1",
            "skill.cloudmold.procurement.supplier-confirmation-readback.v1",
            "skill.cloudmold.procurement.order-lifecycle.v1",
            "skill.cloudmold.supplier.sourcing-decision-readback.v1",
            "skill.cloudmold.supplier.sourcing-lifecycle.v1",
            "skill.cloudmold.quality.inspection-recall-lifecycle.v1",
            "skill.cloudmold.quality.recall-readback.v1",
            "skill.cloudmold.risk.dispute-readback.v1",
            "skill.cloudmold.supply-planning.prepare.v1",
            "skill.cloudmold.warehouse.allocation-transfer-readback.v1",
            "skill.cloudmold.warehouse.inbound-readback.v1"
            ,"skill.cloudmold.wms.operations.v1"
            ,"skill.cloudmold.supply.replenishment-lifecycle.v1"
            ,"skill.cloudmold.supply-planning.sop-lifecycle.v1"
            ,"skill.cloudmold.customer-service.resolution-lifecycle.v1"
    );

    static final Set<String> APPROVAL_SKILL_IDS = Set.of(
            "skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
            "skill.cloudmold.commerce.aftersale-saga.v1",
            "skill.cloudmold.commerce.autonomous-day.v1",
            "skill.cloudmold.commerce.category-daily-operations.v1",
            "skill.cloudmold.commerce.catalog-matrix.v1",
            "skill.cloudmold.commerce.full-chain-hsf.v1",
            "skill.cloudmold.commerce.order-cancellation-operational.v1",
            "skill.cloudmold.commerce.product-to-listing.v1",
            "skill.cloudmold.commerce.reuse-ready-master.v1",
            "skill.cloudmold.consumer.shopping-journey.v1",
            "skill.cloudmold.engagement.promotion-campaign-operations.v1",
            "skill.cloudmold.growth.experiment-lifecycle.v1",
            "skill.cloudmold.merchant.onboarding-lifecycle.v1",
            "skill.cloudmold.procurement.order-lifecycle.v1",
            "skill.cloudmold.supplier.sourcing-lifecycle.v1",
            "skill.cloudmold.supply-planning.prepare.v1"
            ,"skill.cloudmold.wms.operations.v1"
            ,"skill.cloudmold.supply.replenishment-lifecycle.v1"
            ,"skill.cloudmold.supply-planning.sop-lifecycle.v1"
            ,"skill.cloudmold.customer-service.resolution-lifecycle.v1"
            ,"skill.cloudmold.finance.close-lifecycle.v1"
            ,"skill.cloudmold.quality.inspection-recall-lifecycle.v1"
            ,"skill.cloudmold.consumer.in-transit-order-scenario.v1"
            ,"skill.cloudmold.consumer.paid-unshipped-order-scenario.v1"
            ,"skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1"
            ,"skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1"
            ,"skill.cloudmold.crossborder.bonded-customs-lifecycle.v1"
            ,"skill.cloudmold.partner-marketing.kol-media-operations.v1"
            ,"skill.cloudmold.mes.production-readiness.v1"
            ,"skill.cloudmold.mes.production-execution-lifecycle.v1"
    );

    private static final Set<String> R3_SKILL_IDS = Set.of(
            "skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
            "skill.cloudmold.commerce.aftersale-saga.v1",
            "skill.cloudmold.commerce.autonomous-day.v1",
            "skill.cloudmold.commerce.category-daily-operations.v1",
            "skill.cloudmold.commerce.full-chain-hsf.v1",
            "skill.cloudmold.commerce.order-cancellation-operational.v1",
            "skill.cloudmold.commerce.product-to-listing.v1",
            "skill.cloudmold.consumer.shopping-journey.v1",
            "skill.cloudmold.merchant.onboarding-lifecycle.v1",
            "skill.cloudmold.procurement.order-lifecycle.v1",
            "skill.cloudmold.supplier.sourcing-lifecycle.v1"
            ,"skill.cloudmold.wms.operations.v1"
            ,"skill.cloudmold.supply.replenishment-lifecycle.v1"
            ,"skill.cloudmold.supply-planning.sop-lifecycle.v1"
            ,"skill.cloudmold.finance.close-lifecycle.v1"
            ,"skill.cloudmold.quality.inspection-recall-lifecycle.v1"
            ,"skill.cloudmold.consumer.in-transit-order-scenario.v1"
            ,"skill.cloudmold.consumer.paid-unshipped-order-scenario.v1"
            ,"skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1"
            ,"skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1"
            ,"skill.cloudmold.crossborder.bonded-customs-lifecycle.v1"
            ,"skill.cloudmold.partner-marketing.kol-media-operations.v1"
            ,"skill.cloudmold.mes.production-execution-lifecycle.v1"
    );

    private static final Map<String, String> NON_DEFAULT_VERSIONS = Map.of(
            "skill.cloudmold.commerce.aftersale-saga.v1", "1.4.0",
            "skill.cloudmold.consumer.shopping-journey.v1", "1.1.0",
            "skill.cloudmold.commerce.catalog-matrix.v1", "1.2.0",
            "skill.cloudmold.commerce.full-chain-hsf.v1", "1.3.0",
            "skill.cloudmold.commerce.legacy-projection-plan.v1", "1.2.0",
            "skill.cloudmold.commerce.reuse-ready-master.v1", "1.2.1",
            "skill.cloudmold.commerce.terminal-readback.v1", "1.2.0"
            ,"skill.cloudmold.commerce.order-cancellation-operational.v1", "1.1.0"
            ,"skill.cloudmold.wms.operations.v1", "1.1.0"
    );

    private ManagedWorkflowDailyAutomationFixtures() {
    }

    static List<ManagedSkillTaskWorkflowView> workflows() {
        return MANAGED_SKILL_IDS.stream()
                .map(ManagedWorkflowDailyAutomationFixtures::workflow)
                .toList();
    }

    static ManagedSkillTaskWorkflowView workflow(String skillId) {
        boolean approvalRequired = APPROVAL_SKILL_IDS.contains(skillId);
        return ManagedSkillTaskWorkflowView.builder()
                .skillId(skillId)
                .skillVersion(NON_DEFAULT_VERSIONS.getOrDefault(skillId, "1.0.0"))
                .displayName(skillId)
                .riskLevel(R3_SKILL_IDS.contains(skillId)
                        ? "R3" : approvalRequired ? "R2" : "R1")
                .definitionClosureSha256("a".repeat(64))
                .approvalRequired(approvalRequired)
                .build();
    }
}
