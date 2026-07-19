package cn.iocoder.yudao.module.cloudmold.merchant.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范商家分页项")
@Data
public class MerchantPageItem {

    @Schema(description = "规范商家 ID")
    private String merchantId;

    @Schema(description = "商家编码")
    private String merchantCode;

    @Schema(description = "法人实体 ID")
    private String legalEntityId;

    @Schema(description = "法人名称（JOIN 法人实体表取得）")
    private String legalName;

    @Schema(description = "商家状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
