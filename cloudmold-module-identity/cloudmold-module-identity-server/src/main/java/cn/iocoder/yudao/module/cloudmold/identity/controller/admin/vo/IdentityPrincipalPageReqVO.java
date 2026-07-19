package cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范身份主体分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IdentityPrincipalPageReqVO extends PageParam {

    @Schema(description = "身份主体类型，精确匹配：PLATFORM_OPERATOR / MEMBER / MERCHANT_OPERATOR / WAREHOUSE_OPERATOR",
            example = "MERCHANT_OPERATOR")
    private String principalType;

    @Schema(description = "身份主体状态：ACTIVE / DISABLED", example = "ACTIVE")
    private String status;
}
