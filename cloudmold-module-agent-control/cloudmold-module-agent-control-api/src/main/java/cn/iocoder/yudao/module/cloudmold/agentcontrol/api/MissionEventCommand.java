package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;
import java.io.Serializable;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MissionEventCommand implements Serializable {
    private String eventId;
    private Long tenantId;
    private String eventType;
    private String schemaVersion;
    private String sourceSystem;
    private String aggregateType;
    private String aggregateId;
    private String correlationId;
    private String payloadSha256;
    private Instant occurredAt;
}
