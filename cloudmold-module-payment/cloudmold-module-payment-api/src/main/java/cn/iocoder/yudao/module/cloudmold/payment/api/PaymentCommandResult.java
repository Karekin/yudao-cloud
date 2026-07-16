package cn.iocoder.yudao.module.cloudmold.payment.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCommandResult {
    private Long operationId;
    private Long transactionId;
    private String paymentId;
    private String paymentNo;
    private String orderId;
    private String previousStatus;
    private String currentStatus;
    private Long aggregateVersion;
    private Long capturedAmountMinor;
    private Long refundedAmountMinor;
    private Long transactionAmountMinor;
    private Long remainingRefundableAmountMinor;
    private String currencyCode;
    private Boolean testMode;
    private Boolean duplicate;
}
