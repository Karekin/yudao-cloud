package cn.iocoder.yudao.module.cloudmold.payment.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCommand {
    private PaymentOperation operation;
    private String idempotencyKey;
    private String runId;
    private String paymentId;
    private Long expectedVersion;
    private String orderId;
    private Long amountMinor;
    private String currencyCode;
    private String providerCode;
    private String providerTransactionId;
    private String reason;
    private String correlationId;
    private String causationId;
    private String cancellationSagaId;
    private Integer cancellationStepOrdinal;
    private Instant occurredAt;
}
