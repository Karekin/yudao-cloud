package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSpuValidationApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.reference.CatalogSpuReferenceValidationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Fail-closed Engagement adapter to canonical Catalog SPU authority. */
@Component
@RequiredArgsConstructor
public class CanonicalCatalogSpuReferenceValidationAdapter implements CatalogSpuReferenceValidationPort {

    private final CatalogSpuValidationApi catalogSpuValidationApi;

    @Override
    public void requireActive(Long tenantId, String canonicalSpuId) {
        requireCurrentTenant(tenantId);
        catalogSpuValidationApi.requireActiveSpu(canonicalSpuId);
    }

    private static void requireCurrentTenant(Long tenantId) {
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), tenantId)) {
            throw new IllegalArgumentException("Catalog SPU reference tenant does not match current tenant");
        }
    }
}
