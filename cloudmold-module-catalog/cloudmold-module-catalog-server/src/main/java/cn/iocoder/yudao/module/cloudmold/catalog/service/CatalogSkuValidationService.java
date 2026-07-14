package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.CatalogSkuDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogLifecycleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogSkuValidationService implements CatalogSkuValidationApi {

    private final CatalogLifecycleMapper lifecycleMapper;

    @Override
    public void requireActiveSku(String canonicalSkuId) {
        if (canonicalSkuId == null || canonicalSkuId.isBlank()) {
            throw new IllegalArgumentException("canonicalSkuId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CatalogSkuDO sku = lifecycleMapper.selectSku(tenantId, canonicalSkuId);
        if (sku == null) {
            throw new IllegalArgumentException("canonical SKU does not exist in Catalog");
        }
        if (sku.getStatus() == null || sku.getStatus() != 10) {
            throw new IllegalArgumentException("canonical SKU is not ACTIVE");
        }
    }
}
