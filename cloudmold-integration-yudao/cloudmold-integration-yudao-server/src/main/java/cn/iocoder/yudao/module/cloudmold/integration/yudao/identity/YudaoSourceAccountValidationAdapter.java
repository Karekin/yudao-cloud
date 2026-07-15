package cn.iocoder.yudao.module.cloudmold.integration.yudao.identity;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.identity.api.source.SourceAccountValidationPort;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Validates Yudao source accounts through their public APIs. No upstream Mapper, DO, or internal service crosses
 * this anti-corruption boundary.
 */
@Component
@RequiredArgsConstructor
public class YudaoSourceAccountValidationAdapter implements SourceAccountValidationPort {

    public static final String MEMBER = "MEMBER";
    public static final String MEMBER_USER = "MEMBER_USER";
    public static final String SYSTEM = "SYSTEM";
    public static final String SYSTEM_ADMIN_USER = "SYSTEM_ADMIN_USER";

    private final MemberUserApi memberUserApi;
    private final AdminUserApi adminUserApi;

    @Override
    public boolean supports(String sourceSystem, String sourceType) {
        return (MEMBER.equals(sourceSystem) && MEMBER_USER.equals(sourceType))
                || (SYSTEM.equals(sourceSystem) && SYSTEM_ADMIN_USER.equals(sourceType));
    }

    @Override
    public void requireActive(Long tenantId, String sourceSystem, String sourceType, String sourceId) {
        require(tenantId != null && tenantId > 0, "source account tenant is required");
        require(Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()),
                "source account tenant does not match current tenant");
        require(supports(sourceSystem, sourceType), "source account type is not supported");
        Long numericSourceId = parseSourceId(sourceId);
        try {
            if (MEMBER.equals(sourceSystem)) {
                requireActiveMember(numericSourceId);
            } else {
                requireActiveAdmin(numericSourceId);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("source account validation failed", exception);
        }
    }

    private void requireActiveMember(Long sourceId) {
        MemberUserRespDTO user = memberUserApi.getUser(sourceId).getCheckedData();
        require(user != null && Objects.equals(user.getId(), sourceId), "source member user does not exist");
        require(CommonStatusEnum.isEnable(user.getStatus()), "source member user is not active");
    }

    private void requireActiveAdmin(Long sourceId) {
        AdminUserRespDTO user = adminUserApi.getUser(sourceId).getCheckedData();
        require(user != null && Objects.equals(user.getId(), sourceId), "source admin user does not exist");
        require(CommonStatusEnum.isEnable(user.getStatus()), "source admin user is not active");
    }

    private static Long parseSourceId(String sourceId) {
        require(sourceId != null && !sourceId.isBlank(), "source account id is required");
        try {
            long parsed = Long.parseLong(sourceId);
            require(parsed > 0, "source account id must be positive");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("source account id must be a positive integer", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
