package cn.iocoder.yudao.module.cloudmold.engagement.api.reference;

/** Adapter boundary for canonical Catalog SPU authority. */
public interface CatalogSpuReferenceValidationPort {
    void requireActive(Long tenantId, String canonicalSpuId);
}
