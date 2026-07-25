package cn.iocoder.yudao.module.cloudmold.order.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderView;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderQueryServiceTest {

    private final OrderQueryMapper mapper = mock(OrderQueryMapper.class);
    private final OrderQueryService service = new OrderQueryService(mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void ownedAppOrderPreservesCanonicalRunLineage() {
        TenantContextHolder.setTenantId(11L);
        OrderDetailVO detail = new OrderDetailVO();
        detail.setOrderId("order-1");
        detail.setOrderNo("CMO1");
        detail.setRunId("checkout-lineage-1");
        detail.setBuyerId("principal-1");
        detail.setStatus("INVENTORY_RESERVED");
        detail.setItems(List.of());
        when(mapper.selectOrderDetail(11L, "order-1")).thenReturn(detail);
        when(mapper.selectOrderItems(11L, "order-1")).thenReturn(List.of());

        AppOrderView result = service.requireOwned("principal-1", "order-1");

        assertThat(result.getRunId()).isEqualTo("checkout-lineage-1");
    }
}
