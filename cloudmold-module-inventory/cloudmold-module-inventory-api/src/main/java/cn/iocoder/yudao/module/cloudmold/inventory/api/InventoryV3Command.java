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
public class InventoryV3Command {
    private InventoryV3Operation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal quantity;
    /**
     * Optional authoritative cost evidence supplied by Procurement/Finance for an on-hand movement.
     * When present, the complete cost tuple is required and the Inventory event is emitted as schema v5.
     */
    private Long unitCostAmountMinor;
    private Long movementCostAmountMinor;
    private String currencyCode;
    private String costSourceSystem;
    private String costSourceRef;
    private String costPolicyVersion;
    private String reservationId;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private String businessNo;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
