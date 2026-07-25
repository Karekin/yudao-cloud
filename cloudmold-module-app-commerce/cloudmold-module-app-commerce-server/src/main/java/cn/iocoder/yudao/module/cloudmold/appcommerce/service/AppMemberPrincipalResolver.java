package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.identity.api.*;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppMemberPrincipalResolver {

    static final String SOURCE_SYSTEM = "MEMBER";
    static final String SOURCE_TYPE = "MEMBER_USER";

    private final IdentityQueryApi identityQueryApi;
    private final IdentityCommandApi identityCommandApi;
    private final MemberUserApi memberUserApi;

    public AppMemberPrincipalView requireCurrent() {
        LoginUser login = SecurityFrameworkUtils.getLoginUser();
        require(login != null && login.getId() != null, "member login is required");
        require(login.getExpiresTime() != null && login.getExpiresTime().isAfter(LocalDateTime.now()),
                "a real, unexpired Member access token is required");
        require(Objects.equals(login.getUserType(), UserTypeEnum.MEMBER.getValue()),
                "only a Member login may access the consumer commerce facade");
        long memberUserId = login.getId();
        var member = memberUserApi.getUser(memberUserId).getCheckedData();
        require(member != null && Objects.equals(member.getStatus(), CommonStatusEnum.ENABLE.getStatus()),
                "member account is not ACTIVE");
        SourceIdentityReference reference = new SourceIdentityReference(
                SOURCE_SYSTEM, SOURCE_TYPE, Long.toString(memberUserId));
        SourceIdentityView source;
        try {
            source = identityQueryApi.resolveActiveSource(reference);
        } catch (IllegalArgumentException notLinked) {
            if (!"active source identity does not exist".equals(notLinked.getMessage())) throw notLinked;
            require(!identityQueryApi.hasHistoricalSource(reference),
                    "member source mapping was revoked and cannot be auto-provisioned");
            source = createOrResolve(memberUserId, reference);
        }
        require("MEMBER".equals(source.getPrincipalType()), "source account is not linked to a MEMBER principal");
        require("ACTIVE".equals(source.getPrincipalStatus()) && "ACTIVE".equals(source.getSourceStatus()),
                "member principal mapping is not ACTIVE");
        return AppMemberPrincipalView.builder().memberUserId(memberUserId)
                .principalId(source.getPrincipalId()).principalStatus(source.getPrincipalStatus())
                .sourceSystem(source.getSourceSystem()).sourceType(source.getSourceType()).build();
    }

    private SourceIdentityView createOrResolve(long memberUserId, SourceIdentityReference reference) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String identityKey = "app-member:" + tenantId + ":" + memberUserId;
        String correlationId = UUID.nameUUIDFromBytes(identityKey.getBytes(StandardCharsets.UTF_8)).toString();
        try {
            identityCommandApi.linkSource(LinkSourceIdentityCommand.builder()
                    .idempotencyKey("app-member-link:" + tenantId + ":" + memberUserId)
                    .runId(identityKey).principalType("MEMBER")
                    .sourceSystem(SOURCE_SYSTEM).sourceType(SOURCE_TYPE)
                    .sourceId(Long.toString(memberUserId)).correlationId(correlationId)
                    .occurredAt(Instant.now()).build());
        } catch (IllegalArgumentException concurrentLink) {
            // A concurrent first request may have committed the same trusted source mapping.
        }
        return identityQueryApi.resolveActiveSource(reference);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
