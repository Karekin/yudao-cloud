package cn.iocoder.yudao.module.cloudmold.order.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.controller.admin.vo.OrderPageReqVO;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderQueryServiceTest {

    private final OrderQueryMapper mapper = mock(OrderQueryMapper.class);
    private final OrderQueryService service = new OrderQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPageOnlyWithinCurrentTenantAndNormalizeFilters() {
        OrderPageReqVO request = new OrderPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setOrderId("  order-1  ");
        request.setOrderNo("  CMO-1001 ");
        request.setBuyerId(" buyer-1 ");
        request.setStatus(" PAYMENT_CONFIRMED ");
        OrderPageItem item = new OrderPageItem();
        item.setOrderId("order-1");
        when(mapper.countOrderPage(7L, "order-1", "CMO-1001", "buyer-1", "PAYMENT_CONFIRMED")).thenReturn(21L);
        when(mapper.selectOrderPage(7L, "order-1", "CMO-1001", "buyer-1", "PAYMENT_CONFIRMED", 20L, 20))
                .thenReturn(List.of(item));

        PageResult<OrderPageItem> result = service.getOrderPage(request);

        assertThat(result.getTotal()).isEqualTo(21L);
        assertThat(result.getList()).containsExactly(item);
        verify(mapper).selectOrderPage(7L, "order-1", "CMO-1001", "buyer-1", "PAYMENT_CONFIRMED", 20L, 20);
    }

    @Test
    void shouldAvoidPageQueryWhenNoOrderMatches() {
        OrderPageReqVO request = new OrderPageReqVO();
        when(mapper.countOrderPage(7L, null, null, null, null)).thenReturn(0L);

        PageResult<OrderPageItem> result = service.getOrderPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(mapper, never()).selectOrderPage(anyLong(), any(), any(), any(), any(), anyLong(), anyInt());
    }
}
