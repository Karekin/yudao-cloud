package cn.iocoder.yudao.module.cloudmold.tokenplatform.api;

public interface TokenPlatformQueryApi {
    TokenPlatformAggregateView get(String aggregateType, String aggregateId);
}
