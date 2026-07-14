package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventOutboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventOutboxMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayServiceTest {

    private final CloudmoldEventOutboxMapper mapper = mock(CloudmoldEventOutboxMapper.class);
    private final EventTransportPublisher publisher = mock(EventTransportPublisher.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 12, 12, 0);

    @Test
    void shouldClaimAndPublish() throws Exception {
        CloudmoldEventOutboxDO row = event(0, 20, "catalog-events");
        when(mapper.selectClaimableEventIds(now, 10)).thenReturn(List.of(row.getEventId()));
        when(mapper.claim(eq(row.getEventId()), eq("worker-1"), any(), eq(now))).thenReturn(1);
        when(mapper.selectById(row.getEventId())).thenReturn(row);
        when(publisher.supports("catalog-events")).thenReturn(true);
        when(mapper.markPublished(row.getEventId(), "worker-1", now)).thenReturn(1);

        OutboxRelayResult result = new OutboxRelayService(mapper, List.of(publisher))
                .relayBatch("worker-1", 10, now);

        assertThat(result).isEqualTo(new OutboxRelayResult(1, 1, 1, 0, 0, 0));
        verify(publisher).publish(argThat(message -> message.getEventId().equals(row.getEventId())
                && message.getPayloadHash().equals(row.getPayloadHash())));
    }

    @Test
    void shouldRetryWithExponentialBackoff() throws Exception {
        CloudmoldEventOutboxDO row = event(2, 20, "catalog-events");
        when(mapper.selectClaimableEventIds(now, 10)).thenReturn(List.of(row.getEventId()));
        when(mapper.claim(anyString(), anyString(), any(), any())).thenReturn(1);
        when(mapper.selectById(row.getEventId())).thenReturn(row);
        when(publisher.supports("catalog-events")).thenReturn(true);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(any());
        when(mapper.markPublishFailed(eq(row.getEventId()), eq("worker-1"), eq(0),
                eq(now.plusSeconds(4)), eq("IllegalStateException"), eq("broker unavailable"))).thenReturn(1);

        OutboxRelayResult result = new OutboxRelayService(mapper, List.of(publisher))
                .relayBatch("worker-1", 10, now);

        assertThat(result.retried()).isEqualTo(1);
        assertThat(result.dead()).isZero();
    }

    @Test
    void shouldDeadLetterAfterMaximumAttempts() throws Exception {
        CloudmoldEventOutboxDO row = event(19, 20, "catalog-events");
        when(mapper.selectClaimableEventIds(now, 10)).thenReturn(List.of(row.getEventId()));
        when(mapper.claim(anyString(), anyString(), any(), any())).thenReturn(1);
        when(mapper.selectById(row.getEventId())).thenReturn(row);
        when(publisher.supports("catalog-events")).thenReturn(true);
        doThrow(new IllegalStateException("still unavailable")).when(publisher).publish(any());
        when(mapper.markPublishFailed(eq(row.getEventId()), eq("worker-1"), eq(30), eq(now),
                eq("IllegalStateException"), eq("still unavailable"))).thenReturn(1);

        OutboxRelayResult result = new OutboxRelayService(mapper, List.of(publisher))
                .relayBatch("worker-1", 10, now);

        assertThat(result.dead()).isEqualTo(1);
        assertThat(result.retried()).isZero();
    }

    @Test
    void shouldReleaseUnsupportedDestinationWithoutConsumingAttempt() {
        CloudmoldEventOutboxDO row = event(3, 20, "unconfigured");
        when(mapper.selectClaimableEventIds(now, 10)).thenReturn(List.of(row.getEventId()));
        when(mapper.claim(anyString(), anyString(), any(), any())).thenReturn(1);
        when(mapper.selectById(row.getEventId())).thenReturn(row);
        when(mapper.releaseUnsupported(row.getEventId(), "worker-1", now.plusMinutes(1),
                "No publisher configured for destination unconfigured")).thenReturn(1);

        OutboxRelayResult result = new OutboxRelayService(mapper, List.of())
                .relayBatch("worker-1", 10, now);

        assertThat(result.unsupported()).isEqualTo(1);
        verify(mapper, never()).markPublishFailed(anyString(), anyString(), anyInt(), any(), anyString(), anyString());
    }

    @Test
    void shouldCapBackoff() {
        assertThat(OutboxRelayService.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxRelayService.backoff(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxRelayService.backoff(100)).isEqualTo(Duration.ofMinutes(30));
    }

    private static CloudmoldEventOutboxDO event(int attempts, int maxAttempts, String destination) {
        return new CloudmoldEventOutboxDO()
                .setEventId("d9ba92e6-f8de-45d7-baa1-f901a037a6f8")
                .setEventType("catalog.sku.upserted").setSchemaVersion(1)
                .setSourceSystem("cloudmold-catalog").setTenantId(1L)
                .setAggregateType("SKU").setAggregateId("sku-1").setAggregateVersion(1L).setEventSequence((short) 1)
                .setOccurredAt(LocalDateTime.of(2026, 7, 12, 11, 59))
                .setCorrelationId("dad77cdb-99f8-4203-845d-263921da55b3")
                .setPayload("{\"sku_id\":\"sku-1\"}").setPayloadHash("a".repeat(64))
                .setDestination(destination).setAttemptCount(attempts).setMaxAttempts(maxAttempts);
    }

}
