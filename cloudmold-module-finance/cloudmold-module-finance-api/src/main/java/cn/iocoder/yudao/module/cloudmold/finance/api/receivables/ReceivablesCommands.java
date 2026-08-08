package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public final class ReceivablesCommands {
    private ReceivablesCommands() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterPlan implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String receivablePlanId;
        private Long expectedVersion;
        private String planCode;
        private String customerId;
        private String salesContractId;
        private Long plannedAmountMinor;
        private String currencyCode;
        private LocalDate dueDate;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordReceipt implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String receiptId;
        private Long expectedVersion;
        private String receiptCode;
        private String customerId;
        private String salesContractId;
        private Long receiptAmountMinor;
        private String currencyCode;
        private LocalDate receiptDate;
        private String externalReference;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocateReceipt implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String receiptId;
        private Long expectedVersion;
        private String reasonCode;
        private List<AllocationLine> allocations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationLine implements Serializable {
        private String receiptAllocationId;
        private String receivablePlanId;
        private Long expectedPlanVersion;
        private Long amountMinor;
    }
}
