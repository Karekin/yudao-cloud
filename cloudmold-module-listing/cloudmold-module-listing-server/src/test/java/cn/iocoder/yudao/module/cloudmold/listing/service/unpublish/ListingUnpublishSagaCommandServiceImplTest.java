package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish.*;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingUnpublishSagaCommandServiceImplTest {

    private final ListingUnpublishSagaOperationMapper operationMapper = mock(ListingUnpublishSagaOperationMapper.class);
    private final ListingUnpublishSagaMapper sagaMapper = mock(ListingUnpublishSagaMapper.class);
    private final ListingUnpublishSagaItemMapper itemMapper = mock(ListingUnpublishSagaItemMapper.class);
    private final ListingHeaderMapper listingMapper = mock(ListingHeaderMapper.class);
    private final ListingUnpublishSagaCheckpointService checkpointService =
            mock(ListingUnpublishSagaCheckpointService.class);
    private final ListingUnpublishSagaCommandServiceImpl service = new ListingUnpublishSagaCommandServiceImpl(
            operationMapper, sagaMapper, itemMapper, listingMapper, checkpointService);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void freezesPublishedListingsAndCreatesStableChildOperations() {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        AtomicReference<ListingUnpublishSagaDO> storedSaga = new AtomicReference<>();
        List<ListingUnpublishSagaItemDO> storedItems = new ArrayList<>();
        when(operationMapper.insertOrResolve(eq(7L), anyString(), eq("START"), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(1L);
        when(operationMapper.selectForUpdate(1L, 7L)).thenAnswer(ignored ->
                new ListingUnpublishSagaOperationDO().setOperationId(1L).setAttemptToken(attemptToken.get())
                        .setStatus(0));
        when(operationMapper.markSucceeded(eq(1L), eq(7L), anyString(), anyString(), any())).thenReturn(1);
        when(sagaMapper.selectBySourceEvent(7L, "70000000-0000-4000-8000-000000000901")).thenReturn(null);
        when(listingMapper.selectPublishedForEligibilityEnforcement(7L, "merchant-1", "shop-1"))
                .thenReturn(List.of(
                        listing("listing-b", 5L),
                        listing("listing-a", 3L)));
        when(sagaMapper.insert(any(ListingUnpublishSagaDO.class))).thenAnswer(invocation -> {
            storedSaga.set(invocation.getArgument(0));
            return 1;
        });
        when(itemMapper.insert(any(ListingUnpublishSagaItemDO.class))).thenAnswer(invocation -> {
            storedItems.add(invocation.getArgument(0));
            return 1;
        });
        when(itemMapper.selectBySaga(eq(7L), anyString())).thenAnswer(ignored -> storedItems);

        ListingUnpublishSagaView result = service.execute(ListingUnpublishSagaCommand.builder()
                .operation(ListingUnpublishSagaOperation.START)
                .idempotencyKey("merchant-shop-pause-event-901")
                .runId("merchant-lifecycle-run")
                .sourceEventId("70000000-0000-4000-8000-000000000901")
                .sourceEntityType("SHOP")
                .sourceAggregateVersion(3L)
                .merchantId("merchant-1")
                .shopId("shop-1")
                .reason("inventory reconciliation")
                .correlationId("70000000-0000-4000-8000-000000000902")
                .occurredAt(Instant.parse("2026-07-15T02:00:00Z"))
                .build());

        assertThat(result.getStatus()).isEqualTo("REQUESTED");
        assertThat(result.getExpectedListingCount()).isEqualTo(2);
        assertThat(storedSaga.get()).satisfies(saga -> {
            assertThat(saga.getSourceEventId()).isEqualTo("70000000-0000-4000-8000-000000000901");
            assertThat(saga.getCausationId()).isEqualTo(saga.getSourceEventId());
            assertThat(saga.getExpectedListingCount()).isEqualTo(2);
        });
        assertThat(storedItems).extracting(ListingUnpublishSagaItemDO::getListingId)
                .containsExactly("listing-b", "listing-a");
        assertThat(storedItems).allSatisfy(item -> {
            assertThat(item.getStatus()).isEqualTo("PENDING");
            assertThat(item.getUnpublishIdempotencyKey())
                    .isEqualTo("listing-unpublish:" + result.getSagaId() + ":" + item.getListingId());
        });
        assertThat(storedItems).extracting(ListingUnpublishSagaItemDO::getListingVersionAtRequest)
                .containsExactly(5L, 3L);
        verify(checkpointService).appendInitial(same(storedSaga.get()), any());
    }

    private static ListingHeaderDO listing(String listingId, long version) {
        return new ListingHeaderDO().setListingId(listingId).setMerchantId("merchant-1")
                .setShopId("shop-1").setStatus("PUBLISHED").setVersion(version);
    }
}
