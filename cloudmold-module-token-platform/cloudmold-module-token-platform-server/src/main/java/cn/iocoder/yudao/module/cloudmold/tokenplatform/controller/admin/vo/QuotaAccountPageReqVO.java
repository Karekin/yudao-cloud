package cn.iocoder.yudao.module.cloudmold.tokenplatform.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI Token 配额账户分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QuotaAccountPageReqVO extends PageParam {

    @Schema(description = "账户 ID")
    private String accountId;

    @Schema(description = "持有者主体 ID")
    private String principalId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
