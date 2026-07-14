package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class OutboxMessage {

    String eventId;
    String eventType;
    Integer schemaVersion;
    String sourceSystem;
    Long tenantId;
    String aggregateType;
    String aggregateId;
    Long aggregateVersion;
    Short eventSequence;
    LocalDateTime occurredAt;
    String correlationId;
    String causationId;
    String payload;
    String headers;
    String payloadHash;
    String destination;

}
