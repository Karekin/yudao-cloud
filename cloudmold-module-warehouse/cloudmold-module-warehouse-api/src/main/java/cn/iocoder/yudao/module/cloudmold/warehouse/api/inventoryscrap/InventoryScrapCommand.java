package cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap;

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
public class InventoryScrapCommand {
    private InventoryScrapOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String actorPrincipalId;
    private Instant occurredAt;
    private String scrapId;
    private String scrapCode;
    private String reasonCode;
    private String remark;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private List<LineDefinition> lines;
    private DispositionBatchDefinition dispositionBatch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDefinition {
        private String lineId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private String locationId;
        private String lotId;
        private String stockStatus;
        private String qualityStatus;
        private String baseUomCode;
        private BigDecimal requestedQuantity;
        private String evidenceType;
        private String evidenceRef;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispositionBatchDefinition {
        private String batchId;
        private String batchNo;
        private String dispositionType;
        private String proofType;
        private String proofRef;
        private String remark;
        private List<DispositionLineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispositionLineDefinition {
        private String dispositionLineId;
        private Integer lineNumber;
        private BigDecimal disposedQuantity;
        private String remark;
    }
}
