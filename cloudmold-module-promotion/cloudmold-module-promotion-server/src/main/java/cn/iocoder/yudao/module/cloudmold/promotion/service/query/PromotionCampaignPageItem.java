package cn.iocoder.yudao.module.cloudmold.promotion.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 营销活动分页项")
@Data
public class PromotionCampaignPageItem {

    private String campaignId;
    private String campaignCode;
    private String campaignKind;
    private String name;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
