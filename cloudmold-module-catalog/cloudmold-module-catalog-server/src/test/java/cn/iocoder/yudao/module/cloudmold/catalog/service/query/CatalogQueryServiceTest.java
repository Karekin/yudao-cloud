package cn.iocoder.yudao.module.cloudmold.catalog.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.controller.admin.vo.CatalogSkuPageReqVO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogQueryServiceTest {

    private final CatalogQueryMapper mapper = mock(CatalogQueryMapper.class);
    private final CatalogQueryService service = new CatalogQueryService(mapper);

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
        CatalogSkuPageReqVO request = new CatalogSkuPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setSkuCode("  SKU-RED  ");
        request.setSpuCode("   ");
        request.setStatus(10);
        CatalogSkuPageItem item = new CatalogSkuPageItem();
        item.setCanonicalSkuId("sku-1");
        when(mapper.countSkuPage(7L, "SKU-RED", null, 10)).thenReturn(21L);
        when(mapper.selectSkuPage(7L, "SKU-RED", null, 10, 20L, 20)).thenReturn(List.of(item));

        PageResult<CatalogSkuPageItem> result = service.getSkuPage(request);

        assertThat(result.getTotal()).isEqualTo(21L);
        assertThat(result.getList()).containsExactly(item);
        verify(mapper).selectSkuPage(7L, "SKU-RED", null, 10, 20L, 20);
    }

    @Test
    void shouldAvoidPageQueryWhenNoSkuMatches() {
        CatalogSkuPageReqVO request = new CatalogSkuPageReqVO();
        when(mapper.countSkuPage(7L, null, null, null)).thenReturn(0L);

        PageResult<CatalogSkuPageItem> result = service.getSkuPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(mapper, never()).selectSkuPage(anyLong(), any(), any(), any(), anyLong(), anyInt());
    }
}
