package cn.iocoder.yudao.module.cloudmold.crm.service.actor;

import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class CanonicalCrmActorPrincipalAdapterTest {

    private final IdentityQueryApi identityQueryApi = mock(IdentityQueryApi.class);
    private final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
    private final CanonicalCrmActorPrincipalAdapter adapter =
            new CanonicalCrmActorPrincipalAdapter(identityQueryApi, principalValidationApi);

    @Test
    void resolvesSystemAdminToCanonicalPrincipal() {
        when(identityQueryApi.resolveActiveSource(any())).thenReturn(SourceIdentityView.builder()
                .principalId("principal-admin-1").principalStatus("ACTIVE").sourceStatus("ACTIVE").build());

        assertThat(adapter.resolveSystemAdmin(1L)).isEqualTo("principal-admin-1");
        verify(identityQueryApi).resolveActiveSource(argThat(reference ->
                "SYSTEM".equals(reference.getSourceSystem())
                        && "SYSTEM_ADMIN_USER".equals(reference.getSourceType())
                        && "1".equals(reference.getSourceId())));
    }

    @Test
    void failsClosedWhenCanonicalPrincipalMissing() {
        assertThatThrownBy(() -> adapter.resolveSystemAdmin(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("active canonical actor Principal is required");
    }
}
