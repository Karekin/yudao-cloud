package cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

public final class RiskRecords {
    private RiskRecords() {
    }

    @Data @Accessors(chain = true)
    public static class Operation {
        private Long operationId; private Long tenantId; private String idempotencyKey; private String commandType;
        private String requestHash; private String attemptToken; private Integer status; private String aggregateId;
        private String resultJson; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class Policy {
        private String policyId; private Long tenantId; private String policyCode; private String policyName;
        private String status; private Long currentVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class PolicyVersion {
        private String policyVersionId; private Long tenantId; private String policyId; private Long policyVersion;
        private String rulesSha256; private String approvedByPrincipalId; private LocalDateTime effectiveFrom;
        private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class PolicyRule {
        private String ruleId; private Long tenantId; private String policyVersionId; private String policyId;
        private Long policyVersion; private Integer ruleSequence; private String ruleCode; private String signalType;
        private String operatorCode; private String thresholdValue; private String outcomeCode;
        private String explanationTemplate; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Signal {
        private String signalId; private Long tenantId; private String subjectPrincipalId; private String policyId;
        private Long policyVersion; private String signalType; private String severity; private String evidenceRef;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Relation {
        private String relationId; private Long tenantId; private String subjectPrincipalId;
        private String relatedPrincipalId; private String mediumType; private String mediumToken;
        private Integer keyVersion; private LocalDateTime firstSeenAt; private LocalDateTime lastSeenAt;
        private Integer confidenceBasisPoints; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Cluster {
        private String clusterId; private Long tenantId; private String clusterCode; private String status;
        private String riskLevel; private Integer memberCount; private Integer edgeCount; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ClusterMember {
        private String clusterMemberId; private Long tenantId; private String clusterId; private String memberType;
        private String memberRef; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class ReviewCase {
        private String caseId; private Long tenantId; private String clusterId; private String status;
        private String reviewerPrincipalId; private Long version; private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class Decision {
        private String decisionId; private Long tenantId; private String caseId; private String clusterId;
        private String decisionType; private String reasonCode; private String decidedByPrincipalId;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Feedback {
        private String feedbackId; private Long tenantId; private String decisionId; private String caseId;
        private String feedbackType; private String reasonCode; private String recordedByPrincipalId;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class StatusHistory {
        private Long tenantId; private String aggregateType; private String aggregateId; private Long aggregateVersion;
        private String previousStatus; private String currentStatus; private Long operationId; private String reasonCode;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }
}
