package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Strongly typed write contract for the canonical procurement sourcing aggregate. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SourcingCommand {
    private SourcingOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private EventDefinition event;
    private EventTransitionDefinition eventTransition;
    private InvitationDefinition invitation;
    private QuotationRevisionDefinition quotationRevision;
    private QuotationWithdrawalDefinition quotationWithdrawal;
    private EvaluationPolicyDefinition evaluationPolicy;
    private EvaluationScoreDefinition evaluationScore;
    private AwardDefinition award;
    private AwardTransitionDefinition awardTransition;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventDefinition {
        private String eventId;
        private String eventCode;
        private String requisitionId;
        private String title;
        private LocalDateTime quotationDeadline;
        private List<EventLineDefinition> lines;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventLineDefinition {
        private String sourcingLineId;
        private Integer lineNumber;
        private String requisitionLineId;
        private List<EventScheduleDefinition> schedules;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventScheduleDefinition {
        private String sourcingScheduleId;
        private Integer scheduleNumber;
        private String requisitionScheduleId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventTransitionDefinition {
        private String eventId;
        private Long expectedVersion;
        private String reasonCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvitationDefinition {
        private String invitationId;
        private String eventId;
        private String supplierId;
        private Long expectedEventVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuotationRevisionDefinition {
        private String quotationId;
        private String quotationCode;
        private String revisionId;
        private Integer revisionNumber;
        private String eventId;
        private String supplierId;
        private String currencyCode;
        private Long expectedEventVersion;
        private List<QuotationRevisionLineDefinition> lines;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuotationRevisionLineDefinition {
        private String revisionLineId;
        private Integer lineNumber;
        private String sourcingLineId;
        private BigDecimal offeredQuantity;
        private String uomCode;
        private BigDecimal unitNetPriceMinor;
        private String taxCode;
        private Integer taxRateBps;
        private List<QuotationRevisionScheduleDefinition> schedules;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuotationRevisionScheduleDefinition {
        private String revisionScheduleId;
        private Integer scheduleNumber;
        private String sourcingScheduleId;
        private BigDecimal offeredQuantity;
        private LocalDate promisedDeliveryDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuotationWithdrawalDefinition {
        private String revisionId;
        private String eventId;
        private Long expectedEventVersion;
        private String reasonCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EvaluationPolicyDefinition {
        private String policyId;
        private String policyCode;
        private Integer policyVersion;
        private String eventId;
        private Long expectedEventVersion;
        private List<EvaluationDimensionDefinition> dimensions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EvaluationDimensionDefinition {
        private String dimensionId;
        private String dimensionCode;
        private String dimensionName;
        private Integer weightBps;
        private Integer maximumScore;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EvaluationScoreDefinition {
        private String scoreId;
        private String eventId;
        private String policyId;
        private Integer policyVersion;
        private String quotationRevisionId;
        private String reviewerEvidenceSha256;
        private Long expectedEventVersion;
        private List<DimensionScoreDefinition> dimensions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DimensionScoreDefinition {
        private String dimensionId;
        private Integer score;
        private String evidenceReference;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AwardDefinition {
        private String awardId;
        private String awardCode;
        private String eventId;
        private String policyId;
        private Integer policyVersion;
        private String decisionReasonCode;
        private Long expectedEventVersion;
        private List<AwardLineDefinition> lines;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AwardLineDefinition {
        private String awardLineId;
        private Integer lineNumber;
        private String sourcingLineId;
        private String sourcingScheduleId;
        private String quotationRevisionLineId;
        private String quotationRevisionScheduleId;
        private String supplierId;
        private BigDecimal awardedQuantity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AwardTransitionDefinition {
        private String awardId;
        private Long expectedAwardVersion;
        private Long expectedEventVersion;
        private String reasonCode;
    }
}
