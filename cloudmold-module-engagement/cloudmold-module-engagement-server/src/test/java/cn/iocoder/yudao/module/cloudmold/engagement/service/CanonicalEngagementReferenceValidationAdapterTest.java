package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSpuValidationApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CanonicalEngagementReferenceValidationAdapterTest {

    private final PrincipalValidationApi principalApi = mock(PrincipalValidationApi.class);
    private final CatalogSpuValidationApi spuApi = mock(CatalogSpuValidationApi.class);
    private final CanonicalPrincipalReferenceValidationAdapter principalAdapter =
            new CanonicalPrincipalReferenceValidationAdapter(principalApi);
    private final CanonicalCatalogSpuReferenceValidationAdapter spuAdapter =
            new CanonicalCatalogSpuReferenceValidationAdapter(spuApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDelegateReferencesWithinCurrentTenant() {
        assertThatCode(() -> principalAdapter.requireActive(7L, "principal-1")).doesNotThrowAnyException();
        assertThatCode(() -> spuAdapter.requireActive(7L, "spu-1")).doesNotThrowAnyException();
        verify(principalApi).requireActivePrincipal("principal-1");
        verify(spuApi).requireActiveSpu("spu-1");
    }

    @Test
    void shouldRejectCrossTenantReferencesBeforeDelegation() {
        assertThatThrownBy(() -> principalAdapter.requireActive(8L, "principal-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Principal reference tenant does not match current tenant");
        assertThatThrownBy(() -> spuAdapter.requireActive(8L, "spu-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog SPU reference tenant does not match current tenant");
    }
}
