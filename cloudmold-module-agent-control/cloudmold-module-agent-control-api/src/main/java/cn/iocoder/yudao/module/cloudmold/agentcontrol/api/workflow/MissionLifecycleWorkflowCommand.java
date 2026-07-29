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
public class MissionLifecycleWorkflowCommand implements Serializable {

    private String idempotencyKey;
    private String missionId;
    private String title;
    private String objectiveJson;
    private String correlationId;
    private Long supervisorUserId;
    private Long inventoryAgentUserId;
    private Long buyerAgentUserId;
    private Long customerServiceAgentUserId;
    private Instant deadlineAt;
}
