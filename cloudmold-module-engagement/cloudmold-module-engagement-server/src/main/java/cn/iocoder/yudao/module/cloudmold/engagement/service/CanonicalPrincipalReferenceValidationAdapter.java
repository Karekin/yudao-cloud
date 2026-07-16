package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.engagement.api.reference.PrincipalReferenceValidationPort;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Fail-closed Engagement adapter to canonical Principal authority. */
@Component
@RequiredArgsConstructor
public class CanonicalPrincipalReferenceValidationAdapter implements PrincipalReferenceValidationPort {

    private final PrincipalValidationApi principalValidationApi;

    @Override
    public void requireActive(Long tenantId, String principalId) {
        requireCurrentTenant(tenantId);
        principalValidationApi.requireActivePrincipal(principalId);
    }

    private static void requireCurrentTenant(Long tenantId) {
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), tenantId)) {
            throw new IllegalArgumentException("Principal reference tenant does not match current tenant");
        }
    }
}
