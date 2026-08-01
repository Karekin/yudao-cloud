package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Workflow evidence digest view")
@Builder
public record WorkflowEvidenceDigestView(
        String workflowId,
        String granularity,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        LocalDateTime generatedAt,
        List<WorkflowEvidenceDigestBucketView> buckets) {
}
