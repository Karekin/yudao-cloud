package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 工作流运行行")
@Data
public class AiWorkflowRunPageItem {

    private String runId;
    private String runKey;
    private String applicationId;
    private String workflowId;
    private String workflowVersion;
    private String triggerType;
    private String status;
    private String errorCode;
    private Long aggregateVersion;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
