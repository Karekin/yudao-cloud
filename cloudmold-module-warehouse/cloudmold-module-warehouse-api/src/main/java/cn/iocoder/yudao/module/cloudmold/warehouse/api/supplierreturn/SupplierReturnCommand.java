package cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn;

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
public class SupplierReturnCommand implements Serializable {
    private SupplierReturnOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String returnId;
    private Long expectedVersion;
    private CreateDefinition create;
    private DispatchBatchDefinition dispatchBatch;
    private CancelDefinition cancel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDefinition implements Serializable {
        private String returnId;
        private String returnCode;
        private String purchaseOrderId;
        private String receiptId;
        private String reasonCode;
        private String remark;
        private List<LineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDefinition implements Serializable {
        private String returnLineId;
        private Integer lineNumber;
        private String qualityDecisionId;
        private Long decisionVersion;
        private SupplierReturnSourceDisposition sourceDisposition;
        private BigDecimal returnQuantity;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchBatchDefinition implements Serializable {
        private String batchId;
        private String batchNo;
        private String remark;
        private List<DispatchLineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchLineDefinition implements Serializable {
        private String executionLineId;
        private String returnLineId;
        private Integer lineNumber;
        private BigDecimal dispatchQuantity;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelDefinition implements Serializable {
        private String reasonCode;
        private String remark;
    }
}
