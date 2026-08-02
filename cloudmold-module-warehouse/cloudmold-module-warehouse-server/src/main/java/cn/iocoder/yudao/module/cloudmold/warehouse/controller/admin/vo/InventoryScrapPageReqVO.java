package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 库存报废分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InventoryScrapPageReqVO extends PageParam {

    @Schema(description = "报废单号、原因码或货主管理对象关键字")
    private String keyword;

    @Schema(description = "报废单状态", example = "APPROVED")
    private String status;

    @Schema(description = "规范仓库 ID")
    private String warehouseId;
}
