package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentBusinessStageView {
    private String recommendationId;
    private String planId;
    private String recommendationStatus;
    private String targetType;
    /**
     * Immutable target-document status captured when the replenishment conversion was created.
     * It is intentionally separate from the current operational status, e.g. stockTransferStatus.
     */
    private String projectionDocumentStatus;
    private String purchaseRequisitionId;
    private String purchaseRequisitionNo;
    private String purchaseRequisitionStatus;
    private String procurementOrderId;
    private String procurementOrderNo;
    private String procurementOrderStatus;
    private String stockTransferId;
    private String stockTransferNo;
    private String stockTransferStatus;
    private String supplierConfirmationStatus;
    private String asnStatus;
    private String receiptStatus;
    private String qualityStatus;
    private String putawayStatus;
    private String nextWaitingEventCode;
    private String nextWaitingEventLabel;
    private String inventoryLedgerTransactionId;
    private String inventoryBalanceId;
}
