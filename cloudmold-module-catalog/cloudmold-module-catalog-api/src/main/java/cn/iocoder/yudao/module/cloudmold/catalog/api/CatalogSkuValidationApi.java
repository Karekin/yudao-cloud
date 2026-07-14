package cn.iocoder.yudao.module.cloudmold.catalog.api;

public interface CatalogSkuValidationApi {
    void requireActiveSku(String canonicalSkuId);
}
