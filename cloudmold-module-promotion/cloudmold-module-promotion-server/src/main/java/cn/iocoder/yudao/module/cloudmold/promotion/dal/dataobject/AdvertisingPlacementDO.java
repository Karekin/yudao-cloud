package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_advertising_placement")
public class AdvertisingPlacementDO {
    @TableId(type = IdType.INPUT)
    private String placementId;
    private Long tenantId;
    private String placementCode;
    private String campaignId;
    private String name;
    private String channelCode;
    private String pageCode;
    private String slotCode;
    private String creativeRef;
    private String status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
