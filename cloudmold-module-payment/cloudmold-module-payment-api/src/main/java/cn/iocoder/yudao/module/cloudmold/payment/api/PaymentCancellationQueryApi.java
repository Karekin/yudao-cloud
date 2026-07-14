package cn.iocoder.yudao.module.cloudmold.payment.api;

public interface PaymentCancellationQueryApi {
    PaymentCancellationView requireCaptured(String orderId, String paymentId);
    PaymentCancellationView requireRefunded(String orderId, String paymentId);
}
