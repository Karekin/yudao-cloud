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
public class StockCountCommand {
    private StockCountOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String stockCountId;
    private String stockCountCode;
    private String countMode;
    private String scopeType;
    private String scopeLabel;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String reasonCode;
    private String remark;
    private String actorPrincipalId;
    private String approvalRemark;
    private List<LineDefinition> lines;
    private CountBatchDefinition countBatch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDefinition {
        private String lineId;
        private Integer lineNumber;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private String stockStatus;
        private String qualityStatus;
        private String baseUomCode;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountBatchDefinition {
        private String batchId;
        private String batchNo;
        private String remark;
        private List<CountLineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountLineDefinition {
        private String executionLineId;
        private String stockCountLineId;
        private BigDecimal countedOnHandQuantity;
        private String remark;
    }
}
