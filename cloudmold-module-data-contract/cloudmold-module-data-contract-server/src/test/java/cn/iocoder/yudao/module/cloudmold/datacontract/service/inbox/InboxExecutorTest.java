package cn.iocoder.yudao.module.cloudmold.datacontract.service.inbox;

import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventInboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventInboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InboxExecutorTest {

    private final CloudmoldEventInboxMapper mapper = mock(CloudmoldEventInboxMapper.class);
    private final InboxExecutor executor = new InboxExecutor(mapper);

    @Test
    void shouldExecuteSideEffectOnceAndCompleteClaim() {
        InboxEvent event = event();
        when(mapper.insertClaim(any(CloudmoldEventInboxDO.class))).thenReturn(1);
        when(mapper.complete(eq(event.getConsumerId()), eq(event.getEventId()), eq("result-42"), any())).thenReturn(1);
        AtomicInteger calls = new AtomicInteger();

        InboxExecutionResult<Integer> result = executor.execute(event, () -> {
            calls.incrementAndGet();
            return 42;
        }, value -> "result-" + value);

        assertThat(result).isEqualTo(new InboxExecutionResult<>(false, 42, "result-42"));
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldSkipAlreadyProcessedEvent() {
        InboxEvent event = event();
        when(mapper.selectOne(event.getConsumerId(), event.getEventId())).thenReturn(existing(event));
        AtomicInteger calls = new AtomicInteger();

        InboxExecutionResult<Integer> result = executor.execute(event, calls::incrementAndGet, Object::toString);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.resultHash()).isEqualTo("prior-result");
        assertThat(calls).hasValue(0);
        verify(mapper, never()).insertClaim(any());
    }

    @Test
    void shouldResolveConcurrentDuplicateWithoutSideEffect() {
        InboxEvent event = event();
        when(mapper.insertClaim(any())).thenThrow(new DuplicateKeyException("concurrent"));
        when(mapper.selectOne(event.getConsumerId(), event.getEventId())).thenReturn(null, existing(event));
        AtomicInteger calls = new AtomicInteger();

        InboxExecutionResult<Integer> result = executor.execute(event, calls::incrementAndGet, Object::toString);

        assertThat(result.duplicate()).isTrue();
        assertThat(calls).hasValue(0);
    }

    @Test
    void shouldRejectSameIdentityWithDifferentPayload() {
        InboxEvent event = event();
        CloudmoldEventInboxDO existing = existing(event).setPayloadHash("b".repeat(64));
        when(mapper.selectOne(event.getConsumerId(), event.getEventId())).thenReturn(existing);

        assertThatThrownBy(() -> executor.execute(event, () -> 1, Object::toString))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different event content");
    }

    @Test
    void shouldPropagateSideEffectFailureSoTransactionRollsBack() {
        InboxEvent event = event();
        when(mapper.insertClaim(any())).thenReturn(1);

        assertThatThrownBy(() -> executor.execute(event, () -> {
            throw new IllegalStateException("projection failed");
        }, Object::toString)).hasMessage("projection failed");

        verify(mapper, never()).complete(anyString(), anyString(), any(), any());
    }

    private static InboxEvent event() {
        return InboxEvent.builder()
                .consumerId("inventory-projection-v1")
                .eventId("d9ba92e6-f8de-45d7-baa1-f901a037a6f8")
                .tenantId(1L).eventType("catalog.sku.upserted").schemaVersion(1)
                .payloadHash("a".repeat(64)).build();
    }

    private static CloudmoldEventInboxDO existing(InboxEvent event) {
        return new CloudmoldEventInboxDO()
                .setConsumerId(event.getConsumerId()).setEventId(event.getEventId())
                .setTenantId(event.getTenantId()).setEventType(event.getEventType())
                .setSchemaVersion(event.getSchemaVersion()).setPayloadHash(event.getPayloadHash())
                .setResultHash("prior-result");
    }

}
