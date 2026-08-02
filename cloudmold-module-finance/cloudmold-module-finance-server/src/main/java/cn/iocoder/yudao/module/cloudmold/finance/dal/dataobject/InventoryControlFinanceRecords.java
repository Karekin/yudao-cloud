package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class InventoryControlFinanceRecords {
    private InventoryControlFinanceRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Ledger {
        private String ledgerId;
        private Long tenantId;
        private String legalEntityId;
        private String functionalCurrencyCode;
        private String status;
    }

    @Data
    @Accessors(chain = true)
    public static class StockCountSource {
        private String stockCountId;
        private String stockCountCode;
        private String stockCountStatus;
        private Long stockCountVersion;
        private String lineId;
        private Integer lineNumber;
        private String lineStatus;
        private Long lineVersion;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String lotId;
        private String baseUomCode;
        private BigDecimal differenceQuantity;
        private String adjustmentId;
        private Long adjustmentLedgerTransactionId;
    }

    @Data
    @Accessors(chain = true)
    public static class StockCountGainBasis {
        private String stockCountGainBasisId;
        private Long tenantId;
        private String basisCode;
        private String stockCountId;
        private String stockCountLineId;
        private Long stockCountVersion;
        private Long stockCountLineVersion;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private BigDecimal gainQuantity;
        private String unitOfMeasure;
        private Long unitCostAmountMinor;
        private Long totalCostAmountMinor;
        private String currencyCode;
        private String evidenceSha256;
        private String status;
        private String submittedByPrincipalId;
        private String approvedByPrincipalId;
        private LocalDateTime approvedAt;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryScrapSource {
        private String scrapId;
        private String scrapCode;
        private String scrapStatus;
        private Long scrapVersion;
        private String lineId;
        private Integer lineNumber;
        private String lineStatus;
        private Long lineVersion;
        private String dispositionLineId;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String lotId;
        private String baseUomCode;
        private BigDecimal disposedQuantity;
        private Long inventoryLedgerTransactionId;
    }

    @Data
    @Accessors(chain = true)
    public static class ValuationLayerCandidate {
        private String valuationLayerId;
        private Long tenantId;
        private String valuationLayerSourceType;
        private String ledgerId;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private BigDecimal remainingQuantity;
        private Long remainingCostAmountMinor;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String lotId;
        private String status;
        private Long version;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryControlPosting {
        private String inventoryControlPostingId;
        private Long tenantId;
        private String sourceType;
        private String sourceDocumentId;
        private String sourceLineId;
        private String sourceReferenceId;
        private Long sourceDocumentVersion;
        private Long sourceLineVersion;
        private Long inventoryLedgerTransactionId;
        private String legalEntityId;
        private String ledgerId;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String postingRuleId;
        private Long postingRuleVersion;
        private BigDecimal quantity;
        private String unitOfMeasure;
        private String currencyCode;
        private Long totalAmountMinor;
        private String journalEntryId;
        private String journalCode;
        private String reversalJournalEntryId;
        private String postingEvidenceSha256;
        private String status;
        private String createdByPrincipalId;
        private String reversedByPrincipalId;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryControlValuationLayer {
        private String valuationLayerId;
        private Long tenantId;
        private String valuationLayerSourceType;
        private String ledgerId;
        private String sourceDocumentId;
        private String sourceLineId;
        private String sourceReferenceId;
        private Long inventoryLedgerTransactionId;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String lotId;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private BigDecimal quantity;
        private String unitOfMeasure;
        private Long unitCostAmountMinor;
        private Long totalCostAmountMinor;
        private String currencyCode;
        private BigDecimal remainingQuantity;
        private Long remainingCostAmountMinor;
        private String status;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryControlPostingAllocation {
        private String allocationId;
        private Long tenantId;
        private String inventoryControlPostingId;
        private Integer sequenceNo;
        private String valuationLayerSourceType;
        private String valuationLayerId;
        private BigDecimal allocatedQuantity;
        private Long allocatedCostAmountMinor;
        private LocalDateTime createdAt;
    }
}
