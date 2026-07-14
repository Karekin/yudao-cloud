package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventOutboxDO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.CloudmoldEventOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxAppenderImplTest {

    private CloudmoldEventOutboxMapper mapper;
    private OutboxAppenderImpl appender;

    @BeforeEach
    void setUp() {
        mapper = mock(CloudmoldEventOutboxMapper.class);
        appender = new OutboxAppenderImpl(mapper);
    }

    @Test
    void shouldAppendPendingImmutableEvent() {
        when(mapper.insert(any(CloudmoldEventOutboxDO.class))).thenReturn(1);

        AppendDomainEventResult result = appender.append(command());

        ArgumentCaptor<CloudmoldEventOutboxDO> captor = ArgumentCaptor.forClass(CloudmoldEventOutboxDO.class);
        verify(mapper).insert(captor.capture());
        CloudmoldEventOutboxDO row = captor.getValue();
        assertEquals("inventory.stock.changed", row.getEventType());
        assertEquals(OutboxAppenderImpl.STATUS_PENDING, row.getStatus());
        assertEquals(64, row.getPayloadHash().length());
        assertFalse(result.isDuplicate());
        assertEquals(row.getEventId(), result.getEventId());
    }

    @Test
    void shouldReturnExistingEventForSameIdempotencyAndPayload() {
        AppendDomainEventCommand command = command();
        String hash = DomainEventCanonicalizer.canonicalize(command.getPayload()).sha256();
        CloudmoldEventOutboxDO existing = new CloudmoldEventOutboxDO()
                .setEventId("77777777-7777-4777-8777-777777777777")
                .setEventType(command.getEventType())
                .setAggregateType(command.getAggregateType())
                .setAggregateId(command.getAggregateId())
                .setPayloadHash(hash);
        when(mapper.insert(any(CloudmoldEventOutboxDO.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectByIdempotencyKey(command.getTenantId(), command.getEventType(), command.getIdempotencyKey()))
                .thenReturn(existing);

        AppendDomainEventResult result = appender.append(command);

        assertTrue(result.isDuplicate());
        assertEquals(existing.getEventId(), result.getEventId());
        assertEquals(hash, result.getPayloadHash());
    }

    @Test
    void shouldRejectInvalidEventTypeBeforeDatabaseWrite() {
        AppendDomainEventCommand invalid = AppendDomainEventCommand.builder()
                .eventType("InventoryChanged")
                .schemaVersion(1)
                .tenantId(1L)
                .aggregateType("inventory_balance")
                .aggregateId("sku-1:warehouse-1")
                .aggregateVersion(1L)
                .occurredAt(Instant.now())
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .idempotencyKey("invalid-event-test")
                .payload(Map.of())
                .destination("lakehouse")
                .build();

        assertThrows(IllegalArgumentException.class, () -> appender.append(invalid));
        verifyNoInteractions(mapper);
    }

    private static AppendDomainEventCommand command() {
        return AppendDomainEventCommand.builder()
                .eventType("inventory.stock.changed")
                .schemaVersion(1)
                .sourceSystem("cloudmold-inventory")
                .tenantId(1L)
                .aggregateType("inventory_balance")
                .aggregateId("sku-1:warehouse-1")
                .aggregateVersion(1L)
                .occurredAt(Instant.parse("2026-07-12T10:00:00Z"))
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .idempotencyKey("receipt-1-line-1")
                .payload(Map.of("deltaQuantity", "10.000000", "afterQuantity", "10.000000"))
                .destination("lakehouse")
                .build();
    }

}
