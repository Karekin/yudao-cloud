package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.listing.controller.admin.vo.ListingPageReqVO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingQueryServiceTest {

    private final ListingQueryMapper mapper = mock(ListingQueryMapper.class);
    private final ListingQueryService service = new ListingQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldNormalizeFiltersPropagateTenantAndComputeOffset() {
        ListingPageReqVO request = new ListingPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setListingId(" listing-1 ");
        request.setListingNo(" CML-2026 ");
        request.setTitle(" Summer ");
        request.setMerchantId(" merchant-1 ");
        request.setShopId(" shop-1 ");
        request.setChannelCode(" yshopping_internal ");
        request.setCanonicalSpuId(" spu-1 ");
        request.setStatus(" published ");
        ListingPageItem item = new ListingPageItem();
        item.setListingId("listing-1");
        when(mapper.countListingPage(7L, "listing-1", "CML-2026", "Summer", "merchant-1", "shop-1",
                "YSHOPPING_INTERNAL", "spu-1", "PUBLISHED")).thenReturn(21L);
        when(mapper.selectListingPage(7L, "listing-1", "CML-2026", "Summer", "merchant-1", "shop-1",
                "YSHOPPING_INTERNAL", "spu-1", "PUBLISHED", 20L, 20)).thenReturn(List.of(item));

        PageResult<ListingPageItem> result = service.getListingPage(request);

        assertThat(result.getTotal()).isEqualTo(21L);
        assertThat(result.getList()).containsExactly(item);
        verify(mapper).selectListingPage(7L, "listing-1", "CML-2026", "Summer", "merchant-1", "shop-1",
                "YSHOPPING_INTERNAL", "spu-1", "PUBLISHED", 20L, 20);
    }

    @Test
    void shouldAvoidPageQueryWhenNoListingMatches() {
        ListingPageReqVO request = new ListingPageReqVO();
        when(mapper.countListingPage(7L, null, null, null, null, null, null, null, null)).thenReturn(0L);

        PageResult<ListingPageItem> result = service.getListingPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(mapper, never()).selectListingPage(anyLong(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyLong(), anyInt());
    }
}
