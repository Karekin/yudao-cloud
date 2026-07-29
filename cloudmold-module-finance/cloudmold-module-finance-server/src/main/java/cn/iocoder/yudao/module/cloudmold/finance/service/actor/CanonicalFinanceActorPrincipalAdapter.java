package cn.iocoder.yudao.module.cloudmold.finance.service.actor;

import cn.iocoder.yudao.module.cloudmold.identity.api.IdentityQueryApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityReference;
import cn.iocoder.yudao.module.cloudmold.identity.api.SourceIdentityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanonicalFinanceActorPrincipalAdapter implements FinanceActorPrincipalPort {
    private static final String SOURCE_SYSTEM = "SYSTEM";
    private static final String SOURCE_TYPE = "SYSTEM_ADMIN_USER";

    private final IdentityQueryApi identityQueryApi;
    private final PrincipalValidationApi principalValidationApi;

    @Override
    public String resolveSystemAdmin(Long loginUserId) {
        if (loginUserId == null) {
            throw new IllegalStateException("authenticated admin user is required");
        }
        SourceIdentityView source = identityQueryApi.resolveActiveSource(
                new SourceIdentityReference(SOURCE_SYSTEM, SOURCE_TYPE, loginUserId.toString()));
        if (source == null || source.getPrincipalId() == null || source.getPrincipalId().isBlank()
                || !"ACTIVE".equals(source.getPrincipalStatus())
                || !"ACTIVE".equals(source.getSourceStatus())) {
            throw new IllegalStateException("active canonical actor Principal is required");
        }
        return source.getPrincipalId();
    }

    @Override
    public void requireActive(String principalId) {
        principalValidationApi.requireActivePrincipal(principalId);
    }
}
