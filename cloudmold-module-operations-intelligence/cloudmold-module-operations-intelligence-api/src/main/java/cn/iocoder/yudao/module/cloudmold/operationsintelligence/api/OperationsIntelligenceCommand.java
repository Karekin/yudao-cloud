package cn.iocoder.yudao.module.cloudmold.operationsintelligence.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsIntelligenceCommand {
    private OperationsIntelligenceOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private ObservationDefinition observation;
    private ModelResultDefinition modelResult;
    private ClueSourceVersionDefinition clueSourceVersion;
    private ClueSourceDeliveryDefinition clueSourceDelivery;
    private ClueDefinition clue;
    private AlertDefinition alert;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ObservationDefinition {
        private String observationId;
        private String sourceSystem;
        private String sourceEventId;
        private String observationType;
        private String taxonomyId;
        private Long taxonomyDefinitionVersion;
        private String eventCode;
        private String intelligenceLevelCode;
        private String subjectType;
        private String subjectRef;
        private String evidenceRef;
        private String contentSha256;
        private Instant observedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModelResultDefinition {
        private String modelResultId;
        private String observationId;
        private String invocationAttemptRef;
        private String modelVersionRef;
        private String outcomeCode;
        private Integer scoreBasisPoints;
        private Integer retryNo;
        private String evidenceRef;
        private String resultSha256;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ClueDefinition {
        private String clueId;
        private String sourceVersionId;
        private String observationId;
        private String modelResultId;
        private String clueType;
        private String sourceCode;
        private Instant sourcePublishedAt;
        private String evidenceRef;
        private String evidenceSha256;
        private Long expectedVersion;
        private String reviewerPrincipalId;
        private String reviewDecision;
        private String reasonCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ClueSourceVersionDefinition {
        private String sourceVersionId;
        private String sourceSystem;
        private String sourceBizId;
        private Long businessRevision;
        private String supersedesSourceVersionId;
        private String intelligenceTypeCode;
        private String sourceCode;
        private Instant sourcePublishedAt;
        private Boolean sourceValid;
        private Boolean sourceDeleted;
        private String titleSha256;
        private String summarySha256;
        private String clueInfoSha256;
        private Integer clueInfoItemCount;
        private Instant sourceObservedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ClueSourceDeliveryDefinition {
        private String deliveryId;
        private String sourceVersionId;
        private String sourceDatasetId;
        private Long sourceDatasetVersion;
        private String declaredSourceAsset;
        private String physicalSourceAsset;
        private String sourceTransport;
        private String sourceRecordKey;
        private String sourceRecordVersion;
        private String payloadSchemaVersion;
        private String sourceSchemaSha256;
        private String sourceEvidenceRef;
        private String sourceEvidenceSha256;
        private Instant sourceObservedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AlertDefinition {
        private String alertId;
        private String alertCode;
        private String sourceType;
        private String sourceRef;
        private String severity;
        private String category;
        private String subcategory;
        private String evidenceRef;
        private String titleSha256;
        private Long expectedVersion;
        private String actorPrincipalId;
        private String reasonCode;
    }
}
