package cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范来源身份分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IdentitySourcePageReqVO extends PageParam {

    @Schema(description = "规范身份主体 ID，精确匹配", example = "00000000-0000-0000-0000-000000000000")
    private String principalId;

    @Schema(description = "来源系统，精确匹配", example = "TMALL")
    private String sourceSystem;

    @Schema(description = "来源类型，精确匹配", example = "SHOP")
    private String sourceType;

    @Schema(description = "来源身份状态：ACTIVE / REVOKED", example = "ACTIVE")
    private String status;
}
