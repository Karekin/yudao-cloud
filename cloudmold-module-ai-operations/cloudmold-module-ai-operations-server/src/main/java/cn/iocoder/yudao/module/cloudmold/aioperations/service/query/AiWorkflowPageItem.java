package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 工作流控制台行")
@Data
public class AiWorkflowPageItem {

    private String applicationId;
    private String applicationCode;
    private String applicationName;
    private String applicationStatus;
    private String workflowId;
    private String workflowCode;
    private String workflowVersionId;
    private Long workflowVersion;
    private String definitionRef;
    private String definitionSha256;
    private LocalDateTime publishedAt;
    private Long runCount;
    private Long runningRunCount;
    private Long succeededRunCount;
    private Long failedRunCount;
    private Long cancelledRunCount;
    private String lastRunId;
    private String lastRunStatus;
    private LocalDateTime lastRunStartedAt;
    private LocalDateTime lastRunFinishedAt;
}
