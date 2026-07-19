package cn.iocoder.yudao.module.cloudmold.promotion.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 营销活动分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PromotionCampaignPageReqVO extends PageParam {

    @Schema(description = "活动 ID")
    private String campaignId;

    @Schema(description = "活动编码，支持模糊匹配")
    private String campaignCode;

    @Schema(description = "活动名称，支持模糊匹配")
    private String name;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "活动类型 ACTIVITY/COUPON/ADVERTISING/GENERAL")
    private String campaignKind;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
