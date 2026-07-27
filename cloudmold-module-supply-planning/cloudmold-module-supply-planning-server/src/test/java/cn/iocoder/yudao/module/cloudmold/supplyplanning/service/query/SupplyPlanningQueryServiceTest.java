package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SupplyPlanningQueryServiceTest {
    private final SupplyPlanningMapper mapper = mock(SupplyPlanningMapper.class);
    private final ProcurementQueryApi procurementQueryApi = mock(ProcurementQueryApi.class);
    private final YudaoWarehouseInboundQueryApi warehouseInboundQueryApi = mock(YudaoWarehouseInboundQueryApi.class);
    private final SupplyPlanningQueryService service =
            new SupplyPlanningQueryService(mapper, procurementQueryApi, warehouseInboundQueryApi);

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

        ReplenishmentBusinessStageView result = service.requireReplenishmentBusinessStage("recommendation-02");

        assertThat(result.getProcurementOrderId()).isNull();
        assertThat(result.getProjectionDocumentType()).isEqualTo("MOVEMENT_ORDER");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("TRANSFER_OUTBOUND");
        assertThat(result.getSupplierConfirmationStatus()).isEqualTo("NOT_APPLICABLE");
    }
}
