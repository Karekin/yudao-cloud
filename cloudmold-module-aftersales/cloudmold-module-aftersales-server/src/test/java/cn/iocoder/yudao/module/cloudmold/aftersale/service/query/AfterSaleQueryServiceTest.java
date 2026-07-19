package cn.iocoder.yudao.module.cloudmold.aftersale.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aftersale.controller.admin.vo.AfterSalePageReqVO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSalePageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfterSaleQueryServiceTest {

    private final AfterSalePageMapper mapper = mock(AfterSalePageMapper.class);
    private final AfterSaleQueryService service = new AfterSaleQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(8L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPageWithinCurrentTenantAndNormalizeFilters() {
        AfterSalePageReqVO request = new AfterSalePageReqVO();
        request.setPageNo(2);
        request.setPageSize(10);
        request.setAfterSaleId("  as-1  ");
        request.setAfterSaleNo("  CMAS  ");
        request.setOrderId("   ");
        request.setOrderNo("  CMO  ");
        request.setCanonicalSkuId("  sku-1 ");
        request.setCaseStatus(" completed ");
        request.setRefundStatus(" succeeded ");
        request.setAfterSaleType(" return_and_refund ");
        request.setReasonCode(" size_not_fit ");
        request.setResponsibility(" buyer ");
        AfterSalePageItem item = new AfterSalePageItem();
        item.setAfterSaleId("as-1");

        when(mapper.countPage(8L, "as-1", "CMAS", null, "CMO", "sku-1", "COMPLETED", "SUCCEEDED",
                "RETURN_AND_REFUND", "SIZE_NOT_FIT", "BUYER")).thenReturn(13L);
        when(mapper.selectPage(8L, "as-1", "CMAS", null, "CMO", "sku-1", "COMPLETED", "SUCCEEDED",
                "RETURN_AND_REFUND", "SIZE_NOT_FIT", "BUYER", 10L, 10)).thenReturn(List.of(item));

        PageResult<AfterSalePageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isEqualTo(13L);
        assertThat(result.getList()).containsExactly(item);
        verify(mapper).selectPage(8L, "as-1", "CMAS", null, "CMO", "sku-1", "COMPLETED", "SUCCEEDED",
                "RETURN_AND_REFUND", "SIZE_NOT_FIT", "BUYER", 10L, 10);
    }

    @Test
    void shouldAvoidSelectWhenNoAfterSaleMatches() {
        AfterSalePageReqVO request = new AfterSalePageReqVO();
        when(mapper.countPage(8L, null, null, null, null, null, null, null, null, null, null)).thenReturn(0L);

        PageResult<AfterSalePageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(mapper, never()).selectPage(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyLong(), anyInt());
    }
}
