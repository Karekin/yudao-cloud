package cn.iocoder.yudao.module.cloudmold.fulfillment.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin.vo.FulfillmentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FulfillmentQueryServiceTest {

    private final FulfillmentQueryMapper mapper = mock(FulfillmentQueryMapper.class);
    private final FulfillmentQueryService service = new FulfillmentQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(5L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldNormalizeFiltersPropagateTenantAndComputeOffset() {
        FulfillmentPageReqVO request = new FulfillmentPageReqVO();
        request.setPageNo(2);
        request.setPageSize(25);
        request.setFulfillmentId(" fulfillment-1 ");
        request.setFulfillmentNo(" CMF-2026 ");
        request.setOrderId("   ");
        request.setOrderNo(" CMO-2026 ");
        request.setSellerId(" seller-1 ");
        request.setWarehouseId(" warehouse-1 ");
        request.setStatus(" delivered ");
        request.setShipmentId(" shipment-1 ");
        request.setShipmentStatus(" in_transit ");
        request.setCarrierCode(" sf ");
        request.setWaybillNo(" WB-001 ");
        FulfillmentPageItem item = new FulfillmentPageItem();
        item.setFulfillmentId("fulfillment-1");
        when(mapper.countPage(5L, "fulfillment-1", "CMF-2026", null, "CMO-2026", "seller-1", "warehouse-1",
                "DELIVERED", "shipment-1", "IN_TRANSIT", "SF", "WB-001")).thenReturn(26L);
        when(mapper.selectPage(5L, "fulfillment-1", "CMF-2026", null, "CMO-2026", "seller-1", "warehouse-1",
                "DELIVERED", "shipment-1", "IN_TRANSIT", "SF", "WB-001", 25L, 25)).thenReturn(List.of(item));

        PageResult<FulfillmentPageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isEqualTo(26L);
        assertThat(result.getList()).containsExactly(item);
        verify(mapper).selectPage(5L, "fulfillment-1", "CMF-2026", null, "CMO-2026", "seller-1", "warehouse-1",
                "DELIVERED", "shipment-1", "IN_TRANSIT", "SF", "WB-001", 25L, 25);
    }

    @Test
    void shouldShortCircuitWhenNoRowsMatch() {
        FulfillmentPageReqVO request = new FulfillmentPageReqVO();
        when(mapper.countPage(5L, null, null, null, null, null, null, null, null, null, null, null))
                .thenReturn(0L);

        PageResult<FulfillmentPageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(mapper, never()).selectPage(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), anyInt());
    }
}
