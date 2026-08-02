package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockTransferPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
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
    void readsPrepareOrderAsTransferOutboundStage() {
        when(mapper.selectRequestBySourceBusiness(1L, "REPLENISHMENT", "recommendation-01"))
                .thenReturn(new StockTransferRequestDO()
                        .setRequestId("request-01").setRequestCode("STRRQ-0001")
                        .setSourceBusinessType("REPLENISHMENT").setSourceBusinessRef("recommendation-01")
                        .setOwnerType("MERCHANT").setOwnerId("merchant-01")
                        .setSourceWarehouseId("warehouse-source")
                        .setTargetWarehouseId("warehouse-target")
                        .setReasonCode("REPLENISHMENT_APPROVED").setRemark("replenishment transfer")
                        .setStatus("APPROVED").setVersion(1L));
        when(mapper.selectOrderByRequestId(1L, "request-01"))
                .thenReturn(new StockTransferOrderDO()
                        .setOrderId("order-01").setOrderCode("STRORD-0001")
                        .setStatus("PREPARE").setVersion(1L));
        when(mapper.selectRequestLines(1L, "request-01")).thenReturn(List.of(
                new StockTransferRequestLineDO().setLineId("line-01").setLineNumber(10)
                        .setCanonicalSkuId("sku-01").setRequestedQuantity(new BigDecimal("8.000000"))
                        .setUomCode("PIECE").setRemark("line-01")));
        when(mapper.selectStatusHistory(1L, "request-01", "order-01")).thenReturn(List.of(
                new StockTransferStatusHistoryDO().setHistoryId("history-01").setBusinessObjectType("REQUEST")
                        .setBusinessObjectId("request-01").setStatus("APPROVED").setStatusVersion(1L)
                        .setStageCode("REQUEST_APPROVED").setStageLabel("Transfer request approved")
                        .setChangedAt(LocalDateTime.parse("2026-08-02T10:00:00"))));
        when(queryMapper.selectWarehouseIdentity(1L, "warehouse-source"))
                .thenReturn(new StockTransferWarehouseIdentity("warehouse-source", "WH-SOURCE", "来源仓"));
        when(queryMapper.selectWarehouseIdentity(1L, "warehouse-target"))
                .thenReturn(new StockTransferWarehouseIdentity("warehouse-target", "WH-TARGET", "目标仓"));

        StockTransferView result = service.requireBySourceBusiness("REPLENISHMENT", "recommendation-01");

        assertThat(result.getOrderStatus()).isEqualTo("PREPARE");
        assertThat(result.getCurrentStageCode()).isEqualTo("TRANSFER_OUTBOUND");
        assertThat(result.getCurrentStageLabel()).isEqualTo("等待调拨出库");
        assertThat(result.isTerminal()).isFalse();
        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getStatusHistory()).hasSize(1);
        assertThat(result.getSourceWarehouseCode()).isEqualTo("WH-SOURCE");
        assertThat(result.getTargetWarehouseName()).isEqualTo("目标仓");
    }

    @Test
    void pagesOnlyCanonicalStockTransferDocuments() {
        StockTransferPageReqVO request = new StockTransferPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setKeyword(" TO-001 ");
        request.setOrderStatus(" PREPARE ");
        StockTransferPageItem row = new StockTransferPageItem();
        row.setRequestId("request-01");
        row.setRequestStatus("APPROVED");
        row.setOrderStatus("PREPARE");
        when(queryMapper.countPage(1L, "TO-001", "PREPARE", null, null)).thenReturn(1L);
        when(queryMapper.selectPage(1L, "TO-001", "PREPARE", null, null, 20L, 20))
                .thenReturn(List.of(row));

        PageResult<StockTransferPageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getCurrentStageCode()).isEqualTo("TRANSFER_OUTBOUND");
                    assertThat(item.getCurrentStageLabel()).isEqualTo("等待调拨出库");
                    assertThat(item.isTerminal()).isFalse();
                });
    }
}
