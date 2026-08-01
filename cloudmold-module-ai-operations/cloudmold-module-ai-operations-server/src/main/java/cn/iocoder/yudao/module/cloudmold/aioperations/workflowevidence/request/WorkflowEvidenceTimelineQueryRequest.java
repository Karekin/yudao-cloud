package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "Workflow Evidence timeline query request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkflowEvidenceTimelineQueryRequest extends PageParam {

    @Schema(description = "固定 workflowId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String workflowId;

    @Schema(description = "时间窗起点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime windowStart;

    @Schema(description = "时间窗终点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime windowEnd;

    @Override
    @Max(value = 100, message = "每页条数最大值为 100")
    public Integer getPageSize() {
        return super.getPageSize();
    }
}
