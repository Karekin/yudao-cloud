package cn.iocoder.yudao.module.cloudmold.risk.api;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RiskCommand {
    private RiskOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private Long expectedVersion;

    private String policyId;
    private String policyCode;
    private String policyName;
    private String approvedByPrincipalId;
    private Instant effectiveFrom;
    private List<RuleDefinition> rules;

    private String taxonomyId;
    private String eventCode;
    private List<String> intelligenceLevels;
    private String sourceSystem;
    private String sourceTable;
    private String sourceRecordKey;
    private String sourceVersion;
    private Instant sourceObservedAt;
    private String sourceEvidenceRef;
    private String sourceEvidenceSha256;
    private String retiredByPrincipalId;

    private String signalId;
    private String subjectPrincipalId;
    private String signalType;
    private String severity;
    private String evidenceRef;

    private String relatedPrincipalId;
    private String mediumType;
    private String mediumToken;
    private Integer keyVersion;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Integer confidenceBasisPoints;

    private String clusterId;
    private String clusterCode;
    private String clusterStatus;
    private String riskLevel;
    private String memberType;
    private String memberRef;
    private String relationId;

    private String caseId;
    private String reviewerPrincipalId;
    private String decisionId;
    private String decisionType;
    private String reasonCode;
    private String decidedByPrincipalId;
    private String feedbackId;
    private String feedbackType;
    private String recordedByPrincipalId;

    private String orderId;
    private String paymentId;
    private String riskType;
    private String orderRiskCaseId;

    private String disputeId;
    private String disputeType;
    private String disputeStatus;
    private Long amountMinor;
    private String currencyCode;
    private String externalRef;

    private String lossEntryId;
    private String lossEntryType;
    private Long signedAmountMinor;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleDefinition {
        private String ruleCode;
        private String signalType;
        private String operatorCode;
        private String thresholdValue;
        private String outcomeCode;
        private String explanationTemplate;
    }
}
