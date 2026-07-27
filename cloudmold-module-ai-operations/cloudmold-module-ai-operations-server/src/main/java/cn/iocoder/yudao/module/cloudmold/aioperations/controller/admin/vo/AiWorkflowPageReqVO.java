package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold AI 工作流控制台分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiWorkflowPageReqVO extends PageParam {

    @Schema(description = "应用 ID")
    private String applicationId;

    @Schema(description = "应用编码")
    private String applicationCode;

    @Schema(description = "应用状态")
    private String applicationStatus;

    @Schema(description = "工作流 ID")
    private String workflowId;

    @Schema(description = "工作流编码")
    private String workflowCode;
}
