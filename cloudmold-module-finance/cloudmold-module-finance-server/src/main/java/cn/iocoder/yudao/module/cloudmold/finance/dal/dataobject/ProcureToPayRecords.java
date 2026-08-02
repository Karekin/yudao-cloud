package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProcureToPayRecords {
    private ProcureToPayRecords() {
    }

    @Data @Accessors(chain = true)
    public static class MatchPolicy {
        private String matchPolicyId; private Long tenantId; private String policyCode;
        private String legalEntityId; private Long policyVersion; private Long priceToleranceAmountMinor;
        private Long taxToleranceAmountMinor; private BigDecimal quantityTolerance;
        private String status; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class PaymentTermRule {
        private String installmentRuleId; private Long tenantId; private String paymentTermId;
        private Long termVersion; private Integer installmentNumber; private Integer dueDaysAfterIssue;
        private Integer allocationBasisPoints;
    }

    @Data @Accessors(chain = true)
    public static class PayeeInstrument {
        private String payeeInstrumentId; private Long tenantId; private String legalEntityId;
        private String supplierId; private String currencyCode; private String status;
        private String verificationStatus; private LocalDate validFrom; private LocalDate validUntil;
    }

    @Data @Accessors(chain = true)
    public static class PostingAccount {
        private String accountRole; private String ledgerId; private String accountId; private String accountCode;
    }

    @Data @Accessors(chain = true)
    public static class EventInbox {
        private Long inboxId; private Long tenantId; private String sourceEventId;
        private String sourceEventType; private Integer sourceSchemaVersion;
        private String sourceAggregateId; private Long sourceAggregateVersion;
        private String evidenceSha256; private String attemptToken; private LocalDateTime sourceOccurredAt;
        private LocalDateTime receivedAt;
    }

    @Data @Accessors(chain = true)
    public static class PurchaseOrderLineEvidence {
        private String poLineEvidenceId; private Long tenantId; private Long inboxId;
        private String purchaseOrderId; private String purchaseOrderItemId;
        private String deliveryScheduleId; private String legalEntityId; private String supplierId;
        private String currencyCode; private BigDecimal orderedQuantity; private String unitOfMeasure;
        private BigDecimal unitNetPrice; private Long netAmountMinor; private Long taxAmountMinor;
        private Long grossAmountMinor; private Long sourceVersion; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class ReceiptLineEvidence {
        private String receiptLineEvidenceId; private Long tenantId; private Long inboxId;
        private String receiptId; private String receiptLineId; private String purchaseOrderId;
        private String purchaseOrderItemId; private Long purchaseOrderLineVersion; private String deliveryScheduleId;
        private BigDecimal receivedQuantity; private String unitOfMeasure;
        private Long sourceVersion; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class QualityDispositionEvidence {
        private String qualityEvidenceId; private Long tenantId; private Long inboxId;
        private String qualityDispositionId; private String receiptLineId;
        private Long receiptLineVersion; private String purchaseOrderItemId; private BigDecimal inspectedQuantity;
        private BigDecimal acceptedQuantity; private BigDecimal rejectedQuantity;
        private BigDecimal heldQuantity; private String unitOfMeasure;
        private Long sourceVersion; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class InventoryMovementEvidence {
        private String inventoryEvidenceId; private Long tenantId; private Long inboxId;
        private String inventoryMovementId; private String receiptLineId;
        private String qualityDispositionId; private Long qualityDispositionVersion; private String disposition;
        private String purchaseOrderItemId;
        private BigDecimal movementQuantity; private String unitOfMeasure;
        private Long unitCostAmountMinor; private Long movementCostAmountMinor; private String currencyCode;
        private String valuationPolicyId; private String valuationPolicyVersion;
        private Long sourceVersion; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class SupplierInvoice {
        private String supplierInvoiceId; private Long tenantId; private String invoiceCode;
        private String legalEntityId; private String ledgerId; private String accountingPeriodId;
        private String supplierId; private String supplierInvoiceNumber; private String invoiceType;
        private String currencyCode; private LocalDate issueDate; private LocalDate accountingDate;
        private LocalDate dueDate; private Long netAmountMinor; private Long taxAmountMinor;
        private Long grossAmountMinor; private String lifecycleStatus; private String matchStatus;
        private String settlementStatus; private String evidenceSha256;
        private String paymentTermId; private Long paymentTermVersion;
        private String postingRuleId; private Long postingRuleVersion;
        private String createdByPrincipalId; private String approvedByPrincipalId;
        private String postedJournalEntryId; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class SupplierInvoiceLine {
        private String invoiceLineId; private Long tenantId; private String supplierInvoiceId;
        private Integer lineNumber; private String purchaseOrderId; private String purchaseOrderItemId;
        private String skuId; private BigDecimal quantity; private String unitOfMeasure;
        private BigDecimal unitNetPrice; private Long netAmountMinor; private String taxCode;
        private BigDecimal taxRate; private Long taxAmountMinor; private Long grossAmountMinor;
        private Long version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class MatchRun {
        private String matchRunId; private Long tenantId; private String supplierInvoiceId;
        private Long invoiceVersion; private String matchPolicyId; private Long matchPolicyVersion;
        private Long priceToleranceAmountMinor; private Long taxToleranceAmountMinor;
        private BigDecimal quantityTolerance; private String status;
        private String requestedByPrincipalId; private Long version;
        private LocalDateTime createdAt; private LocalDateTime completedAt;
    }

    @Data @Accessors(chain = true)
    public static class MatchLine {
        private String matchLineId; private Long tenantId; private String matchRunId;
        private String invoiceLineId; private String purchaseOrderItemId;
        private Long purchaseOrderLineVersion;
        private BigDecimal invoiceQuantity; private BigDecimal eligibleQuantity;
        private Long invoiceNetAmountMinor; private Long expectedPoNetAmountMinor;
        private Long priceDifferenceAmountMinor; private Long taxDifferenceAmountMinor;
        private String resultStatus; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class MatchCandidate {
        private String receiptLineId; private Long receiptLineVersion;
        private String qualityDispositionId; private Long qualityDispositionVersion;
        private String inventoryMovementId; private Long inventoryMovementVersion;
        private String purchaseOrderItemId;
        private BigDecimal acceptedQuantity; private BigDecimal previouslyAllocatedQuantity;
        private String unitOfMeasure;
    }

    @Data @Accessors(chain = true)
    public static class MatchAllocation {
        private String matchAllocationId; private Long tenantId; private String matchRunId;
        private String matchLineId; private String invoiceLineId; private String receiptLineId;
        private Long receiptLineVersion; private String qualityDispositionId;
        private Long qualityDispositionVersion; private String inventoryMovementId;
        private Long inventoryMovementVersion;
        private BigDecimal allocatedQuantity; private String unitOfMeasure;
        private String status; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class MatchException {
        private String exceptionId; private Long tenantId; private String matchRunId;
        private String matchLineId; private String exceptionCode; private String exceptionType; private Boolean blocking;
        private BigDecimal expectedQuantity; private BigDecimal actualQuantity;
        private Long expectedAmountMinor; private Long actualAmountMinor;
        private String expectedCode; private String actualCode; private String status;
        private String openedByPrincipalId; private String resolvedByPrincipalId;
        private String resolutionEvidenceSha256; private String reasonCode; private Long version;
        private LocalDateTime openedAt; private LocalDateTime resolvedAt;
    }

    @Data @Accessors(chain = true)
    public static class ApOpenItem {
        private String apOpenItemId; private Long tenantId; private String supplierInvoiceId;
        private String legalEntityId; private String ledgerId; private String supplierId;
        private String currencyCode; private Long originalAmountMinor; private Long settledAmountMinor;
        private Long openAmountMinor; private LocalDate dueDate; private String status;
        private Long version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ApInstallment {
        private String apInstallmentId; private Long tenantId; private String apOpenItemId;
        private Integer installmentNumber; private LocalDate dueDate; private Long amountMinor;
        private Long settledAmountMinor; private String status; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class PaymentInstruction {
        private String paymentInstructionId; private Long tenantId; private String paymentCode;
        private String legalEntityId; private String ledgerId; private String accountingPeriodId;
        private String supplierId; private String payeeInstrumentId; private String currencyCode;
        private LocalDate requestedExecutionDate; private Long totalAmountMinor; private String status;
        private String postingRuleId; private Long postingRuleVersion;
        private String createdByPrincipalId; private String approvedByPrincipalId;
        private String releasedByPrincipalId; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class PaymentAllocation {
        private String paymentAllocationId; private Long tenantId;
        private String paymentInstructionId; private String apOpenItemId;
        private Long amountMinor; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class JournalEntry {
        private String journalEntryId; private Long tenantId; private String journalCode;
        private String legalEntityId; private String ledgerId; private String periodId;
        private LocalDate accountingDate; private String sourceType; private String sourceId;
        private String currencyCode; private String documentCurrencyCode;
        private Long debitTotalMinor; private Long creditTotalMinor; private String evidenceSha256;
        private String status; private String preparedByPrincipalId; private String postedByPrincipalId;
        private String reasonCode; private Long version; private LocalDateTime preparedAt;
        private LocalDateTime postedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class JournalLine {
        private String journalLineId; private Long tenantId; private String journalEntryId; private String ledgerId;
        private Integer lineNumber; private String accountId; private String accountCode; private Long debitAmountMinor;
        private Long creditAmountMinor; private String transactionCurrencyCode;
        private Long transactionAmountMinor; private String supplierId;
        private String supplierInvoiceId; private String apOpenItemId;
        private String purchaseOrderId; private String purchaseOrderItemId;
        private String receiptLineId; private String inventoryMovementId;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class InventoryValuationLayer {
        private String valuationLayerId; private Long tenantId; private String ledgerId;
        private String inventoryMovementId; private Long inventoryMovementVersion;
        private String receiptLineId; private String qualityDispositionId;
        private String purchaseOrderItemId; private String valuationPolicyId; private String valuationPolicyVersion;
        private BigDecimal quantity; private String unitOfMeasure; private Long unitCostAmountMinor;
        private Long totalCostAmountMinor; private String currencyCode; private BigDecimal remainingQuantity;
        private Long remainingCostAmountMinor; private String status; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }
}
