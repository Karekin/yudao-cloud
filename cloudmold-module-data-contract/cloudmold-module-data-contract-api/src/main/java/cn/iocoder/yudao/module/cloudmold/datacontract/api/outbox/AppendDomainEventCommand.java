package cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Append-only domain event. Business code must call the appender in the same transaction as its aggregate mutation.
 */
@Value
@Builder
public class AppendDomainEventCommand {

    String eventId;
    String eventType;
    Integer schemaVersion;
    String sourceSystem;
    Long tenantId;
    String aggregateType;
    String aggregateId;
    Long aggregateVersion;
    Short eventSequence;
    Instant occurredAt;
    String traceId;
    String correlationId;
    String causationId;
    String idempotencyKey;
    Map<String, Object> payload;
    Map<String, Object> headers;
    String destination;
    Integer maxAttempts;

}
