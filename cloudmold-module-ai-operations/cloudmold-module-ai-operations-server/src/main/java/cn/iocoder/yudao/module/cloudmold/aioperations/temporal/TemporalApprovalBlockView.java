package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批门与被暂停的 Temporal 运行之间的可审计关联。
 */
@Data
@Builder
public class TemporalApprovalBlockView {
    private String approvalId;
    private String workOrderId;
    private String scheduleId;
    private String temporalWorkflowId;
    private String temporalRunId;
    private String skillTaskId;
    private String managedRunId;
    private String status;
    private LocalDateTime updatedAt;
}
