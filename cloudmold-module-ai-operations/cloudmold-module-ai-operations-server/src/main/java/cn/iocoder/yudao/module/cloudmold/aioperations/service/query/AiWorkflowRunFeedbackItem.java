package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 运行反馈工件")
@Data
public class AiWorkflowRunFeedbackItem {

    private String feedbackId;
    private String feedbackType;
    private String outcomeCode;
    private String evaluatorType;
    private String evidenceRef;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
