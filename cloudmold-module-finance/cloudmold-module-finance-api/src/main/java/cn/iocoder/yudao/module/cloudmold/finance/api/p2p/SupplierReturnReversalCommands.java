package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public final class SupplierReturnReversalCommands {
    private SupplierReturnReversalCommands() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Post implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String supplierReturnId;
        private Long expectedSupplierReturnVersion;
        private String ledgerId;
        private String accountingPeriodId;
        private LocalDate accountingDate;
        private String postingRuleId;
        private Long postingRuleVersion;
        private String reversalEvidenceSha256;
        private String reasonCode;
        private List<JournalDimensionAssignment> journalDimensions;
    }
}
