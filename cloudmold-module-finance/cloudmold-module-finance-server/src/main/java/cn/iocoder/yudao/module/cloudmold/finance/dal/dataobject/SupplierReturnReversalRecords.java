package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SupplierReturnReversalRecords {
    private SupplierReturnReversalRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String operationType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateId;
        private String resultJson;
    }

    @Data
    @Accessors(chain = true)
    public static class WarehouseReturn {
        private String returnId;
        private Long tenantId;
        private String returnCode;
        private String purchaseOrderId;
        private String receiptId;
        private String supplierId;
        private String ownerType;
        private String ownerId;
        private String warehouseId;
        private String status;
        private Long version;
        private String reasonCode;
        private String remark;
    }

    @Data
    @Accessors(chain = true)
    public static class WarehouseReturnLine {
        private String returnLineId;
        private Long tenantId;
        private String returnId;
        private Integer lineNumber;
        private String receiptLineId;
        private String purchaseOrderItemId;
        private String purchaseOrderScheduleId;
        private String qualityDecisionId;
        private Long decisionVersion;
        private String inspectionSplitId;
        private String sourceDisposition;
        private String canonicalSkuId;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private BigDecimal returnQuantity;
        private BigDecimal dispatchedQuantity;
        private BigDecimal outstandingQuantity;
        private String uomCode;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String qualityEvidenceRef;
        private String status;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierDebitAdjustment {
        private String supplierDebitAdjustmentId;
        private Long tenantId;
        private String adjustmentCode;
        private String supplierReturnId;
        private Long supplierReturnVersion;
        private String legalEntityId;
        private String ledgerId;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String supplierId;
        private String currencyCode;
        private Long apReversalAmountMinor;
        private Long taxReversalAmountMinor;
        private Long valuationReversalAmountMinor;
        private Long purchasePriceVarianceAmountMinor;
        private String postingRuleId;
        private Long postingRuleVersion;
        private String journalEntryId;
        private String reversalEvidenceSha256;
        private String reasonCode;
        private String status;
        private String postedByPrincipalId;
        private LocalDateTime postedAt;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierDebitAdjustmentLine {
        private String adjustmentLineId;
        private Long tenantId;
        private String supplierDebitAdjustmentId;
        private String supplierReturnLineId;
        private Integer lineNumber;
        private String receiptLineId;
        private String qualityDispositionId;
        private Long qualityDispositionVersion;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String purchaseOrderScheduleId;
        private String valuationLayerId;
        private String canonicalSkuId;
        private BigDecimal reversalQuantity;
        private String unitOfMeasure;
        private Long unitCostAmountMinor;
        private Long valuationReversalAmountMinor;
        private Long netReversalAmountMinor;
        private Long taxReversalAmountMinor;
        private Long grossReversalAmountMinor;
        private Long purchasePriceVarianceAmountMinor;
        private String currencyCode;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierReturnApReversal {
        private String apReversalId;
        private Long tenantId;
        private String supplierDebitAdjustmentId;
        private String supplierReturnLineId;
        private String supplierInvoiceId;
        private String invoiceLineId;
        private String apOpenItemId;
        private BigDecimal reversalQuantity;
        private String unitOfMeasure;
        private Long netReversalAmountMinor;
        private Long taxReversalAmountMinor;
        private Long grossReversalAmountMinor;
        private Long journalEntryVersion;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierReturnAllocationCandidate {
        private String supplierInvoiceId;
        private String invoiceLineId;
        private String apOpenItemId;
        private String legalEntityId;
        private String supplierId;
        private String currencyCode;
        private BigDecimal invoiceLineQuantity;
        private Long invoiceLineNetAmountMinor;
        private Long invoiceLineTaxAmountMinor;
        private Long invoiceLineGrossAmountMinor;
        private BigDecimal allocatedQuantity;
        private String unitOfMeasure;
        private Long availableOpenAmountMinor;
    }
}
