package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class AutomationOutboxEventRecord {

    private Long tenantId;
    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private String sourceSystem;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private LocalDateTime occurredAt;
    private String correlationId;
    private String causationId;
    private String payload;
    private String payloadHash;
    private LocalDateTime recordedAt;
}
