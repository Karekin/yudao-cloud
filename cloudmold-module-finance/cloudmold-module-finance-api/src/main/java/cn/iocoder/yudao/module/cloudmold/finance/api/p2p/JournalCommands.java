package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

public final class JournalCommands {
    private JournalCommands() {
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Reverse implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String originalJournalEntryId;
        private Long expectedVersion;
        private String reversalJournalEntryId;
        private String reversalJournalCode;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String reversalEvidenceSha256;
        private String reasonCode;
    }
}
