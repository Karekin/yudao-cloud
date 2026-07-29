package cn.iocoder.yudao.module.cloudmold.aftersale.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleView {
    private Long operationId;
    private String afterSaleId;
    private String afterSaleNo;
    private String runId;
    private String orderId;
    private String afterSaleItemId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String afterSaleType;
    private String reasonCode;
    private String responsibility;
    private String caseStatus;
    private Long aggregateVersion;
    private String refundStatus;
    private Long approvedAmountMinor;
    private Long grossAmountMinor;
    private Long benefitAmountMinor;
    private Long netAmountMinor;
    private String currencyCode;
    private String returnFulfillmentId;
    private String returnFulfillmentStatus;
    private String returnShipmentId;
    private String inspectionId;
    private String dispositionAssessmentId;
    private String recommendedDisposition;
    private String recommendedQualityStatus;
    private String dispositionCode;
    private String conditionGrade;
    private Integer dispositionConfidence;
    private String dispositionRationaleCode;
    private String resolutionSagaId;
    private String resolutionSagaStatus;
    private Long resolutionSagaVersion;
    private Long paymentRefundTransactionId;
    private Long inventoryOperationId;
    private Long inventoryLedgerTransactionId;
    private Long disposalOperationId;
    private Long disposalLedgerTransactionId;
    private String orderSettlementEffectId;
    private Long orderSettlementVersion;
    private Boolean orderReturnFull;
    private String benefitReversalStatus;
    private String benefitReversalBatchId;
    private Long benefitReversalAmountMinor;
    private Boolean duplicate;
}
