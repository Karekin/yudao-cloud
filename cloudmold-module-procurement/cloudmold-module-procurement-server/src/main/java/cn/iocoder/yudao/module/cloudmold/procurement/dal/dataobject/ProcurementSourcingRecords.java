package cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProcurementSourcingRecords {
    private ProcurementSourcingRecords() {}

    @Data @Accessors(chain = true)
    public static class SourcingEvent {
        private String eventId; private Long tenantId; private String eventCode; private String requisitionId;
        private Long requisitionVersion; private String title; private String status;
        private LocalDateTime quotationDeadline; private Long version; private String createdByPrincipalId;
        private String publishedByPrincipalId; private LocalDateTime publishedAt; private String awardedByPrincipalId;
        private LocalDateTime awardedAt; private String quotingOpenedByPrincipalId; private LocalDateTime quotingOpenedAt;
        private String evaluatingByPrincipalId; private LocalDateTime evaluatingAt; private String awardSubmittedByPrincipalId;
        private LocalDateTime awardSubmittedAt; private String closedByPrincipalId; private LocalDateTime closedAt;
        private String cancelledByPrincipalId; private LocalDateTime cancelledAt; private String terminalReasonCode;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class SourcingLine {
        private String sourcingLineId; private Long tenantId; private String eventId; private Integer lineNumber;
        private String requisitionLineId; private String canonicalSkuId; private BigDecimal requestedQuantity;
        private String uomCode; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class SourcingSchedule {
        private String sourcingScheduleId; private Long tenantId; private String eventId; private String sourcingLineId;
        private Integer scheduleNumber; private String requisitionScheduleId; private String canonicalWarehouseId;
        private BigDecimal requestedQuantity; private LocalDate requiredDeliveryDate; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class SupplierInvitation {
        private String invitationId; private Long tenantId; private String eventId; private String supplierId;
        private String status; private String invitedByPrincipalId; private LocalDateTime invitedAt;
    }

    @Data @Accessors(chain = true)
    public static class Quotation {
        private String quotationId; private Long tenantId; private String quotationCode; private String eventId;
        private String supplierId; private String currencyCode; private Integer latestRevisionNumber;
        private String activeRevisionId; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class QuotationRevision {
        private String revisionId; private Long tenantId; private String quotationId; private String eventId;
        private Integer revisionNumber; private String status; private String submittedByPrincipalId;
        private LocalDateTime submittedAt; private String terminalByPrincipalId; private LocalDateTime terminalAt;
        private String withdrawalReasonCode; private String payloadSha256;
    }

    @Data @Accessors(chain = true)
    public static class QuotationRevisionLine {
        private String revisionLineId; private Long tenantId; private String revisionId; private String eventId;
        private Integer lineNumber; private String sourcingLineId; private BigDecimal offeredQuantity; private String uomCode;
        private BigDecimal unitNetPriceMinor; private String taxCode; private Integer taxRateBps;
        private Long lineNetAmountMinor; private Long lineTaxAmountMinor; private Long lineGrossAmountMinor;
    }

    @Data @Accessors(chain = true)
    public static class QuotationRevisionSchedule {
        private String revisionScheduleId; private Long tenantId; private String revisionId; private String revisionLineId;
        private Integer scheduleNumber; private String sourcingScheduleId; private BigDecimal offeredQuantity;
        private LocalDate promisedDeliveryDate;
    }

    @Data @Accessors(chain = true)
    public static class EvaluationPolicy {
        private String policyId; private Long tenantId; private String policyCode; private String eventId;
        private Integer activeVersion; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class EvaluationPolicyVersion {
        private String policyId; private Long tenantId; private Integer policyVersion; private String status;
        private String createdByPrincipalId; private LocalDateTime createdAt; private String policySha256;
    }

    @Data @Accessors(chain = true)
    public static class EvaluationDimension {
        private String dimensionId; private Long tenantId; private String policyId; private Integer policyVersion;
        private String dimensionCode; private String dimensionName; private Integer weightBps; private Integer maximumScore;
    }

    @Data @Accessors(chain = true)
    public static class EvaluationScore {
        private String scoreId; private Long tenantId; private String eventId; private String policyId;
        private Integer policyVersion; private String quotationRevisionId; private String reviewerPrincipalId;
        private Integer weightedScoreBps; private String reviewerEvidenceSha256; private String evaluationSummarySha256;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class EvaluationDimensionScore {
        private String scoreId; private Long tenantId; private String dimensionId; private Integer score;
        private String evidenceReference;
    }

    @Data @Accessors(chain = true)
    public static class Award {
        private String awardId; private Long tenantId; private String awardCode; private String eventId;
        private String policyId; private Integer policyVersion; private String status; private String decisionReasonCode;
        private Long version; private String createdByPrincipalId; private String submittedByPrincipalId;
        private String approvedByPrincipalId; private String rejectedByPrincipalId; private LocalDateTime submittedAt;
        private LocalDateTime approvedAt; private LocalDateTime rejectedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class AwardLine {
        private String awardLineId; private Long tenantId; private String awardId; private Integer lineNumber;
        private String sourcingLineId; private String sourcingScheduleId; private String quotationRevisionLineId;
        private String quotationRevisionScheduleId; private String supplierId; private String canonicalSkuId;
        private String canonicalWarehouseId; private BigDecimal awardedQuantity; private String uomCode;
        private String currencyCode; private BigDecimal unitNetPriceMinor; private String taxCode; private Integer taxRateBps;
        private LocalDate promisedDeliveryDate; private Long lineNetAmountMinor; private Long lineTaxAmountMinor;
        private Long lineGrossAmountMinor; private String policyId; private Integer policyVersion;
        private Integer evaluationWeightedScoreBps; private String evaluationSummarySha256; private String reviewerEvidenceSha256;
    }

    @Data @Accessors(chain = true)
    public static class AwardSnapshot {
        private String snapshotId; private Long tenantId; private String awardId; private Long awardVersion;
        private String eventId; private Long eventVersion; private String status; private String decisionReasonCode;
        private String approvedByPrincipalId; private LocalDateTime approvedAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class AwardSnapshotLine extends AwardLine {
        private String snapshotLineId; private String snapshotId;
    }

    @Data @Accessors(chain = true)
    public static class SourcingHistory {
        private Long tenantId; private String eventId; private Long operationId; private Long aggregateVersion;
        private String status; private String actionCode; private String actorPrincipalId;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class AwardHistory {
        private Long tenantId; private String awardId; private Long operationId; private Long aggregateVersion;
        private String status; private String actionCode; private String reasonCode; private String actorPrincipalId;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }
}
