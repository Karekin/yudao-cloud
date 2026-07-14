package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.CatalogSkuDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogLifecycleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogSkuValidationServiceTest {

    private final CatalogLifecycleMapper mapper = mock(CatalogLifecycleMapper.class);
    private final CatalogSkuValidationService service = new CatalogSkuValidationService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAcceptActiveSkuInCurrentTenant() {
        when(mapper.selectSku(1L, "sku-active")).thenReturn(new CatalogSkuDO().setSkuId("sku-active").setStatus(10));
        assertThatCode(() -> service.requireActiveSku("sku-active")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingSku() {
        assertThatThrownBy(() -> service.requireActiveSku("missing"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("canonical SKU does not exist in Catalog");
    }

    @Test
    void shouldRejectDraftSku() {
        when(mapper.selectSku(1L, "sku-draft")).thenReturn(new CatalogSkuDO().setSkuId("sku-draft").setStatus(0));
        assertThatThrownBy(() -> service.requireActiveSku("sku-draft"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("canonical SKU is not ACTIVE");
    }
}
