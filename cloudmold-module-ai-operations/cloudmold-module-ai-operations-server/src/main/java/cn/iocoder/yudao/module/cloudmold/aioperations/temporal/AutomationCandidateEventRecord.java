package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Immutable provenance link between a domain event and an automation route.
 *
 * <p>The primary key is consumer + event, so a poison event is durably
 * rejected for one route without blocking other routes or later events.</p>
 */
@Data
@Accessors(chain = true)
public class AutomationCandidateEventRecord {

    private Long tenantId;
    private String consumerId;
    private String candidateId;
    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private String sourceSystem;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String correlationId;
    private String causationId;
    private String payloadHash;
    private String disposition;
    private String rejectionReason;
    private LocalDateTime occurredAt;
    private LocalDateTime processedAt;
}
