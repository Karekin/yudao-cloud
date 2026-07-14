package cn.iocoder.yudao.module.cloudmold.listing.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.*;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingCommandServiceImplTest {

    private final ListingOperationMapper operationMapper = mock(ListingOperationMapper.class);
    private final ListingHeaderMapper headerMapper = mock(ListingHeaderMapper.class);
    private final ListingOfferMapper offerMapper = mock(ListingOfferMapper.class);
    private final ListingStatusHistoryMapper historyMapper = mock(ListingStatusHistoryMapper.class);
    private final ListingReviewDecisionMapper reviewMapper = mock(ListingReviewDecisionMapper.class);
    private final CatalogSkuProjectionApi catalogApi = mock(CatalogSkuProjectionApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final ListingCommandServiceImpl service = new ListingCommandServiceImpl(operationMapper, headerMapper,
            offerMapper, historyMapper, reviewMapper, catalogApi, outboxAppender);

    private final AtomicReference<ListingHeaderDO> storedHeader = new AtomicReference<>();
    private final List<ListingOfferDO> storedOffers = new ArrayList<>();
    private final AtomicReference<String> currentAttempt = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationMapper.selectLastInsertId()).thenReturn(11L);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { currentAttempt.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new ListingOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken(currentAttempt.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(headerMapper.insert(any(ListingHeaderDO.class))).thenAnswer(invocation -> {
            storedHeader.set(invocation.getArgument(0)); return 1;
        });
        when(headerMapper.selectForUpdate(eq(1L), anyString())).thenAnswer(ignored -> storedHeader.get());
        when(headerMapper.transition(anyLong(), anyString(), anyLong(), anyString(), anyString(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), nullable(String.class), any())).thenReturn(1);
        when(offerMapper.insert(any(ListingOfferDO.class))).thenAnswer(invocation -> {
            storedOffers.add(invocation.getArgument(0)); return 1;
        });
        when(offerMapper.selectByListingRevision(eq(1L), anyString(), anyInt())).thenAnswer(invocation -> {
            int revision = invocation.getArgument(2);
            return storedOffers.stream().filter(offer -> offer.getRevision() == revision).toList();
        });
        when(historyMapper.insert(any(ListingStatusHistoryDO.class))).thenReturn(1);
        when(reviewMapper.insert(any(ListingReviewDecisionDO.class))).thenReturn(1);
        when(catalogApi.getActiveSku(anyString())).thenAnswer(invocation -> {
            CatalogSkuProjectionView view = new CatalogSkuProjectionView();
            view.setCanonicalSpuId("spu-1"); view.setCanonicalSkuId(invocation.getArgument(0)); view.setCatalogStatus("ACTIVE");
            return view;
        });
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPublishOnlyAfterSequentialThreeStageApproval() {
        ListingCommandResult result = service.execute(createCommand("listing-happy-create"));
        assertThat(result.getCurrentStatus()).isEqualTo("DRAFT");
        assertThat(result.getOffers()).hasSize(2);

        result = service.execute(transition(ListingOperation.SUBMIT, result, "listing-happy-submit"));
        result = service.execute(transition(ListingOperation.PASS_COMPLETION, result, "listing-happy-completion"));
        result = service.execute(transition(ListingOperation.APPROVE_BUSINESS, result, "listing-happy-business"));
        result = service.execute(transition(ListingOperation.APPROVE_RISK, result, "listing-happy-risk"));
        ListingCommand publish = transition(ListingOperation.PUBLISH, result, "listing-happy-publish");
        publish.setPublisherRef("internal-agent");
        result = service.execute(publish);

        assertThat(result.getCurrentStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getAggregateVersion()).isEqualTo(6L);
        assertThat(result.getCompletionPassed()).isTrue();
        assertThat(result.getBusinessApproved()).isTrue();
        assertThat(result.getRiskApproved()).isTrue();
        verify(catalogApi).getActiveSku("sku-1");
        verify(catalogApi).getActiveSku("sku-2");
        verify(historyMapper, times(6)).insert(any(ListingStatusHistoryDO.class));
        verify(reviewMapper, times(3)).insert(any(ListingReviewDecisionDO.class));
        verify(outboxAppender, times(9)).append(any());
    }

    @Test
    void shouldReplayImmutableFirstResultBeforeRevalidatingCatalog() {
        ListingCommand command = createCommand("listing-replay-create");
        String requestHash = captureRequestHash(command);
        ListingCommandResult first = ListingCommandResult.builder().operationId(11L).listingId("listing-1")
                .listingNo("CML1").currentStatus("DRAFT").revision(1).aggregateVersion(1L).duplicate(false).build();
        when(operationMapper.selectForUpdate(11L, 1L)).thenReturn(new ListingOperationDO().setOperationId(11L)
                .setTenantId(1L).setAttemptToken("existing").setRequestHash(requestHash).setStatus(10)
                .setResultJson(JsonUtils.toJsonString(first)));
        reset(catalogApi);

        ListingCommandResult replay = service.execute(command);

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getListingId()).isEqualTo("listing-1");
        verifyNoInteractions(catalogApi);
        verify(headerMapper, never()).insert(any(ListingHeaderDO.class));
    }

    @Test
    void shouldRejectSkippedPublishState() {
        service.execute(createCommand("listing-skip-create"));
        ListingCommand command = transition(ListingOperation.PUBLISH,
                ListingCommandResult.builder().listingId(storedHeader.get().getListingId()).aggregateVersion(1L).build(),
                "listing-skip-publish");
        command.setPublisherRef("internal-agent");

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PUBLISH requires RISK_APPROVED, UNPUBLISHED, or SUSPENDED");
        verify(headerMapper, never()).transition(anyLong(), anyString(), anyLong(), anyString(), eq("PUBLISHED"),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void shouldRecordRejectionThenCreateImmutableRevisionForResubmit() {
        ListingCommandResult result = service.execute(createCommand("listing-reject-create"));
        result = service.execute(transition(ListingOperation.SUBMIT, result, "listing-reject-submit"));
        ListingCommand reject = transition(ListingOperation.REJECT_COMPLETION, result, "listing-reject-decision");
        reject.setReason("missing required material composition");
        result = service.execute(reject);
        assertThat(result.getCurrentStatus()).isEqualTo("REJECTED");

        ListingCommand revise = transition(ListingOperation.REVISE, result, "listing-reject-revise");
        revise.setOffers(List.of(offer("sku-1", 10900L), offer("sku-2", 11900L)));
        result = service.execute(revise);
        assertThat(result.getCurrentStatus()).isEqualTo("DRAFT");
        assertThat(result.getRevision()).isEqualTo(2);
        assertThat(storedOffers).extracting(ListingOfferDO::getRevision).contains(1, 2);

        result = service.execute(transition(ListingOperation.SUBMIT, result, "listing-reject-resubmit"));
        assertThat(result.getCurrentStatus()).isEqualTo("SUBMITTED");
        assertThat(result.getRevision()).isEqualTo(2);
        verify(reviewMapper).insert(argThat((ListingReviewDecisionDO decision) -> decision.getStage().equals("COMPLETION")
                && decision.getDecision().equals("REJECTED") && decision.getReason() != null));
    }

    @Test
    void shouldCloseOrderOfferGateImmediatelyAfterUnpublish() {
        ListingHeaderDO header = publishedHeader();
        storedHeader.set(header);
        ListingOfferDO offer = listingOffer(header.getListingId(), 1, "sku-1", 9900L);
        storedOffers.add(offer);
        when(headerMapper.selectPublishedOffer(1L, header.getListingId(), offer.getListingOfferId(), "sku-1"))
                .thenAnswer(ignored -> "PUBLISHED".equals(header.getStatus()) ? PublishedListingOfferView.builder()
                        .listingId(header.getListingId()).listingNo(header.getListingNo())
                        .listingOfferId(offer.getListingOfferId()).channelCode(header.getChannelCode())
                        .shopId(header.getShopId()).canonicalSpuId(header.getCanonicalSpuId()).canonicalSkuId("sku-1")
                        .listingRevision(1).listingVersion(header.getVersion()).priceMinor(9900L).currencyCode("CNY")
                        .build() : null);
        PublishedOfferValidationCommand validation = PublishedOfferValidationCommand.builder()
                .listingId(header.getListingId()).listingOfferId(offer.getListingOfferId()).canonicalSkuId("sku-1")
                .expectedPriceMinor(9900L).currencyCode("CNY").build();
        assertThat(service.requirePublishedOffer(validation).getPriceMinor()).isEqualTo(9900L);

        ListingCommandResult current = ListingCommandResult.builder().listingId(header.getListingId())
                .aggregateVersion(header.getVersion()).build();
        ListingCommandResult unpublished = service.execute(
                transition(ListingOperation.UNPUBLISH, current, "listing-unpublish-gate"));

        assertThat(unpublished.getCurrentStatus()).isEqualTo("UNPUBLISHED");
        assertThatThrownBy(() -> service.requirePublishedOffer(validation)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listing offer is not currently published");
    }

    private String captureRequestHash(ListingCommand command) {
        AtomicReference<String> hash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { hash.set(invocation.getArgument(3)); return 0; });
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new ListingOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken("conflict").setRequestHash(hash.get())
                .setStatus(10).setResultJson("{}"));
        service.execute(command);
        return hash.get();
    }

    private static ListingCommand createCommand(String key) {
        return ListingCommand.builder().operation(ListingOperation.CREATE_DRAFT).idempotencyKey(key)
                .runId("listing-run-1").merchantId("merchant-1").channelCode("YSHOPPING_INTERNAL")
                .shopId("shop-1").canonicalSpuId("spu-1").title("Apparel listing")
                .categoryRef("YSHOPPING:CATEGORY:1").brandRef("YSHOPPING:BRAND:1")
                .sourceSystem("YSHOPPING").publisherRef("internal-agent")
                .offers(List.of(offer("sku-1", 9900L), offer("sku-2", 10900L)))
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-12T00:00:00Z")).build();
    }

    private static ListingOfferCommand offer(String skuId, long priceMinor) {
        return ListingOfferCommand.builder().canonicalSkuId(skuId).priceMinor(priceMinor)
                .currencyCode("CNY").enabled(true).build();
    }

    private static ListingCommand transition(ListingOperation operation, ListingCommandResult current, String key) {
        return ListingCommand.builder().operation(operation).idempotencyKey(key).runId("listing-run-1")
                .listingId(current.getListingId()).expectedVersion(current.getAggregateVersion())
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-12T00:00:01Z")).build();
    }

    private static ListingHeaderDO publishedHeader() {
        return new ListingHeaderDO().setListingId("listing-1").setTenantId(1L).setListingNo("CML1")
                .setRunId("listing-run-1").setMerchantId("merchant-1").setChannelCode("YSHOPPING_INTERNAL")
                .setShopId("shop-1").setCanonicalSpuId("spu-1").setRevision(1).setTitle("Listing")
                .setCategoryRef("category-1").setBrandRef("brand-1").setSourceSystem("YSHOPPING")
                .setPublisherRef("internal-agent").setCurrencyCode("CNY").setStatus("PUBLISHED")
                .setCompletionPassed(true).setBusinessApproved(true).setRiskApproved(true).setVersion(6L);
    }

    private static ListingOfferDO listingOffer(String listingId, int revision, String skuId, long priceMinor) {
        return new ListingOfferDO().setListingOfferId("offer-1").setTenantId(1L).setListingId(listingId)
                .setRevision(revision).setCanonicalSkuId(skuId).setPriceMinor(priceMinor).setCurrencyCode("CNY")
                .setEnabled(true);
    }
}
