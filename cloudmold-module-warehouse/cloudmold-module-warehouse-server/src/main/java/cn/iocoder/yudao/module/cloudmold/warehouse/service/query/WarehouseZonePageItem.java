package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范库区分页项")
@Data
public class WarehouseZonePageItem {

    @Schema(description = "规范库区 ID")
    private String zoneId;

    @Schema(description = "所属仓库 ID")
    private String warehouseId;

    @Schema(description = "仓库编码（JOIN 仓库表取得）")
    private String warehouseCode;

    @Schema(description = "库区编码")
    private String zoneCode;

    @Schema(description = "库区名称")
    private String name;

    @Schema(description = "库区类型")
    private String zoneType;

    @Schema(description = "库区状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
