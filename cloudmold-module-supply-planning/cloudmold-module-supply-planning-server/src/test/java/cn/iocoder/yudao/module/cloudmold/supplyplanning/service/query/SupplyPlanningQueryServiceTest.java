package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SupplyPlanningQueryServiceTest {
    private final SupplyPlanningMapper mapper = mock(SupplyPlanningMapper.class);
    private final PurchaseRequisitionQueryApi purchaseRequisitionQueryApi = mock(PurchaseRequisitionQueryApi.class);
    private final StockTransferQueryApi stockTransferQueryApi = mock(StockTransferQueryApi.class);
    private final SupplyPlanningQueryService service =
            new SupplyPlanningQueryService(
                    mapper, purchaseRequisitionQueryApi, stockTransferQueryApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void exposesCanonicalPurchaseRequisitionAsTheNextBusinessStage() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-01")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-01")
                        .planId("plan-01")
                        .recommendationStatus("CONVERTED")
                        .targetType("PURCHASE_REQUEST")
                        .targetAggregateType("purchase_requisition")
                        .targetAggregateId("purchase-requisition:conversion-01")
                        .targetAggregateNo("PR-CM-CONVERSION01")
                        .targetAggregateStatus("APPROVED")
                        .build());
        when(purchaseRequisitionQueryApi.requireBySourceBusiness("REPLENISHMENT", "recommendation-01"))
                .thenReturn(PurchaseRequisitionView.builder()
                        .requisitionId("purchase-requisition:conversion-01")
                        .requisitionCode("PR-CM-CONVERSION01").status("APPROVED").build());

        ReplenishmentBusinessStageView result = service.requireReplenishmentBusinessStage("recommendation-01");

        assertThat(result.getPurchaseRequisitionId()).isEqualTo("purchase-requisition:conversion-01");
        assertThat(result.getPurchaseRequisitionStatus()).isEqualTo("APPROVED");
        assertThat(result.getProcurementOrderId()).isNull();
        assertThat(result.getAsnStatus()).isEqualTo("WAITING_PURCHASE_ORDER");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("PROCUREMENT_SOURCING");
    }

    @Test
    void keepsCanonicalTransferOrderInPrepareWaitingState() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-02")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-02")
                        .planId("plan-02")
                        .recommendationStatus("CONVERTED")
                        .targetType("TRANSFER_REQUEST")
                        .targetAggregateType("STOCK_TRANSFER_ORDER")
                        .targetAggregateId("transfer-order-01")
                        .targetAggregateNo("STO-01")
                        .targetAggregateStatus("PREPARE")
                        .build());
        when(stockTransferQueryApi.requireBySourceBusiness("REPLENISHMENT", "recommendation-02"))
                .thenReturn(StockTransferView.builder()
                        .requestId("transfer-request-01").requestStatus("APPROVED")
                        .orderId("transfer-order-01").orderCode("STO-01").orderStatus("PREPARE")
                        .currentStageCode("TRANSFER_OUTBOUND").currentStageLabel("等待调拨出库")
                        .terminal(false).build());

        ReplenishmentBusinessStageView result = service.requireReplenishmentBusinessStage("recommendation-02");

        assertThat(result.getProcurementOrderId()).isNull();
        assertThat(result.getStockTransferId()).isEqualTo("transfer-order-01");
        assertThat(result.getStockTransferStatus()).isEqualTo("PREPARE");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("TRANSFER_OUTBOUND");
        assertThat(result.getSupplierConfirmationStatus()).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void readsFinishedTransferOrderAsCompletedBusinessStage() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-finished")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-finished")
                        .planId("plan-finished")
                        .recommendationStatus("CONVERTED")
                        .targetType("TRANSFER_REQUEST")
                        .targetAggregateType("STOCK_TRANSFER_ORDER")
                        .targetAggregateId("transfer-order-02")
                        .targetAggregateStatus("PREPARE")
                        .build());
        when(stockTransferQueryApi.requireBySourceBusiness(
                "REPLENISHMENT", "recommendation-finished"))
                .thenReturn(StockTransferView.builder()
                        .orderId("transfer-order-02").orderCode("STO-02").orderStatus("COMPLETED")
                        .currentStageCode("NONE").currentStageLabel("调拨已完成")
                        .terminal(true).build());

        ReplenishmentBusinessStageView result =
                service.requireReplenishmentBusinessStage("recommendation-finished");

        assertThat(result.getStockTransferNo()).isEqualTo("STO-02");
        assertThat(result.getStockTransferStatus()).isEqualTo("COMPLETED");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("NONE");
        assertThat(result.getNextWaitingEventLabel()).isEqualTo("调拨已完成");
    }

    @Test
    void readsCanceledTransferOrderAsCanceledBusinessStage() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-canceled")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-canceled")
                        .targetType("TRANSFER_REQUEST")
                        .targetAggregateType("STOCK_TRANSFER_ORDER")
                        .targetAggregateId("transfer-order-03")
                        .targetAggregateStatus("PREPARE")
                        .build());
        when(stockTransferQueryApi.requireBySourceBusiness(
                "REPLENISHMENT", "recommendation-canceled"))
                .thenReturn(StockTransferView.builder()
                        .orderId("transfer-order-03").orderCode("STO-03").orderStatus("CANCELED")
                        .currentStageCode("NONE").currentStageLabel("调拨单已取消")
                        .terminal(true).build());

        ReplenishmentBusinessStageView result =
                service.requireReplenishmentBusinessStage("recommendation-canceled");

        assertThat(result.getStockTransferStatus()).isEqualTo("CANCELED");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("NONE");
        assertThat(result.getNextWaitingEventLabel()).isEqualTo("调拨单已取消");
    }

    @Test
    void listsOnlyMapperVerifiedReadyExecutionProposalsWithinBoundedLimit() {
        ReplenishmentExecutionProposalView proposal =
                ReplenishmentExecutionProposalView.builder()
                        .proposalId("proposal-01")
                        .recommendationId("recommendation-01")
                        .expectedRecommendationVersion(2L)
                        .targetType("PURCHASE_REQUEST")
                        .policyCode("REPLENISHMENT_EXECUTION_V1")
                        .policySha256("d".repeat(64))
                        .build();
        when(mapper.selectReadyReplenishmentExecutionProposals(17L, 20))
                .thenReturn(List.of(proposal));

        assertThat(service.listReadyReplenishmentExecutionProposals(20))
                .containsExactly(proposal);
        verify(mapper).selectReadyReplenishmentExecutionProposals(17L, 20);

        assertThatThrownBy(() -> service.listReadyReplenishmentExecutionProposals(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> service.listReadyReplenishmentExecutionProposals(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void requiresProposalByStableIdAfterMapperRevalidatesLifecycle() {
        ReplenishmentExecutionProposalView proposal =
                ReplenishmentExecutionProposalView.builder()
                        .proposalId("proposal-01")
                        .recommendationId("recommendation-01")
                        .expectedRecommendationVersion(2L)
                        .targetType("TRANSFER_REQUEST")
                        .ownerType("MERCHANT").ownerId("merchant-01")
                        .sourceWarehouseId("warehouse-source")
                        .targetWarehouseId("warehouse-target")
                        .build();
        when(mapper.selectReadyReplenishmentExecutionProposal(17L, "proposal-01"))
                .thenReturn(proposal);

        assertThat(service.requireReadyReplenishmentExecutionProposal("proposal-01"))
                .isSameAs(proposal);

        assertThatThrownBy(() ->
                service.requireReadyReplenishmentExecutionProposal("missing-proposal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ready replenishment execution proposal not found");
        assertThatThrownBy(() ->
                service.requireReadyReplenishmentExecutionProposal("bad proposal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("proposalId must be a safe opaque reference");
    }
}
