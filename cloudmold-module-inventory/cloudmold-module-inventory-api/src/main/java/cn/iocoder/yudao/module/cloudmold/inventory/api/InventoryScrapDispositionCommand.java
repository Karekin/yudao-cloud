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
public class InventoryScrapDispositionCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String dispositionLineId;
    private String scrapDocumentId;
    private String scrapLineId;
    private String dispositionBatchId;
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
    private String businessNo;
    private Instant occurredAt;
}
