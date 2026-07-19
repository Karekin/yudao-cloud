package cn.iocoder.yudao.module.cloudmold.identity.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范身份主体分页项")
@Data
public class IdentityPrincipalPageItem {

    @Schema(description = "规范身份主体 ID")
    private String principalId;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "身份主体类型")
    private String principalType;

    @Schema(description = "身份主体状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
