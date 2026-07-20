package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;
import java.io.Serializable;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentCheckpointCommand implements Serializable {
    private String checkpointId;
    private String workOrderId;
    private String runId;
    private String leaseOwner;
    private String leaseToken;
    private Long fencingToken;
    private String decisionCode;
    private String decisionJson;
}
