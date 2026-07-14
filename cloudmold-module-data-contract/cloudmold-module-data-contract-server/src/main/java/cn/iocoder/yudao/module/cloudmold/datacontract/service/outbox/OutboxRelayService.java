package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventOutboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    static final int STATUS_PENDING = 0;
    static final int STATUS_PUBLISHED = 20;
    static final int STATUS_DEAD = 30;
    static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final CloudmoldEventOutboxMapper outboxMapper;
    private final List<EventTransportPublisher> publishers;

    /**
     * Claims each row with a conditional update, so competing workers cannot publish the same lease concurrently.
     * A crash after transport publish and before acknowledgement can still redeliver: consumers must use Inbox.
     */
    public OutboxRelayResult relayBatch(String leaseOwner, int batchSize, LocalDateTime now) {
        require(leaseOwner != null && !leaseOwner.isBlank(), "leaseOwner is required");
        require(batchSize > 0 && batchSize <= 1000, "batchSize must be between 1 and 1000");
        require(now != null, "now is required");

        List<String> candidates = outboxMapper.selectClaimableEventIds(now, batchSize);
        int claimed = 0;
        int published = 0;
        int retried = 0;
        int dead = 0;
        int unsupported = 0;
        for (String eventId : candidates) {
            if (outboxMapper.claim(eventId, leaseOwner, now.plus(LEASE_DURATION), now) != 1) {
                continue;
            }
            claimed++;
            CloudmoldEventOutboxDO row = outboxMapper.selectById(eventId);
            if (row == null) {
                continue;
            }
            EventTransportPublisher publisher = publishers.stream()
                    .filter(candidate -> candidate.supports(row.getDestination()))
                    .findFirst().orElse(null);
            if (publisher == null) {
                releaseUnsupported(row, leaseOwner, now);
                unsupported++;
                continue;
            }
            try {
                publisher.publish(toMessage(row));
                requireTransition(outboxMapper.markPublished(eventId, leaseOwner, now), eventId);
                published++;
            } catch (Exception failure) {
                boolean exhausted = row.getAttemptCount() + 1 >= row.getMaxAttempts();
                LocalDateTime availableAt = exhausted ? now : now.plus(backoff(row.getAttemptCount() + 1));
                requireTransition(outboxMapper.markPublishFailed(eventId, leaseOwner,
                        exhausted ? STATUS_DEAD : STATUS_PENDING, availableAt,
                        failure.getClass().getSimpleName(), summarize(failure)), eventId);
                if (exhausted) {
                    dead++;
                } else {
                    retried++;
                }
            }
        }
        return new OutboxRelayResult(candidates.size(), claimed, published, retried, dead, unsupported);
    }

    private void releaseUnsupported(CloudmoldEventOutboxDO row, String leaseOwner, LocalDateTime now) {
        requireTransition(outboxMapper.releaseUnsupported(row.getEventId(), leaseOwner, now.plusMinutes(1),
                "No publisher configured for destination " + row.getDestination()), row.getEventId());
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(Math.max(attempt - 1, 0), 30));
        return Duration.ofSeconds(seconds);
    }

    private static OutboxMessage toMessage(CloudmoldEventOutboxDO row) {
        return OutboxMessage.builder()
                .eventId(row.getEventId()).eventType(row.getEventType()).schemaVersion(row.getSchemaVersion())
                .sourceSystem(row.getSourceSystem())
                .tenantId(row.getTenantId()).aggregateType(row.getAggregateType()).aggregateId(row.getAggregateId())
                .aggregateVersion(row.getAggregateVersion()).eventSequence(row.getEventSequence())
                .occurredAt(row.getOccurredAt()).correlationId(row.getCorrelationId()).causationId(row.getCausationId())
                .payload(row.getPayload()).headers(row.getHeaders()).payloadHash(row.getPayloadHash())
                .destination(row.getDestination()).build();
    }

    private static String summarize(Exception failure) {
        String message = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private static void requireTransition(int rows, String eventId) {
        if (rows != 1) {
            throw new IllegalStateException("outbox lease lost for event " + eventId);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

}
