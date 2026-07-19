package cn.iocoder.yudao.module.cloudmold.identity.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范来源身份分页项")
@Data
public class IdentitySourcePageItem {

    @Schema(description = "规范来源身份 ID")
    private String sourceIdentityId;

    @Schema(description = "所属规范身份主体 ID")
    private String principalId;

    @Schema(description = "身份主体类型（JOIN 身份主体表取得）")
    private String principalType;

    @Schema(description = "来源系统")
    private String sourceSystem;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "来源外部 ID")
    private String sourceId;

    @Schema(description = "来源身份状态")
    private String status;

    @Schema(description = "聚合版本")
    private Long version;

    @Schema(description = "生效起始时间")
    private LocalDateTime validFrom;

    @Schema(description = "生效结束时间")
    private LocalDateTime validTo;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
