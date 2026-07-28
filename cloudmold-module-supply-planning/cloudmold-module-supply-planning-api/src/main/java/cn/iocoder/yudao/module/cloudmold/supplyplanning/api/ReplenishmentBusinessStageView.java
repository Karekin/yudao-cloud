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
    private String procurementOrderId;
    private String procurementOrderNo;
    private String procurementOrderStatus;
    private String projectionSourceSystem;
    private String projectionDocumentType;
    private String projectionExternalDocumentId;
    private String projectionExternalDocumentNo;
    private String projectionDocumentStatus;
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
