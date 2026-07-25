package cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 供应链计划工作项分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SupplyPlanningPageReqVO extends PageParam {
    @Schema(description = "工作项类型 FORECAST/SUPPLY_PLAN/REPLENISHMENT/INVENTORY_ISSUE")
    private String itemType;

    @Schema(description = "状态")
    private String status;
}
