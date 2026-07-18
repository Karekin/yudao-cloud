package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventOutboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OutboxAppenderImpl implements OutboxAppender {

    static final int STATUS_PENDING = 0;
    private static final Pattern EVENT_TYPE = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

    private final CloudmoldEventOutboxMapper outboxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppendDomainEventResult append(AppendDomainEventCommand command) {
        validate(command);
        DomainEventCanonicalizer.CanonicalPayload payload = DomainEventCanonicalizer.canonicalize(command.getPayload());
        String eventId = command.getEventId() != null ? command.getEventId() : UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        CloudmoldEventOutboxDO row = new CloudmoldEventOutboxDO()
                .setEventId(eventId)
                .setEventType(command.getEventType())
                .setSchemaVersion(command.getSchemaVersion())
                .setSourceSystem(command.getSourceSystem())
                .setTenantId(command.getTenantId())
                .setAggregateType(command.getAggregateType())
                .setAggregateId(command.getAggregateId())
                .setAggregateVersion(command.getAggregateVersion())
                .setEventSequence(command.getEventSequence() != null ? command.getEventSequence() : (short) 1)
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC))
                .setRecordedAt(now)
                .setTraceId(command.getTraceId())
                .setCorrelationId(command.getCorrelationId())
                .setCausationId(command.getCausationId())
                .setIdempotencyKey(command.getIdempotencyKey())
                .setPayload(payload.json())
                .setHeaders(command.getHeaders() != null ? JsonUtils.toJsonString(command.getHeaders()) : null)
                .setPayloadHash(payload.sha256())
                .setDestination(command.getDestination())
                .setStatus(STATUS_PENDING)
                .setAvailableAt(now)
                .setAttemptCount(0)
                .setMaxAttempts(command.getMaxAttempts() != null ? command.getMaxAttempts() : 20);
        try {
            outboxMapper.insert(row);
            return new AppendDomainEventResult(eventId, payload.sha256(), false);
        } catch (DuplicateKeyException duplicate) {
            CloudmoldEventOutboxDO existing = outboxMapper.selectByIdempotencyKey(
                    command.getTenantId(), command.getEventType(), command.getIdempotencyKey());
            if (existing != null && Objects.equals(existing.getPayloadHash(), payload.sha256())
                    && Objects.equals(existing.getAggregateType(), command.getAggregateType())
                    && Objects.equals(existing.getAggregateId(), command.getAggregateId())) {
                return new AppendDomainEventResult(existing.getEventId(), existing.getPayloadHash(), true);
            }
            String databaseCause = duplicate.getMostSpecificCause().getMessage();
            throw new IllegalStateException("event uniqueness conflict with different semantic content: "
                    + databaseCause, duplicate);
        }
    }

    private static void validate(AppendDomainEventCommand command) {
        require(command != null, "command is required");
        require(command.getTenantId() != null && command.getTenantId() > 0, "tenantId must be positive");
        require(command.getEventType() != null && EVENT_TYPE.matcher(command.getEventType()).matches(), "invalid eventType");
        require(command.getSchemaVersion() != null && command.getSchemaVersion() > 0, "schemaVersion must be positive");
        require(notBlank(command.getSourceSystem()), "sourceSystem is required");
        require(notBlank(command.getAggregateType()), "aggregateType is required");
        require(notBlank(command.getAggregateId()), "aggregateId is required");
        require(command.getAggregateVersion() != null && command.getAggregateVersion() > 0, "aggregateVersion must be positive");
        require(command.getOccurredAt() != null, "occurredAt is required");
        require(notBlank(command.getCorrelationId()), "correlationId is required");
        require(notBlank(command.getIdempotencyKey()) && command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        require(command.getPayload() != null, "payload is required");
        require(notBlank(command.getDestination()), "destination is required");
        if (command.getEventId() != null) {
            UUID.fromString(command.getEventId());
        }
        UUID.fromString(command.getCorrelationId());
        if (command.getCausationId() != null) {
            UUID.fromString(command.getCausationId());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

}
