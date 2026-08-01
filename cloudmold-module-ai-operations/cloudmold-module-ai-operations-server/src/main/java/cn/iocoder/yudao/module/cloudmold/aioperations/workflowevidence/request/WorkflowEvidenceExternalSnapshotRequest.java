package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Workflow external evidence snapshot request")
@Data
public class WorkflowEvidenceExternalSnapshotRequest {

    @Schema(description = "可选快照 ID")
    private String snapshotId;

    @Schema(description = "外部 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "抓取时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime fetchedAt;

    @Schema(description = "摘要", requiredMode = Schema.RequiredMode.REQUIRED)
    private String summary;

    @Schema(description = "内容摘要 hash", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contentHashSha256;

    @Schema(description = "来源类别", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceClass;

    @Schema(description = "置信度 0-1", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal confidence;

    @Schema(description = "适用地区")
    private String region;

    @Schema(description = "适用范围")
    private String applicability;

    @Schema(description = "严重级别", requiredMode = Schema.RequiredMode.REQUIRED)
    private String severity;

    @Schema(description = "DQC 状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dqcStatus;

    @Schema(description = "记录状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "新鲜度截止")
    private LocalDateTime freshUntil;

    @Schema(description = "证据时间窗起点", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime windowStart;

    @Schema(description = "证据时间窗终点", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime windowEnd;
}
