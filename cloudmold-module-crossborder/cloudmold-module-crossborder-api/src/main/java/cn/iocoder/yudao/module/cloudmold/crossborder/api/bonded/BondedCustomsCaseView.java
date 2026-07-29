package cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BondedCustomsCaseView implements Serializable {
    private String caseId;
    private String caseNo;
    private String mode;
    private String canonicalOrderId;
    private String status;
    private String tripleMatchStatus;
    private String customsStatus;
    private String bondedReleaseStatus;
    private String deliveryStatus;
    private Long version;
    private String approvalRef;
    private String declarationRef;
    private String customsAcceptanceRef;
    private String bondedReleaseRef;
    private String deliveryConfirmationRef;
    private String closeReason;
    private TripleOrderView tripleOrder;
    private EligibilityAssessmentView eligibilityAssessment;
    private GoodsClassificationView goodsClassification;
    private TaxCalculationView taxCalculation;
    private List<HistoryView> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TripleOrderView implements Serializable {
        private String orderRef;
        private String paymentRef;
        private String logisticsRef;
        private Long orderAmountMinor;
        private Long paymentAmountMinor;
        private Long logisticsAmountMinor;
        private String currency;
        private String buyerIdentityHash;
        private String receiverIdentityHash;
        private String declarantIdentityHash;
        private String orderSnapshotRef;
        private String paymentSnapshotRef;
        private String logisticsSnapshotRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibilityAssessmentView implements Serializable {
        private List<String> facts;
        private List<String> options;
        private String recommendation;
        private List<String> risks;
        private BigDecimal confidence;
        private List<String> missingFacts;
        private String evidenceRef;
        private Instant assessedAt;
        private String assessedByPrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoodsClassificationView implements Serializable {
        private String hsCode;
        private String positiveListCode;
        private String goodsName;
        private String evidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxCalculationView implements Serializable {
        private Long dutiableAmountMinor;
        private Long consumptionTaxMinor;
        private Long valueAddedTaxMinor;
        private Long totalTaxMinor;
        private String currency;
        private String evidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryView implements Serializable {
        private Long aggregateVersion;
        private String operation;
        private String previousStatus;
        private String currentStatus;
        private String actorPrincipalId;
        private Instant occurredAt;
        private Instant recordedAt;
        private String detailJson;
    }
}
