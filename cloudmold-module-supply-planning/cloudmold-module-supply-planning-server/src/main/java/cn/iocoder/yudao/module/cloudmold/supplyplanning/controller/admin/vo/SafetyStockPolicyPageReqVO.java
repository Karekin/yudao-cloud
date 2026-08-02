package cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 安全库存策略分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SafetyStockPolicyPageReqVO extends PageParam {
    @Schema(description = "状态 DRAFT/APPROVED/PUBLISHED/RETIRED")
    private String status;

    @Schema(description = "关键字，匹配 policyCode / ownerId / canonicalSkuId / warehouseNetworkId")
    private String keyword;
}
