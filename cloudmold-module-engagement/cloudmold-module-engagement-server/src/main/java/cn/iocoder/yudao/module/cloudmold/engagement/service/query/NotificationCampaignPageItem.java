package cn.iocoder.yudao.module.cloudmold.engagement.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 通知活动分页项")
@Data
public class NotificationCampaignPageItem {

    private String campaignId;
    private String campaignCode;
    private String campaignName;
    private String channel;
    private String status;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
