package cn.iocoder.yudao.module.cloudmold.finance.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCloseCommand implements Serializable {
    private FinanceCloseOperation operation;
    private String correlationId;
    private String causationId;
    private String runId;
    private String idempotencyKey;
    private Instant occurredAt;
    private PeriodDefinition period;
    private StatementDefinition statement;
    private DifferenceResolutionDefinition differenceResolution;
    private SettlementDefinition settlement;
    private JournalEntryDefinition journalEntry;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodDefinition implements Serializable {
        private String periodId;
        private String periodCode;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String currencyCode;
        private Long expectedVersion;
        private String evidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementDefinition implements Serializable {
        private String statementId;
        private String statementCode;
        private String periodId;
        private String channelCode;
        private LocalDate statementDate;
        private String currencyCode;
        private Long grossAmountMinor;
        private Long refundAmountMinor;
        private Long feeAmountMinor;
        private Long netSettlementAmountMinor;
        private Long expectedBusinessNetAmountMinor;
        private String evidenceSha256;
        private Long expectedVersion;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifferenceResolutionDefinition implements Serializable {
        private String differenceId;
        private Long adjustmentAmountMinor;
        private String resolutionType;
        private String resolutionEvidenceSha256;
        private Long expectedVersion;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementDefinition implements Serializable {
        private String settlementBatchId;
        private String settlementCode;
        private String periodId;
        private String statementId;
        private Long expectedAmountMinor;
        private Long settledAmountMinor;
        private String bankReference;
        private String settlementEvidenceSha256;
        private Long expectedVersion;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalEntryDefinition implements Serializable {
        private String journalEntryId;
        private String journalCode;
        private String periodId;
        private String sourceType;
        private String sourceId;
        private Long debitTotalMinor;
        private Long creditTotalMinor;
        private String evidenceSha256;
        private Long expectedVersion;
        private String reasonCode;
    }
}
