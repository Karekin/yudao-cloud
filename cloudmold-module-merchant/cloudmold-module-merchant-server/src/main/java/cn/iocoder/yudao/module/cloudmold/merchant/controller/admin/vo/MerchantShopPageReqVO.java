package cn.iocoder.yudao.module.cloudmold.merchant.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范店铺分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MerchantShopPageReqVO extends PageParam {

    @Schema(description = "所属商家 ID，精确匹配", example = "M-2026-0001")
    private String merchantId;

    @Schema(description = "渠道编码，精确匹配", example = "TMALL")
    private String channelCode;

    @Schema(description = "店铺状态：DRAFT / ACTIVE / PAUSED / CLOSED", example = "ACTIVE")
    private String status;
}
