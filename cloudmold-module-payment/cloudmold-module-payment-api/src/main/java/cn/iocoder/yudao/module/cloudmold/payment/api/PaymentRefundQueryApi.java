package cn.iocoder.yudao.module.cloudmold.payment.api;

public interface PaymentRefundQueryApi {
    PaymentRefundView requireRefundable(String orderId, String paymentId, Long amountMinor, String currencyCode);
    PaymentRefundView requireRefunded(String orderId, String paymentId, Long amountMinor, String currencyCode);
}
