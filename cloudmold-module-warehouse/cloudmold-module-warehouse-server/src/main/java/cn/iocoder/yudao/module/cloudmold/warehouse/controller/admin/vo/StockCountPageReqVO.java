package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范库存盘点分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StockCountPageReqVO extends PageParam {

    @Schema(description = "盘点单号、规范 SKU、仓库、库位关键字")
    private String keyword;

    @Schema(description = "盘点状态，精确匹配", example = "COUNTING")
    private String status;

    @Schema(description = "盘点模式，精确匹配", example = "BLIND_COUNT")
    private String countMode;
}
