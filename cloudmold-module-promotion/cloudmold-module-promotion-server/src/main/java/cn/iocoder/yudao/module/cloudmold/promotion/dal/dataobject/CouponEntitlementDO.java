package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_coupon_entitlement")
public class CouponEntitlementDO {
    @TableId(type = IdType.INPUT)
    private String entitlementId;
    private Long tenantId;
    private String entitlementCode;
    private String templateId;
    private String campaignId;
    private String principalId;
    private String orderRef;
    private Long faceAmountMinor;
    private Long thresholdMinor;
    private String currencyCode;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
