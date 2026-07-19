package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold AI 工作流运行分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiWorkflowRunPageReqVO extends PageParam {

    @Schema(description = "运行 ID")
    private String runId;

    @Schema(description = "运行业务键")
    private String runKey;

    @Schema(description = "应用 ID")
    private String applicationId;

    @Schema(description = "工作流 ID")
    private String workflowId;

    @Schema(description = "触发类型")
    private String triggerType;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
