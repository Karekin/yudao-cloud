package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 调用观测行")
@Data
public class AiObservationPageItem {

    private String attemptId;
    private String runId;
    private String runKey;
    private String applicationId;
    private String workflowId;
    private Long workflowVersion;
    private String stepRef;
    private Integer attemptNo;
    private String providerCode;
    private String modelCode;
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
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
