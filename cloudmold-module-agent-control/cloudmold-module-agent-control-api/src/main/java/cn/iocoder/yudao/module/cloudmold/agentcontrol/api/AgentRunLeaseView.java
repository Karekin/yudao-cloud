package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;
import java.io.Serializable;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentRunLeaseView implements Serializable {
    private String missionId;
    private String workOrderId;
    private String runId;
    private String leaseToken;
    private Long fencingToken;
    private String roleCode;
    private String actionCode;
    private Instant leaseUntil;
}
