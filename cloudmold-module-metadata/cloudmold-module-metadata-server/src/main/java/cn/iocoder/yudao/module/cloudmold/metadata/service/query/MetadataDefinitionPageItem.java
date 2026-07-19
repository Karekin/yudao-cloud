package cn.iocoder.yudao.module.cloudmold.metadata.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 元数据定义分页项")
@Data
public class MetadataDefinitionPageItem {

    private String definitionId;
    private String definitionKind;
    private String definitionCode;
    private String displayName;
    private String status;
    private Long currentVersion;
    private String ownerPrincipalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
