package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class YudaoWmsReceiptInventoryBridgeRow {
    private Long bridgeId;
    private Long tenantId;
    private Long receiptOrderId;
    private String receiptOrderNo;
    private Long receiptOrderLineId;
    private Long wmsMerchantId;
    private Long wmsWarehouseId;
    private Long wmsSkuId;
    private String canonicalOwnerId;
    private String canonicalSkuId;
    private String warehouseMappingId;
    private String canonicalWarehouseId;
    private String canonicalZoneId;
    private String canonicalLocationId;
    private String lotMappingStatus;
    private String canonicalLotId;
    private BigDecimal receiptQuantity;
    private String baseUomCode;
    private String inventoryIdempotencyKey;
    private String inventorySourceEventId;
    private String inventoryBusinessId;
    private String inventoryBusinessItemId;
    private Long inventoryOperationId;
    private Long inventoryLedgerTransactionId;
    private String inventoryBalanceId;
    private Long inventoryAggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
