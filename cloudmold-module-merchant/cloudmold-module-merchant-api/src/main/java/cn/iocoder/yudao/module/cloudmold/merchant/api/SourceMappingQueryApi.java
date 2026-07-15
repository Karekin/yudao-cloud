package cn.iocoder.yudao.module.cloudmold.merchant.api;

/** Fail-closed query port for qualified Merchant source mappings. */
public interface SourceMappingQueryApi {
    SourceMappingView resolveActive(SourceReference reference);
}
