package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_invocation_attempt")
public class AiInvocationAttemptDO {
    @TableId(type = IdType.INPUT)
    private String attemptId;
    private Long tenantId;
    private String attemptKey;
    private String runId;
    private String applicationId;
    private String workflowId;
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
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
