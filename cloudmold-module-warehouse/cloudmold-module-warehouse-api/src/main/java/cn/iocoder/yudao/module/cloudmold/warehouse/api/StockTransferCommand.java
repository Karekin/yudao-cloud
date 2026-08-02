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
}
