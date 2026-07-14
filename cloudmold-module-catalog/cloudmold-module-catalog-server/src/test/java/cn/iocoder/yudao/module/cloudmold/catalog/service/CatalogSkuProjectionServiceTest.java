package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogSkuProjectionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogSkuProjectionServiceTest {

    private final CatalogSkuProjectionMapper mapper = mock(CatalogSkuProjectionMapper.class);
    private final CatalogSkuProjectionService service = new CatalogSkuProjectionService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnSameTenantActiveProjection() {
        CatalogSkuProjectionView expected = new CatalogSkuProjectionView();
        expected.setCanonicalSkuId("sku-1");
        expected.setCatalogStatus("ACTIVE");
        expected.setAggregateVersion(2L);
        when(mapper.selectActiveSku(1L, "sku-1")).thenReturn(expected);

        CatalogSkuProjectionView result = service.getActiveSku("sku-1");

        assertThat(result).isSameAs(expected);
        verify(mapper).selectActiveSku(1L, "sku-1");
    }

    @Test
    void shouldRejectSkuWithoutProjectionReadyHierarchy() {
        when(mapper.selectActiveSku(1L, "sku-inactive")).thenReturn(null);

        assertThatThrownBy(() -> service.getActiveSku("sku-inactive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canonical SKU is not projection-ready ACTIVE Catalog data");
    }
}
