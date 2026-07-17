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
    private ClueDefinition clue;
    private AlertDefinition alert;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ObservationDefinition {
        private String observationId;
        private String sourceSystem;
        private String sourceEventId;
        private String observationType;
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
