package cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProcureToPayAdminVOs {
    private ProcureToPayAdminVOs() { }

    @Data public static class PageRequest { private Integer pageNo=1; private Integer pageSize=20; private String keyword; private String status; }

    @Data public static class SupplierInvoicePageItem {
        private String supplierInvoiceId; private String invoiceCode; private String supplierId;
        private String supplierInvoiceNumber; private String currencyCode; private String grossAmountMinor;
        private String status; private String matchStatus; private Integer exceptionCount; private Long aggregateVersion;
        private LocalDate accountingDate; private LocalDate dueDate; private LocalDateTime postedAt; private LocalDateTime updatedAt;
    }
    @Data public static class InvoiceLineage {
        private String invoiceLineId; private String purchaseOrderId; private String purchaseOrderItemId;
        private String deliveryScheduleId; private String receiptLineId; private String qualityDispositionId;
        private String inventoryMovementId;
    }
    @Data public static class SupplierInvoiceMatchLine {
        private String invoiceLineId; private Integer lineNumber; private String canonicalSkuId;
        private String invoiceQuantity; private String acceptedReceiptQuantity; private String uomCode;
        private String invoiceUnitNetPriceMinor; private String purchaseOrderUnitNetPriceMinor;
        private String lineGrossAmountMinor; private String differenceAmountMinor; private String differenceType;
        private InvoiceLineage lineage;
        @JsonIgnore private String lineageInvoiceLineId; @JsonIgnore private String lineagePurchaseOrderId;
        @JsonIgnore private String lineagePurchaseOrderItemId; @JsonIgnore private String lineageDeliveryScheduleId;
        @JsonIgnore private String lineageReceiptLineId; @JsonIgnore private String lineageQualityDispositionId;
        @JsonIgnore private String lineageInventoryMovementId;
    }
    @Data public static class SupplierInvoiceDetail extends SupplierInvoicePageItem {
        private String legalEntityId; private String accountingPeriodId; private LocalDate issueDate;
        private String netAmountMinor; private String taxAmountMinor; private String paymentConditionCode;
        private String matchRunId; private String matchPolicyId; private Long matchPolicyVersion;
        private List<SupplierInvoiceMatchLine> invoiceLines; private List<ApInstallmentPageItem> apInstallments;
        private List<SupplierPaymentPageItem> paymentInstructions; private List<String> journalEntryIds;
    }
    @Data public static class MatchExceptionPageItem {
        private String exceptionId; private String exceptionCode; private String exceptionType; private String status;
        private String matchRunId; private String supplierInvoiceId; private String invoiceCode; private String invoiceLineId;
        private String currencyCode; private String expectedAmountMinor; private String actualAmountMinor;
        private String expectedQuantity; private String actualQuantity; private Long aggregateVersion;
        private LocalDateTime updatedAt;
    }
    @Data public static class ApInstallmentPageItem {
        private String apOpenItemId; private String supplierInvoiceId; private String invoiceCode; private String supplierId;
        private Integer installmentNumber; private LocalDate dueDate; private String currencyCode;
        private String originalAmountMinor; private String paidAmountMinor; private String openAmountMinor;
        private String status; private LocalDateTime updatedAt;
    }
    @Data public static class SupplierPaymentPageItem {
        private String paymentInstructionId; private String paymentCode; private String supplierId; private String currencyCode;
        private String totalAmountMinor; private String allocatedAmountMinor; private String settledAmountMinor;
        private LocalDate requestedExecutionDate; private LocalDateTime executedAt; private String status;
        private Long aggregateVersion; private LocalDateTime updatedAt;
    }
    @Data public static class JournalPageItem {
        private String journalEntryId; private String journalCode; private String journalType;
        private String sourceAggregateType; private String sourceAggregateId; private LocalDate accountingDate;
        private String currencyCode; private String totalDebitAmountMinor; private String totalCreditAmountMinor;
        private String status; private String reversedByJournalEntryId; private Long aggregateVersion;
        private LocalDateTime postedAt; private LocalDateTime updatedAt;
    }
    @Data public static class JournalDimension {
        private String dimensionType; private String dimensionValue;
    }
    @Data public static class JournalLine {
        private String journalLineId; private Integer lineNumber; private String accountCode; private String accountName;
        private String debitAmountMinor; private String creditAmountMinor; private List<JournalDimension> dimensions;
    }
    @Data public static class JournalDetail extends JournalPageItem {
        private String originalJournalEntryId; private String postingEvidenceSha256; private List<JournalLine> lines;
    }
    @Data public static class MatchPolicyItem {
        private String matchPolicyId; private String policyCode; private String legalEntityId; private Long policyVersion;
        private String priceToleranceAmountMinor; private String taxToleranceAmountMinor;
        private String quantityTolerance; private String status;
    }
    @Data public static class AdminCommandResult {
        private String operationId; private Boolean duplicate; private String aggregateType; private String aggregateId;
        private Long aggregateVersion; private String status; private String supplierInvoiceId; private String matchRunId;
        private String exceptionId; private String apOpenItemId; private String paymentInstructionId; private String journalEntryId;
    }

    @Data public static class SupplierInvoiceCommandRequest {
        private FinanceCommandEnvelope envelope; private String operation; private String supplierInvoiceId;
        private Long expectedVersion; private String reasonCode; private String matchPolicyId; private Long matchPolicyVersion;
    }
    @Data public static class MatchExceptionCommandRequest {
        private FinanceCommandEnvelope envelope; private String operation; private String exceptionId;
        private Long expectedVersion; private String reasonCode; private String resolutionEvidenceSha256;
    }
    @Data public static class SupplierPaymentCommandRequest {
        private FinanceCommandEnvelope envelope; private String operation; private String paymentInstructionId;
        private Long expectedVersion; private String reasonCode; private String settlementId; private LocalDate settlementDate;
        private String settledAmountMinor; private String currencyCode; private String bankReference;
        private String settlementEvidenceSha256;
    }
    @Data public static class JournalCommandRequest {
        private FinanceCommandEnvelope envelope; private String operation; private String originalJournalEntryId;
        private Long expectedVersion; private String reversalJournalEntryId; private String reversalJournalCode;
        private String accountingPeriodId; private LocalDate accountingDate; private String reversalEvidenceSha256;
        private String reasonCode;
    }
}
