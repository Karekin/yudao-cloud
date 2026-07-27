package cn.iocoder.yudao.module.cloudmold.payment.api;

public interface PaymentWorkflowFactsApi {
    PaymentWorkflowFactsView get(String orderId, String paymentId);
}
