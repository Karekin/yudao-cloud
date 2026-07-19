package cn.iocoder.yudao.module.cloudmold.operationsintelligence.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 运营告警工单分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OperationsAlertPageReqVO extends PageParam {

    @Schema(description = "告警 ID")
    private String alertId;

    @Schema(description = "告警编码，支持模糊匹配")
    private String alertCode;

    @Schema(description = "来源类型 OBSERVATION/CLUE/METADATA_TASK/METRIC")
    private String sourceType;

    @Schema(description = "严重程度 LOW/MEDIUM/HIGH/CRITICAL")
    private String severity;

    @Schema(description = "类目")
    private String category;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "当前处理人主体")
    private String currentActorPrincipalId;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
