package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范库区分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WarehouseZonePageReqVO extends PageParam {

    @Schema(description = "所属仓库 ID，精确匹配", example = "WH-2026-0001")
    private String warehouseId;

    @Schema(description = "库区编码，支持模糊匹配", example = "Z-A")
    private String zoneCode;

    @Schema(description = "库区状态：DRAFT / ACTIVE / INACTIVE", example = "ACTIVE")
    private String status;
}
