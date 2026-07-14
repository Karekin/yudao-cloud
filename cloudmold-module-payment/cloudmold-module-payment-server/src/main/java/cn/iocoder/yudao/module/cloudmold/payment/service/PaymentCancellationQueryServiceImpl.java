package cn.iocoder.yudao.module.cloudmold.payment.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.PaymentDO;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentCancellationQueryServiceImpl implements PaymentCancellationQueryApi {
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentCancellationView requireCaptured(String orderId, String paymentId) {
        return requireStatus(orderId, paymentId, "CAPTURED");
    }

    @Override
    public PaymentCancellationView requireRefunded(String orderId, String paymentId) {
        return requireStatus(orderId, paymentId, "REFUNDED");
    }

    private PaymentCancellationView requireStatus(String orderId, String paymentId, String status) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PaymentDO payment = paymentMapper.selectForUpdate(tenantId, paymentId);
        require(payment != null, "canonical payment does not exist");
        require(Objects.equals(payment.getOrderId(), orderId), "payment does not belong to canonical order");
        require(status.equals(payment.getStatus()), "canonical payment is not " + status);
        return PaymentCancellationView.builder().paymentId(payment.getPaymentId()).orderId(payment.getOrderId())
                .status(payment.getStatus()).aggregateVersion(payment.getVersion())
                .capturedAmountMinor(payment.getCapturedAmountMinor())
                .refundedAmountMinor(payment.getRefundedAmountMinor()).currencyCode(payment.getCurrencyCode())
                .providerCode(payment.getProviderCode()).testMode(payment.getTestMode()).build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
