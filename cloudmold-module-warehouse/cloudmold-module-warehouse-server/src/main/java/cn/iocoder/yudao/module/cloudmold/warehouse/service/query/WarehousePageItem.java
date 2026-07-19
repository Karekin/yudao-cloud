package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范仓库分页项")
@Data
public class WarehousePageItem {

    @Schema(description = "规范仓库 ID")
    private String warehouseId;

    @Schema(description = "仓库编码")
    private String warehouseCode;

    @Schema(description = "仓库名称")
    private String name;

    @Schema(description = "仓库类型")
    private String warehouseType;

    @Schema(description = "时区")
    private String timezone;

    @Schema(description = "仓库状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
