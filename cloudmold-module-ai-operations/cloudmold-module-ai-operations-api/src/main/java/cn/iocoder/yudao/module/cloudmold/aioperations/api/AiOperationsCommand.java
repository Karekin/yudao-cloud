package cn.iocoder.yudao.module.cloudmold.aioperations.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOperationsCommand {
    private AiOperationsOperation operation;
    private String idempotencyKey;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private ApplicationDefinition application;
    private WorkflowVersionDefinition workflowVersion;
    private WorkflowRunDefinition workflowRun;
    private InvocationAttemptDefinition invocationAttempt;
    private OutcomeFeedbackDefinition outcomeFeedback;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApplicationDefinition {
        private String applicationId;
        private String applicationCode;
        private String name;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkflowVersionDefinition {
        private String workflowId;
        private String workflowCode;
        private String applicationId;
        private String workflowVersionId;
        private Long workflowVersion;
        private Long expectedDefinitionVersion;
        private String definitionRef;
        private String definitionSha256;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkflowRunDefinition {
        private String runId;
        private String runKey;
        private String applicationId;
        private String workflowId;
        private Long workflowVersion;
        private String triggerType;
        private String businessRef;
        private Integer expectedInvocationCount;
        private Long expectedVersion;
        private String errorCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvocationAttemptDefinition {
        private String attemptId;
        private String attemptKey;
        private String runId;
        private String stepRef;
        private Integer attemptNo;
        private String providerCode;
        private String modelCode;
        private String providerRequestRef;
        private String outcome;
        private Long inputTokens;
        private Long cachedInputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private Long latencyMillis;
        private Long costAmountMinor;
        private String currencyCode;
        private String pricingVersionRef;
        private String errorCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OutcomeFeedbackDefinition {
        private String feedbackId;
        private String feedbackKey;
        private String runId;
        private String feedbackType;
        private String outcomeCode;
        private String evaluatorType;
        private String evidenceRef;
    }
}
