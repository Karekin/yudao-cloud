package cn.iocoder.yudao.module.cloudmold.promotion.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAttributionView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.promotion.api.*;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.List;
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
    private final AdvertisingLedgerEntryMapper advertisingLedgerEntryMapper = mock(AdvertisingLedgerEntryMapper.class);
    private final PromotionExperimentResultMapper promotionExperimentResultMapper = mock(PromotionExperimentResultMapper.class);
    private final GrowthExperimentMapper growthExperimentMapper = mock(GrowthExperimentMapper.class);
    private final GrowthExperimentVariantMapper growthExperimentVariantMapper = mock(GrowthExperimentVariantMapper.class);
    private final GrowthExperimentExposureMapper growthExperimentExposureMapper = mock(GrowthExperimentExposureMapper.class);
    private final GrowthExperimentMetricSnapshotMapper growthExperimentMetricSnapshotMapper =
            mock(GrowthExperimentMetricSnapshotMapper.class);
    private final OrderQueryApi orderQueryApi = mock(OrderQueryApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final PromotionServiceImpl service = new PromotionServiceImpl(operationMapper, campaignMapper,
            templateMapper, entitlementMapper, ledgerMapper, placementMapper, interactionMapper,
            advertisingLedgerEntryMapper, promotionExperimentResultMapper, growthExperimentMapper,
            growthExperimentVariantMapper, growthExperimentExposureMapper,
            growthExperimentMetricSnapshotMapper, orderQueryApi, outboxAppender);

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

    @Test
    void recordsAdvertisingRevenueLedgerEntryWithCampaignScopedMoneyFact() {
        prepareNewOperation(PromotionOperation.RECORD_ADVERTISING_LEDGER_ENTRY);
        LocalDateTime from = LocalDateTime.parse("2026-07-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-08-01T00:00:00");
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE").setStartsAt(from).setEndsAt(to));
        when(interactionMapper.selectById(7L, "attribution-1")).thenReturn(new AdvertisingInteractionDO()
                .setInteractionId("attribution-1").setInteractionType("ATTRIBUTION").setCampaignId("campaign-1")
                .setPlacementId("placement-1"));
        when(orderQueryApi.requireAttributedOrder("order-1")).thenReturn(OrderAttributionView.builder()
                .orderId("order-1").orderNo("CMO1").buyerId("buyer-1").paymentId("pay-1")
                .status("PAYMENT_CONFIRMED").aggregateVersion(3L).merchantId("merchant-1").build());
        PromotionCommand command = envelope(PromotionOperation.RECORD_ADVERTISING_LEDGER_ENTRY)
                .advertisingLedger(PromotionCommand.AdvertisingLedgerDefinition.builder()
                        .ledgerEntryCode("ad-ledger-001").campaignId("campaign-1").merchantId("merchant-1")
                        .entryType("REVENUE").chargeModel("SETTLEMENT").revenueType("ADVERTISING")
                        .sourceInteractionId("attribution-1")
                        .orderRef("order-1").amountMinor(1900L).currencyCode("cny").build())
                .build();

        PromotionCommandResult result = service.execute(command);

        assertThat(result.getAggregateType()).isEqualTo("promotion_advertising_ledger_entry");
        verify(advertisingLedgerEntryMapper).insert(argThat((AdvertisingLedgerEntryDO row) ->
                row.getCampaignId().equals("campaign-1")
                        && row.getMerchantId().equals("merchant-1")
                        && row.getEntryType().equals("REVENUE")
                        && row.getRevenueType().equals("ADVERTISING")
                        && row.getAmountMinor().equals(1900L)
                        && row.getCurrencyCode().equals("CNY")
                        && row.getOrderRef().equals("order-1")));
        verify(outboxAppender).append(argThat(event ->
                event.getEventType().equals("promotion.advertising_ledger.recorded")
                        && Long.valueOf(1900L).equals(event.getPayload().get("amount_minor"))
                        && "REVENUE".equals(event.getPayload().get("entry_type"))
                        && "ADVERTISING".equals(event.getPayload().get("revenue_type"))));
    }

    @Test
    void rejectsAdvertisingRevenueWhenMerchantDoesNotOwnAttributedOrder() {
        prepareNewOperation(PromotionOperation.RECORD_ADVERTISING_LEDGER_ENTRY);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE")
                .setStartsAt(LocalDateTime.parse("2026-07-01T00:00:00"))
                .setEndsAt(LocalDateTime.parse("2026-08-01T00:00:00")));
        when(orderQueryApi.requireAttributedOrder("order-1")).thenReturn(OrderAttributionView.builder()
                .orderId("order-1").buyerId("buyer-1").paymentId("pay-1")
                .status("PAYMENT_CONFIRMED").merchantId("merchant-owner").build());
        PromotionCommand command = envelope(PromotionOperation.RECORD_ADVERTISING_LEDGER_ENTRY)
                .advertisingLedger(PromotionCommand.AdvertisingLedgerDefinition.builder()
                        .ledgerEntryCode("ad-ledger-wrong-merchant").campaignId("campaign-1")
                        .merchantId("merchant-other").entryType("REVENUE").chargeModel("SETTLEMENT")
                        .revenueType("COMMISSION").orderRef("order-1")
                        .amountMinor(1900L).currencyCode("CNY").build())
                .build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REVENUE ledger merchantId does not match canonical order merchant");
        verify(advertisingLedgerEntryMapper, never()).insert(any(AdvertisingLedgerEntryDO.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsPromotionExperimentWithoutBaselineContributionProfit() {
        prepareNewOperation(PromotionOperation.UPSERT_PROMOTION_EXPERIMENT_RESULT);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE")
                .setStartsAt(LocalDateTime.parse("2026-07-01T00:00:00"))
                .setEndsAt(LocalDateTime.parse("2026-08-01T00:00:00")));
        PromotionCommand command = envelope(PromotionOperation.UPSERT_PROMOTION_EXPERIMENT_RESULT)
                .occurredAt(Instant.parse("2026-07-18T10:00:00Z"))
                .promotionExperimentResult(PromotionCommand.PromotionExperimentResultDefinition.builder()
                        .experimentCode("exp-merchant-001").campaignId("campaign-1").merchantId("merchant-1")
                        .measuredFrom(Instant.parse("2026-07-10T00:00:00Z"))
                        .measuredTo(Instant.parse("2026-07-17T00:00:00Z"))
                        .treatmentContributionProfitMinor(18000L)
                        .incrementalContributionProfitMinor(3200L)
                        .promotionCostMinor(8000L).currencyCode("CNY").build())
                .build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baselineContributionProfitMinor must be non-negative");
        verifyNoInteractions(promotionExperimentResultMapper, outboxAppender);
    }

    @Test
    void upsertsPromotionExperimentResultAsVersionedCurrentFact() {
        prepareNewOperation(PromotionOperation.UPSERT_PROMOTION_EXPERIMENT_RESULT);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE")
                .setStartsAt(LocalDateTime.parse("2026-07-01T00:00:00"))
                .setEndsAt(LocalDateTime.parse("2026-08-01T00:00:00")));
        PromotionCommand command = envelope(PromotionOperation.UPSERT_PROMOTION_EXPERIMENT_RESULT)
                .occurredAt(Instant.parse("2026-07-18T10:00:00Z"))
                .promotionExperimentResult(PromotionCommand.PromotionExperimentResultDefinition.builder()
                        .experimentCode("exp-merchant-001").campaignId("campaign-1").merchantId("merchant-1")
                        .measuredFrom(Instant.parse("2026-07-10T00:00:00Z"))
                        .measuredTo(Instant.parse("2026-07-17T00:00:00Z"))
                        .baselineContributionProfitMinor(12000L)
                        .treatmentContributionProfitMinor(18000L)
                        .incrementalContributionProfitMinor(6000L)
                        .promotionCostMinor(8000L)
                        .eligiblePopulationCount(1000)
                        .treatmentPopulationCount(500)
                        .controlPopulationCount(500)
                        .currencyCode("CNY")
                        .methodologyRef("holdout-v1").build())
                .build();

        PromotionCommandResult result = service.execute(command);

        assertThat(result.getAggregateType()).isEqualTo("promotion_experiment_result");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(promotionExperimentResultMapper).insert(argThat((PromotionExperimentResultDO row) ->
                row.getExperimentCode().equals("exp-merchant-001")
                        && row.getBaselineContributionProfitMinor().equals(12000L)
                        && row.getIncrementalContributionProfitMinor().equals(6000L)
                        && row.getPromotionCostMinor().equals(8000L)
                        && row.getCurrencyCode().equals("CNY")
                        && row.getVersion().equals(1L)));
        verify(outboxAppender).append(argThat(event ->
                event.getEventType().equals("promotion.experiment_result.upserted")
                        && Long.valueOf(12000L).equals(event.getPayload().get("baseline_contribution_profit_minor"))
                        && Long.valueOf(6000L).equals(event.getPayload().get("incremental_contribution_profit_minor"))));
    }

    @Test
    void updatesEditableDraftCampaignFieldsWithoutStatusChange() {
        prepareNewOperation(PromotionOperation.UPDATE_CAMPAIGN);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("DRAFT").setVersion(3L));
        when(campaignMapper.updateFieldsCas(eq(7L), eq("campaign-1"), eq(3L), eq("七月会员活动-改"),
                eq("GENERAL"), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(1);
        PromotionCommand command = envelope(PromotionOperation.UPDATE_CAMPAIGN)
                .campaign(PromotionCommand.CampaignDefinition.builder().campaignId("campaign-1")
                        .expectedVersion(3L).name("七月会员活动-改").campaignKind("GENERAL")
                        .startsAt(Instant.parse("2026-07-02T00:00:00Z"))
                        .endsAt(Instant.parse("2026-08-02T00:00:00Z")).build()).build();

        PromotionCommandResult result = service.execute(command);

        assertThat(result.getAggregateVersion()).isEqualTo(4L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(campaignMapper).updateFieldsCas(eq(7L), eq("campaign-1"), eq(3L), eq("七月会员活动-改"),
                eq("GENERAL"), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(campaignMapper, never()).updateStatusCas(anyLong(), anyString(), anyLong(), anyString(), any());
        verify(outboxAppender).append(argThat(event ->
                event.getEventType().equals("promotion.campaign.fields_updated")
                        && event.getAggregateVersion().equals(4L)));
    }

    @Test
    void rejectsUpdateWhenCampaignIsNotEditable() {
        prepareNewOperation(PromotionOperation.UPDATE_CAMPAIGN);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE").setVersion(3L));
        PromotionCommand command = envelope(PromotionOperation.UPDATE_CAMPAIGN)
                .campaign(PromotionCommand.CampaignDefinition.builder().campaignId("campaign-1")
                        .expectedVersion(3L).name("改名").campaignKind("ACTIVITY")
                        .startsAt(Instant.parse("2026-07-02T00:00:00Z"))
                        .endsAt(Instant.parse("2026-08-02T00:00:00Z")).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("campaign is not editable");
        verify(campaignMapper, never()).updateFieldsCas(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void createsAuditableGrowthExperimentWithFixedAllocation() {
        prepareNewOperation(PromotionOperation.CREATE_GROWTH_EXPERIMENT);
        when(campaignMapper.selectForUpdate(7L, "campaign-1")).thenReturn(new PromotionCampaignDO()
                .setCampaignId("campaign-1").setStatus("ACTIVE"));

        PromotionCommand command = envelope(PromotionOperation.CREATE_GROWTH_EXPERIMENT)
                .growthExperiment(PromotionCommand.GrowthExperimentDefinition.builder()
                        .experimentId("experiment-1").experimentCode("EXP-1").campaignId("campaign-1")
                        .name("结算页推荐实验").hypothesis("新推荐策略提高支付转化")
                        .primaryMetricCode("PAYMENT_CONVERSION")
                        .minimumSampleSizePerVariant(30)
                        .startsAt(Instant.parse("2026-07-17T00:00:00Z"))
                        .endsAt(Instant.parse("2026-07-19T00:00:00Z"))
                        .variants(List.of(
                                PromotionCommand.GrowthExperimentVariantDefinition.builder()
                                        .variantCode("CONTROL").variantKind("CONTROL")
                                        .allocationBasisPoints(5000).build(),
                                PromotionCommand.GrowthExperimentVariantDefinition.builder()
                                        .variantCode("TREATMENT").variantKind("TREATMENT")
                                        .allocationBasisPoints(5000).build()))
                        .build()).build();

        PromotionCommandResult result = service.execute(command);

        assertThat(result.getAggregateType()).isEqualTo("promotion_growth_experiment");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(growthExperimentMapper).insert(argThat((GrowthExperimentDO row) ->
                row.getExperimentId().equals("experiment-1")
                        && row.getMinimumSampleSizePerVariant().equals(30)
                        && row.getPrimaryMetricCode().equals("PAYMENT_CONVERSION")));
        verify(growthExperimentVariantMapper, times(2)).insert(any(GrowthExperimentVariantDO.class));
        verify(outboxAppender).append(argThat(event ->
                event.getEventType().equals("promotion.growth_experiment.created")
                        && event.getPayload().get("experiment_id").equals("experiment-1")));
    }

    @Test
    void rejectsGrowthExperimentConclusionBelowMinimumSample() {
        prepareNewOperation(PromotionOperation.CONCLUDE_GROWTH_EXPERIMENT);
        when(growthExperimentMapper.selectForUpdate(7L, "experiment-1")).thenReturn(new GrowthExperimentDO()
                .setExperimentId("experiment-1").setStatus("RUNNING").setVersion(2L)
                .setEndsAt(LocalDateTime.parse("2026-07-15T00:00:00"))
                .setPrimaryMetricCode("PAYMENT_CONVERSION").setMinimumSampleSizePerVariant(30));
        when(growthExperimentVariantMapper.selectByExperiment(7L, "experiment-1")).thenReturn(List.of(
                new GrowthExperimentVariantDO().setVariantCode("CONTROL"),
                new GrowthExperimentVariantDO().setVariantCode("TREATMENT")));
        when(growthExperimentMetricSnapshotMapper.selectLatestByVariant(
                7L, "experiment-1", "PAYMENT_CONVERSION")).thenReturn(List.of(
                new GrowthExperimentMetricSnapshotDO().setVariantCode("CONTROL").setSampleCount(20)
                        .setDataFreshUntil(LocalDateTime.parse("2026-07-17T00:00:00")),
                new GrowthExperimentMetricSnapshotDO().setVariantCode("TREATMENT").setSampleCount(20)
                        .setDataFreshUntil(LocalDateTime.parse("2026-07-17T00:00:00"))));

        PromotionCommand command = envelope(PromotionOperation.CONCLUDE_GROWTH_EXPERIMENT)
                .growthExperimentConclusion(PromotionCommand.GrowthExperimentConclusionDefinition.builder()
                        .experimentId("experiment-1").expectedVersion(2L).decision("TREATMENT")
                        .confidenceBasisPoints(9700).guardrailStatus("PASSED")
                        .evidenceRef("evidence://experiment/EXP-1/conclusion")
                        .reason("治疗组通过预设门槛").build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum sample size not reached");
        verify(growthExperimentMapper, never()).concludeCas(anyLong(), anyString(), anyLong(), anyString(),
                anyInt(), anyString(), anyString(), anyString(), any(LocalDateTime.class));
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
