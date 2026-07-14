package cn.iocoder.yudao.module.cloudmold.datacontract;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventOutboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventInboxMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventOutboxMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.inbox.InboxEvent;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.inbox.InboxExecutor;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox.OutboxAppenderImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({OutboxAppenderImpl.class, InboxExecutor.class})
class DataContractDatabaseIntegrationTest extends BaseDbUnitTest {

    @Resource
    private OutboxAppenderImpl outboxAppender;
    @Resource
    private InboxExecutor inboxExecutor;
    @Resource
    private CloudmoldEventOutboxMapper outboxMapper;
    @Resource
    private CloudmoldEventInboxMapper inboxMapper;

    @Test
    void shouldPersistAndDeduplicateCanonicalEvent() {
        AppendDomainEventCommand command = command();

        AppendDomainEventResult first = outboxAppender.append(command);
        AppendDomainEventResult duplicate = outboxAppender.append(command);

        assertThat(first.isDuplicate()).isFalse();
        assertThat(duplicate.isDuplicate()).isTrue();
        assertThat(duplicate.getEventId()).isEqualTo(first.getEventId());
        CloudmoldEventOutboxDO stored = outboxMapper.selectById(first.getEventId());
        assertThat(stored.getPayload()).isEqualTo("{\"a\":1,\"z\":2}");
        assertThat(stored.getPayloadHash()).isEqualTo(first.getPayloadHash());
    }

    @Test
    void shouldExecuteLeaseRetryAndPublishTransitions() {
        AppendDomainEventResult appended = outboxAppender.append(command());
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);

        assertThat(outboxMapper.selectClaimableEventIds(now, 10)).contains(appended.getEventId());
        assertThat(outboxMapper.claim(appended.getEventId(), "integration-worker", now.plusSeconds(30), now)).isOne();
        assertThat(outboxMapper.claim(appended.getEventId(), "competing-worker", now.plusSeconds(30), now)).isZero();
        assertThat(outboxMapper.markPublishFailed(appended.getEventId(), "integration-worker", 0,
                now.plusSeconds(2), "BROKER_DOWN", "probe")).isOne();

        CloudmoldEventOutboxDO retried = outboxMapper.selectById(appended.getEventId());
        assertThat(retried.getAttemptCount()).isOne();
        assertThat(retried.getStatus()).isZero();

        LocalDateTime retryAt = now.plusSeconds(3);
        assertThat(outboxMapper.claim(appended.getEventId(), "integration-worker", retryAt.plusSeconds(30), retryAt)).isOne();
        assertThat(outboxMapper.markPublished(appended.getEventId(), "integration-worker", retryAt)).isOne();
        assertThat(outboxMapper.selectById(appended.getEventId()).getStatus()).isEqualTo(20);
    }

    @Test
    void shouldRollbackInboxClaimWhenSideEffectFails() {
        InboxEvent event = InboxEvent.builder()
                .consumerId("inventory-projection-v1").eventId(UUID.randomUUID().toString())
                .tenantId(1L).eventType("catalog.sku.upserted").schemaVersion(1)
                .payloadHash("a".repeat(64)).build();

        assertThatThrownBy(() -> inboxExecutor.execute(event, () -> {
            throw new IllegalStateException("projection failed");
        }, Object::toString)).hasMessage("projection failed");

        assertThat(inboxMapper.selectOne(event.getConsumerId(), event.getEventId())).isNull();
    }

    private static AppendDomainEventCommand command() {
        return AppendDomainEventCommand.builder()
                .eventType("catalog.sku.upserted").schemaVersion(1)
                .sourceSystem("cloudmold-catalog").tenantId(1L)
                .aggregateType("SKU").aggregateId("canonical-sku-1").aggregateVersion(1L)
                .occurredAt(Instant.parse("2026-07-12T10:00:00Z"))
                .correlationId("dad77cdb-99f8-4203-845d-263921da55b3")
                .idempotencyKey("catalog-sku-1-version-1")
                .payload(Map.of("z", 2, "a", 1)).destination("catalog-events")
                .build();
    }

}
