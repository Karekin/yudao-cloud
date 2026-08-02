package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementReceiptInspectionCommand implements Serializable {
    private ProcurementReceiptInspectionOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String inspectionId;
    private Long expectedVersion;
    private CreateDefinition create;
    private ResultsDefinition results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDefinition implements Serializable {
        private String inspectionId;
        private String inspectionCode;
        private String receiptId;
        private String purchaseOrderId;
        private String supplierId;
        private String ownerType;
        private String ownerId;
        private String businessNo;
        private String standardId;
        private Long standardVersion;
        private String standardVersionId;
        private String standardContentSha256;
        private List<LineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDefinition implements Serializable {
        private String inspectionLineId;
        private Integer lineNumber;
        private String receiptLineId;
        private String itemId;
        private String scheduleId;
        private String canonicalSkuId;
        private String uomCode;
        private String valuationPolicy;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private BigDecimal receivedQuantity;
        private List<SplitDefinition> splits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitDefinition implements Serializable {
        private String inspectionSplitId;
        private Integer splitNumber;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private BigDecimal receivedQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultsDefinition implements Serializable {
        private String resultBatchId;
        private List<LineResultDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineResultDefinition implements Serializable {
        private String inspectionLineId;
        private Long expectedVersion;
        private List<SplitResultDefinition> splits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitResultDefinition implements Serializable {
        private String resultSplitId;
        private String qualityDecisionId;
        private String inspectionSplitId;
        private Long expectedVersion;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private String acceptedDispositionCode;
        private String rejectedDispositionCode;
        private String quarantineDispositionCode;
        private String decisionEvidenceSha256;
        private String evidenceRef;
        private List<DefectDefinition> defects;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefectDefinition implements Serializable {
        private String defectId;
        private String defectCode;
        private String defectCategory;
        private String severity;
        private BigDecimal affectedQuantity;
        private String evidenceSha256;
        private String evidenceRef;
    }
}
