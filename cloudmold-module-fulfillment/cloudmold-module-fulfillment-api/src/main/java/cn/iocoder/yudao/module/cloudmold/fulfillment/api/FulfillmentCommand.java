package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

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
public class FulfillmentCommand {
    private FulfillmentOperation operation;
    private String idempotencyKey;
    private String runId;
    private String fulfillmentId;
    private Long expectedVersion;
    private String orderId;
    private String sellerId;
    private String warehouseId;
    private List<FulfillmentLineCommand> items;
    private String deliveryPromiseVersionRef;
    private Instant promisedDeliveryAt;
    private String carrierCode;
    private String waybillNo;
    private String reason;
    private String correlationId;
    private String causationId;
    private String cancellationSagaId;
    private Integer cancellationStepOrdinal;
    private Instant occurredAt;
}
