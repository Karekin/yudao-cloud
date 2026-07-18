package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationSagaCommand {
    private OrderCancellationSagaOperation operation;
    private String cancellationMode;
    private String responsibilityParty;
    private String responsibilityCode;
    private String idempotencyKey;
    private String runId;
    private String sagaId;
    private Long expectedVersion;
    private String orderId;
    private String reason;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
