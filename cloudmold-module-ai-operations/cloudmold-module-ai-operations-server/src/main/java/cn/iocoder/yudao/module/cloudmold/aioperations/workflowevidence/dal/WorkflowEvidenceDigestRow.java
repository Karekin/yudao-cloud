package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal;

import lombok.Data;

import java.time.LocalDate;

@Data
public class WorkflowEvidenceDigestRow {
    private LocalDate bucketStart;
    private LocalDate bucketEnd;
    private Long totalCount;
    private Long observationCount;
    private Long problemCount;
    private Long feedbackCount;
    private Long externalSnapshotCount;
    private Long releaseEligibleCount;
    private Long criticalCount;
    private Long dqcFailCount;
    private Long staleCount;
    private Long openProblemCount;
}
