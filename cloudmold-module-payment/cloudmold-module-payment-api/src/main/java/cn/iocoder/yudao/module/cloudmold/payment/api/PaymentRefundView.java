package cn.iocoder.yudao.module.cloudmold.payment.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundView {
    private String paymentId;
    private String orderId;
    private String status;
    private Long aggregateVersion;
    private Long capturedAmountMinor;
    private Long refundedAmountMinor;
    private Long remainingRefundableAmountMinor;
    private String currencyCode;
    private String providerCode;
    private Boolean testMode;
    private Long refundTransactionId;
    private String providerRefundTransactionId;
}
