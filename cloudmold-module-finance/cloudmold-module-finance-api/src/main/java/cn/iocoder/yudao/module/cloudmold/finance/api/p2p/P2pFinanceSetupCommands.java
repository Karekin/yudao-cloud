package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class P2pFinanceSetupCommands {
    private P2pFinanceSetupCommands() { }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Ledger implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String ledgerId; private String legalEntityId; private String ledgerCode;
        private String functionalCurrencyCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Account implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String accountId; private String ledgerId; private String accountCode;
        private String accountName; private String accountType; private String normalBalance;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SupplierPayeeInstrument implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String payeeInstrumentId; private String legalEntityId; private String supplierId;
        private String instrumentToken; private String maskedAccount; private String bankCountryCode;
        private String bankCode; private String currencyCode; private String verificationEvidenceSha256;
        private LocalDate validFrom; private LocalDate validUntil;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MatchPolicy implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String matchPolicyId; private String policyCode; private String legalEntityId;
        private Long policyVersion; private Long priceToleranceAmountMinor;
        private Long taxToleranceAmountMinor; private BigDecimal quantityTolerance;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentTerm implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String paymentTermId; private String termCode; private String legalEntityId;
        private Long termVersion; private List<InstallmentRule> installments;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InstallmentRule implements Serializable {
        private Integer installmentNumber; private Integer dueDaysAfterIssue;
        private Integer allocationBasisPoints;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PostingRule implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String postingRuleId; private String ruleCode; private String ledgerId;
        private String sourceType; private Long ruleVersion; private List<PostingRuleLine> lines;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PostingRuleLine implements Serializable {
        private String postingRuleLineId; private String accountRole; private String accountId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DimensionType implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String dimensionTypeId; private String dimensionCode; private String dimensionName;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DimensionValue implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String dimensionValueId; private String dimensionTypeId;
        private String valueCode; private String valueName;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InventoryValuationPolicy implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String valuationPolicyId; private String policyCode; private String ledgerId;
        private String policyVersion; private String costMethod;
    }
}
