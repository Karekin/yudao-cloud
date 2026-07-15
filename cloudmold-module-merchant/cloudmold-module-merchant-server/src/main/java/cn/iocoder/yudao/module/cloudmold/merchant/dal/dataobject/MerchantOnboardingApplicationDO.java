package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_onboarding_application")
@Data
@Accessors(chain = true)
public class MerchantOnboardingApplicationDO {
    @TableId(type = IdType.INPUT)
    private String applicationId;
    private Long tenantId;
    private String runId;
    private String legalEntityId;
    private String ownerPrincipalId;
    private String channelCode;
    private String externalShopId;
    private String status;
    private String decisionReason;
    private String merchantId;
    private String shopId;
    private String ownerAssignmentId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
