package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_invocation_usage")
@Data
@Accessors(chain = true)
public class InvocationUsageDO {
    @TableId(type = IdType.INPUT)
    private String usageId;
    private Long tenantId;
    private String requestId;
    private String principalId;
    private String accountId;
    private String credentialId;
    private String offeringId;
    private String providerCode;
    private String modelCode;
    private String resultStatus;
    private Long inputTokens;
    private Long cachedInputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long durationMillis;
    private Long quotaCostMicrounits;
    private String pricingVersionId;
    private String ledgerEntryId;
    private String errorCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
