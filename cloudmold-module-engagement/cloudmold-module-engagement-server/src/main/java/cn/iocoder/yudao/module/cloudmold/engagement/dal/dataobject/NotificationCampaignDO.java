package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_notification_campaign")
public class NotificationCampaignDO {
    @TableId(type = IdType.INPUT)
    private String campaignId;
    private Long tenantId;
    private String campaignCode;
    private String campaignName;
    private String channel;
    private String status;
    private Long version;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
