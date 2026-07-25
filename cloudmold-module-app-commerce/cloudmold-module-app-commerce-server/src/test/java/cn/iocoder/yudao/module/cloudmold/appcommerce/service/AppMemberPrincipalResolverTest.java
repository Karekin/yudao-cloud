package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.identity.api.*;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppMemberPrincipalResolverTest {

    private final IdentityQueryApi identityQueryApi = mock(IdentityQueryApi.class);
    private final IdentityCommandApi identityCommandApi = mock(IdentityCommandApi.class);
    private final MemberUserApi memberUserApi = mock(MemberUserApi.class);
    private final AppMemberPrincipalResolver resolver =
            new AppMemberPrincipalResolver(identityQueryApi, identityCommandApi, memberUserApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
        LoginUser login = new LoginUser();
        login.setId(1001L);
        login.setUserType(UserTypeEnum.MEMBER.getValue());
        login.setExpiresTime(LocalDateTime.now().plusHours(1));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null));
        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(1001L);
        member.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(memberUserApi.getUser(1001L)).thenReturn(CommonResult.success(member));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void neverLinkedMemberShouldBeProvisionedFromTrustedMemberIdentity() {
        SourceIdentityView active = activeSource();
        when(identityQueryApi.resolveActiveSource(any()))
                .thenThrow(new IllegalArgumentException("active source identity does not exist"))
                .thenReturn(active);
        when(identityQueryApi.hasHistoricalSource(any())).thenReturn(false);
        when(identityCommandApi.linkSource(any())).thenReturn(LinkSourceIdentityResult.builder()
                .principalId("principal-member-1").sourceIdentityId("source-member-1").build());

        AppMemberPrincipalView result = resolver.requireCurrent();

        assertThat(result.getPrincipalId()).isEqualTo("principal-member-1");
        verify(identityCommandApi).linkSource(argThat(command ->
                "MEMBER".equals(command.getPrincipalType())
                        && "MEMBER".equals(command.getSourceSystem())
                        && "MEMBER_USER".equals(command.getSourceType())
                        && "1001".equals(command.getSourceId())));
    }

    @Test
    void historicallyRevokedMappingMustNeverBeAutoProvisioned() {
        when(identityQueryApi.resolveActiveSource(any()))
                .thenThrow(new IllegalArgumentException("active source identity does not exist"));
        when(identityQueryApi.hasHistoricalSource(any())).thenReturn(true);

        assertThatThrownBy(resolver::requireCurrent)
                .hasMessage("member source mapping was revoked and cannot be auto-provisioned");
        verifyNoInteractions(identityCommandApi);
    }

    @Test
    void mockTokenWithoutExpiryMustBeRejectedBeforeIdentityLookup() {
        LoginUser login = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        login.setExpiresTime(null);

        assertThatThrownBy(resolver::requireCurrent)
                .hasMessage("a real, unexpired Member access token is required");
        verifyNoInteractions(identityQueryApi, identityCommandApi, memberUserApi);
    }

    private static SourceIdentityView activeSource() {
        return SourceIdentityView.builder().sourceIdentityId("source-member-1")
                .principalId("principal-member-1").principalType("MEMBER").principalStatus("ACTIVE")
                .sourceSystem("MEMBER").sourceType("MEMBER_USER").sourceId("1001").sourceStatus("ACTIVE")
                .principalVersion(1L).sourceVersion(1L).build();
    }
}
