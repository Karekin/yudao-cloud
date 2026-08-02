package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockTransferPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionBatchDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferQueryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StockTransferQueryServiceTest {

    private final StockTransferStoreMapper mapper = mock(StockTransferStoreMapper.class);
    private final StockTransferQueryMapper queryMapper = mock(StockTransferQueryMapper.class);
    private final StockTransferQueryService service = new StockTransferQueryService(mapper, queryMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void readsInboundStageWithExecutionFactsAndMovementGroup() {
        when(mapper.selectRequestBySourceBusiness(1L, "REPLENISHMENT", "recommendation-01"))
                .thenReturn(new StockTransferRequestDO()
                        .setRequestId("request-01").setRequestCode("STRRQ-0001")
                        .setSourceBusinessType("REPLENISHMENT").setSourceBusinessRef("recommendation-01")
                        .setOwnerType("MERCHANT").setOwnerId("merchant-01")
                        .setSourceWarehouseId("warehouse-source").setTargetWarehouseId("warehouse-target")
                        .setReasonCode("REPLENISHMENT_APPROVED").setRemark("replenishment transfer")
                        .setStatus("APPROVED").setVersion(1L));
        when(mapper.selectOrderByRequestId(1L, "request-01"))
                .thenReturn(new StockTransferOrderDO()
                        .setOrderId("order-01").setOrderCode("STRORD-0001")
                        .setStatus("IN_TRANSIT").setVersion(2L));
        when(mapper.selectRequestLines(1L, "request-01")).thenReturn(List.of(
                new StockTransferRequestLineDO().setLineId("request-line-01").setLineNumber(10)
                        .setCanonicalSkuId("sku-01").setRequestedQuantity(new BigDecimal("8.000000"))
                        .setUomCode("PIECE").setRemark("line-01")));
        when(mapper.selectOrderLines(1L, "order-01")).thenReturn(List.of(
                new StockTransferOrderLineDO().setLineId("order-line-01").setLineNumber(10)
                        .setCanonicalSkuId("sku-01").setMovementGroupId("mg-01")
                        .setRequestedQuantity(new BigDecimal("8.000000"))
                        .setOutboundQuantity(new BigDecimal("8.000000"))
                        .setReceivedQuantity(new BigDecimal("3.000000"))
                        .setUomCode("PIECE").setStatus("PARTIAL_RECEIVED").setVersion(3L)));
        when(mapper.selectStatusHistory(1L, "request-01", "order-01")).thenReturn(List.of(
                new StockTransferStatusHistoryDO().setHistoryId("history-01").setBusinessObjectType("ORDER_LINE")
                        .setBusinessObjectId("order-line-01").setStatus("PARTIAL_RECEIVED").setStatusVersion(3L)
                        .setStageCode("TRANSFER_INBOUND").setStageLabel("等待调拨入库")
                        .setChangedAt(LocalDateTime.parse("2026-08-02T10:00:00"))));
        when(mapper.selectExecutionBatches(1L, "order-01")).thenReturn(List.of(
                new StockTransferExecutionBatchDO().setBatchId("batch-01").setBatchNo("STROUT-0001")
                        .setBatchType("OUTBOUND").setStatus("COMPLETED").setVersion(1L)
                        .setOccurredAt(LocalDateTime.parse("2026-08-02T09:00:00")).setRemark("outbound")));
        when(mapper.selectExecutionLines(1L, "order-01")).thenReturn(List.of(
                new StockTransferExecutionLineDO().setExecutionLineId("exec-01").setLineNumber(10)
                        .setCanonicalSkuId("sku-01").setMovementGroupId("mg-01")
                        .setExecutedQuantity(new BigDecimal("8.000000"))
                        .setReceivedQuantity(new BigDecimal("3.000000"))
                        .setCumulativeDispatchedQuantity(new BigDecimal("8.000000"))
                        .setCumulativeReceivedQuantity(new BigDecimal("3.000000"))
                        .setOutstandingQuantity(new BigDecimal("5.000000"))
                        .setStatus("PARTIAL_RECEIVED").setTargetLocationId("target-loc-01")
                        .setTargetStockStatus("SELLABLE").setTargetQualityStatus("QUALIFIED")
                        .setBatchId("batch-01")));
        when(queryMapper.selectWarehouseIdentity(1L, "warehouse-source"))
                .thenReturn(new StockTransferWarehouseIdentity("warehouse-source", "WH-SOURCE", "来源仓"));
        when(queryMapper.selectWarehouseIdentity(1L, "warehouse-target"))
                .thenReturn(new StockTransferWarehouseIdentity("warehouse-target", "WH-TARGET", "目标仓"));

        StockTransferView result = service.requireBySourceBusiness("REPLENISHMENT", "recommendation-01");

        assertThat(result.getCurrentStageCode()).isEqualTo("TRANSFER_INBOUND");
        assertThat(result.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getMovementGroupId()).isEqualTo("mg-01");
            assertThat(line.getLineStatus()).isEqualTo("PARTIAL_RECEIVED");
            assertThat(line.getCurrentStageCode()).isEqualTo("TRANSFER_INBOUND");
        });
        assertThat(result.getExecutionBatches()).singleElement().satisfies(batch ->
                assertThat(batch.getLines()).singleElement().satisfies(line ->
                        assertThat(line.getOutstandingQuantity()).isEqualByComparingTo("5.000000")));
    }

    @Test
    void pagesUsingWarehouseCumulativeQuantities() {
        StockTransferPageReqVO request = new StockTransferPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setKeyword(" TO-001 ");
        request.setOrderStatus(" IN_TRANSIT ");
        StockTransferPageItem row = new StockTransferPageItem();
        row.setRequestId("request-01");
        row.setRequestStatus("APPROVED");
        row.setOrderStatus("IN_TRANSIT");
        row.setTotalRequestedQuantity(new BigDecimal("12.250000"));
        row.setTotalOutboundQuantity(new BigDecimal("12.250000"));
        row.setTotalReceivedQuantity(new BigDecimal("3.000000"));
        when(queryMapper.countPage(1L, "TO-001", "IN_TRANSIT", null, null)).thenReturn(1L);
        when(queryMapper.selectPage(1L, "TO-001", "IN_TRANSIT", null, null, 20L, 20))
                .thenReturn(List.of(row));

        PageResult<StockTransferPageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).singleElement().satisfies(item -> {
            assertThat(item.getCurrentStageCode()).isEqualTo("TRANSFER_INBOUND");
            assertThat(item.getCurrentStageLabel()).isEqualTo("等待调拨入库");
            assertThat(item.isTerminal()).isFalse();
        });
    }
}
