package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 反馈证据工件行")
@Data
public class AiArtifactPageItem {

    private String feedbackId;
    private String runId;
    private String runKey;
    private String applicationId;
    private String applicationCode;
    private String workflowId;
    private String workflowCode;
    private Long workflowVersion;
    private String feedbackType;
    private String outcomeCode;
    private String evaluatorType;
    private String evidenceRef;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
