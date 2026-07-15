package cn.iocoder.yudao.module.cloudmold.engagement.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementCommandApi.*;
import cn.iocoder.yudao.module.cloudmold.engagement.api.reference.CatalogSpuReferenceValidationPort;
import cn.iocoder.yudao.module.cloudmold.engagement.api.reference.PrincipalReferenceValidationPort;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EngagementCommandServiceImplTest {

    private static final String PRINCIPAL_ID = "1633b87f-28d8-4c55-85f0-f521fef508af";
    private static final String SPU_ID = "6b6401a0-7f32-48f2-b179-a5c1be76e660";
    private static final String FAVORITE_ID = "d602e764-2f0a-48f6-905d-95485c4bab55";
    private static final String CORRELATION_ID = "6f9619ff-8b86-d011-b42d-00cf4fc964ff";

    private final EngagementOperationMapper operationMapper = mock(EngagementOperationMapper.class);
    private final FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
    private final NotificationCampaignMapper campaignMapper = mock(NotificationCampaignMapper.class);
    private final NotificationDeliveryMapper deliveryMapper = mock(NotificationDeliveryMapper.class);
    private final CommunityMapper communityMapper = mock(CommunityMapper.class);
    private final PrincipalReferenceValidationPort principalPort = mock(PrincipalReferenceValidationPort.class);
    private final CatalogSpuReferenceValidationPort spuPort = mock(CatalogSpuReferenceValidationPort.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final EngagementCommandServiceImpl service = new EngagementCommandServiceImpl(operationMapper,
            favoriteMapper, campaignMapper, deliveryMapper, communityMapper, principalPort, spuPort, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        when(operationMapper.selectLastInsertId()).thenReturn(91L);
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void shouldPersistFavoriteStateAndBehaviorAndPublishTwoTenantSafeEvents() {
        claimNewOperation();
        when(favoriteMapper.insert(any(FavoriteDO.class))).thenReturn(1);
        when(favoriteMapper.insertBehavior(any(FavoriteBehaviorDO.class))).thenReturn(1);

        EngagementCommandResult result = service.changeFavorite(favoriteCommand());

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(principalPort).requireActive(7L, PRINCIPAL_ID);
        verify(spuPort).requireActive(7L, SPU_ID);
        ArgumentCaptor<AppendDomainEventCommand> events = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(AppendDomainEventCommand::getEventType)
                .containsExactly("engagement.favorite.status_changed", "engagement.favorite.behavior_recorded");
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.getTenantId()).isEqualTo(7L);
            assertThat(event.getPayload()).doesNotContainKey("tenant_id");
            assertThat(event.getAggregateId()).isEqualTo(FAVORITE_ID);
        });
    }

    @Test
    void shouldReplayImmutableResultBeforeReferenceAndDomainWrites() {
        AtomicReference<String> hash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { hash.set(invocation.getArgument(3)); return 0; });
        EngagementCommandResult first = EngagementCommandResult.builder().operationId(91L).aggregateId(FAVORITE_ID)
                .aggregateType("engagement_favorite").status("ACTIVE").aggregateVersion(1L).duplicate(false).build();
        when(operationMapper.selectForUpdate(91L, 7L)).thenAnswer(ignored -> new EngagementOperationDO()
                .setOperationId(91L).setCommandType("CHANGE_FAVORITE").setAttemptToken("existing")
                .setRequestHash(hash.get()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        EngagementCommandResult replay = service.changeFavorite(favoriteCommand());

        assertThat(replay.getDuplicate()).isTrue();
        verifyNoInteractions(principalPort, spuPort, outboxAppender);
        verify(favoriteMapper, never()).insert(any(FavoriteDO.class));
    }

    @Test
    void shouldAdvanceDeliveryAttemptWithCasAndPublishStateAndAttemptFacts() {
        claimNewOperation();
        String deliveryId = "48f10f98-49ef-4c14-bfc2-e030a6e0feef";
        NotificationDeliveryDO delivery = new NotificationDeliveryDO().setDeliveryId(deliveryId)
                .setDeliveryKey("campaign-7-principal-1").setCampaignId("11cb4113-cff6-4d19-ad1b-b2dd64437472")
                .setPrincipalId(PRINCIPAL_ID).setChannel("APP_PUSH").setStatus("QUEUED")
                .setAttemptCount(0).setReceiptCount(0).setVersion(1L);
        when(deliveryMapper.selectForUpdate(7L, deliveryId)).thenReturn(delivery);
        when(deliveryMapper.insertAttempt(any(NotificationDeliveryAttemptDO.class))).thenReturn(1);
        when(deliveryMapper.advanceAttempt(eq(7L), eq(deliveryId), eq(1L), eq("SENT"), any())).thenReturn(1);
        RecordNotificationAttemptCommand command = RecordNotificationAttemptCommand.builder()
                .idempotencyKey("attempt-provider-0001").runId("run-notify-1")
                .attemptId("b2a122d5-d5ab-4af7-aa04-3ac23f799096").deliveryId(deliveryId).expectedVersion(1L)
                .attemptNo(1).providerCode("APNS").providerReference("apns-123").outcome("ACCEPTED")
                .correlationId(CORRELATION_ID).occurredAt(Instant.parse("2026-07-16T00:00:03Z")).build();

        EngagementCommandResult result = service.recordNotificationAttempt(command);

        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        ArgumentCaptor<AppendDomainEventCommand> events = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(AppendDomainEventCommand::getEventType)
                .containsExactly("engagement.notification.delivery_status_changed",
                        "engagement.notification.delivery_attempt_recorded");
    }

    @Test
    void shouldCreateActiveSmsCampaignWithQualifiedSource() {
        claimNewOperation();
        String campaignId = "11cb4113-cff6-4d19-ad1b-b2dd64437472";
        when(campaignMapper.insert(any(NotificationCampaignDO.class))).thenReturn(1);
        SaveNotificationCampaignCommand command = SaveNotificationCampaignCommand.builder()
                .idempotencyKey("campaign-create-0001").runId("run-notify-1").campaignId(campaignId)
                .campaignCode("WELCOME_SMS").campaignName("Welcome SMS").channel("SMS").desiredStatus("ACTIVE")
                .sourceSystem("Y_SHOPPING").sourceType("MARKETING_PLAN").sourceId("welcome-2026")
                .correlationId(CORRELATION_ID).occurredAt(Instant.parse("2026-07-16T00:00:02Z")).build();

        EngagementCommandResult result = service.saveNotificationCampaign(command);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(campaignMapper).selectByBusinessKey(7L, "WELCOME_SMS");
        verify(outboxAppender).append(argThat(event ->
                "engagement.notification.campaign_status_changed".equals(event.getEventType())
                        && "Y_SHOPPING".equals(event.getPayload().get("source_system"))));
    }

    @Test
    void shouldPersistEffectReceiptAndAdvanceDeliveryWithCas() {
        claimNewOperation();
        String deliveryId = "48f10f98-49ef-4c14-bfc2-e030a6e0feef";
        NotificationDeliveryDO delivery = new NotificationDeliveryDO().setDeliveryId(deliveryId)
                .setDeliveryKey("delivery-business-1").setCampaignId("11cb4113-cff6-4d19-ad1b-b2dd64437472")
                .setPrincipalId(PRINCIPAL_ID).setChannel("SMS").setStatus("SENT")
                .setAttemptCount(1).setReceiptCount(0).setVersion(2L);
        when(deliveryMapper.selectForUpdate(7L, deliveryId)).thenReturn(delivery);
        when(deliveryMapper.insertReceipt(any(NotificationDeliveryReceiptDO.class))).thenReturn(1);
        when(deliveryMapper.advanceReceipt(eq(7L), eq(deliveryId), eq(2L), eq("DELIVERED"), any())).thenReturn(1);
        RecordNotificationReceiptCommand command = RecordNotificationReceiptCommand.builder()
                .idempotencyKey("receipt-provider-0001").runId("run-notify-1")
                .receiptId("49bfc908-af5f-46cd-9aec-68d2a1ecc53a").deliveryId(deliveryId).expectedVersion(2L)
                .externalReceiptId("provider-receipt-1").receiptStatus("DELIVERED")
                .correlationId(CORRELATION_ID).occurredAt(Instant.parse("2026-07-16T00:00:04Z")).build();

        EngagementCommandResult result = service.recordNotificationReceipt(command);

        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        verify(outboxAppender).append(argThat(event ->
                "engagement.notification.delivery_receipt_recorded".equals(event.getEventType())
                        && "provider-receipt-1".equals(event.getPayload().get("external_receipt_id"))));
    }

    @Test
    void shouldRecordLikeOnlyAgainstPublishedContent() {
        claimNewOperation();
        String contentId = "695e8fb1-5389-4d45-bf17-2bcb55090920";
        when(communityMapper.selectContent(7L, contentId)).thenReturn(new CommunityContentDO()
                .setContentId(contentId).setStatus("PUBLISHED"));
        when(communityMapper.insertInteraction(any(CommunityInteractionDO.class))).thenReturn(1);
        RecordCommunityInteractionCommand command = RecordCommunityInteractionCommand.builder()
                .idempotencyKey("community-like-0001").runId("run-community-1")
                .interactionId("07e90fe2-b479-45b1-80ed-f3e7759cf2a7").actorPrincipalId(PRINCIPAL_ID)
                .interactionType("LIKE").targetType("CONTENT").targetId(contentId)
                .correlationId(CORRELATION_ID).occurredAt(Instant.parse("2026-07-16T00:00:04Z")).build();

        EngagementCommandResult result = service.recordCommunityInteraction(command);

        assertThat(result.getStatus()).isEqualTo("RECORDED");
        verify(principalPort).requireActive(7L, PRINCIPAL_ID);
        verify(outboxAppender).append(argThat(event ->
                "engagement.community.interaction_recorded".equals(event.getEventType())
                        && "LIKE".equals(event.getPayload().get("interaction_type"))));
    }

    @Test
    void shouldHideContentWhenModerationCaseIsActioned() {
        claimNewOperation();
        String caseId = "c04dd424-7682-4fd5-a856-eec247a1e693";
        String contentId = "695e8fb1-5389-4d45-bf17-2bcb55090920";
        CommunityModerationCaseDO moderation = new CommunityModerationCaseDO().setModerationCaseId(caseId)
                .setContentId(contentId).setReporterPrincipalId(PRINCIPAL_ID).setReportReasonCode("SPAM")
                .setStatus("OPEN").setVersion(1L);
        CommunityContentDO content = new CommunityContentDO().setContentId(contentId).setAuthorPrincipalId(PRINCIPAL_ID)
                .setContentType("POST").setBodyRef("object://community/body-1").setStatus("PUBLISHED").setVersion(3L);
        when(communityMapper.selectModerationCaseForUpdate(7L, caseId)).thenReturn(moderation);
        when(communityMapper.selectContentForUpdate(7L, contentId)).thenReturn(content);
        when(communityMapper.decideModerationCase(eq(7L), eq(caseId), eq(1L), eq(PRINCIPAL_ID), eq("ACTIONED"),
                eq("HIDE"), eq("POLICY_SPAM"), any())).thenReturn(1);
        when(communityMapper.updateContentStatus(eq(7L), eq(contentId), eq(3L), eq("HIDDEN"), any())).thenReturn(1);
        DecideModerationCaseCommand command = DecideModerationCaseCommand.builder()
                .idempotencyKey("moderation-decision-1").runId("run-community-1").moderationCaseId(caseId)
                .moderatorPrincipalId(PRINCIPAL_ID).decision("HIDE").decisionReasonCode("POLICY_SPAM")
                .expectedVersion(1L).correlationId(CORRELATION_ID)
                .occurredAt(Instant.parse("2026-07-16T00:00:05Z")).build();

        EngagementCommandResult result = service.decideModerationCase(command);

        assertThat(result.getStatus()).isEqualTo("ACTIONED");
        ArgumentCaptor<AppendDomainEventCommand> events = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(events.capture());
        assertThat(events.getAllValues()).extracting(AppendDomainEventCommand::getEventType)
                .containsExactly("engagement.community.moderation_status_changed",
                        "engagement.community.content_status_changed");
        assertThat(events.getAllValues().get(1).getPayload()).containsEntry("current_status", "HIDDEN")
                .doesNotContainKey("tenant_id");
    }

    @Test
    void shouldFailClosedWithoutTenantBeforeOperationClaim() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> service.changeFavorite(favoriteCommand()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("租户编号");
        verifyNoInteractions(operationMapper, favoriteMapper, principalPort, spuPort, outboxAppender);
    }

    @Test
    void shouldNotCompleteOperationWhenOutboxAppendFails() {
        claimNewOperation();
        when(favoriteMapper.insert(any(FavoriteDO.class))).thenReturn(1);
        when(favoriteMapper.insertBehavior(any(FavoriteBehaviorDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenThrow(new IllegalStateException("outbox unavailable"));

        assertThatThrownBy(() -> service.changeFavorite(favoriteCommand()))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox unavailable");
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    private void claimNewOperation() {
        AtomicReference<String> attempt = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { attempt.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectForUpdate(91L, 7L)).thenAnswer(ignored -> new EngagementOperationDO()
                .setOperationId(91L).setAttemptToken(attempt.get()).setStatus(0));
    }

    private static ChangeFavoriteCommand favoriteCommand() {
        return ChangeFavoriteCommand.builder().idempotencyKey("favorite-change-0001").runId("run-engagement-1")
                .favoriteId(FAVORITE_ID).principalId(PRINCIPAL_ID).canonicalSpuId(SPU_ID).desiredStatus("ACTIVE")
                .sourceSystem("Y_SHOPPING").sourceType("PRODUCT_SPU").sourceId("legacy-spu-1001")
                .correlationId(CORRELATION_ID).occurredAt(Instant.parse("2026-07-16T00:00:01Z")).build();
    }
}
