package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSpuValidationApi;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.CatalogSkuDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.CatalogSpuDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogLifecycleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogSkuValidationService implements CatalogSkuValidationApi, CatalogSpuValidationApi {

    private static final int SKU_STATUS_ACTIVE = 10;
    private static final int SPU_STATUS_ACTIVE = 30;

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
        if (sku.getStatus() == null || sku.getStatus() != SKU_STATUS_ACTIVE) {
            throw new IllegalArgumentException("canonical SKU is not ACTIVE");
        }
    }

    @Override
    public void requireActiveSpu(String canonicalSpuId) {
        if (canonicalSpuId == null || canonicalSpuId.isBlank()) {
            throw new IllegalArgumentException("canonicalSpuId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CatalogSpuDO spu = lifecycleMapper.selectSpu(tenantId, canonicalSpuId);
        if (spu == null) {
            throw new IllegalArgumentException("canonical SPU does not exist in Catalog");
        }
        if (spu.getStatus() == null || spu.getStatus() != SPU_STATUS_ACTIVE) {
            throw new IllegalArgumentException("canonical SPU is not ACTIVE");
        }
    }
}
