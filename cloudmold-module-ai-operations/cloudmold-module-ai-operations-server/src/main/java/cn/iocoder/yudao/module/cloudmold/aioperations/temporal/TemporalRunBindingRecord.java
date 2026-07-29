package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class TemporalRunBindingRecord {
    private Long tenantId;
    private String temporalRunId;
    private String temporalWorkflowId;
    private String scheduleId;
    private String workOrderId;
    private String approvalId;
    private String managedRunId;
    private String skillTaskId;
    private String status;
    private String errorCode;
    /** 业务关联键，补货场景为 recommendationId；进入 WAITING_EVENT 时物化，供事件桥接反查 */
    private String businessReferenceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
