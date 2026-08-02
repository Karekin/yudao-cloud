package cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol;

import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.JournalDimensionAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public final class InventoryControlAccountingCommands {
    private InventoryControlAccountingCommands() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitStockCountGainBasis implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String stockCountGainBasisId;
        private String basisCode;
        private String stockCountId;
        private String stockCountLineId;
        private Long expectedStockCountVersion;
        private Long expectedLineVersion;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private String unitOfMeasure;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String evidenceSha256;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApproveStockCountGainBasis implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String stockCountGainBasisId;
        private Long expectedVersion;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostStockCountAdjustment implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String stockCountId;
        private String stockCountLineId;
        private Long expectedStockCountVersion;
        private Long expectedLineVersion;
        private String ledgerId;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String postingRuleId;
        private Long postingRuleVersion;
        private String journalEntryId;
        private String journalCode;
        private String postingEvidenceSha256;
        private List<JournalDimensionAssignment> journalDimensions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostInventoryScrap implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String scrapId;
        private String scrapLineId;
        private String dispositionLineId;
        private Long expectedScrapVersion;
        private Long expectedLineVersion;
        private String ledgerId;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String postingRuleId;
        private Long postingRuleVersion;
        private String journalEntryId;
        private String journalCode;
        private String postingEvidenceSha256;
        private List<JournalDimensionAssignment> journalDimensions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reverse implements Serializable {
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
}
