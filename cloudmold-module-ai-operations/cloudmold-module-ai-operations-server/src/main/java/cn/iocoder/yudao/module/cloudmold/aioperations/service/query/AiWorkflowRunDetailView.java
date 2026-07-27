package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CloudMold AI 工作流运行控制台详情")
@Data
public class AiWorkflowRunDetailView {

    private RunView run;
    private ApplicationView application;
    private WorkflowView workflow;
    private List<AiObservationPageItem> invocationAttempts;
    private List<AiWorkflowRunFeedbackItem> feedbackArtifacts;
    private List<AiWorkflowRunStatusHistoryItem> statusHistory;

    @Schema(description = "运行详情")
    @Data
    public static class RunView {
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
    }

    @Schema(description = "应用详情")
    @Data
    public static class ApplicationView {
        private String applicationId;
        private String applicationCode;
        private String applicationName;
        private String applicationStatus;
    }

    @Schema(description = "工作流与版本详情")
    @Data
    public static class WorkflowView {
        private String workflowId;
        private String workflowCode;
        private String workflowVersionId;
        private Long workflowVersion;
        private Long currentWorkflowVersion;
        private String definitionRef;
        private String definitionSha256;
        private LocalDateTime publishedAt;
    }
}
