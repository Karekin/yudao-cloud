package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferView {
    private String requestId;
    private String requestCode;
    private String requestStatus;
    private Long requestVersion;
    private String orderId;
    private String orderCode;
    private String orderStatus;
    private Long orderVersion;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String ownerType;
    private String ownerId;
    private String sourceWarehouseId;
    private String sourceWarehouseCode;
    private String sourceWarehouseName;
    private String targetWarehouseId;
    private String targetWarehouseCode;
    private String targetWarehouseName;
    private String reasonCode;
    private String remark;
    private String currentStageCode;
    private String currentStageLabel;
    private boolean terminal;
    private List<LineView> lines;
    private List<StatusHistoryView> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView {
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
    public static class StatusHistoryView {
        private String historyId;
        private String businessObjectType;
        private String businessObjectId;
        private String status;
        private Long statusVersion;
        private String stageCode;
        private String stageLabel;
        private LocalDateTime changedAt;
    }
}
