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
    private String currencyCode;
    private String returnFulfillmentId;
    private String returnFulfillmentStatus;
    private String returnShipmentId;
    private String inspectionId;
    private String resolutionSagaId;
    private String resolutionSagaStatus;
    private Long resolutionSagaVersion;
    private Long paymentRefundTransactionId;
    private Long inventoryOperationId;
    private Long inventoryLedgerTransactionId;
    private Boolean duplicate;
}
