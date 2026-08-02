package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseProcurementQualityDecisionResult {
    private Long operationId;
    private String receiptId;
    private String receiptLineId;
    private Long receiptVersion;
    private Long receiptLineVersion;
    private Long scheduleFulfillmentVersion;
    private Long financeReceiptEvidenceOperationId;
    private String financeReceiptEvidenceId;
    private Long financeReceiptEvidenceVersion;
    private String receiptStatus;
    private String receiptLineStatus;
    private BigDecimal pendingQualityQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
    private boolean duplicate;
}
