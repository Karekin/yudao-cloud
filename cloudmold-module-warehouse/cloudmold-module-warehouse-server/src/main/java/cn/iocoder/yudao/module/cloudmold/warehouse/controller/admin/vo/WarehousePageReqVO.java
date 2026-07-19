package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范仓库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WarehousePageReqVO extends PageParam {

    @Schema(description = "仓库编码，支持模糊匹配", example = "WH-2026-0001")
    private String warehouseCode;

    @Schema(description = "仓库类型，精确匹配", example = "FULFILLMENT")
    private String warehouseType;

    @Schema(description = "仓库状态：DRAFT / ACTIVE / INACTIVE", example = "ACTIVE")
    private String status;
}
