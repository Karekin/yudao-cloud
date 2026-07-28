package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String phase;
    private String temporalWorkflowId;
    private String temporalRunId;
    private Long executionUserId;
    private String workOrderId;
    private String approvalId;
    private String taskId;
    private String managedRunId;
    private String errorCode;
    private String waitingOn;
    private String resumableStatus;
    private String approvalDecision;
    private String pauseReason;
    private String cancelReason;
    private String businessReferenceId;
    private String waitReference;
    private Integer controlEventCount;
    private TemporalManagedBusinessResult businessResult;

    @JsonIgnore
    public boolean isTerminal() {
        return "REJECTED".equals(status)
                || "CANCELLED".equals(status)
                || "TIMED_OUT".equals(status)
                || "SUCCEEDED".equals(status)
                || "NEEDS_REVIEW".equals(status);
    }

    @JsonIgnore
    public boolean isWaitingApproval() {
        return "WAITING_APPROVAL".equals(status);
    }

    @JsonIgnore
    public boolean isPaused() {
        return "PAUSED".equals(status);
    }
}
