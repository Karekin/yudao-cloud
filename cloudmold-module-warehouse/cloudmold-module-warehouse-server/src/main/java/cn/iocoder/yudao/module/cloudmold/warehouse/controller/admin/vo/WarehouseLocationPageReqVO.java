package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范库位分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WarehouseLocationPageReqVO extends PageParam {

    @Schema(description = "所属仓库 ID，精确匹配", example = "WH-2026-0001")
    private String warehouseId;

    @Schema(description = "所属库区 ID，精确匹配", example = "Z-A")
    private String zoneId;

    @Schema(description = "库位编码，支持模糊匹配", example = "A-01-02")
    private String locationCode;

    @Schema(description = "库位状态：DRAFT / ACTIVE / INACTIVE", example = "ACTIVE")
    private String status;
}
