package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LakehouseCdcTransportPublisherTest {

    private final LakehouseCdcTransportPublisher publisher = new LakehouseCdcTransportPublisher();

    @Test
    void shouldReleaseCanonicalCdcDestinationsOnly() {
        assertThat(publisher.supports("lakehouse")).isTrue();
        assertThat(publisher.supports("catalog-events")).isTrue();
        assertThat(publisher.supports("unknown-events")).isFalse();
    }

    @Test
    void shouldRequireDurableIdentityBeforeAcknowledgement() {
        assertThatThrownBy(() -> publisher.publish(OutboxMessage.builder().eventId("event-1").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durable Outbox message identity and payload hash are required");
        assertThatCode(() -> publisher.publish(OutboxMessage.builder()
                .eventId("event-1").payloadHash("a".repeat(64)).build())).doesNotThrowAnyException();
    }
}
