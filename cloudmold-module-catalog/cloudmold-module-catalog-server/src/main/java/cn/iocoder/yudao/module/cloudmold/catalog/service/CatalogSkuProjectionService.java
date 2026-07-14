package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogSkuProjectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogSkuProjectionService implements CatalogSkuProjectionApi {

    private final CatalogSkuProjectionMapper projectionMapper;

    @Override
    public CatalogSkuProjectionView getActiveSku(String canonicalSkuId) {
        if (canonicalSkuId == null || canonicalSkuId.isBlank()) {
            throw new IllegalArgumentException("canonicalSkuId is required");
        }
        CatalogSkuProjectionView view = projectionMapper.selectActiveSku(
                TenantContextHolder.getRequiredTenantId(), canonicalSkuId);
        if (view == null) {
            throw new IllegalArgumentException("canonical SKU is not projection-ready ACTIVE Catalog data");
        }
        return view;
    }

}
