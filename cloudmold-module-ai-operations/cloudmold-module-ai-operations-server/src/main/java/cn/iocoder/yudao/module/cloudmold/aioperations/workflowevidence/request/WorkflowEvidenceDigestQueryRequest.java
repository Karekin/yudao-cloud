package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Workflow Evidence digest query request")
@Data
public class WorkflowEvidenceDigestQueryRequest {

    @Schema(description = "固定 workflowId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String workflowId;

    @Schema(description = "时间窗起点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime windowStart;

    @Schema(description = "时间窗终点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime windowEnd;
}
