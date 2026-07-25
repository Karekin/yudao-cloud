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
public class InventoryCheckoutReservationCommand {
    private String idempotencyKey;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String orderId;
    private String orderItemId;
    private String orderNo;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
