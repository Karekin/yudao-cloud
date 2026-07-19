package cn.iocoder.yudao.module.cloudmold.identity.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 身份操作记录分页项")
@Data
public class IdentityOperationPageItem {

    @Schema(description = "操作记录 ID")
    private Long operationId;

    @Schema(description = "幂等键")
    private String idempotencyKey;

    @Schema(description = "命令类型")
    private String commandType;

    @Schema(description = "操作状态：0=待处理 / 10=成功")
    private Integer status;

    @Schema(description = "关联规范身份主体 ID")
    private String principalId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
