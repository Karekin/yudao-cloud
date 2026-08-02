package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范库存调拨分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StockTransferPageReqVO extends PageParam {

    @Schema(description = "调拨单号、申请单号、规范 SKU、仓库或来源业务关键字")
    private String keyword;

    @Schema(description = "调拨单状态，精确匹配", example = "PREPARE")
    private String orderStatus;

    @Schema(description = "来源规范仓库 ID")
    private String sourceWarehouseId;

    @Schema(description = "目标规范仓库 ID")
    private String targetWarehouseId;
}
