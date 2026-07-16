package cn.iocoder.yudao.module.cloudmold.promotion.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.promotion.api.*;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionServiceImplTest {

    private final PromotionOperationMapper operationMapper = mock(PromotionOperationMapper.class);
    private final PromotionCampaignMapper campaignMapper = mock(PromotionCampaignMapper.class);
    private final CanonicalCouponTemplateMapper templateMapper = mock(CanonicalCouponTemplateMapper.class);
    private final CouponEntitlementMapper entitlementMapper = mock(CouponEntitlementMapper.class);
    private final CouponEntitlementLedgerMapper ledgerMapper = mock(CouponEntitlementLedgerMapper.class);
    private final AdvertisingPlacementMapper placementMapper = mock(AdvertisingPlacementMapper.class);
    private final AdvertisingInteractionMapper interactionMapper = mock(AdvertisingInteractionMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final PromotionServiceImpl service = new PromotionServiceImpl(operationMapper, campaignMapper,
            templateMapper, entitlementMapper, ledgerMapper, placementMapper, interactionMapper, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult(
                "11111111-1111-4111-8111-111111111111", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsTenantScopedCampaignAndPublishesVersionedEvent() {
        prepareNewOperation(PromotionOperation.CREATE_CAMPAIGN);

        PromotionCommandResult result = service.execute(createCampaign());

        assertThat(result.getAggregateType()).isEqualTo("promotion_campaign");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(campaignMapper).insert(argThat((PromotionCampaignDO row) -> row.getTenantId().equals(7L)
                && row.getCampaignCode().equals("ACT-2026-07") && row.getVersion().equals(1L)));
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("promotion.campaign.state_changed");
        assertThat(event.getValue().getSchemaVersion()).isEqualTo(1);
        assertThat(event.getValue().getAggregateVersion()).isEqualTo(1L);
        assertThat(event.getValue().getPayload()).containsEntry("campaign_code", "ACT-2026-07")
                .containsEntry("campaign_kind", "ACTIVITY")
                .containsEntry("current_status", "DRAFT")
                .doesNotContainKeys("tenant_id", "status");
    }

    @Test
    void replaysImmutableResultForSameTenantIdempotencyKey() {
        PromotionCommand command = createCampaign();
        PromotionCommandResult first = PromotionCommandResult.builder().operationId(42L)
                .aggregateType("promotion_campaign").aggregateId("campaign-existing")
                .aggregateVersion(1L).status("DRAFT").build();
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, 7L)).thenReturn(new PromotionOperationDO()
                .setOperationId(42L).setAttemptToken("original-attempt")
                .setRequestHash(PromotionServiceImpl.fingerprint(7L, command)).setStatus(10)
                .setResultJson(JsonUtils.toJsonString(first)));

        PromotionCommandResult replay = service.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getAggregateId()).isEqualTo("campaign-existing");
        verifyNoInteractions(campaignMapper, templateMapper, entitlementMapper, ledgerMapper,
                placementMapper, interactionMapper, outboxAppender);
    }

    @Test
    void issuesEntitlementWithImmutableMoneySnapshotAndLedger() {
        prepareNewOperation(PromotionOperation.ISSUE_COUPON_ENTITLEMENT);
        when(templateMapper.selectForUpdate(7L, "template-1")).thenReturn(new CouponTemplateDO()
                .setTemplateId("template-1").setCampaignId("campaign-1").setStatus("ACTIVE")
                .setFaceAmountMinor(2500L).setThresholdMinor(10000L).setCurrencyCode("CNY")
                .setValidFrom(LocalDateTime.parse("2026-07-01T00:00:00"))
                .setValidTo(LocalDateTime.parse("2026-08-01T00:00:00")));
        PromotionCommand command = envelope(PromotionOperation.ISSUE_COUPON_ENTITLEMENT)
                .couponEntitlement(PromotionCommand.CouponEntitlementDefinition.builder()
                        .entitlementCode("CPN-U100-001").templateId("template-1").principalId("user-100")
                        .reason("campaign grant").build()).build();

        PromotionCommandResult result = service.execute(command);

        assertThat(result.getStatus()).isEqualTo("ISSUED");
        verify(entitlementMapper).insert(argThat((CouponEntitlementDO row) -> row.getTenantId().equals(7L)
                && row.getFaceAmountMinor().equals(2500L) && row.getThresholdMinor().equals(10000L)
                && row.getCurrencyCode().equals("CNY") && row.getVersion().equals(1L)));
        verify(ledgerMapper).insert(argThat((CouponEntitlementLedgerDO row) -> row.getEntitlementVersion().equals(1L)
                && row.getPreviousStatus() == null && row.getCurrentStatus().equals("ISSUED")
                && row.getFaceAmountMinor().equals(2500L) && row.getCurrencyCode().equals("CNY")));
        verify(outboxAppender).append(argThat(event -> event.getEventType()
                .equals("promotion.coupon_entitlement.state_changed")
                && Long.valueOf(2500L).equals(event.getPayload().get("face_amount_minor"))
                && "CNY".equals(event.getPayload().get("currency_code"))
                && event.getPayload().containsKey("ledger_entry_id")));
    }

    @Test
    void rejectsClickWhoseSourceIsNotAnImpression() {
        prepareNewOperation(PromotionOperation.RECORD_CLICK);
        LocalDateTime from = LocalDateTime.parse("2026-07-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-08-01T00:00:00");
        when(placementMapper.selectForUpdate(7L, "placement-1")).thenReturn(new AdvertisingPlacementDO()
                .setPlacementId("placement-1").setCampaignId("campaign-1").setStatus("ACTIVE")
                .setValidFrom(from).setValidTo(to));
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE").setStartsAt(from).setEndsAt(to));
        when(interactionMapper.selectById(7L, "source-1")).thenReturn(new AdvertisingInteractionDO()
                .setInteractionId("source-1").setInteractionType("CLICK").setPlacementId("placement-1")
                .setSessionId("session-1"));
        PromotionCommand command = envelope(PromotionOperation.RECORD_CLICK)
                .advertisingInteraction(PromotionCommand.AdvertisingInteractionDefinition.builder()
                        .deduplicationKey("click-100").placementId("placement-1").sessionId("session-1")
                        .sourceInteractionId("source-1").build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CLICK must reference a IMPRESSION");
        verify(interactionMapper, never()).insert(any(AdvertisingInteractionDO.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsOptimisticVersionMismatchBeforeCampaignMutation() {
        prepareNewOperation(PromotionOperation.ACTIVATE_CAMPAIGN);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("DRAFT").setVersion(3L));
        PromotionCommand command = envelope(PromotionOperation.ACTIVATE_CAMPAIGN)
                .campaign(PromotionCommand.CampaignDefinition.builder().campaignId("campaign-1")
                        .expectedVersion(2L).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregate version conflict");
        verify(campaignMapper, never()).updateStatusCas(anyLong(), anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(outboxAppender);
    }

    private void prepareNewOperation(PromotionOperation operation) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(7L), anyString(), eq(operation.name()), anyString(), anyString(),
                any(LocalDateTime.class))).thenAnswer(invocation -> {
            attemptToken.set(invocation.getArgument(4));
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, 7L)).thenAnswer(ignored -> new PromotionOperationDO()
                .setOperationId(42L).setTenantId(7L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(eq(42L), eq(7L), anyString(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(1);
    }

    private static PromotionCommand createCampaign() {
        return envelope(PromotionOperation.CREATE_CAMPAIGN)
                .campaign(PromotionCommand.CampaignDefinition.builder().campaignCode("ACT-2026-07")
                        .campaignKind("ACTIVITY").name("七月会员活动")
                        .startsAt(Instant.parse("2026-07-01T00:00:00Z"))
                        .endsAt(Instant.parse("2026-08-01T00:00:00Z")).build()).build();
    }

    private static PromotionCommand.PromotionCommandBuilder envelope(PromotionOperation operation) {
        return PromotionCommand.builder().operation(operation).idempotencyKey("promotion-test-" + operation.name())
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-16T10:00:00Z"));
    }
}
