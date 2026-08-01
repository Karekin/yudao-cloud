package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Workflow observation ingest request")
@Data
public class WorkflowEvidenceIngestRequest {

    @Schema(description = "服务端幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String idempotencyKey;

    @Schema(description = "可选 observation ID")
    private String observationId;

    @Schema(description = "lineageId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String lineageId;

    @Schema(description = "workflowId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String workflowId;

    @Schema(description = "workflow version", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String workflowVersion;

    @Schema(description = "proposalId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String proposalId;

    @Schema(description = "sourceType", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String sourceType;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String headline;

    @Schema(description = "详情", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String detailText;

    @Schema(description = "脱敏 evidenceRef")
    private String evidenceRef;

    @Schema(description = "来源 allowlist 绑定的 summary refs")
    private List<String> summarySourceRefs;

    @Schema(description = "模型摘要")
    private String modelSummary;

    @Schema(description = "指标 JSON")
    private JsonNode metrics;

    @Schema(description = "severity", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String severity;

    @Schema(description = "DQC 状态", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String dqcStatus;

    @Schema(description = "记录状态", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String status;

    @Schema(description = "是否可用于 release 证据")
    private Boolean releaseEligible;

    @Schema(description = "支撑 release 的额外 source types")
    private List<String> corroboratingSourceTypes;

    @Schema(description = "freshUntil")
    private LocalDateTime freshUntil;

    @Schema(description = "时间窗起点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime windowStart;

    @Schema(description = "时间窗终点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime windowEnd;

    @Schema(description = "观察发生时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDateTime observedAt;

    @Schema(description = "关联外部证据快照")
    private List<WorkflowEvidenceExternalSnapshotRequest> externalSnapshots;
}
