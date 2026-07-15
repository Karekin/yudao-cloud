package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_campaign")
public class PromotionCampaignDO {
    @TableId(type = IdType.INPUT)
    private String campaignId;
    private Long tenantId;
    private String campaignCode;
    private String campaignKind;
    private String name;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
