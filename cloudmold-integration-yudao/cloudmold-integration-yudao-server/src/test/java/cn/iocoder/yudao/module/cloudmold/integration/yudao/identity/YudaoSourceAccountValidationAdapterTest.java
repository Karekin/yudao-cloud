package cn.iocoder.yudao.module.cloudmold.integration.yudao.identity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class YudaoSourceAccountValidationAdapterTest {

    private final MemberUserApi memberUserApi = mock(MemberUserApi.class);
    private final AdminUserApi adminUserApi = mock(AdminUserApi.class);
    private final YudaoSourceAccountValidationAdapter adapter =
            new YudaoSourceAccountValidationAdapter(memberUserApi, adminUserApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(9L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSupportOnlyCanonicalYudaoSourceTypes() {
        assertThat(adapter.supports("MEMBER", "MEMBER_USER")).isTrue();
        assertThat(adapter.supports("SYSTEM", "SYSTEM_ADMIN_USER")).isTrue();
        assertThat(adapter.supports("MEMBER", "USER")).isFalse();
        assertThat(adapter.supports("SYSTEM", "ADMIN_USER")).isFalse();
    }

    @Test
    void shouldValidateActiveMemberAndAdminThroughPublicApis() {
        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(81L);
        member.setStatus(0);
        AdminUserRespDTO admin = new AdminUserRespDTO();
        admin.setId(82L);
        admin.setStatus(0);
        when(memberUserApi.getUser(81L)).thenReturn(CommonResult.success(member));
        when(adminUserApi.getUser(82L)).thenReturn(CommonResult.success(admin));

        assertThatCode(() -> adapter.requireActive(9L, "MEMBER", "MEMBER_USER", "81"))
                .doesNotThrowAnyException();
        assertThatCode(() -> adapter.requireActive(9L, "SYSTEM", "SYSTEM_ADMIN_USER", "82"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailClosedForMissingOrDisabledSourceAccount() {
        MemberUserRespDTO disabled = new MemberUserRespDTO();
        disabled.setId(81L);
        disabled.setStatus(1);
        when(memberUserApi.getUser(80L)).thenReturn(CommonResult.success(null));
        when(memberUserApi.getUser(81L)).thenReturn(CommonResult.success(disabled));

        assertThatThrownBy(() -> adapter.requireActive(9L, "MEMBER", "MEMBER_USER", "80"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source member user does not exist");
        assertThatThrownBy(() -> adapter.requireActive(9L, "MEMBER", "MEMBER_USER", "81"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source member user is not active");
    }

    @Test
    void shouldFailClosedForAuthorityErrorOrMismatchedResponse() {
        when(adminUserApi.getUser(82L)).thenThrow(new IllegalStateException("rpc unavailable"));
        AdminUserRespDTO wrong = new AdminUserRespDTO();
        wrong.setId(999L);
        wrong.setStatus(0);
        when(adminUserApi.getUser(83L)).thenReturn(CommonResult.success(wrong));

        assertThatThrownBy(() -> adapter.requireActive(9L, "SYSTEM", "SYSTEM_ADMIN_USER", "82"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source account validation failed")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.requireActive(9L, "SYSTEM", "SYSTEM_ADMIN_USER", "83"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source admin user does not exist");
    }

    @Test
    void shouldRejectTenantMismatchAndMalformedIdsWithoutCallingAuthority() {
        assertThatThrownBy(() -> adapter.requireActive(10L, "MEMBER", "MEMBER_USER", "81"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source account tenant does not match current tenant");
        assertThatThrownBy(() -> adapter.requireActive(9L, "MEMBER", "MEMBER_USER", "not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source account id must be a positive integer");
        assertThatThrownBy(() -> adapter.requireActive(9L, "MEMBER", "MEMBER_USER", "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source account id must be positive");
        verifyNoInteractions(memberUserApi, adminUserApi);
    }
}
