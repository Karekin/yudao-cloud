package cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 库存健康快照分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InventoryHealthSnapshotPageReqVO extends PageParam {
    @Schema(description = "策略 ID")
    private String policyId;

    @Schema(description = "关键字，匹配 snapshotCode / policyId / policyVersionId / ledgerWatermarkRef")
    private String keyword;
}
