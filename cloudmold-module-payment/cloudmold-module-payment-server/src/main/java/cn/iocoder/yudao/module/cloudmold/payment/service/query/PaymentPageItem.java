package cn.iocoder.yudao.module.cloudmold.payment.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 Payment 分页项")
@Data
public class PaymentPageItem {

    private String paymentId;
    private String paymentNo;
    private String orderId;
    private String status;
    private Long payableAmountMinor;
    private Long capturedAmountMinor;
    private Long refundedAmountMinor;
    private Long remainingAmountMinor;
    private String currencyCode;
    private String providerCode;
    private String providerTransactionReferenceMasked;
    private Boolean testMode;
    @Schema(description = "INTERNAL_TEST 表示测试支付；LIVE 表示非测试语义")
    private String executionMode;
    private Long aggregateVersion;
    private LocalDateTime capturedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
