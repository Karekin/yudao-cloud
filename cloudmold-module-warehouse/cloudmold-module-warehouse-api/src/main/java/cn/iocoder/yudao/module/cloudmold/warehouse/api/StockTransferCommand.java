package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferCommand {
    private StockTransferOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String requestId;
    private String requestCode;
    private String orderId;
    private String orderCode;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String ownerType;
    private String ownerId;
    private String sourceWarehouseId;
    private String targetWarehouseId;
    private String reasonCode;
    private String remark;
    private List<LineDefinition> lines;
    private OutboundBatchDefinition outboundBatch;
    private ReceiptBatchDefinition receiptBatch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDefinition {
        private String lineId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private BigDecimal requestedQuantity;
        private String uomCode;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundBatchDefinition {
        private String batchId;
        private String batchNo;
        private String remark;
        private List<OutboundLineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundLineDefinition {
        private String executionLineId;
        private Integer lineNumber;
        private BigDecimal quantity;
        private String lotId;
        private String sourceLocationId;
        private String sourceStockStatus;
        private String sourceQualityStatus;
        private String targetLocationId;
        private String targetStockStatus;
        private String targetQualityStatus;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptBatchDefinition {
        private String batchId;
        private String batchNo;
        private String remark;
        private List<ReceiptLineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptLineDefinition {
        private String executionLineId;
        private String outboundExecutionLineId;
        private BigDecimal quantity;
        private String remark;
    }
}
