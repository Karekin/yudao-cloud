package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundPutawayView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundReceiptProgressView;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InboundQueryServiceTest {

    private final AsnMapper asnMapper = mock(AsnMapper.class);
    private final AsnLineMapper asnLineMapper = mock(AsnLineMapper.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final ReceiptLineMapper receiptLineMapper = mock(ReceiptLineMapper.class);
    private final PutawayMapper putawayMapper = mock(PutawayMapper.class);
    private final PutawayLineMapper putawayLineMapper = mock(PutawayLineMapper.class);
    private final InboundQueryService service = new InboundQueryService(
            asnMapper, asnLineMapper, receiptMapper, receiptLineMapper, putawayMapper, putawayLineMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void readsMultiReceiptProgressForOneAsn() {
        when(asnMapper.selectByProcurementOrderId(1L, "po-01")).thenReturn(List.of(new AsnDO()
                .setAsnId("asn-01").setAsnNo("ASN-01").setProcurementOrderId("po-01")
                .setSupplierId("supplier-01").setWarehouseId("warehouse-01")
                .setStatus("PARTIAL_RECEIVED").setVersion(2L)));
        when(asnLineMapper.selectByAsn(1L, "asn-01")).thenReturn(List.of(new AsnLineDO()
                .setAsnLineId("asn-line-01").setLineNo(10).setProcurementOrderItemId("item-01")
                .setDeliveryScheduleId("schedule-01").setPoReleaseVersion(4L).setFulfillmentVersion(3L)
                .setSupplierId("supplier-01").setWarehouseId("warehouse-01").setReceiptLocationId("loc-01")
                .setCanonicalSkuId("sku-01").setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setBaseUomCode("PIECE").setScheduledQuantity(new BigDecimal("10.000000"))
                .setAllowedOverReceiptQuantity(new BigDecimal("1.000000"))
                .setReceivedQuantity(new BigDecimal("5.000000"))
                .setPendingQualityQuantity(new BigDecimal("5.000000"))
                .setValuationPolicyId("STANDARD_V1").setValuationPolicyVersion("valuation-v1")
                .setValuationPolicyHash("b".repeat(64)).setUnitCostAmountMinor(1200L)
                .setCurrencyCode("CNY").setRoundingPolicyCode("HALF_UP")
                .setTolerancePolicyVersion("tol-v1").setTolerancePolicyHash("a".repeat(64))
                .setStatus("PARTIAL_RECEIVED_PENDING_QUALITY")));
        when(receiptMapper.selectByAsn(1L, "asn-01")).thenReturn(List.of(
                new ReceiptDO().setReceiptId("receipt-01").setReceiptNo("RCV-01").setProcurementOrderId("po-01")
                        .setWarehouseId("warehouse-01").setStatus("PENDING_QUALITY").setVersion(1L)
                        .setCreatedAt(LocalDateTime.parse("2026-08-02T10:00:00")),
                new ReceiptDO().setReceiptId("receipt-02").setReceiptNo("RCV-02").setProcurementOrderId("po-01")
                        .setWarehouseId("warehouse-01").setStatus("PENDING_QUALITY").setVersion(1L)
                        .setCreatedAt(LocalDateTime.parse("2026-08-02T11:00:00"))));
        when(receiptLineMapper.selectByReceipt(1L, "receipt-01")).thenReturn(List.of(receiptLine("receipt-line-01", "2.000000")));
        when(receiptLineMapper.selectByReceipt(1L, "receipt-02")).thenReturn(List.of(receiptLine("receipt-line-02", "3.000000")));
        when(receiptMapper.selectCurrent(1L, "receipt-02")).thenReturn(new ReceiptDO()
                .setReceiptId("receipt-02").setProcurementOrderId("po-01"));
        when(receiptMapper.countPage(1L, null, null, null, "po-01", null, null, null)).thenReturn(2L);
        when(receiptMapper.selectPage(1L, null, null, null, "po-01", null, null, null, 0L, 20))
                .thenReturn(List.of(receiptPageItem("receipt-01", "RCV-01"),
                        receiptPageItem("receipt-02", "RCV-02")));

        InboundQueryApi.InboundStageView stage = service.requireInboundStage("po-01");
        InboundReceiptProgressView progress = service.requireReceiptProgress("po-01");
        PageResult<InboundQueryService.InboundReceiptPageItem> page = service.getReceiptPage(pageReq());
        InboundReceiptProgressView.ReceiptView detail = service.requireReceiptDetail("receipt-02");

        assertThat(stage.nextWaitingEventCode()).isEqualTo("PUTAWAY_COMPLETED");
        assertThat(progress.getReceiptCount()).isEqualTo(2);
        assertThat(progress.getTotalReceivedQuantity()).isEqualByComparingTo("5.000000");
        assertThat(page.getList()).hasSize(2);
        assertThat(detail.getReceiptNo()).isEqualTo("RCV-02");
    }

    @Test
    void pagesAndReadsAuthoritativePutawayFacts() {
        LocalDateTime createdAt = LocalDateTime.parse("2026-08-02T12:00:00");
        when(putawayMapper.countPage(1L, "RCV", null, "po-01", "warehouse-01", "COMPLETED"))
                .thenReturn(1L);
        when(putawayMapper.selectPage(1L, "RCV", null, "po-01", "warehouse-01", "COMPLETED", 0L, 20))
                .thenReturn(List.of(new InboundPutawayPageItem("putaway-01", "receipt-01", "RCV-01", "po-01",
                        "supplier-01", "warehouse-01", "COMPLETED", 1L, 1L,
                        new BigDecimal("2.000000"), createdAt, createdAt)));
        when(putawayMapper.selectCurrent(1L, "putaway-01")).thenReturn(new PutawayDO()
                .setPutawayId("putaway-01").setReceiptId("receipt-01").setWarehouseId("warehouse-01")
                .setStatus("COMPLETED").setVersion(1L).setCreatedAt(createdAt).setUpdatedAt(createdAt));
        when(receiptMapper.selectCurrent(1L, "receipt-01")).thenReturn(new ReceiptDO()
                .setReceiptId("receipt-01").setReceiptNo("RCV-01").setProcurementOrderId("po-01")
                .setSupplierId("supplier-01").setWarehouseId("warehouse-01"));
        when(putawayLineMapper.selectByPutaway(1L, "putaway-01")).thenReturn(List.of(new PutawayLineDO()
                .setPutawayLineId("putaway-line-01").setPutawayId("putaway-01").setReceiptId("receipt-01")
                .setReceiptLineId("receipt-line-01").setWarehouseId("warehouse-01")
                .setSourceLocationId("receiving-01").setTargetLocationId("storage-01")
                .setCanonicalSkuId("sku-01").setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setLotId("lot-01").setBaseUomCode("PIECE").setPutawayQuantity(new BigDecimal("2.000000"))
                .setCumulativePutawayQuantity(new BigDecimal("4.000000")).setStatus("COMPLETED").setVersion(1L)
                .setInventoryOperationId(701L).setInventoryLedgerTxId(702L)
                .setInventoryMovementGroupId("movement-group-01").setInventoryTargetBalanceId("balance-01")
                .setCreatedAt(createdAt).setUpdatedAt(createdAt)));

        InboundQueryService.InboundPutawayPageReqVO request = new InboundQueryService.InboundPutawayPageReqVO();
        request.setKeyword(" RCV ");
        request.setProcurementOrderId("po-01");
        request.setWarehouseId("warehouse-01");
        request.setStatus("COMPLETED");
        request.setPageNo(1);
        request.setPageSize(20);

        PageResult<InboundPutawayPageItem> page = service.getPutawayPage(request);
        InboundPutawayView detail = service.requirePutawayDetail("putaway-01");

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList().get(0).totalPutawayQuantity()).isEqualByComparingTo("2.000000");
        assertThat(detail.getReceiptNo()).isEqualTo("RCV-01");
        assertThat(detail.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getSourceLocationId()).isEqualTo("receiving-01");
            assertThat(line.getTargetLocationId()).isEqualTo("storage-01");
            assertThat(line.getInventoryLedgerTransactionId()).isEqualTo("702");
            assertThat(line.getInventoryMovementGroupId()).isEqualTo("movement-group-01");
            assertThat(line.getInventoryTargetBalanceId()).isEqualTo("balance-01");
        });
    }

    @Test
    void appliesAllReceiptFiltersInAuthoritativePageQuery() {
        when(receiptMapper.countPage(1L, "RCV", "receipt-01", "RCV-01", "po-01",
                "supplier-01", "warehouse-01", "QUALITY_ACCEPTED")).thenReturn(0L);
        InboundQueryService.InboundReceiptPageReqVO request = new InboundQueryService.InboundReceiptPageReqVO();
        request.setKeyword(" RCV ");
        request.setReceiptId("receipt-01");
        request.setReceiptNo("RCV-01");
        request.setProcurementOrderId("po-01");
        request.setSupplierId("supplier-01");
        request.setWarehouseId("warehouse-01");
        request.setStatus("QUALITY_ACCEPTED");
        request.setPageNo(1);
        request.setPageSize(20);

        PageResult<InboundQueryService.InboundReceiptPageItem> page = service.getReceiptPage(request);

        assertThat(page.getTotal()).isZero();
        verify(receiptMapper).countPage(1L, "RCV", "receipt-01", "RCV-01", "po-01",
                "supplier-01", "warehouse-01", "QUALITY_ACCEPTED");
        verify(receiptMapper, never()).selectPage(anyLong(), any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt());
    }

    private static InboundQueryService.InboundReceiptPageReqVO pageReq() {
        InboundQueryService.InboundReceiptPageReqVO value = new InboundQueryService.InboundReceiptPageReqVO();
        value.setProcurementOrderId("po-01");
        value.setPageNo(1);
        value.setPageSize(20);
        return value;
    }

    private static ReceiptLineDO receiptLine(String receiptLineId, String received) {
        return new ReceiptLineDO().setReceiptLineId(receiptLineId).setLineNo(10).setAsnLineId("asn-line-01")
                .setProcurementOrderId("po-01").setProcurementOrderItemId("item-01").setDeliveryScheduleId("schedule-01")
                .setPoReleaseVersion(4L).setFulfillmentVersionBefore(2L).setFulfillmentVersionAfter(3L)
                .setSupplierId("supplier-01").setWarehouseId("warehouse-01").setReceiptLocationId("loc-01")
                .setCanonicalSkuId("sku-01").setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setBaseUomCode("PIECE").setLotId("lot-01").setQualityStatus("PENDING_QUALITY")
                .setReceivedQuantity(new BigDecimal(received)).setPendingQualityQuantity(new BigDecimal(received))
                .setAcceptedQuantity(new BigDecimal("0.000000")).setRejectedQuantity(new BigDecimal("0.000000"))
                .setQuarantinedQuantity(new BigDecimal("0.000000"))
                .setCumulativePutawayQuantity(new BigDecimal("0.000000")).setVersion(1L)
                .setValuationPolicyId("STANDARD_V1").setValuationPolicyVersion("valuation-v1")
                .setValuationPolicyHash("b".repeat(64)).setUnitCostAmountMinor(1200L).setMovementCostAmountMinor(2400L)
                .setCurrencyCode("CNY").setRoundingPolicyCode("HALF_UP")
                .setTolerancePolicyVersion("tol-v1").setTolerancePolicyHash("a".repeat(64))
                .setInventoryOperationId(201L).setInventoryLedgerTxId(301L).setInventoryBalanceId("bal-01");
    }

    private static InboundQueryService.InboundReceiptPageItem receiptPageItem(String receiptId, String receiptNo) {
        return new InboundQueryService.InboundReceiptPageItem(receiptId, receiptNo, "po-01", "asn-01",
                "supplier-01", "warehouse-01", "PENDING_QUALITY", 1L,
                new BigDecimal("2.000000"), new BigDecimal("2.000000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1L,
                LocalDateTime.parse("2026-08-02T10:00:00"), LocalDateTime.parse("2026-08-02T10:00:00"));
    }
}
