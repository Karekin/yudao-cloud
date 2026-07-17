package cn.iocoder.yudao.module.cloudmold.identity.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityReference;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.PrincipalDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.SourceIdentityDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.PrincipalMapper;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.SourceIdentityMapper;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityQueryServiceTest {

    private final PrincipalMapper principalMapper = mock(PrincipalMapper.class);
    private final SourceIdentityMapper sourceIdentityMapper = mock(SourceIdentityMapper.class);
    private final IdentityQueryService service = new IdentityQueryService(principalMapper, sourceIdentityMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldResolveOnlyActiveSourceAndPrincipalInCurrentTenant() {
        when(sourceIdentityMapper.selectActiveBySource(7L, "MEMBER", "MEMBER_USER", "88"))
                .thenReturn(new SourceIdentityDO().setSourceIdentityId("source-1").setTenantId(7L)
                        .setPrincipalId("principal-1").setSourceSystem("MEMBER").setSourceType("MEMBER_USER")
                        .setSourceId("88").setStatus("ACTIVE").setVersion(1L));
        when(principalMapper.selectByTenantAndId(7L, "principal-1"))
                .thenReturn(new PrincipalDO().setPrincipalId("principal-1").setTenantId(7L)
                        .setPrincipalType("MEMBER").setStatus("ACTIVE").setVersion(1L));

        assertThat(service.resolveActiveSource(new SourceIdentityReference("MEMBER", "MEMBER_USER", "88"))
                .getPrincipalId()).isEqualTo("principal-1");
        assertThatCode(() -> service.requireActivePrincipal("principal-1")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingOrInactivePrincipal() {
        assertThatThrownBy(() -> service.requireActivePrincipal("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canonical Principal does not exist");
        when(principalMapper.selectByTenantAndId(7L, "disabled"))
                .thenReturn(new PrincipalDO().setPrincipalId("disabled").setStatus("DISABLED"));
        assertThatThrownBy(() -> service.requireActivePrincipal("disabled"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canonical Principal is not ACTIVE");
    }

    @Test
    void shouldRejectSourceWhosePrincipalIsNoLongerActive() {
        when(sourceIdentityMapper.selectActiveBySource(7L, "MEMBER", "MEMBER_USER", "88"))
                .thenReturn(new SourceIdentityDO().setPrincipalId("disabled"));
        when(principalMapper.selectByTenantAndId(7L, "disabled"))
                .thenReturn(new PrincipalDO().setPrincipalId("disabled").setStatus("DISABLED"));

        assertThatThrownBy(() -> service.resolveActiveSource(new SourceIdentityReference("MEMBER", "MEMBER_USER", "88")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canonical Principal is not ACTIVE");
    }
}
