package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;
import java.io.Serializable;
import java.math.BigDecimal;
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
    private String metricId;
    private String metricVersion;
    private String dimensionHash;
    private String comparisonOperator;
    private BigDecimal thresholdValue;
    private String unitCode;
    private Integer maxAgeSeconds;
    private Instant dueAt;
}
