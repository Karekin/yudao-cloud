package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyOperationsQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SupplyPlanningQueryServiceTest {
    private final SupplyPlanningMapper mapper = mock(SupplyPlanningMapper.class);
    private final ProcurementQueryApi procurementQueryApi = mock(ProcurementQueryApi.class);
    private final YudaoWarehouseInboundQueryApi warehouseInboundQueryApi = mock(YudaoWarehouseInboundQueryApi.class);
    private final YudaoLegacyOperationsQueryApi legacyOperationsQueryApi =
            mock(YudaoLegacyOperationsQueryApi.class);
    private final SupplyPlanningQueryService service =
            new SupplyPlanningQueryService(
                    mapper, procurementQueryApi, warehouseInboundQueryApi, legacyOperationsQueryApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void aggregatesPurchaseReplenishmentIntoProcurementAndInboundStages() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-01")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-01")
                        .planId("plan-01")
                        .recommendationStatus("CONVERTED")
                        .targetType("PURCHASE_REQUEST")
                        .sourceSystem("YUDAO_ERP")
                        .documentType("PURCHASE_ORDER")
                        .externalDocumentId("781")
                        .externalDocumentNo("PO-20260725-01")
                        .documentStatus("PREPARE")
                        .nextWaitingEventCode("SUPPLIER_CONFIRMATION")
                        .nextWaitingEventLabel("等待供应商确认采购单")
                        .build());
        when(procurementQueryApi.requireBySourceBusiness("REPLENISHMENT", "recommendation-01")).thenReturn(
                ProcurementOrderView.builder()
                        .orderId("procurement-order:conversion-01")
                        .orderCode("PO-CM-CONVERSION01")
                        .status("SUPPLIER_CONFIRMED")
                        .projectionSourceSystem("YUDAO_ERP")
                        .projectionDocumentType("PURCHASE_ORDER")
                        .projectionExternalDocumentId("781")
                        .projectionExternalDocumentNo("PO-20260725-01")
                        .projectionDocumentStatus("PREPARE")
                        .build());
        when(warehouseInboundQueryApi.getPurchaseInboundTerminal(any())).thenReturn(
                new YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalView(
                        "CLOUDMOLD", "PROCUREMENT_ORDER", "procurement-order:conversion-01",
                        "PO-CM-CONVERSION01", null, null, "WAITING_ASN_CREATION",
                        "WAITING_RECEIPT_ORDER", "WAITING_RECEIPT_ORDER",
                        "WAITING_QUALITY_RELEASE", "ASN_CREATED", "等待创建收货预约/ASN", null, null));

        ReplenishmentBusinessStageView result = service.requireReplenishmentBusinessStage("recommendation-01");

        assertThat(result.getProcurementOrderId()).isEqualTo("procurement-order:conversion-01");
        assertThat(result.getProcurementOrderStatus()).isEqualTo("SUPPLIER_CONFIRMED");
        assertThat(result.getAsnStatus()).isEqualTo("WAITING_ASN_CREATION");
        assertThat(result.getReceiptStatus()).isEqualTo("WAITING_RECEIPT_ORDER");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("ASN_CREATED");
    }

    @Test
    void keepsTransferRequestInWmsPrepareWaitingState() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-02")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-02")
                        .planId("plan-02")
                        .recommendationStatus("CONVERTED")
                        .targetType("TRANSFER_REQUEST")
                        .sourceSystem("YUDAO_WMS")
                        .documentType("MOVEMENT_ORDER")
                        .externalDocumentId("9901")
                        .externalDocumentNo("MO-9901")
                        .documentStatus("PREPARE")
                        .nextWaitingEventCode("TRANSFER_OUTBOUND")
                        .nextWaitingEventLabel("等待调拨出库")
                        .build());
        when(legacyOperationsQueryApi.getMovementOrder(9901L)).thenReturn(
                new YudaoLegacyOperationsQueryApi.LegacyDocumentView(
                        "YUDAO_WMS", "MOVEMENT_ORDER", 9901L, "MO-9901", 0,
                        "2026-07-29T09:00:00", null, null, "replenishment transfer"));

        ReplenishmentBusinessStageView result = service.requireReplenishmentBusinessStage("recommendation-02");

        assertThat(result.getProcurementOrderId()).isNull();
        assertThat(result.getProjectionDocumentType()).isEqualTo("MOVEMENT_ORDER");
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
                        .sourceSystem("YUDAO_WMS")
                        .documentType("MOVEMENT_ORDER")
                        .externalDocumentId("9902")
                        .externalDocumentNo("MO-STALE")
                        .documentStatus("PREPARE")
                        .nextWaitingEventCode("TRANSFER_OUTBOUND")
                        .nextWaitingEventLabel("等待调拨出库")
                        .build());
        when(legacyOperationsQueryApi.getMovementOrder(9902L)).thenReturn(
                new YudaoLegacyOperationsQueryApi.LegacyDocumentView(
                        "YUDAO_WMS", "MOVEMENT_ORDER", 9902L, "MO-9902", 4,
                        "2026-07-29T09:30:00", null, null, "completed transfer"));

        ReplenishmentBusinessStageView result =
                service.requireReplenishmentBusinessStage("recommendation-finished");

        assertThat(result.getProjectionExternalDocumentNo()).isEqualTo("MO-9902");
        assertThat(result.getProjectionDocumentStatus()).isEqualTo("FINISHED");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("NONE");
        assertThat(result.getNextWaitingEventLabel()).isEqualTo("调拨已完成");
    }

    @Test
    void readsCanceledTransferOrderAsCanceledBusinessStage() {
        when(mapper.selectReplenishmentExecution(17L, "recommendation-canceled")).thenReturn(
                ReplenishmentExecutionView.builder()
                        .recommendationId("recommendation-canceled")
                        .targetType("TRANSFER_REQUEST")
                        .sourceSystem("YUDAO_WMS")
                        .documentType("MOVEMENT_ORDER")
                        .externalDocumentId("9903")
                        .documentStatus("PREPARE")
                        .build());
        when(legacyOperationsQueryApi.getMovementOrder(9903L)).thenReturn(
                new YudaoLegacyOperationsQueryApi.LegacyDocumentView(
                        "YUDAO_WMS", "MOVEMENT_ORDER", 9903L, "MO-9903", 5,
                        "2026-07-29T10:00:00", null, null, "canceled transfer"));

        ReplenishmentBusinessStageView result =
                service.requireReplenishmentBusinessStage("recommendation-canceled");

        assertThat(result.getProjectionDocumentStatus()).isEqualTo("CANCELED");
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
                        .mappingEvidenceSha256("c".repeat(64))
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
