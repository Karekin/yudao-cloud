package cn.iocoder.yudao.module.cloudmold.identity.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 身份操作记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IdentityOperationPageReqVO extends PageParam {

    @Schema(description = "规范身份主体 ID，精确匹配", example = "00000000-0000-0000-0000-000000000000")
    private String principalId;

    @Schema(description = "命令类型，精确匹配", example = "LINK_SOURCE")
    private String commandType;

    @Schema(description = "操作状态：0=待处理 / 10=成功", example = "10")
    private Integer status;
}
