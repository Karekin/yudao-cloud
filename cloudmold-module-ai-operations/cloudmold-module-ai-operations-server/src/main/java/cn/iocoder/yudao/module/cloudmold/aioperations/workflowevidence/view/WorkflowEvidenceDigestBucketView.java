package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Schema(description = "Workflow evidence digest bucket")
@Builder
public record WorkflowEvidenceDigestBucketView(
        LocalDate bucketStart,
        LocalDate bucketEnd,
        Long totalCount,
        Long observationCount,
        Long problemCount,
        Long feedbackCount,
        Long externalSnapshotCount,
        Long releaseEligibleCount,
        Long criticalCount,
        Long dqcFailCount,
        Long staleCount,
        Long openProblemCount) {
}
