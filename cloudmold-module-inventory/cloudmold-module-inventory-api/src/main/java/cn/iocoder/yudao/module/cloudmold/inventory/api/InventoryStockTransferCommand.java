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
public class InventoryStockTransferCommand {
    private InventoryStockTransferOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String movementGroupId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String sourceWarehouseId;
    private String sourceLocationId;
    private String targetWarehouseId;
    private String targetLocationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal quantity;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private String businessNo;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
