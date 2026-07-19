package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范 Inventory v3 余额分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InventoryV3BalancePageReqVO extends PageParam {

    @Schema(description = "SKU 编码，支持模糊匹配", example = "YS-DA4E609E-BLACK-L")
    private String skuCode;

    @Schema(description = "仓库编码，支持模糊匹配", example = "WH-SH-01")
    private String warehouseCode;

    @Schema(description = "库位编码，支持模糊匹配", example = "A-01-01")
    private String locationCode;

    @Schema(description = "批次编码，支持模糊匹配", example = "LOT-20260715-01")
    private String lotCode;

    @Schema(description = "货主类型", example = "MERCHANT")
    private String ownerType;

    @Schema(description = "货主 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String ownerId;

    @Schema(description = "库存状态", example = "SELLABLE")
    private String stockStatus;

    @Schema(description = "质量状态", example = "QUALIFIED")
    private String qualityStatus;

    @Schema(description = "仅返回非零余额", example = "true")
    private Boolean onlyNonZero;
}
