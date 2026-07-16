package cn.iocoder.yudao.module.cloudmold.order.api;

import java.math.BigDecimal;

public interface OrderAfterSaleQueryApi {
    OrderAfterSaleView requireEligible(String orderId, String orderItemId);

    OrderAfterSaleView requireEligible(String orderId, String orderItemId, BigDecimal requestedQuantity);
}
