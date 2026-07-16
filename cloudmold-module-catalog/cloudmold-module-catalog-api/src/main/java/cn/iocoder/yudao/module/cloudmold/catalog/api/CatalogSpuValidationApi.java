package cn.iocoder.yudao.module.cloudmold.catalog.api;

/** Read-only validation boundary for canonical Catalog SPU references. */
public interface CatalogSpuValidationApi {
    void requireActiveSpu(String canonicalSpuId);
}
