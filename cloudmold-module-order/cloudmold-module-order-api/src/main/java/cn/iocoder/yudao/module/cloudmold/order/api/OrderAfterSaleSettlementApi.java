package cn.iocoder.yudao.module.cloudmold.order.api;

public interface OrderAfterSaleSettlementApi {
    OrderAfterSaleSettlementResult record(OrderAfterSaleSettlementCommand command);
}
