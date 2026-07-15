package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_coupon_template")
public class CouponTemplateDO {
    @TableId(type = IdType.INPUT)
    private String templateId;
    private Long tenantId;
    private String templateCode;
    private String campaignId;
    private String title;
    private String benefitType;
    private Long faceAmountMinor;
    private Long thresholdMinor;
    private Integer discountBasisPoints;
    private Long capAmountMinor;
    private String currencyCode;
    private String funderType;
    private String merchantId;
    private String status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
