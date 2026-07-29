package cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class FinanceCloseRecords {
    private FinanceCloseRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
    }

    @Data
    @Accessors(chain = true)
    public static class AccountingPeriod {
        private String periodId;
        private Long tenantId;
        private String periodCode;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String currencyCode;
        private String status;
        private String openedByPrincipalId;
        private String closedByPrincipalId;
        private String closeEvidenceSha256;
        private String reasonCode;
        private Long version;
        private LocalDateTime openedAt;
        private LocalDateTime closedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ChannelStatement {
        private String statementId;
        private Long tenantId;
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
        private Long differenceAmountMinor;
        private String evidenceSha256;
        private String status;
        private String importedByPrincipalId;
        private String reconciledByPrincipalId;
        private String reasonCode;
        private Long version;
        private LocalDateTime importedAt;
        private LocalDateTime reconciledAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ReconciliationDifference {
        private String differenceId;
        private Long tenantId;
        private String periodId;
        private String statementId;
        private Long differenceAmountMinor;
        private Long adjustmentAmountMinor;
        private String status;
        private String resolutionType;
        private String resolutionEvidenceSha256;
        private String openedByPrincipalId;
        private String resolvedByPrincipalId;
        private String reasonCode;
        private Long version;
        private LocalDateTime openedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SettlementBatch {
        private String settlementBatchId;
        private Long tenantId;
        private String settlementCode;
        private String periodId;
        private String statementId;
        private String currencyCode;
        private Long expectedAmountMinor;
        private Long settledAmountMinor;
        private String bankReference;
        private String settlementEvidenceSha256;
        private String status;
        private String preparedByPrincipalId;
        private String settledByPrincipalId;
        private String reasonCode;
        private Long version;
        private LocalDateTime preparedAt;
        private LocalDateTime settledAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class JournalEntry {
        private String journalEntryId;
        private Long tenantId;
        private String journalCode;
        private String periodId;
        private String sourceType;
        private String sourceId;
        private String currencyCode;
        private Long debitTotalMinor;
        private Long creditTotalMinor;
        private String evidenceSha256;
        private String status;
        private String preparedByPrincipalId;
        private String postedByPrincipalId;
        private String reasonCode;
        private Long version;
        private LocalDateTime preparedAt;
        private LocalDateTime postedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
