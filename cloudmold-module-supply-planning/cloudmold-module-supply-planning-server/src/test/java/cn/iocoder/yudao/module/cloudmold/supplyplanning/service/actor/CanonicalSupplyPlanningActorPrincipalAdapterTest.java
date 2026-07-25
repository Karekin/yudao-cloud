package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor;

import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class CanonicalSupplyPlanningActorPrincipalAdapterTest {

    private final IdentityQueryApi identityQueryApi = mock(IdentityQueryApi.class);
    private final PrincipalValidationApi principalValidationApi =
            mock(PrincipalValidationApi.class);
    private final CanonicalSupplyPlanningActorPrincipalAdapter adapter =
            new CanonicalSupplyPlanningActorPrincipalAdapter(
                    identityQueryApi, principalValidationApi);

    @Test
    void resolvesAuthenticatedAdminThroughCanonicalIdentity() {
        when(identityQueryApi.resolveActiveSource(any())).thenReturn(SourceIdentityView.builder()
                .principalId("principal-admin-42").principalStatus("ACTIVE")
                .sourceStatus("ACTIVE").build());

        assertThat(adapter.resolveSystemAdmin(42L)).isEqualTo("principal-admin-42");
        verify(identityQueryApi).resolveActiveSource(argThat(reference ->
                reference.getSourceSystem().equals("SYSTEM")
                        && reference.getSourceType().equals("SYSTEM_ADMIN_USER")
                        && reference.getSourceId().equals("42")));
    }

    @Test
    void failsClosedWhenCanonicalMappingIsMissing() {
        assertThatThrownBy(() -> adapter.resolveSystemAdmin(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("active canonical actor Principal is required");
    }
}
