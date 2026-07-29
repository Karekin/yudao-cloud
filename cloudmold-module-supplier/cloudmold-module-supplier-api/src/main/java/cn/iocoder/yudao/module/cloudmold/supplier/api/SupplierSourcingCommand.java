package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierSourcingCommand {
    private SupplierSourcingOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private SupplierDefinition supplier;
    private SourcingCaseDefinition sourcingCase;
    private QuoteDefinition quote;
    private SampleEvaluationDefinition sampleEvaluation;
    private AdmissionDefinition admission;
    private AwardDefinition award;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierDefinition {
        private String supplierId;
        private String supplierCode;
        private String supplierName;
        private String countryCode;
        private String capabilitySummary;
        private String riskLevel;
        private String reasonCode;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourcingCaseDefinition {
        private String sourcingCaseId;
        private String rfqCode;
        private String requestRef;
        private String canonicalSkuId;
        private BigDecimal targetQuantity;
        private String uomCode;
        private String currencyCode;
        private Long maxUnitCostMinor;
        private LocalDate requiredDeliveryDate;
        private String requirements;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteDefinition {
        private String quoteId;
        private String sourcingCaseId;
        private String supplierId;
        private Integer quoteVersion;
        private Long unitCostMinor;
        private BigDecimal moq;
        private Integer leadTimeDays;
        private BigDecimal capacityQuantity;
        private LocalDate validUntil;
        private String termsSummary;
        private Long expectedCaseVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleEvaluationDefinition {
        private String evaluationId;
        private String sourcingCaseId;
        private String supplierId;
        private String quoteId;
        private Integer qualityScore;
        private Integer fitScore;
        private Integer deliveryScore;
        private Integer riskScore;
        private String result;
        private String notes;
        private Long expectedCaseVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdmissionDefinition {
        private String supplierId;
        private String qualificationEvidenceSha256;
        private String riskEvidenceSha256;
        private String reasonCode;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AwardDefinition {
        private String sourcingCaseId;
        private String supplierId;
        private String quoteId;
        private String decisionRationale;
        private Long expectedCaseVersion;
    }
}
