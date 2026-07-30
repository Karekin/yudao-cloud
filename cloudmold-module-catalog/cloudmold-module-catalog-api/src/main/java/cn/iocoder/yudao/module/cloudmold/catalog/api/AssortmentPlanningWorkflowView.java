package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssortmentPlanningWorkflowView implements Serializable {
    private String workflowType;
    private String businessKey;
    private String waveId;
    private String waveCode;
    private String status;
    private Long aggregateVersion;
    private Boolean terminal;
    private String phase;
    private String summary;
    private String actionRequired;
    private Integer planningYear;
    private String seasonCode;
    private String categoryCode;
    private String trendBrief;
    private String targetAudience;
    private Integer targetStyleCount;
    private Integer candidateCount;
    private Integer evaluatedCandidateCount;
    private Integer selectedStyleCount;
    private Integer targetGrossMarginBps;
    private Integer maxReturnRateBps;
    private LocalDate launchStartDate;
    private LocalDate launchEndDate;
    private String currencyCode;
    private String decisionPolicyVersion;
    private String decisionSummary;
    private String launchCalendarRef;
    private String downstreamHandoffRef;
    private List<Candidate> candidates;
    private List<Artifact> artifacts;
    private List<Blocker> blockers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate implements Serializable {
        private String candidateId;
        private String candidateCode;
        private String productConcept;
        private String priceBandCode;
        private Long targetPriceMinor;
        private Long expectedUnitCostMinor;
        private Integer expectedGrossMarginBps;
        private Integer trendScore;
        private Integer demandScore;
        private Integer audienceFitScore;
        private Integer supplyRiskScore;
        private Integer predictedReturnRateBps;
        private Integer weightedScore;
        private String status;
        private String rationale;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact implements Serializable {
        private String type;
        private String referenceId;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Blocker implements Serializable {
        private String code;
        private String message;
    }
}
