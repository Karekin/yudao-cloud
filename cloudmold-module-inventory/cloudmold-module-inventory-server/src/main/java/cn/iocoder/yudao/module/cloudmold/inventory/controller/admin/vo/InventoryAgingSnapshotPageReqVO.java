package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - Inventory 库龄效期快照分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InventoryAgingSnapshotPageReqVO extends PageParam {
    @Schema(description = "关键字，匹配 snapshotCode / bucketPolicyVersion / ledgerWatermarkRef")
    private String keyword;
}
