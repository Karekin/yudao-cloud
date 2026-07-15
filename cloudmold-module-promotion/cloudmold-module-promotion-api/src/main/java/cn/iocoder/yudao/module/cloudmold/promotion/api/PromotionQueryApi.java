package cn.iocoder.yudao.module.cloudmold.promotion.api;

public interface PromotionQueryApi {
    PromotionAggregateView get(String aggregateType, String aggregateId);
}
