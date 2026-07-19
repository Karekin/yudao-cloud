package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范库位分页项")
@Data
public class WarehouseLocationPageItem {

    @Schema(description = "规范库位 ID")
    private String locationId;

    @Schema(description = "所属仓库 ID")
    private String warehouseId;

    @Schema(description = "仓库编码（JOIN 仓库表取得）")
    private String warehouseCode;

    @Schema(description = "所属库区 ID")
    private String zoneId;

    @Schema(description = "库区编码（JOIN 库区表取得）")
    private String zoneCode;

    @Schema(description = "库位编码")
    private String locationCode;

    @Schema(description = "库位名称")
    private String name;

    @Schema(description = "库位类型")
    private String locationType;

    @Schema(description = "巷道编码")
    private String aisleCode;

    @Schema(description = "货架编码")
    private String rackCode;

    @Schema(description = "贝位编码")
    private String bayCode;

    @Schema(description = "层位编码")
    private String levelCode;

    @Schema(description = "是否允许混款")
    private Boolean allowItemMixing;

    @Schema(description = "是否允许混批")
    private Boolean allowLotMixing;

    @Schema(description = "容量数量")
    private BigDecimal capacityQuantity;

    @Schema(description = "容量单位编码")
    private String capacityUomCode;

    @Schema(description = "库位状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
