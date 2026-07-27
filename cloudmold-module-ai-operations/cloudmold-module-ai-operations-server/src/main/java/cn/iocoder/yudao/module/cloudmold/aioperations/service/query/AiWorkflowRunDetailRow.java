package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiWorkflowRunDetailRow {

    private String runId;
    private String runKey;
    private String applicationId;
    private String workflowId;
    private String workflowVersionId;
    private Long workflowVersion;
    private String triggerType;
    private String businessRef;
    private String status;
    private String errorCode;
    private Integer expectedInvocationCount;
    private Long aggregateVersion;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String applicationCode;
    private String applicationName;
    private String applicationStatus;

    private String workflowCode;
    private Long currentWorkflowVersion;
    private String definitionRef;
    private String definitionSha256;
    private LocalDateTime publishedAt;
}
