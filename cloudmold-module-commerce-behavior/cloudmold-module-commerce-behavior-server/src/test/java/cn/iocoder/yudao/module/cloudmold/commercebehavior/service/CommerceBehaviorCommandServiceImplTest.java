package cn.iocoder.yudao.module.cloudmold.commercebehavior.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.*;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingOfferView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAttributionView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderQueryApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommerceBehaviorCommandServiceImplTest {

    private static final String RUN_ID = "1706c359-8ce5-4b7e-b111-43ad6efdc1f7";
    private static final String SESSION_ID = "264af31b-7b3d-4daa-8bd7-047d5254e059";
    private static final String PRINCIPAL_ID = "1633b87f-28d8-4c55-85f0-f521fef508af";
    private static final String OTHER_PRINCIPAL_ID = "8402d028-7d2a-4bdc-915e-daaa84ed8528";
    private static final String BEHAVIOR_ID = "1fea36a1-9205-4b84-9c0a-1b0e12f9fbf7";
    private static final String SPU_ID = "6b6401a0-7f32-48f2-b179-a5c1be76e660";
    private static final String CORRELATION_ID = "6f9619ff-8b86-d011-b42d-00cf4fc964ff";

    private final CommerceBehaviorOperationMapper operationMapper = mock(CommerceBehaviorOperationMapper.class);
    private final CommerceSessionMapper sessionMapper = mock(CommerceSessionMapper.class);
    private final CommerceSessionIdentityLinkMapper identityLinkMapper = mock(CommerceSessionIdentityLinkMapper.class);
    private final CommerceBehaviorEventMapper behaviorEventMapper = mock(CommerceBehaviorEventMapper.class);
    private final CommerceSessionPaymentAttributionMapper paymentAttributionMapper = mock(CommerceSessionPaymentAttributionMapper.class);
    private final AppRecommendationReadMapper appRecommendationReadMapper = mock(AppRecommendationReadMapper.class);
    private final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
    private final ListingQueryApi listingQueryApi = mock(ListingQueryApi.class);
    private final OrderQueryApi orderQueryApi = mock(OrderQueryApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final CommerceBehaviorCommandServiceImpl service = new CommerceBehaviorCommandServiceImpl(
            operationMapper, sessionMapper, identityLinkMapper, behaviorEventMapper, paymentAttributionMapper,
            appRecommendationReadMapper, principalValidationApi, listingQueryApi, orderQueryApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(91L);
        when(operationMapper.selectForUpdate(91L, 7L)).thenAnswer(ignored -> new CommerceBehaviorOperationDO()
                .setOperationId(91L).setTenantId(7L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldStartAnonymousSessionAndPublishTenantSafeStatusEvent() {
        claimNewOperation();
        when(sessionMapper.insert(any(CommerceSessionDO.class))).thenReturn(1);

        CommerceBehaviorCommandResult result = service.startSession(StartCommerceSessionCommand.builder()
                .idempotencyKey("session-start-0001")
                .runId(RUN_ID)
                .sessionId(SESSION_ID)
                .channelCode("WEB")
                .entrypointCode("HOME")
                .sourceSystem("STORE_FRONT")
                .sourceType("SESSION_BOOTSTRAP")
                .sourceId("storefront-home-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:00:00Z"))
                .build());

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verifyNoInteractions(principalValidationApi);
        verify(outboxAppender).append(argThat(event ->
                "commerce.session.status_changed".equals(event.getEventType())
                        && SESSION_ID.equals(event.getAggregateId())
                        && !event.getPayload().containsKey("tenant_id")
                        && event.getPayload().get("principal_id") == null
                        && Boolean.FALSE.equals(event.getHeaders().get("pii_safe"))
                        && "RESTRICTED_BEHAVIOR".equals(event.getHeaders().get("data_classification"))));
    }

    @Test
    void shouldLinkValidatedPrincipalAndEmitIdentityEvent() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(1L).setIdentityLinkVersion(0L)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00")));
        when(identityLinkMapper.insert(any(CommerceSessionIdentityLinkDO.class))).thenReturn(1);
        when(sessionMapper.advanceIdentity(eq(7L), eq(SESSION_ID), eq(1L), eq(PRINCIPAL_ID), eq("ACTIVE"),
                eq(1L), any(), any())).thenReturn(1);

        CommerceBehaviorCommandResult result = service.linkSessionIdentity(LinkCommerceSessionIdentityCommand.builder()
                .idempotencyKey("identity-link-0001")
                .runId(RUN_ID)
                .sessionId(SESSION_ID)
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(1L)
                .sourceSystem("STORE_FRONT")
                .sourceType("LOGIN_SUCCESS")
                .sourceId("login-form-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:03:00Z"))
                .build());

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(principalValidationApi).requireActivePrincipal(PRINCIPAL_ID);
        verify(outboxAppender).append(argThat(event ->
                "commerce.session.identity_linked".equals(event.getEventType())
                        && PRINCIPAL_ID.equals(event.getPayload().get("current_principal_id"))
                        && !event.getPayload().containsKey("source_id_digest")
                        && Boolean.FALSE.equals(event.getHeaders().get("pii_safe"))));
    }

    @Test
    void shouldRecordProductDetailViewAgainstLinkedPrincipalSession() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(2L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:04:00")));
        when(sessionMapper.advanceActivity(eq(7L), eq(SESSION_ID), eq(2L), any(), any())).thenReturn(1);
        when(behaviorEventMapper.insert(any(CommerceBehaviorEventDO.class))).thenReturn(1);
        when(listingQueryApi.requirePublishedOffer(any())).thenReturn(PublishedListingOfferView.builder()
                .listingId("8fa63e30-c604-4573-8033-9f686573e34b")
                .listingOfferId("4ae91ba4-a9bd-4417-a7ff-6253bcfd10b8")
                .merchantId("e2f33091-a702-48fd-af1e-c33280cd8d34")
                .shopId("74ad9d20-9202-4f81-aa7b-e38d6474fd35")
                .channelCode("WECHAT")
                .canonicalSpuId(SPU_ID)
                .canonicalSkuId("e28705da-ab41-4c42-af9f-bb6bf4703546")
                .priceMinor(39800L)
                .currencyCode("CNY")
                .build());

        CommerceBehaviorCommandResult result = service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-pdp-0001")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("PDP_VIEWED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(2L)
                .canonicalSpuId(SPU_ID)
                .skuId("e28705da-ab41-4c42-af9f-bb6bf4703546")
                .listingId("8fa63e30-c604-4573-8033-9f686573e34b")
                .listingOfferId("4ae91ba4-a9bd-4417-a7ff-6253bcfd10b8")
                .expectedPriceMinor(39800L)
                .currencyCode("CNY")
                .sourceSystem("STORE_FRONT")
                .sourceType("PDP")
                .sourceId("pdp-main-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:05:00Z"))
                .build());

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        verify(principalValidationApi).requireActivePrincipal(PRINCIPAL_ID);
        verify(outboxAppender).append(argThat(event ->
                "commerce.behavior.recorded".equals(event.getEventType())
                        && Long.valueOf(3L).equals(event.getAggregateVersion())
                        && "PDP_VIEWED".equals(event.getPayload().get("behavior_type"))
                        && SPU_ID.equals(event.getPayload().get("canonical_spu_id"))
                        && "e2f33091-a702-48fd-af1e-c33280cd8d34".equals(event.getPayload().get("merchant_id"))
                        && "74ad9d20-9202-4f81-aa7b-e38d6474fd35".equals(event.getPayload().get("shop_id"))));
    }

    @Test
    void shouldRequireSearchTokenForResultEventsSoSearchFunnelCanBeJoined() {
        RecordCommerceBehaviorCommand command = RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-search-result-0001")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("SEARCH_RESULT_EXPOSED")
                .resultSetToken("result-set-digest-0001")
                .sourceSystem("STORE_FRONT")
                .sourceType("SEARCH")
                .sourceId("search-result-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:05:00Z"))
                .build();

        assertThatThrownBy(() -> service.recordBehavior(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("searchToken is required");
        verifyNoInteractions(operationMapper, sessionMapper, identityLinkMapper, behaviorEventMapper,
                principalValidationApi, outboxAppender);
    }

    @Test
    void shouldMoveSessionToAbandonedWhenCheckoutAbandonedIsRecorded() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("CHECKOUT_IN_PROGRESS")
                .setVersion(3L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:07:00")));
        when(sessionMapper.advanceStatus(eq(7L), eq(SESSION_ID), eq(3L), eq("ABANDONED"), any(), any())).thenReturn(1);
        when(behaviorEventMapper.insert(any(CommerceBehaviorEventDO.class))).thenReturn(1);

        CommerceBehaviorCommandResult result = service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-abandon-0001")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("CHECKOUT_ABANDONED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(3L)
                .checkoutToken("checkout-digest-0001")
                .sourceSystem("STORE_FRONT")
                .sourceType("CHECKOUT")
                .sourceId("cart-confirm-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:08:00Z"))
                .build());

        assertThat(result.getStatus()).isEqualTo("ABANDONED");
        assertThat(result.getAggregateVersion()).isEqualTo(4L);
        ArgumentCaptor<AppendDomainEventCommand> events = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(AppendDomainEventCommand::getEventType)
                .containsExactly("commerce.session.status_changed", "commerce.behavior.recorded");
    }

    @Test
    void shouldAttributePaidOrderBackToLinkedCheckoutSession() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("CHECKOUT_IN_PROGRESS")
                .setVersion(7L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:07:00")));
        when(behaviorEventMapper.selectLatestCheckoutStarted(7L, SESSION_ID, "checkout-digest-0001"))
                .thenReturn(new CommerceBehaviorEventDO()
                        .setBehaviorId(BEHAVIOR_ID)
                        .setSessionId(SESSION_ID)
                        .setCheckoutToken("checkout-digest-0001")
                        .setMerchantId("e2f33091-a702-48fd-af1e-c33280cd8d34")
                        .setShopId("74ad9d20-9202-4f81-aa7b-e38d6474fd35")
                        .setChannelCode("WECHAT")
                        .setOccurredAt(java.time.LocalDateTime.parse("2026-07-18T01:07:00")));
        when(behaviorEventMapper.countCheckoutAbandoned(7L, SESSION_ID, "checkout-digest-0001")).thenReturn(0);
        when(sessionMapper.advanceActivity(eq(7L), eq(SESSION_ID), eq(7L), any(), any())).thenReturn(1);
        when(paymentAttributionMapper.insert(any(CommerceSessionPaymentAttributionDO.class))).thenReturn(1);
        when(orderQueryApi.requireAttributedOrder("f8c9d809-a8d3-4e53-bab9-eea05bdf91fd"))
                .thenReturn(OrderAttributionView.builder()
                        .orderId("f8c9d809-a8d3-4e53-bab9-eea05bdf91fd")
                        .orderNo("CMOATTRIBUTION001")
                        .buyerId(PRINCIPAL_ID)
                        .status("PAYMENT_CONFIRMED")
                        .paymentId("710fe9f9-45fb-4aa0-96d3-a92b0df3b6ef")
                        .aggregateVersion(3L)
                        .merchantId("e2f33091-a702-48fd-af1e-c33280cd8d34")
                        .shopId("74ad9d20-9202-4f81-aa7b-e38d6474fd35")
                        .channelCode("WECHAT")
                        .build());

        CommerceBehaviorCommandResult result = service.attributePaidOrder(AttributePaidOrderCommand.builder()
                .idempotencyKey("behavior-attribution-0001")
                .runId(RUN_ID)
                .attributionId("3441f885-05cc-410e-bce2-ab7b93f38319")
                .sessionId(SESSION_ID)
                .expectedSessionVersion(7L)
                .checkoutToken("checkout-digest-0001")
                .orderId("f8c9d809-a8d3-4e53-bab9-eea05bdf91fd")
                .paymentId("710fe9f9-45fb-4aa0-96d3-a92b0df3b6ef")
                .sourceSystem("STORE_FRONT")
                .sourceType("PAYMENT_CALLBACK")
                .sourceId("payment-webhook-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:10:00Z"))
                .build());

        assertThat(result.getStatus()).isEqualTo("CHECKOUT_IN_PROGRESS");
        assertThat(result.getAggregateVersion()).isEqualTo(8L);
        verify(outboxAppender).append(argThat(event ->
                "commerce.session.payment_attributed".equals(event.getEventType())
                        && "f8c9d809-a8d3-4e53-bab9-eea05bdf91fd".equals(event.getPayload().get("order_id"))
                        && "710fe9f9-45fb-4aa0-96d3-a92b0df3b6ef".equals(event.getPayload().get("payment_id"))
                        && "74ad9d20-9202-4f81-aa7b-e38d6474fd35".equals(event.getPayload().get("shop_id"))));
    }

    @Test
    void shouldValidateRecommendationTokenAndPersistServerResolvedExposure() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(5L)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:05:00")));
        when(sessionMapper.advanceActivity(eq(7L), eq(SESSION_ID), eq(5L), any(), any())).thenReturn(1);
        when(behaviorEventMapper.insert(any(CommerceBehaviorEventDO.class))).thenReturn(1);
        when(appRecommendationReadMapper.selectSnapshot(eq(7L), eq(SESSION_ID), eq("recommend:12345678"),
                eq("8fa63e30-c604-4573-8033-9f686573e34b"), eq(1), any()))
                .thenReturn(recommendationSnapshot());

        CommerceBehaviorCommandResult result = service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("recommendation-exposure-0001")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("RECOMMENDATION_EXPOSED")
                .listingId("8fa63e30-c604-4573-8033-9f686573e34b")
                .resultSetToken("recommend:12345678")
                .resultPosition(1)
                .sourceSystem("STORE_FRONT")
                .sourceType("RECOMMENDATION")
                .sourceId("recommendation-exposure-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:05:30Z"))
                .build());

        assertThat(result.getAggregateVersion()).isEqualTo(6L);
        verifyNoInteractions(principalValidationApi);
        verify(outboxAppender).append(argThat(event ->
                "commerce.behavior.recorded".equals(event.getEventType())
                        && "RECOMMENDATION_EXPOSED".equals(event.getPayload().get("behavior_type"))
                        && SPU_ID.equals(event.getPayload().get("canonical_spu_id"))
                        && "4ae91ba4-a9bd-4417-a7ff-6253bcfd10b8".equals(event.getPayload().get("listing_offer_id"))
                        && Integer.valueOf(1).equals(event.getPayload().get("result_position"))));
    }

    @Test
    void shouldReplayImmutableDuplicateWithoutDomainWrites() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    return 0;
                });
        when(operationMapper.selectForUpdate(91L, 7L)).thenAnswer(ignored -> new CommerceBehaviorOperationDO()
                .setOperationId(91L)
                .setTenantId(7L)
                .setAttemptToken("existing")
                .setRequestHash(requestHash.get())
                .setStatus(10)
                .setResultJson("{\"operationId\":91,\"aggregateId\":\"" + SESSION_ID
                        + "\",\"aggregateType\":\"commerce_session\",\"status\":\"ACTIVE\",\"aggregateVersion\":1,\"duplicate\":false}"));

        CommerceBehaviorCommandResult replay = service.startSession(startCommand());

        assertThat(replay.getDuplicate()).isTrue();
        verifyNoInteractions(principalValidationApi, sessionMapper, identityLinkMapper, behaviorEventMapper, outboxAppender);
    }

    @Test
    void fingerprintShouldIgnoreExecutionTimeButRetainBusinessPayload() {
        StartCommerceSessionCommand first = startCommand();
        StartCommerceSessionCommand retry = startCommand();
        retry.setOccurredAt(first.getOccurredAt().plusSeconds(30));

        assertThat(CommerceBehaviorCommandServiceImpl.fingerprint(retry))
                .isEqualTo(CommerceBehaviorCommandServiceImpl.fingerprint(first));

        retry.setEntrypointCode("SEARCH");
        assertThat(CommerceBehaviorCommandServiceImpl.fingerprint(retry))
                .isNotEqualTo(CommerceBehaviorCommandServiceImpl.fingerprint(first));
    }

    @Test
    void shouldRejectNonUuidRunIdBeforeWrites() {
        assertThatThrownBy(() -> service.startSession(StartCommerceSessionCommand.builder()
                .idempotencyKey("session-start-0001")
                .runId("run-commerce-1")
                .sessionId(SESSION_ID)
                .channelCode("WEB")
                .entrypointCode("HOME")
                .sourceSystem("STORE_FRONT")
                .sourceType("SESSION_BOOTSTRAP")
                .sourceId("storefront-home-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:00:00Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("runId must be a UUID");
        verifyNoInteractions(sessionMapper, outboxAppender);
    }

    @Test
    void shouldRejectSessionRebindingToDifferentPrincipal() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(2L)
                .setPrincipalId(PRINCIPAL_ID).setIdentityLinkVersion(1L)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:03:00")));

        assertThatThrownBy(() -> service.linkSessionIdentity(LinkCommerceSessionIdentityCommand.builder()
                .idempotencyKey("identity-link-0002")
                .runId(RUN_ID)
                .sessionId(SESSION_ID)
                .principalId(OTHER_PRINCIPAL_ID)
                .expectedSessionVersion(2L)
                .sourceSystem("STORE_FRONT")
                .sourceType("LOGIN_SUCCESS")
                .sourceId("login-form-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:04:00Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("session principal rebinding is not supported");
        verify(identityLinkMapper, never()).insert(any(CommerceSessionIdentityLinkDO.class));
        verify(outboxAppender, never()).append(any());
    }

    @Test
    void shouldRejectBehaviorEarlierThanLastActivity() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(2L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:05:00")));

        assertThatThrownBy(() -> service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-pdp-0002")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("PDP_VIEWED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(2L)
                .canonicalSpuId(SPU_ID)
                .sourceSystem("STORE_FRONT")
                .sourceType("PDP")
                .sourceId("pdp-main-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:04:59Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("occurredAt is earlier than session lastActivityAt");
        verify(behaviorEventMapper, never()).insert(any(CommerceBehaviorEventDO.class));
    }

    @Test
    void shouldRejectMateriallyFutureBehavior() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(2L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1))
                .setLastActivityAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(30)));

        assertThatThrownBy(() -> service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-pdp-future")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("PDP_VIEWED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(2L)
                .canonicalSpuId(SPU_ID)
                .sourceSystem("STORE_FRONT")
                .sourceType("PDP")
                .sourceId("pdp-main-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.now().plusSeconds(301))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("occurredAt is materially in the future");
        verify(behaviorEventMapper, never()).insert(any(CommerceBehaviorEventDO.class));
    }

    @Test
    void shouldRejectTerminalSessionBehavior() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ABANDONED").setVersion(4L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:08:00")));

        assertThatThrownBy(() -> service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-after-terminal")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("PDP_VIEWED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(4L)
                .canonicalSpuId(SPU_ID)
                .sourceSystem("STORE_FRONT")
                .sourceType("PDP")
                .sourceId("pdp-main-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:08:00Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terminal session cannot accept more behavior");
        verify(behaviorEventMapper, never()).insert(any(CommerceBehaviorEventDO.class));
    }

    @Test
    void shouldRejectIllegalCheckoutTransitionsAndSessionStartedBehavior() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("CHECKOUT_IN_PROGRESS").setVersion(3L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:07:00")));

        assertThatThrownBy(() -> service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-started-illegal")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("SESSION_STARTED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(3L)
                .sourceSystem("STORE_FRONT")
                .sourceType("CHECKOUT")
                .sourceId("cart-confirm-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:08:00Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("behaviorType is not supported");

        assertThatThrownBy(() -> service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-checkout-illegal")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("CHECKOUT_STARTED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(3L)
                .checkoutToken("checkout-digest-0001")
                .sourceSystem("STORE_FRONT")
                .sourceType("CHECKOUT")
                .sourceId("cart-confirm-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:08:00Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CHECKOUT_STARTED requires ACTIVE session");
    }

    @Test
    void shouldRejectCheckoutAbandonedOutsideCheckoutInProgress() {
        claimNewOperation();
        when(sessionMapper.selectForUpdate(7L, SESSION_ID)).thenReturn(new CommerceSessionDO()
                .setSessionId(SESSION_ID).setTenantId(7L).setStatus("ACTIVE").setVersion(2L).setPrincipalId(PRINCIPAL_ID)
                .setStartedAt(java.time.LocalDateTime.parse("2026-07-18T01:00:00"))
                .setLastActivityAt(java.time.LocalDateTime.parse("2026-07-18T01:05:00")));

        assertThatThrownBy(() -> service.recordBehavior(RecordCommerceBehaviorCommand.builder()
                .idempotencyKey("behavior-abandon-illegal")
                .runId(RUN_ID)
                .behaviorId(BEHAVIOR_ID)
                .sessionId(SESSION_ID)
                .behaviorType("CHECKOUT_ABANDONED")
                .principalId(PRINCIPAL_ID)
                .expectedSessionVersion(2L)
                .checkoutToken("checkout-digest-0001")
                .sourceSystem("STORE_FRONT")
                .sourceType("CHECKOUT")
                .sourceId("cart-confirm-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:06:00Z"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CHECKOUT_ABANDONED requires CHECKOUT_IN_PROGRESS session");
    }

    private void claimNewOperation() {
        reset(operationMapper);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(91L);
        when(operationMapper.selectForUpdate(91L, 7L)).thenAnswer(ignored -> new CommerceBehaviorOperationDO()
                .setOperationId(91L).setTenantId(7L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
    }

    private StartCommerceSessionCommand startCommand() {
        return StartCommerceSessionCommand.builder()
                .idempotencyKey("session-start-0001")
                .runId(RUN_ID)
                .sessionId(SESSION_ID)
                .channelCode("WEB")
                .entrypointCode("HOME")
                .sourceSystem("STORE_FRONT")
                .sourceType("SESSION_BOOTSTRAP")
                .sourceId("storefront-home-01")
                .correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-18T01:00:00Z"))
                .build();
    }

    private AppRecommendationSnapshotDO recommendationSnapshot() {
        AppRecommendationSnapshotDO snapshot = new AppRecommendationSnapshotDO();
        snapshot.setDecisionId("decision-1");
        snapshot.setDecisionToken("recommend:12345678");
        snapshot.setSessionId(SESSION_ID);
        snapshot.setListingId("8fa63e30-c604-4573-8033-9f686573e34b");
        snapshot.setListingOfferId("4ae91ba4-a9bd-4417-a7ff-6253bcfd10b8");
        snapshot.setMerchantId("e2f33091-a702-48fd-af1e-c33280cd8d34");
        snapshot.setShopId("74ad9d20-9202-4f81-aa7b-e38d6474fd35");
        snapshot.setChannelCode("HOME_FEED");
        snapshot.setCanonicalSpuId(SPU_ID);
        snapshot.setCanonicalSkuId("e28705da-ab41-4c42-af9f-bb6bf4703546");
        snapshot.setPriceMinor(39800L);
        snapshot.setCurrencyCode("CNY");
        snapshot.setRankNo(1);
        return snapshot;
    }
}
