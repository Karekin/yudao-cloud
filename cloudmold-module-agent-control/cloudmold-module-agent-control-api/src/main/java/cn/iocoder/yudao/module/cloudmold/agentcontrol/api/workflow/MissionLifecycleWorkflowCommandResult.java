package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionLifecycleWorkflowCommandResult implements Serializable {

    private String workflowScope;
    private String missionId;
    private String missionType;
    private Long aggregateVersion;
    private String status;
    private boolean duplicate;
}
