package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Accessors(chain = true)
public class MerchantCommand {
    private MerchantOperation operation;
    private String idempotencyKey;
    private String runId;
    private String applicationId;
    private String merchantId;
    private String shopId;
    private Long expectedVersion;
    private String legalName;
    /** One-way digest or external restricted-store token. Never a raw registration number. */
    private String registrationHashToken;
    /** External restricted-store token. Never raw license content. */
    private String businessLicenseToken;
    private String ownerPrincipalId;
    private String channelCode;
    private String externalShopId;
    private String reason;
    private SourceReference sourceReference;
    private String sourceMappingId;
    private String targetType;
    private String targetId;
    private Instant validFrom;
    private Instant validTo;
    /** Opaque evidence reference only; never raw verification content or PII. */
    private String verificationRef;
    private String migrationRunId;
    private String sourceSystem;
    private String traceId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String admissionId;
    private String attributionChannelCode;
    private String attributionReference;
    private String attributionEvidenceRef;
    private String evidencePackageId;
    private String evidencePackageRef;
    private List<MerchantManagedEvidenceItem> evidenceItems;
    private String diagnosticId;
    private String recommendationCode;
    private String recommendationSummary;
    private String diagnosticEvidenceRef;
    private String inspectionTaskId;
    private String actorPrincipalId;
    private Instant scheduledAt;
    private String inspectionOutcomeEvidenceRef;
    private String invitationId;
    private String invitationCode;
    private String recruiterPrincipalId;
    private String invitationEvidenceRef;
    private String buyerAssignmentId;
    private String buyerTlPrincipalId;
    private String buyerPrincipalId;
    private String assignmentEvidenceRef;
    private String gradeDecisionId;
    private String gradeCode;
    private String gradeDecisionStatus;
    private String gradeTransitionDecision;
    private List<MerchantBenefitEntitlementItem> benefitEntitlements;
    private String benefitDecisionEvidenceRef;
    private String thresholdsConfigRef;
    private String probationAssessmentId;
    private List<MerchantProbationGateItem> probationGates;
    private String probationAssessmentStatus;
    private String assessmentEvidenceRef;
    private String scorecardId;
    private String scorecardMonth;
    private List<MerchantScorecardItem> scorecardItems;
    private String scorecardStatus;
    private String scorecardEvidenceRef;
    private String exitDecisionId;
    private String exitReasonType;
    private String exitEvidenceRef;
    private String reviewDecision;
    private String reviewNote;
    private String finalReviewEvidenceRef;
}
