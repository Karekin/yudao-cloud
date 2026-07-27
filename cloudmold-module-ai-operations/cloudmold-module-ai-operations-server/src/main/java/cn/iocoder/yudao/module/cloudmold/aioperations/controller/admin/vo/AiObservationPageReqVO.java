package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 调用观测分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiObservationPageReqVO extends PageParam {

    @Schema(description = "运行 ID")
    private String runId;

    @Schema(description = "应用 ID")
    private String applicationId;

    @Schema(description = "工作流 ID")
    private String workflowId;

    @Schema(description = "步骤引用")
    private String stepRef;

    @Schema(description = "提供方编码")
    private String providerCode;

    @Schema(description = "模型编码")
    private String modelCode;

    @Schema(description = "结果")
    private String outcome;

    @Schema(description = "发生时间起")
    private LocalDateTime occurredAtFrom;

    @Schema(description = "发生时间止")
    private LocalDateTime occurredAtTo;
}
