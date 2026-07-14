package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCommand {
    private OrderOperation operation;
    private String idempotencyKey;
    private String runId;
    private String orderId;
    private Long expectedVersion;
    private String buyerId;
    private List<OrderLineCommand> items;
    private Long shippingAmountMinor;
    private Long discountAmountMinor;
    private String currencyCode;
    private List<OrderLineReference> reservationReferences;
    private String paymentId;
    private String fulfillmentId;
    private String shipmentId;
    private String refundId;
    private String cancellationSagaId;
    private String cancellationMode;
    private Integer cancellationStepOrdinal;
    private String reason;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
