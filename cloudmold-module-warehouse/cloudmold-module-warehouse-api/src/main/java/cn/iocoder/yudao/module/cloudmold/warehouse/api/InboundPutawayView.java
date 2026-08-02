package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundPutawayView implements Serializable {
    private String putawayId;
    private String receiptId;
    private String receiptNo;
    private String procurementOrderId;
    private String supplierId;
    private String warehouseId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LineView> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView implements Serializable {
        private String putawayLineId;
        private String receiptLineId;
        private String sourceLocationId;
        private String targetLocationId;
        private String canonicalSkuId;
        private String ownerType;
        private String ownerId;
        private String lotId;
        private String baseUomCode;
        private BigDecimal putawayQuantity;
        private BigDecimal cumulativePutawayQuantity;
        private String status;
        private Long version;
        private String inventoryOperationId;
        private String inventoryLedgerTransactionId;
        private String inventoryMovementGroupId;
        private String inventoryTargetBalanceId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
