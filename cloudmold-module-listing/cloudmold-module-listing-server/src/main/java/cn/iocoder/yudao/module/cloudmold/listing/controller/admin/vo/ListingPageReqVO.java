package cn.iocoder.yudao.module.cloudmold.listing.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold Listing 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ListingPageReqVO extends PageParam {

    @Schema(description = "Listing ID，精确匹配", example = "5d4c5c9-....")
    private String listingId;

    @Schema(description = "Listing 单号，支持模糊匹配", example = "CML20260719")
    private String listingNo;

    @Schema(description = "标题，支持模糊匹配", example = "Summer Dress")
    private String title;

    @Schema(description = "商家 ID，精确匹配", example = "merchant-1")
    private String merchantId;

    @Schema(description = "店铺 ID，精确匹配", example = "shop-1")
    private String shopId;

    @Schema(description = "渠道编码，精确匹配", example = "YSHOPPING")
    private String channelCode;

    @Schema(description = "规范 SPU ID，精确匹配", example = "spu-1")
    private String canonicalSpuId;

    @Schema(description = "Listing 状态，精确匹配", example = "PUBLISHED")
    private String status;
}
