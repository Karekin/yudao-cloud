package cn.iocoder.yudao.module.cloudmold.metadata.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 元数据定义分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MetadataDefinitionPageReqVO extends PageParam {

    @Schema(description = "定义 ID")
    private String definitionId;

    @Schema(description = "定义种类 DATA_SOURCE/DATASET/TASK/LINEAGE/DQC_RULE/METRIC")
    private String definitionKind;

    @Schema(description = "业务编码，支持模糊匹配")
    private String definitionCode;

    @Schema(description = "显示名，支持模糊匹配")
    private String displayName;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "负责人主体")
    private String ownerPrincipalId;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
