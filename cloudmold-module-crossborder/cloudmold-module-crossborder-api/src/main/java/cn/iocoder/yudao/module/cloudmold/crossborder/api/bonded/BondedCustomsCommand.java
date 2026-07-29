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
public class BondedCustomsCommand implements Serializable {
    private BondedCustomsOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private Instant occurredAt;
    private String caseId;
    private Long expectedVersion;
    private String canonicalOrderId;
    private TripleOrder tripleOrder;
    private EligibilityAssessment eligibilityAssessment;
    private GoodsClassification goodsClassification;
    private TaxCalculation taxCalculation;
    private String approvalRef;
    private String declarationRef;
    private String customsAcceptanceRef;
    private String bondedReleaseRef;
    private String deliveryConfirmationRef;
    private String closeReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TripleOrder implements Serializable {
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
    public static class EligibilityAssessment implements Serializable {
        private List<String> facts;
        private List<String> options;
        private String recommendation;
        private List<String> risks;
        private BigDecimal confidence;
        private List<String> missingFacts;
        private String evidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoodsClassification implements Serializable {
        private String hsCode;
        private String positiveListCode;
        private String goodsName;
        private String evidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxCalculation implements Serializable {
        private Long dutiableAmountMinor;
        private Long consumptionTaxMinor;
        private Long valueAddedTaxMinor;
        private Long totalTaxMinor;
        private String currency;
        private String evidenceRef;
    }
}
