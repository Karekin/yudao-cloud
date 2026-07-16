package cn.iocoder.yudao.module.cloudmold.payment.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentRefundQueryApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentRefundView;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.PaymentDO;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.PaymentTransactionDO;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.PaymentMapper;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.PaymentTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentRefundQueryServiceImpl implements PaymentRefundQueryApi {
    private final PaymentMapper paymentMapper;
    private final PaymentTransactionMapper transactionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundView requireRefundable(String orderId, String paymentId, Long amountMinor,
                                               String currencyCode) {
        return require(orderId, paymentId, amountMinor, currencyCode, true, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundView requireRefunded(String orderId, String paymentId, Long amountMinor,
                                             String currencyCode) {
        return require(orderId, paymentId, amountMinor, currencyCode, false, true);
    }

    private PaymentRefundView require(String orderId, String paymentId, Long amountMinor, String currencyCode,
                                      boolean refundable, boolean requireTransaction) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PaymentDO payment = paymentMapper.selectForUpdate(tenantId, paymentId);
        require(payment != null, "canonical payment does not exist");
        require(Objects.equals(orderId, payment.getOrderId()), "payment does not belong to canonical order");
        if (refundable) {
            require("CAPTURED".equals(payment.getStatus()) || "PARTIALLY_REFUNDED".equals(payment.getStatus()),
                    "canonical payment is not refundable");
        } else {
            require("REFUNDED".equals(payment.getStatus()), "canonical payment is not REFUNDED");
        }
        require(Boolean.TRUE.equals(payment.getTestMode()) && "INTERNAL_TEST".equals(payment.getProviderCode()),
                "after-sale first slice supports INTERNAL_TEST payment only");
        long remaining = Math.subtractExact(payment.getCapturedAmountMinor(), payment.getRefundedAmountMinor());
        require(Objects.equals(currencyCode, payment.getCurrencyCode()) && "CNY".equals(currencyCode)
                        && amountMinor != null && amountMinor > 0
                        && (refundable ? amountMinor <= remaining
                        : Objects.equals(amountMinor, payment.getCapturedAmountMinor())),
                "refund amount or currency does not reconcile with remaining capture");
        PaymentTransactionDO refund = transactionMapper.selectLatestRefund(tenantId, paymentId);
        require(!requireTransaction || refund != null, "immutable refund transaction is missing");
        if (refund != null) {
            require(refundable || payment.getRefundedAmountMinor().equals(payment.getCapturedAmountMinor()),
                    "refund transaction aggregate is incomplete");
        }
        return PaymentRefundView.builder().paymentId(payment.getPaymentId()).orderId(payment.getOrderId())
                .status(payment.getStatus()).aggregateVersion(payment.getVersion())
                .capturedAmountMinor(payment.getCapturedAmountMinor())
                .refundedAmountMinor(payment.getRefundedAmountMinor())
                .remainingRefundableAmountMinor(remaining).currencyCode(payment.getCurrencyCode())
                .providerCode(payment.getProviderCode()).testMode(payment.getTestMode())
                .refundTransactionId(refund == null ? null : refund.getTransactionId())
                .providerRefundTransactionId(refund == null ? null : refund.getProviderTransactionId()).build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
