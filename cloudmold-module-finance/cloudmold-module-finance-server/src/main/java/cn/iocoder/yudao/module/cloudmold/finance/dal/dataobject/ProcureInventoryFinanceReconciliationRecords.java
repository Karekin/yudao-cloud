package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProcureInventoryFinanceReconciliationRecords {
    private ProcureInventoryFinanceReconciliationRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class ReconciliationRun {
        private String runId;
        private Long tenantId;
        private String runCode;
        private String legalEntityId;
        private String currencyCode;
        private String reconciliationPolicyVersion;
        private String status;
        private Integer lineCount;
        private Integer matchedCount;
        private Integer differentCount;
        private Integer missingCount;
        private Integer uncomparableCount;
        private String requestedByPrincipalId;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ReconciliationWatermark {
        private String watermarkId;
        private Long tenantId;
        private String runId;
        private String domainCode;
        private String sourceTable;
        private Long maxAggregateVersion;
        private LocalDateTime maxObservedAt;
        private Integer recordCount;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ReconciliationLine {
        private String lineId;
        private Long tenantId;
        private String runId;
        private String lineType;
        private String lineKey;
        private String legalEntityId;
        private String currencyCode;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String deliveryScheduleId;
        private String receiptLineId;
        private String inventoryMovementId;
        private String supplierReturnId;
        private String supplierReturnLineId;
        private String supplierInvoiceId;
        private String supplierInvoiceLineId;
        private String apOpenItemId;
        private String journalEntryId;
        private Long procurementVersion;
        private Long inventoryVersion;
        private Long financeVersion;
        private BigDecimal procurementQuantity;
        private BigDecimal inventoryQuantity;
        private BigDecimal financeQuantity;
        private Long procurementAmountMinor;
        private Long inventoryAmountMinor;
        private Long financeAmountMinor;
        private BigDecimal quantityDifference;
        private Long amountDifferenceMinor;
        private String primaryDifferenceCode;
        private String responsibilityDomain;
        private String matchStatus;
        private Integer differenceCount;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ReconciliationDifference {
        private String differenceId;
        private Long tenantId;
        private String runId;
        private String lineId;
        private String differenceCode;
        private String sourceDomain;
        private String expectedValue;
        private String actualValue;
        private Boolean blocking;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class QualifiedReceiptSource {
        private String legalEntityId;
        private String currencyCode;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String deliveryScheduleId;
        private Long purchaseOrderLineVersion;
        private String receiptLineId;
        private Long receiptLineVersion;
        private String qualityDispositionId;
        private Long qualityDispositionVersion;
        private String inventoryMovementId;
        private Long inventoryMovementVersion;
        private BigDecimal receivedQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal movementQuantity;
        private Long movementCostAmountMinor;
        private String valuationLayerId;
        private Long valuationLayerVersion;
        private Long valuationAmountMinor;
        private String journalEntryId;
        private Long journalVersion;
        private Long journalAmountMinor;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierReturnSource {
        private String legalEntityId;
        private String currencyCode;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String deliveryScheduleId;
        private String receiptLineId;
        private String qualityDecisionId;
        private Long qualityDecisionVersion;
        private String supplierReturnId;
        private String supplierReturnLineId;
        private Long returnLineVersion;
        private String executionLineId;
        private Long executionLineVersion;
        private Long inventoryLedgerTransactionId;
        private Long inventoryAggregateVersion;
        private BigDecimal returnQuantity;
        private BigDecimal dispatchedQuantity;
        private Long movementCostAmountMinor;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierInvoiceSource {
        private String legalEntityId;
        private String currencyCode;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private Long purchaseOrderLineVersion;
        private String supplierInvoiceId;
        private String supplierInvoiceLineId;
        private Long invoiceLineVersion;
        private String apOpenItemId;
        private Long apOpenItemVersion;
        private String journalEntryId;
        private Long journalVersion;
        private String inventoryMovementId;
        private Long inventoryMovementVersion;
        private BigDecimal invoiceQuantity;
        private BigDecimal allocatedQuantity;
        private Long invoiceGrossAmountMinor;
        private Long matchedInventoryAmountMinor;
        private Long apOpenAmountMinor;
        private Long journalAmountMinor;
        private String matchResultStatus;
        private Integer openExceptionCount;
    }
}
