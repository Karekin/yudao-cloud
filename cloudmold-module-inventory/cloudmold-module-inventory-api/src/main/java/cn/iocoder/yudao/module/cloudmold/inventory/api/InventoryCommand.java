package cn.iocoder.yudao.module.cloudmold.inventory.api;

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
public class InventoryCommand {

    private InventoryOperation operation;
    private String idempotencyKey;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String stockStatus;
    private String qualityStatus;
    private String uomCode;
    private BigDecimal quantity;
    private String reservationId;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private String businessNo;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private String cancellationSagaId;
    private Integer cancellationStepOrdinal;
    private Instant occurredAt;

}
