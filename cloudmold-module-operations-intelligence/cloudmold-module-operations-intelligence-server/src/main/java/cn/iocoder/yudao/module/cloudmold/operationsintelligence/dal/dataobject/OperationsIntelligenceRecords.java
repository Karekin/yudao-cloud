package cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

public final class OperationsIntelligenceRecords {
    private OperationsIntelligenceRecords() {}

    @Data @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class Observation {
        private String observationId;
        private Long tenantId;
        private String sourceSystem;
        private String sourceEventId;
        private String observationType;
        private Integer classificationContractVersion;
        private String taxonomyId;
        private String taxonomyVersionId;
        private Long taxonomyDefinitionVersion;
        private String eventCode;
        private String intelligenceLevelCode;
        private String subjectType;
        private String subjectRef;
        private String evidenceRef;
        private String contentSha256;
        private LocalDateTime observedAt;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class ModelResult {
        private String modelResultId;
        private Long tenantId;
        private String observationId;
        private String invocationAttemptRef;
        private String modelVersionRef;
        private String outcomeCode;
        private Integer scoreBasisPoints;
        private Integer retryNo;
        private String evidenceRef;
        private String resultSha256;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Clue {
        private String clueId;
        private Long tenantId;
        private String observationId;
        private String modelResultId;
        private String clueType;
        private String sourceCode;
        private LocalDateTime sourcePublishedAt;
        private String evidenceRef;
        private String evidenceSha256;
        private String status;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ClueReview {
        private String reviewId;
        private Long tenantId;
        private String clueId;
        private String decision;
        private String reasonCode;
        private String reviewerPrincipalId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Alert {
        private String alertId;
        private Long tenantId;
        private String alertCode;
        private String sourceType;
        private String sourceRef;
        private String severity;
        private String category;
        private String subcategory;
        private String evidenceRef;
        private String titleSha256;
        private String status;
        private String currentActorPrincipalId;
        private Long version;
        private LocalDateTime openedAt;
        private LocalDateTime terminalAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class AlertHistory {
        private Long tenantId;
        private String alertId;
        private Long alertVersion;
        private String previousStatus;
        private String currentStatus;
        private String actorPrincipalId;
        private String reasonCode;
        private Long operationId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }
}
