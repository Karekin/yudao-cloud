package cn.iocoder.yudao.module.cloudmold.listing.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
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
    private final MerchantReferenceValidationApi merchantReferenceApi = mock(MerchantReferenceValidationApi.class);
    private final MerchantOperatorAuthorizationApi merchantAuthorizationApi =
            mock(MerchantOperatorAuthorizationApi.class);
    private final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final ListingCommandServiceImpl service = new ListingCommandServiceImpl(operationMapper, headerMapper,
            offerMapper, historyMapper, reviewMapper, catalogApi, merchantReferenceApi, merchantAuthorizationApi,
            principalValidationApi, outboxAppender);

    private final AtomicReference<ListingHeaderDO> storedHeader = new AtomicReference<>();
    private final List<ListingOfferDO> storedOffers = new ArrayList<>();
    private final AtomicReference<String> currentAttempt = new AtomicReference<>();
    private final AtomicReference<String> currentCommandType = new AtomicReference<>();
    private final Map<String, ListingOperationDO> latestReceiptByListing = new HashMap<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationMapper.selectLastInsertId()).thenReturn(11L);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    currentCommandType.set(invocation.getArgument(2));
                    currentAttempt.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new ListingOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken(currentAttempt.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            String listingId = invocation.getArgument(2);
            String resultJson = invocation.getArgument(3);
            if (listingId != null && currentCommandType.get() != null
                    && currentCommandType.get().startsWith("CHANNEL_PUBLISH_")) {
                latestReceiptByListing.put(listingId, new ListingOperationDO()
                        .setListingId(listingId)
                        .setCommandType(currentCommandType.get())
                        .setStatus(10)
                        .setResultJson(resultJson));
            }
            return 1;
        });
        when(headerMapper.insert(any(ListingHeaderDO.class))).thenAnswer(invocation -> {
            storedHeader.set(invocation.getArgument(0)); return 1;
        });
        when(headerMapper.selectForUpdate(eq(1L), anyString())).thenAnswer(ignored -> storedHeader.get());
        when(headerMapper.selectTenantListing(eq(1L), anyString())).thenAnswer(invocation -> {
            ListingHeaderDO header = storedHeader.get();
            return header != null && Objects.equals(header.getListingId(), invocation.getArgument(1)) ? header : null;
        });
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
        when(operationMapper.selectLatestChannelPublishReceipt(eq(1L), anyString()))
                .thenAnswer(invocation -> latestReceiptByListing.get(invocation.getArgument(1)));
        when(catalogApi.getActiveSku(anyString())).thenAnswer(invocation -> {
            CatalogSkuProjectionView view = new CatalogSkuProjectionView();
            view.setCanonicalSpuId("spu-1"); view.setCanonicalSkuId(invocation.getArgument(0)); view.setCatalogStatus("ACTIVE");
            return view;
        });
        when(merchantReferenceApi.requireActiveReference(any())).thenReturn(new MerchantReferenceView()
                .setMerchantId("merchant-1").setMerchantStatus("ACTIVE").setShopId("shop-1")
                .setShopStatus("ACTIVE").setChannelCode("YSHOPPING_INTERNAL"));
        when(merchantAuthorizationApi.requireAuthorizedOperator(any())).thenReturn(
                new MerchantOperatorAuthorizationView().setAssignmentId("assignment-1")
                        .setMerchantId("merchant-1").setShopId("shop-1").setPrincipalId("internal-agent")
                        .setRoleCode("OWNER").setAssignmentStatus("ACTIVE"));
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
        verify(merchantReferenceApi, times(2)).requireActiveReference(argThat(reference ->
                "merchant-1".equals(reference.getMerchantId()) && "shop-1".equals(reference.getShopId())));
        verify(principalValidationApi, times(2)).requireActivePrincipal("internal-agent");
        verify(merchantAuthorizationApi, times(2)).requireAuthorizedOperator(argThat(authorization ->
                "merchant-1".equals(authorization.getMerchantId())
                        && "shop-1".equals(authorization.getShopId())
                        && "internal-agent".equals(authorization.getPrincipalId())
                        && "OWNER".equals(authorization.getRoleCode())));
    }

    @Test
    void shouldRejectDraftWhenMerchantShopReferenceIsInactiveOrUnrelated() {
        when(merchantReferenceApi.requireActiveReference(any())).thenThrow(
                new IllegalArgumentException("merchant/shop reference is not active or does not belong together"));

        assertThatThrownBy(() -> service.execute(createCommand("listing-inactive-merchant")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchant/shop reference is not active or does not belong together");

        verifyNoInteractions(principalValidationApi, merchantAuthorizationApi);
        verify(headerMapper, never()).insert(any(ListingHeaderDO.class));
        verifyNoInteractions(catalogApi);
    }

    @Test
    void shouldRejectDraftWhenShopChannelDoesNotMatchListingChannel() {
        when(merchantReferenceApi.requireActiveReference(any())).thenReturn(new MerchantReferenceView()
                .setMerchantId("merchant-1").setMerchantStatus("ACTIVE").setShopId("shop-1")
                .setShopStatus("ACTIVE").setChannelCode("YSHOPPING"));

        assertThatThrownBy(() -> service.execute(createCommand("listing-shop-channel-mismatch")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchant shop channel does not match listing channel");

        verifyNoInteractions(principalValidationApi, merchantAuthorizationApi);
        verify(headerMapper, never()).insert(any(ListingHeaderDO.class));
        verifyNoInteractions(catalogApi);
    }

    @Test
    void shouldRejectDraftWhenPublisherPrincipalIsInactive() {
        doThrow(new IllegalArgumentException("principal is not active"))
                .when(principalValidationApi).requireActivePrincipal("internal-agent");

        assertThatThrownBy(() -> service.execute(createCommand("listing-inactive-publisher")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("principal is not active");

        verifyNoInteractions(merchantAuthorizationApi);
        verify(headerMapper, never()).insert(any(ListingHeaderDO.class));
        verifyNoInteractions(catalogApi);
    }

    @Test
    void shouldRejectDraftWhenPublisherHasNoActiveMerchantAssignment() {
        when(merchantAuthorizationApi.requireAuthorizedOperator(any())).thenThrow(
                new IllegalArgumentException("principal is not authorized for the merchant shop role"));

        assertThatThrownBy(() -> service.execute(createCommand("listing-unauthorized-publisher")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("principal is not authorized for the merchant shop role");

        verify(headerMapper, never()).insert(any(ListingHeaderDO.class));
        verifyNoInteractions(catalogApi);
    }

    @Test
    void shouldRevalidateMerchantAndPublisherBeforePublish() {
        ListingCommandResult result = service.execute(createCommand("listing-revalidate-create"));
        result = service.execute(transition(ListingOperation.SUBMIT, result, "listing-revalidate-submit"));
        result = service.execute(transition(ListingOperation.PASS_COMPLETION, result, "listing-revalidate-completion"));
        result = service.execute(transition(ListingOperation.APPROVE_BUSINESS, result, "listing-revalidate-business"));
        result = service.execute(transition(ListingOperation.APPROVE_RISK, result, "listing-revalidate-risk"));
        when(merchantReferenceApi.requireActiveReference(any())).thenThrow(
                new IllegalArgumentException("merchant/shop reference is not active or does not belong together"));

        ListingCommand publish = transition(ListingOperation.PUBLISH, result, "listing-revalidate-publish");
        assertThatThrownBy(() -> service.execute(publish)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchant/shop reference is not active or does not belong together");

        verify(headerMapper, never()).transition(anyLong(), anyString(), anyLong(), anyString(), eq("PUBLISHED"),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any());
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
                        .merchantId(header.getMerchantId()).shopId(header.getShopId())
                        .canonicalSpuId(header.getCanonicalSpuId()).canonicalSkuId("sku-1")
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

    @Test
    void shouldClosePublishedOfferGateWhenMerchantOrShopBecomesInactive() {
        ListingHeaderDO header = publishedHeader();
        ListingOfferDO offer = listingOffer(header.getListingId(), 1, "sku-1", 9900L);
        when(headerMapper.selectPublishedOffer(1L, header.getListingId(), offer.getListingOfferId(), "sku-1"))
                .thenReturn(PublishedListingOfferView.builder().listingId(header.getListingId())
                        .listingNo(header.getListingNo()).listingOfferId(offer.getListingOfferId())
                        .merchantId(header.getMerchantId()).channelCode(header.getChannelCode())
                        .shopId(header.getShopId()).canonicalSpuId(header.getCanonicalSpuId()).canonicalSkuId("sku-1")
                        .listingRevision(1).listingVersion(header.getVersion()).priceMinor(9900L).currencyCode("CNY")
                        .build());
        when(merchantReferenceApi.requireActiveReference(any())).thenThrow(
                new IllegalArgumentException("merchant/shop reference is not active or does not belong together"));

        PublishedOfferValidationCommand validation = PublishedOfferValidationCommand.builder()
                .listingId(header.getListingId()).listingOfferId(offer.getListingOfferId()).canonicalSkuId("sku-1")
                .expectedPriceMinor(9900L).currencyCode("CNY").build();
        assertThatThrownBy(() -> service.requirePublishedOffer(validation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchant/shop reference is not active or does not belong together");

        verify(merchantReferenceApi).requireActiveReference(argThat(reference ->
                "merchant-1".equals(reference.getMerchantId()) && "shop-1".equals(reference.getShopId())));
    }

    @Test
    void shouldExposePublishedListingAsPendingChannelConfirmationWhenNoChannelFactExists() {
        ListingHeaderDO header = publishedHeader();
        storedHeader.set(header);
        when(headerMapper.selectTenantListing(1L, header.getListingId())).thenReturn(header);
        storedOffers.add(listingOffer(header.getListingId(), 1, "sku-1", 9900L));
        storedOffers.add(listingOffer(header.getListingId(), 1, "sku-2", 10900L).setListingOfferId("offer-2"));

        ListingTerminalReadbackView readback = service.getListingTerminalReadback(
                ListingTerminalReadbackCommand.builder().listingId(header.getListingId()).build());

        assertThat(readback.getListingNo()).isEqualTo("CML1");
        assertThat(readback.getCurrentStatus()).isEqualTo("PUBLISHED");
        assertThat(readback.getOfferCount()).isEqualTo(2);
        assertThat(readback.getEnabledOfferCount()).isEqualTo(2);
        assertThat(readback.getChannelFactPresent()).isFalse();
        assertThat(readback.getChannelPublicationStatus()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(readback.getOverallResultCode()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(readback.getEvidenceSource()).isEqualTo("CANONICAL_LISTING_ONLY");
        assertThat(readback.getSummary()).isEqualTo("规范刊登已发布，待渠道确认终态回读");
    }

    @Test
    void shouldConfirmPublishedListingOnlyAfterRealChannelReceiptArrives() {
        ListingHeaderDO header = publishedHeader();
        storedHeader.set(header);
        storedOffers.add(listingOffer(header.getListingId(), 1, "sku-1", 9900L));

        ListingChannelPublishReceiptResult receipt = service.recordChannelPublishReceipt(
                receiptCommand(header.getListingId(), header.getVersion()));
        ListingTerminalReadbackView readback = service.getListingTerminalReadback(
                ListingTerminalReadbackCommand.builder().listingId(header.getListingId()).build());

        assertThat(receipt.getOutcome()).isEqualTo(ListingChannelPublishReceiptOutcome.CONFIRMED_PUBLISHED);
        assertThat(readback.getChannelFactPresent()).isTrue();
        assertThat(readback.getOverallResultCode()).isEqualTo("CONFIRMED_PUBLISHED");
        assertThat(readback.getChannelPublicationStatus()).isEqualTo("CONFIRMED_PUBLISHED");
        assertThat(readback.getChannelListingId()).isEqualTo("channel-listing-1");
        assertThat(readback.getConfirmedAt()).isEqualTo(Instant.parse("2026-07-27T10:01:00Z"));
        assertThat(readback.getEvidenceRef()).isEqualTo("channel-evidence-1");
        assertThat(readback.getEvidenceSource()).isEqualTo("REAL_CHANNEL_RECEIPT");
    }

    @Test
    void shouldExposeChannelPublishFailureAsTerminalFailure() {
        ListingHeaderDO header = publishedHeader();
        storedHeader.set(header);
        storedOffers.add(listingOffer(header.getListingId(), 1, "sku-1", 9900L));

        ListingChannelPublishReceiptResult receipt = service.recordChannelPublishReceipt(
                failedReceiptCommand(header.getListingId(), header.getVersion()));
        ListingTerminalReadbackView readback = service.getListingTerminalReadback(
                ListingTerminalReadbackCommand.builder().listingId(header.getListingId()).build());

        assertThat(receipt.getOutcome()).isEqualTo(ListingChannelPublishReceiptOutcome.CHANNEL_PUBLISH_FAILED);
        assertThat(readback.getChannelFactPresent()).isTrue();
        assertThat(readback.getOverallResultCode()).isEqualTo("CHANNEL_PUBLISH_FAILED");
        assertThat(readback.getFailureCode()).isEqualTo("CHANNEL_TIMEOUT");
        assertThat(readback.getFailureMessage()).isEqualTo("channel publish callback timed out");
        assertThat(readback.getRetryable()).isTrue();
        assertThat(readback.getEvidenceRef()).isEqualTo("channel-failure-evidence-1");
    }

    @Test
    void shouldReplayDuplicateChannelReceiptAndKeepFactsIsolatedByListing() {
        ListingHeaderDO headerOne = publishedHeader();
        ListingHeaderDO headerTwo = publishedHeader().setListingId("listing-2").setListingNo("CML2");
        storedHeader.set(headerOne);
        storedOffers.add(listingOffer(headerOne.getListingId(), 1, "sku-1", 9900L));
        latestReceiptByListing.put(headerTwo.getListingId(), new ListingOperationDO()
                .setListingId(headerTwo.getListingId())
                .setCommandType("CHANNEL_PUBLISH_CONFIRMED")
                .setStatus(10)
                .setResultJson(JsonUtils.toJsonString(ListingChannelPublishReceiptResult.builder()
                        .operationId(22L).listingId(headerTwo.getListingId()).listingNo(headerTwo.getListingNo())
                        .aggregateVersion(headerTwo.getVersion())
                        .outcome(ListingChannelPublishReceiptOutcome.CONFIRMED_PUBLISHED)
                        .channelListingId("channel-listing-2").channelStatus("ONLINE")
                        .confirmedAt(Instant.parse("2026-07-27T10:02:00Z")).evidenceRef("evidence-2")
                        .duplicate(false).build())));

        ListingChannelPublishReceiptCommand command = receiptCommand(headerOne.getListingId(), headerOne.getVersion());
        ListingChannelPublishReceiptResult first = service.recordChannelPublishReceipt(command);
        String requestHash = captureReceiptRequestHash(command, first);

        when(operationMapper.selectForUpdate(11L, 1L)).thenReturn(new ListingOperationDO().setOperationId(11L)
                .setTenantId(1L).setAttemptToken("existing").setRequestHash(requestHash).setStatus(10)
                .setResultJson(JsonUtils.toJsonString(first)));

        ListingChannelPublishReceiptResult replay = service.recordChannelPublishReceipt(command);
        ListingTerminalReadbackView one = service.getListingTerminalReadback(
                ListingTerminalReadbackCommand.builder().listingId(headerOne.getListingId()).build());
        storedHeader.set(headerTwo);
        storedOffers.clear();
        storedOffers.add(listingOffer(headerTwo.getListingId(), 1, "sku-2", 10900L));
        ListingTerminalReadbackView two = service.getListingTerminalReadback(
                ListingTerminalReadbackCommand.builder().listingId(headerTwo.getListingId()).build());

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(one.getChannelListingId()).isEqualTo("channel-listing-1");
        assertThat(two.getChannelListingId()).isEqualTo("channel-listing-2");
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

    private String captureReceiptRequestHash(ListingChannelPublishReceiptCommand command,
                                             ListingChannelPublishReceiptResult result) {
        AtomicReference<String> hash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    currentCommandType.set(invocation.getArgument(2));
                    hash.set(invocation.getArgument(3));
                    return 0;
                });
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new ListingOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken("conflict").setRequestHash(hash.get())
                .setStatus(10).setResultJson(JsonUtils.toJsonString(result)));
        service.recordChannelPublishReceipt(command);
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

    private static ListingChannelPublishReceiptCommand receiptCommand(String listingId, long version) {
        return ListingChannelPublishReceiptCommand.builder()
                .idempotencyKey("channel-receipt-success-1")
                .listingId(listingId)
                .expectedVersion(version)
                .outcome(ListingChannelPublishReceiptOutcome.CONFIRMED_PUBLISHED)
                .channelListingId("channel-listing-1")
                .channelStatus("ONLINE")
                .confirmedAt(Instant.parse("2026-07-27T10:01:00Z"))
                .evidenceRef("channel-evidence-1")
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-27T10:01:01Z"))
                .build();
    }

    private static ListingChannelPublishReceiptCommand failedReceiptCommand(String listingId, long version) {
        return ListingChannelPublishReceiptCommand.builder()
                .idempotencyKey("channel-receipt-failure-1")
                .listingId(listingId)
                .expectedVersion(version)
                .outcome(ListingChannelPublishReceiptOutcome.CHANNEL_PUBLISH_FAILED)
                .failureCode("CHANNEL_TIMEOUT")
                .failureMessage("channel publish callback timed out")
                .retryable(true)
                .evidenceRef("channel-failure-evidence-1")
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964fe")
                .occurredAt(Instant.parse("2026-07-27T10:05:01Z"))
                .build();
    }
}
