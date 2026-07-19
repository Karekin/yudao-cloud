package cn.iocoder.yudao.module.cloudmold.merchant.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范商家分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MerchantPageReqVO extends PageParam {

    @Schema(description = "商家编码，支持模糊匹配", example = "M-2026-0001")
    private String merchantCode;

    @Schema(description = "商家状态：PENDING_ACTIVATION / ACTIVE / RESTRICTED / SUSPENDED / EXITING / CLOSED", example = "ACTIVE")
    private String status;

    @Schema(description = "法人名称，支持模糊匹配", example = "杭州某某科技有限公司")
    private String legalName;
}
