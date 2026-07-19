package cn.iocoder.yudao.module.cloudmold.merchant.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范店铺分页项")
@Data
public class MerchantShopPageItem {

    @Schema(description = "规范店铺 ID")
    private String shopId;

    @Schema(description = "所属规范商家 ID")
    private String merchantId;

    @Schema(description = "商家编码（JOIN 商家账户表取得）")
    private String merchantCode;

    @Schema(description = "渠道编码")
    private String channelCode;

    @Schema(description = "外部店铺 ID")
    private String externalShopId;

    @Schema(description = "店铺状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
