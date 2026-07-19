package cn.iocoder.yudao.module.cloudmold.payment.service.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentPageRow {

    private String paymentId;
    private String paymentNo;
    private String orderId;
    private String status;
    private Long payableAmountMinor;
    private Long capturedAmountMinor;
    private Long refundedAmountMinor;
    private String currencyCode;
    private String providerCode;
    private String providerTransactionReference;
    private Boolean testMode;
    private Long aggregateVersion;
    private LocalDateTime capturedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
