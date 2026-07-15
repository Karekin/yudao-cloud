package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingUnpublishSagaWorkerTest {

    private final ListingUnpublishSagaMapper sagaMapper = mock(ListingUnpublishSagaMapper.class);
    private final ListingUnpublishSagaItemMapper itemMapper = mock(ListingUnpublishSagaItemMapper.class);
    private final ListingHeaderMapper listingMapper = mock(ListingHeaderMapper.class);
    private final ListingCommandApi listingCommandApi = mock(ListingCommandApi.class);
    private final ListingUnpublishSagaCheckpointService checkpointService =
            mock(ListingUnpublishSagaCheckpointService.class);
    private final ListingUnpublishSagaWorker worker = new ListingUnpublishSagaWorker(
            sagaMapper, itemMapper, listingMapper, listingCommandApi, checkpointService);

    @Test
    void replaysStableUnpublishAfterTimeoutAndCompletesFrozenSet() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 2, 0);
        ListingUnpublishSagaDO running = saga(0);
        ListingUnpublishSagaDO handled = saga(1);
        ListingUnpublishSagaItemDO item = item();
        when(sagaMapper.selectTenantSaga(7L, "saga-1")).thenReturn(running, running, handled);
        when(itemMapper.selectNext(7L, "saga-1")).thenReturn(item, item, null);
        when(listingMapper.selectTenantListing(7L, "listing-1"))
                .thenReturn(new ListingHeaderDO().setListingId("listing-1").setStatus("PUBLISHED").setVersion(4L));
        when(listingCommandApi.execute(any()))
                .thenThrow(new IllegalStateException("timeout after commit"))
                .thenReturn(ListingCommandResult.builder().operationId(91L).listingId("listing-1")
                        .currentStatus("UNPUBLISHED").aggregateVersion(5L).duplicate(true).build());

        assertThatThrownBy(() -> worker.process(7L, "saga-1", "worker-1", now))
                .isInstanceOf(IllegalStateException.class).hasMessage("timeout after commit");
        assertThatCode(() -> worker.process(7L, "saga-1", "worker-1", now.plusSeconds(1)))
                .doesNotThrowAnyException();

        ArgumentCaptor<ListingCommand> commands = ArgumentCaptor.forClass(ListingCommand.class);
        verify(listingCommandApi, times(2)).execute(commands.capture());
        assertThat(commands.getAllValues()).allSatisfy(command -> {
            assertThat(command.getOperation()).isEqualTo(ListingOperation.UNPUBLISH);
            assertThat(command.getIdempotencyKey()).isEqualTo("listing-unpublish:saga-1:listing-1");
            assertThat(command.getExpectedVersion()).isEqualTo(4L);
            assertThat(command.getCausationId()).isEqualTo("70000000-0000-4000-8000-000000000901");
            assertThat(command.getOccurredAt()).isEqualTo(Instant.parse("2026-07-15T02:00:00Z"));
        });
        verify(checkpointService).markFailure(eq(7L), eq("saga-1"), eq("item-1"), eq("worker-1"),
                any(IllegalStateException.class), any());
        verify(checkpointService).markItemUnpublished(eq(7L), eq("saga-1"), eq("item-1"), eq("worker-1"),
                argThat(result -> result.getOperationId() == 91L && Boolean.TRUE.equals(result.getDuplicate())), any());
        verify(checkpointService).markCompleted(eq(7L), eq("saga-1"), eq("worker-1"), any());
    }

    @Test
    void capsRetryBackoff() {
        assertThat(ListingUnpublishSagaCheckpointService.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(ListingUnpublishSagaCheckpointService.backoff(20)).isEqualTo(Duration.ofMinutes(30));
    }

    private static ListingUnpublishSagaDO saga(int unpublishedCount) {
        return new ListingUnpublishSagaDO().setSagaId("saga-1").setTenantId(7L)
                .setRunId("merchant-lifecycle-run").setSourceEventId("70000000-0000-4000-8000-000000000901")
                .setSourceEntityType("MERCHANT").setSourceAggregateVersion(3L).setMerchantId("merchant-1")
                .setStatus("UNPUBLISHING").setActiveStep("UNPUBLISH_LISTINGS")
                .setExpectedListingCount(1).setUnpublishedListingCount(unpublishedCount).setSkippedListingCount(0)
                .setAttemptCount(1).setMaxAttempts(8).setVersion(2L).setReason("compliance review")
                .setCorrelationId("70000000-0000-4000-8000-000000000902")
                .setOccurredAt(LocalDateTime.of(2026, 7, 15, 2, 0)).setLeaseOwner("worker-1");
    }

    private static ListingUnpublishSagaItemDO item() {
        return new ListingUnpublishSagaItemDO().setSagaItemId("item-1").setTenantId(7L).setSagaId("saga-1")
                .setListingId("listing-1").setListingVersionAtRequest(4L)
                .setUnpublishIdempotencyKey("listing-unpublish:saga-1:listing-1")
                .setStatus("PENDING").setAttemptCount(0);
    }
}
