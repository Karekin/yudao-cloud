package cn.iocoder.yudao.module.cloudmold.partnermarketing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerMarketingCommand implements Serializable {
    private PartnerMarketingOperation operation;
    private String correlationId;
    private String causationId;
    private String runId;
    private String idempotencyKey;
    private Instant occurredAt;
    private String actorPrincipalId;
    private CandidateCaseDefinition candidateCase;
    private QualificationDefinition qualification;
    private OutreachDefinition outreach;
    private BriefDefinition brief;
    private ContentDefinition content;
    private PublicationDefinition publication;
    private AttributionDefinition attribution;
    private SettlementApprovalDefinition settlementApproval;
    private SettlementPaymentDefinition settlementPayment;
    private CaseCloseDefinition caseClose;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateCaseDefinition implements Serializable {
        private String caseId;
        private String caseCode;
        private String creatorPrincipalId;
        private String candidateHandle;
        private String platformCode;
        private String regionCode;
        private String categoryCode;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualificationDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private String riskLevel;
        private String riskDecision;
        private String riskEvidenceSha256;
        private String qualificationNote;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutreachDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private String outreachChannelCode;
        private String outreachExternalRef;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BriefDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private String campaignId;
        private String listingId;
        private String cooperationModel;
        private Long budgetAmountMinor;
        private String currencyCode;
        private String briefSummary;
        private String briefEvidenceSha256;
        private String approvalEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private String contentSummary;
        private String contentEvidenceSha256;
        private String approvalEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicationDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private String externalPublishRef;
        private String externalPublishUrl;
        private String disclosureLabel;
        private String publishEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributionDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private Integer attributedOrderCount;
        private String attributedOrderId;
        private String attributedPaymentId;
        private String attributionSourceRef;
        private Long grossSettlementAmountMinor;
        private Long platformFeeAmountMinor;
        private Long taxWithholdingAmountMinor;
        private Long netPayableAmountMinor;
        private String attributionEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementApprovalDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private Long approvedNetPayableAmountMinor;
        private String approvalEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementPaymentDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private Long paidNetPayableAmountMinor;
        private String settlementReference;
        private String paymentEvidenceSha256;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseCloseDefinition implements Serializable {
        private String caseId;
        private Long expectedVersion;
        private String closureEvidenceSha256;
        private String reasonCode;
    }
}
