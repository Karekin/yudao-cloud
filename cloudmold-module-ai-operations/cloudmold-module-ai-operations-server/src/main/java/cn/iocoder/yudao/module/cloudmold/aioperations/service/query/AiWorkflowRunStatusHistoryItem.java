package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 运行状态历史")
@Data
public class AiWorkflowRunStatusHistoryItem {

    private String historyId;
    private Long operationId;
    private String operationType;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private String errorCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
