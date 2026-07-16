package cn.iocoder.yudao.module.cloudmold.aftersale.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleCommand {
    private AfterSaleOperation operation;
    private String idempotencyKey;
    private String runId;
    private String afterSaleId;
    private Long expectedVersion;
    private String orderId;
    private String orderItemId;
    private BigDecimal requestedQuantity;
    private String afterSaleType;
    private String reasonCode;
    private String responsibility;
    private String reason;
    private String reviewerId;
    private String carrierCode;
    private String waybillNo;
    private String receiverId;
    private String inspectorId;
    private String qualityStatus;
    private Long resolutionSagaExpectedVersion;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
