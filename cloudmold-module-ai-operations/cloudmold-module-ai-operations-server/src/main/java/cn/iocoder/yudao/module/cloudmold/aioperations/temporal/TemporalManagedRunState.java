package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TemporalManagedRunState implements Serializable {
    private String status;
    private String temporalWorkflowId;
    private String temporalRunId;
    private Long executionUserId;
    private String workOrderId;
    private String approvalId;
    private String taskId;
    private String managedRunId;
    private String errorCode;
}
