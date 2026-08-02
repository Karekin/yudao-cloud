package cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo;

import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.JournalDimensionAssignment;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InventoryControlFinanceAdminVOs {
    private InventoryControlFinanceAdminVOs() {
    }

    @Data
    public static class InventoryControlCommandRequest {
        private FinanceCommandEnvelope envelope;
        private String operation;
        private String stockCountGainBasisId;
        private String basisCode;
        private String stockCountId;
        private String stockCountLineId;
        private Long expectedStockCountVersion;
        private Long expectedLineVersion;
        private Long expectedVersion;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String scrapId;
        private String scrapLineId;
        private String dispositionLineId;
        private Long expectedScrapVersion;
        private String ledgerId;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String postingRuleId;
        private Long postingRuleVersion;
        private String journalEntryId;
        private String journalCode;
        private String postingEvidenceSha256;
        private String evidenceSha256;
        private String unitOfMeasure;
        private String currencyCode;
        private String note;
        private String reversalEvidenceSha256;
        private String reasonCode;
        private List<JournalDimensionAssignment> journalDimensions;
    }

    @Data
    public static class InventoryControlJournalReverseRequest {
        private FinanceCommandEnvelope envelope;
        private String inventoryControlPostingId;
        private Long expectedVersion;
        private String reversalJournalEntryId;
        private String reversalJournalCode;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String reversalEvidenceSha256;
        private String reasonCode;
    }

    @Data
    public static class AdminCommandResult {
        private String operationId;
        private boolean duplicate;
        private String inventoryControlPostingId;
        private String sourceType;
        private String sourceDocumentId;
        private String sourceLineId;
        private String sourceReferenceId;
        private Long aggregateVersion;
        private String status;
        private String journalEntryId;
        private String journalCode;
        private String reversalJournalEntryId;
        private String currencyCode;
        private String totalAmountMinor;
    }

    @Data
    public static class PostingPageRequest extends PageParam {
        private String sourceType;
        private String status;
        private String keyword;
    }

    @Data
    public static class PostingPageItem {
        private String inventoryControlPostingId;
        private String sourceType;
        private String sourceDocumentId;
        private String sourceLineId;
        private String sourceReferenceId;
        private String impactType;
        private String quantity;
        private String unitOfMeasure;
        private String currencyCode;
        private String valuationImpactAmountMinor;
        private String apImpactAmountMinor;
        private String totalAmountMinor;
        private String journalEntryId;
        private String journalCode;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String postingStatus;
        private String journalStatus;
        private String balanceStatus;
        private Long aggregateVersion;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class PostingAllocationItem {
        private Integer sequenceNo;
        private String valuationLayerSourceType;
        private String valuationLayerId;
        private String allocatedQuantity;
        private String allocatedCostAmountMinor;
    }

    @Data
    public static class FinancialImpactSourceLine {
        private String sourceLineId;
        private String sourceReferenceId;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String purchaseOrderScheduleId;
        private String receiptLineId;
        private String qualityDispositionId;
        private String valuationLayerId;
        private String quantity;
        private String unitOfMeasure;
        private String valuationImpactAmountMinor;
        private String apImpactAmountMinor;
        private String purchasePriceVarianceAmountMinor;
        private String currencyCode;
    }

    @Data
    public static class FinancialImpactApLineage {
        private String supplierInvoiceId;
        private String invoiceLineId;
        private String apOpenItemId;
        private String reversalQuantity;
        private String unitOfMeasure;
        private String netReversalAmountMinor;
        private String taxReversalAmountMinor;
        private String grossReversalAmountMinor;
    }

    @Data
    public static class PostingDetail extends PostingPageItem {
        private String legalEntityId;
        private String ledgerId;
        private String postingRuleId;
        private Long postingRuleVersion;
        private String postingEvidenceSha256;
        private String reversalJournalEntryId;
        private String createdByPrincipalId;
        private String reversedByPrincipalId;
        private LocalDateTime createdAt;
        private String gainBasisId;
        private String gainBasisStatus;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private String unitCostAmountMinor;
        private List<PostingAllocationItem> allocations;
        private List<FinancialImpactSourceLine> sourceLines;
        private List<FinancialImpactApLineage> apLineage;
        private ProcureToPayAdminVOs.JournalDetail journal;
    }
}
