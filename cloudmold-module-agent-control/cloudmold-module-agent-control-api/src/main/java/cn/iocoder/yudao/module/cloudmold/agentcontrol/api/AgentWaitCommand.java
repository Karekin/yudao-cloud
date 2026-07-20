package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;
import java.io.Serializable;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentWaitCommand implements Serializable {
    private String workOrderId;
    private String runId;
    private String leaseOwner;
    private String leaseToken;
    private Long fencingToken;
    private String waitType;
    private String eventType;
    private String sourceSystem;
    private String aggregateType;
    private String aggregateId;
    private String correlationId;
    private String matcherCode;
    private Instant dueAt;
}
