package cn.iocoder.yudao.module.cloudmold.identity.api;

public interface IdentityQueryApi {
    SourceIdentityView resolveActiveSource(SourceIdentityReference reference);
    boolean hasHistoricalSource(SourceIdentityReference reference);
}
