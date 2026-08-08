package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class CanonicalSalesContractActorPrincipalAdapterTest {

    private final IdentityQueryApi identityQueryApi = mock(IdentityQueryApi.class);
    private final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
    private final CanonicalSalesContractActorPrincipalAdapter adapter =
            new CanonicalSalesContractActorPrincipalAdapter(identityQueryApi, principalValidationApi);

    @Test
    void resolvesAuthenticatedAdminThroughCanonicalIdentity() {
        when(identityQueryApi.resolveActiveSource(any())).thenReturn(SourceIdentityView.builder()
                .principalId("principal-admin-7")
                .principalStatus("ACTIVE")
                .sourceStatus("ACTIVE")
                .build());

        assertThat(adapter.resolveSystemAdmin(7L)).isEqualTo("principal-admin-7");
        verify(identityQueryApi).resolveActiveSource(argThat(reference ->
                reference.getSourceSystem().equals("SYSTEM")
                        && reference.getSourceType().equals("SYSTEM_ADMIN_USER")
                        && reference.getSourceId().equals("7")));
    }

    @Test
    void failsClosedWhenCanonicalMappingIsMissing() {
        assertThatThrownBy(() -> adapter.resolveSystemAdmin(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("active canonical actor Principal is required");
    }
}
