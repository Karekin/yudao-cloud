package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public final class SupplierPaymentCommands {
    private SupplierPaymentCommands() {
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Create implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String paymentInstructionId;
        private String paymentCode;
        private String legalEntityId;
        private String ledgerId;
        private String accountingPeriodId;
        private String supplierId;
        private String payeeInstrumentId;
        private String currencyCode;
        private LocalDate requestedExecutionDate;
        private Long totalAmountMinor;
        private String postingRuleId;
        private Long postingRuleVersion;
        private List<Allocation> allocations;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Allocation implements Serializable {
        private String paymentAllocationId;
        private String apOpenItemId;
        private Long amountMinor;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Transition implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String paymentInstructionId;
        private Long expectedVersion;
        private String reasonCode;
        private List<JournalDimensionAssignment> journalDimensions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Execution implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String paymentInstructionId;
        private Long expectedVersion;
        private String executionId;
        private String providerCode;
        private String providerReference;
        private String executionEvidenceSha256;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Settlement implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String paymentInstructionId;
        private Long expectedVersion;
        private String settlementId;
        private LocalDate settlementDate;
        private Long settledAmountMinor;
        private String currencyCode;
        private String bankReference;
        private String settlementEvidenceSha256;
        private List<JournalDimensionAssignment> journalDimensions;
    }
}
