package cn.iocoder.yudao.module.cloudmold.catalog.api;

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
public class AssortmentPlanningCommand implements Serializable {

    private AssortmentPlanningOperation operation;
    private String correlationId;
    private String causationId;
    private String runId;
    private String idempotencyKey;
    private Instant occurredAt;
    private String actorPrincipalId;
    private WaveDefinition wave;
    private CandidateDefinition candidate;
    private EvaluationDefinition evaluation;
    private PortfolioDefinition portfolio;
    private ApprovalDefinition approval;
    private PublicationDefinition publication;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaveDefinition implements Serializable {
        private String waveId;
        private String waveCode;
        private Integer planningYear;
        private String seasonCode;
        private String categoryCode;
        private String trendBrief;
        private String targetAudience;
        private Integer targetStyleCount;
        private Long targetPriceFloorMinor;
        private Long targetPriceCeilingMinor;
        private Integer targetGrossMarginBps;
        private Integer maxReturnRateBps;
        private LocalDate launchStartDate;
        private LocalDate launchEndDate;
        private String currencyCode;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateDefinition implements Serializable {
        private String waveId;
        private Long expectedWaveVersion;
        private String candidateId;
        private String candidateCode;
        private String productConcept;
        private String sourceSignalType;
        private String sourceSignalRef;
        private String priceBandCode;
        private Long targetPriceMinor;
        private Long expectedUnitCostMinor;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationDefinition implements Serializable {
        private String waveId;
        private Long expectedWaveVersion;
        private String candidateId;
        private Integer trendScore;
        private Integer demandScore;
        private Integer audienceFitScore;
        private Integer supplyRiskScore;
        private Integer predictedReturnRateBps;
        private String evidenceSha256;
        private String rationale;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioDefinition implements Serializable {
        private String waveId;
        private Long expectedWaveVersion;
        private String decisionPolicyVersion;
        private String decisionEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalDefinition implements Serializable {
        private String waveId;
        private Long expectedWaveVersion;
        private String approvalRef;
        private String approvalNote;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicationDefinition implements Serializable {
        private String waveId;
        private Long expectedWaveVersion;
        private String launchCalendarRef;
        private String downstreamHandoffRef;
        private String publicationEvidenceSha256;
        private String reasonCode;
    }
}
