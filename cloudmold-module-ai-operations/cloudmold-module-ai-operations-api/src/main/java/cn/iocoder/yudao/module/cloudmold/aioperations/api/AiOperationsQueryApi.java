package cn.iocoder.yudao.module.cloudmold.aioperations.api;

public interface AiOperationsQueryApi {
    AiOperationsAggregateView get(String aggregateType, String aggregateId);
}
