package cn.iocoder.yudao.module.cloudmold.rpc;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMoldDubboServiceAllowlistTest {

    @Test
    void shouldLoadEveryCloudMoldPublicApiContractName() {
        Set<String> services = CloudMoldDubboServiceAllowlist.load();

        assertThat(services).hasSizeGreaterThanOrEqualTo(100)
                .contains("cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review.AgentApprovalReviewApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantKnowledgeCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantKnowledgeQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessWorkflowQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCheckoutReservationApi",
                        "cn.iocoder.yudao.module.cloudmold.listing.api.AppListingQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.order.api.AppOrderQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception.FulfillmentExceptionCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.BondedCustomsCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.BondedCustomsQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.order.api.workflow.OrderWorkflowFactsApi",
                        "cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsApi",
                        "cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi");
        assertThat(services).allSatisfy(service -> {
            assertThat(service).startsWith("cn.iocoder.yudao.module.cloudmold.");
            assertThat(service).endsWith("Api");
        });
    }
}
