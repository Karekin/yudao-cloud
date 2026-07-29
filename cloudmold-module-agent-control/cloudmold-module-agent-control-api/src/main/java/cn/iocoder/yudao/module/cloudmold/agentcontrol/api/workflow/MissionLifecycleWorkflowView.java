package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionLifecycleWorkflowView implements Serializable {

    private String workflowScope;
    private String missionId;
    private String missionType;
    private String templateVersion;
    private String title;
    private String objectiveJson;
    private String correlationId;
    private String status;
    private Long supervisorUserId;
    private Long version;
    private Instant startedAt;
    private Instant deadlineAt;
    private Instant completedAt;
    private List<MissionLifecycleGoalView> goals;
    private List<MissionLifecycleWorkOrderView> workOrders;
}
