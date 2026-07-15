package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_advertising_interaction")
public class AdvertisingInteractionDO {
    @TableId(type = IdType.INPUT)
    private String interactionId;
    private Long tenantId;
    private String deduplicationKey;
    private String interactionType;
    private String placementId;
    private String campaignId;
    private String principalId;
    private String sessionId;
    private String sourceInteractionId;
    private String orderRef;
    private Long attributionAmountMinor;
    private String currencyCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
