package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - CloudMold 规范 Inventory v3 账本分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InventoryV3LedgerPageReqVO extends PageParam {

    @Schema(description = "移动组 ID，支持模糊匹配", example = "550e8400-e29b-41d4-a716-446655440000")
    private String movementGroupId;

    @Schema(description = "命令类型", example = "RESERVE")
    private String commandType;

    @Schema(description = "业务类型", example = "TRADE_ORDER")
    private String businessType;

    @Schema(description = "业务单据 ID，支持模糊匹配", example = "order-1")
    private String businessId;

    @Schema(description = "业务明细 ID，支持模糊匹配", example = "item-1")
    private String businessItemId;

    @Schema(description = "业务单号，支持模糊匹配", example = "SO202607190001")
    private String businessNo;

    @Schema(description = "SKU 编码，支持模糊匹配", example = "YS-DA4E609E-BLACK-L")
    private String skuCode;

    @Schema(description = "仓库编码，支持模糊匹配", example = "WH-SH-01")
    private String warehouseCode;

    @Schema(description = "库位编码，支持模糊匹配", example = "A-01-01")
    private String locationCode;

    @Schema(description = "批次编码，支持模糊匹配", example = "LOT-20260715-01")
    private String lotCode;

    @Schema(description = "分录角色", example = "SINGLE")
    private String entryRole;

    @Schema(description = "发生时间起始")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime occurredTimeFrom;

    @Schema(description = "发生时间结束")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime occurredTimeTo;
}
