package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;
import java.io.Serializable;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentRunClaimCommand implements Serializable {
    private String workOrderId;
    private Long workOrderExpectedVersion;
    private String leaseOwner;
    private Integer leaseSeconds;
    private String triggerType;
    private String triggerId;
}
