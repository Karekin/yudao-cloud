package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionLifecycleWorkOrderView implements Serializable {

    private String workOrderId;
    private String goalId;
    private String parentWorkOrderId;
    private String title;
    private String roleCode;
    private String actionCode;
    private String status;
    private String riskLevel;
    private Boolean executionRequired;
    private String skillId;
    private String skillVersion;
    private String approvalId;
    private Long requesterUserId;
    private Long assigneeUserId;
    private String waitingReasonCode;
    private String activeRunId;
    private Long version;
    private Instant createdAt;
    private Instant readyAt;
    private Instant deadlineAt;
    private Instant updatedAt;
    private Instant completedAt;
}
