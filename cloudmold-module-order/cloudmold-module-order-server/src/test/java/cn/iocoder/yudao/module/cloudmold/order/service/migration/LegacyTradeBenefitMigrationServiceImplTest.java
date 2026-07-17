package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeBenefitAssessmentCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeBenefitAssessmentResult;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeBenefitMigrationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyTradeBenefitMigrationServiceImplTest {

    private static final String RUN = "38000000-0000-4000-8000-000000000001";
    private static final String CORRELATION = "38000000-0000-4000-8000-000000000002";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-17T01:00:00Z");
    private static final LocalDateTime UPDATED_AT = LocalDateTime.ofInstant(OCCURRED_AT, ZoneOffset.UTC);

    private final LegacyTradeBenefitMigrationMapper mapper = mock(LegacyTradeBenefitMigrationMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final LegacyTradeBenefitMigrationServiceImpl service =
            new LegacyTradeBenefitMigrationServiceImpl(mapper, outboxAppender);
    private final AtomicReference<LegacyTradeBenefitMigrationRunDO> savedRun = new AtomicReference<>();
    private final List<LegacyTradeBenefitMigrationCandidateDO> savedCandidates = new ArrayList<>();
    private final List<LegacyTradeBenefitMigrationComponentDO> savedComponents = new ArrayList<>();
    private final List<LegacyTradeBenefitMigrationItemDO> savedItems = new ArrayList<>();
    private final List<AppendDomainEventCommand> events = new ArrayList<>();
    private LegacyTradeBenefitMigrationOperationDO operation;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> {
            if (operation == null) {
                operation = new LegacyTradeBenefitMigrationOperationDO().setOperationId(1L).setTenantId(1L)
                        .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4))
                        .setStatus(0);
            }
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), nullable(String.class), anyString(),
                anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(1L);
        when(mapper.selectOperationForUpdate(1L, 1L)).thenAnswer(ignored -> operation);
        doAnswer(invocation -> {
            operation.setStatus(10).setMigrationRunId(invocation.getArgument(2))
                    .setResultJson(invocation.getArgument(3));
            return 1;
        }).when(mapper).markOperationSucceeded(eq(1L), eq(1L), anyString(), anyString(), any());
        doAnswer(invocation -> { savedRun.set(invocation.getArgument(0)); return 1; })
                .when(mapper).insertRun(any());
        doAnswer(invocation -> { savedCandidates.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insertCandidate(any());
        doAnswer(invocation -> { savedComponents.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insertComponent(any());
        doAnswer(invocation -> { savedItems.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insertItem(any());
        doAnswer(invocation -> { events.add(invocation.getArgument(0)); return null; })
                .when(outboxAppender).append(any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void persistsAllGovernedAssessmentClassesWithoutOpeningImport() {
        LegacyTradeOrderAssessmentSourceDO noBenefit = source(10L, 0, 0, 0, 0);
        LegacyTradeOrderAssessmentSourceDO pending = source(11L, 10, 20, 30, 40)
                .setLegacyCouponId(501L).setLegacyUsedPointQuantity(30).setLegacySeckillActivityId(601L);
        LegacyTradeOrderAssessmentSourceDO negative = source(12L, -10, 0, 0, 0)
                .setItemGenericDiscountAmountMinor(-10L);
        LegacyTradeOrderAssessmentSourceDO headerItem = source(13L, 10, 0, 0, 0)
                .setItemGenericDiscountAmountMinor(0L).setItemPayAmountMinor(1_000L);
        LegacyTradeOrderAssessmentSourceDO deleted = source(14L, 10, 0, 0, 0).setDeleted(true);
        whenSourceRows(List.of(noBenefit, pending, negative, headerItem, deleted));

        LegacyTradeBenefitAssessmentResult result = service.assess(command());

        assertThat(result.getSourceOrderCount()).isEqualTo(5);
        assertThat(result.getNonDeletedOrderCount()).isEqualTo(4);
        assertThat(result.getDeletedExcludedCount()).isEqualTo(1);
        assertThat(result.getNoBenefitOrderCount()).isEqualTo(1);
        assertThat(result.getBenefitEvidencePendingOrderCount()).isEqualTo(1);
        assertThat(result.getQuarantinedOrderCount()).isEqualTo(2);
        assertThat(result.getBenefitComponentCount()).isEqualTo(6);
        assertThat(result.getSourceBenefitAmountMinor()).isEqualTo(100L);
        assertThat(result.getComponentAmountMinor()).isEqualTo(100L);
        assertThat(result.getSourceItemCount()).isEqualTo(5);
        assertThat(result.getActiveItemCount()).isEqualTo(4);
        assertThat(result.getExcludedItemCount()).isEqualTo(1);
        assertThat(result.getItemEvidenceHash()).matches("[0-9a-f]{64}");
        assertThat(result.getItemEvidenceComplete()).isTrue();
        assertThat(result.getUnresolvedIdentityCount()).isEqualTo(6);
        assertThat(result.getUnresolvedFundingCount()).isEqualTo(6);
        assertThat(result.getImportAllowedComponentCount()).isZero();
        assertThat(result.getProductionMigrationEnabled()).isFalse();
        assertThat(result.getStatus()).isEqualTo("BLOCKED_REQUIRES_GOVERNED_EVIDENCE");
        assertThat(savedRun.get().getSourceSnapshotHash()).matches("[0-9a-f]{64}");
        assertThat(savedCandidates).extracting(LegacyTradeBenefitMigrationCandidateDO::getAssessmentStatus)
                .containsExactly("NO_BENEFIT", "BENEFIT_REQUIRES_IDENTITY_AND_FUNDING",
                        "QUARANTINED_MONEY", "QUARANTINED_HEADER_ITEM", "DELETED_EXCLUDED");
        assertThat(savedCandidates).allSatisfy(candidate -> assertThat(candidate.getCanonicalImportAllowed()).isFalse());
        assertThat(savedCandidates).allSatisfy(candidate -> {
            assertThat(candidate.getLegacyBuyerId()).isPositive();
            assertThat(candidate.getBuyerIdentityStatus()).isEqualTo("MISSING");
            assertThat(candidate.getSourceCreatedAt()).isNotNull();
        });
        assertThat(savedComponents).hasSize(6).allSatisfy(component -> {
            assertThat(component.getIdentityResolutionStatus()).isNotBlank();
            assertThat(component.getFundingResolutionStatus()).isEqualTo("MISSING_NAMED_FUNDER_BREAKDOWN");
            assertThat(component.getCanonicalImportAllowed()).isFalse();
        });
        assertThat(savedItems).hasSize(5).allSatisfy(item -> {
            assertThat(item.getLegacyOrderItemId()).isPositive();
            assertThat(item.getLegacyBuyerId()).isPositive();
            assertThat(item.getLegacyItemSnapshotHash()).matches("[0-9a-f]{64}");
            assertThat(item.getCanonicalImportAllowed()).isFalse();
        });
        assertThat(savedComponents).filteredOn(value -> value.getLegacyOrderId().equals(11L))
                .extracting(LegacyTradeBenefitMigrationComponentDO::getComponentType,
                        LegacyTradeBenefitMigrationComponentDO::getIdentityResolutionStatus,
                        LegacyTradeBenefitMigrationComponentDO::getSourceReference)
                .containsExactlyInAnyOrder(
                        tuple("GENERIC_DISCOUNT", "SOURCE_REFERENCE_WITHOUT_VERSION", "SECKILL:601"),
                        tuple("COUPON", "SOURCE_REFERENCE_WITHOUT_VERSION", "COUPON:501"),
                        tuple("POINT", "MISSING_ENTITLEMENT_VERSION", "POINT_QUANTITY:30"),
                        tuple("VIP", "MISSING_BENEFIT_VERSION", null));
        assertThat(events).hasSize(5).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(LegacyTradeBenefitMigrationServiceImpl.ASSESSMENT_EVENT);
            assertThat(event.getSchemaVersion()).isEqualTo(3);
            assertThat(event.getPayload()).containsEntry("migration_run_id", RUN)
                    .containsEntry("buyer_identity_status", "MISSING")
                    .containsEntry("canonical_import_allowed", false)
                    .containsEntry("item_evidence_complete", true)
                    .containsEntry("run_source_item_count", 5)
                    .containsEntry("run_active_item_count", 4)
                    .containsEntry("run_excluded_item_count", 1)
                    .containsEntry("run_item_evidence_benefit_amount_minor", 90L);
            assertThat(event.getPayload().get("run_item_evidence_hash")).asString().matches("[0-9a-f]{64}");
            assertThat(event.getPayload().get("items")).asList().hasSize(1);
            Long legacyOrderId = (Long) event.getPayload().get("legacy_order_id");
            LegacyTradeBenefitMigrationCandidateDO candidate = savedCandidates.stream()
                    .filter(value -> value.getLegacyOrderId().equals(legacyOrderId)).findFirst().orElseThrow();
            assertThat(event.getPayload().get("assessed_at")).isEqualTo(
                    candidate.getAssessedAt().toInstant(ZoneOffset.UTC).toString());
        });
        assertThat(events).filteredOn(event -> event.getPayload().get("legacy_order_id").equals(11L))
                .singleElement().satisfies(event -> assertThat(event.getPayload().get("components"))
                        .asList().hasSize(4));
    }

    @Test
    void rejectsAmbiguousGenericIdentityAndKeepsNamedFundingClosed() {
        LegacyTradeOrderAssessmentSourceDO source = source(20L, 100, 0, 0, 0)
                .setLegacySeckillActivityId(601L).setLegacyBargainActivityId(602L);
        LegacyTradeBenefitMigrationCandidateDO candidate =
                LegacyTradeBenefitMigrationServiceImpl.assessCandidate(1L, RUN, source, UPDATED_AT);

        assertThat(candidate.getAssessmentStatus()).isEqualTo("BENEFIT_REQUIRES_IDENTITY_AND_FUNDING");
        assertThat(JsonUtils.parseArray(candidate.getReasonCodes(), String.class))
                .containsExactly("EXACT_ITEM_MAPPING_MISSING", "EXACT_ORDER_MAPPING_MISSING",
                        "NAMED_FUNDING_BREAKDOWN_MISSING", "VERSIONED_BENEFIT_IDENTITY_MISSING");
        assertThat(LegacyTradeBenefitMigrationServiceImpl.assessComponents(candidate, source, UPDATED_AT))
                .singleElement().satisfies(component -> {
                    assertThat(component.getSourceReference()).isNull();
                    assertThat(component.getIdentityResolutionStatus()).isEqualTo("AMBIGUOUS_SOURCE_REFERENCE");
                    assertThat(component.getFundingResolutionStatus()).isEqualTo("MISSING_NAMED_FUNDER_BREAKDOWN");
                    assertThat(component.getCanonicalImportAllowed()).isFalse();
                });
    }

    @Test
    void immutableReplayDoesNotReadSourceOrAppendEvents() {
        whenSourceRows(List.of(source(30L, 100, 0, 0, 0)));
        LegacyTradeBenefitAssessmentCommand command = command();
        LegacyTradeBenefitAssessmentResult first = service.assess(command);
        clearInvocations(mapper, outboxAppender);

        LegacyTradeBenefitAssessmentResult replay = service.assess(command);

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getMigrationRunId()).isEqualTo(first.getMigrationRunId());
        assertThat(replay.getSourceSnapshotHash()).isEqualTo(first.getSourceSnapshotHash());
        verify(mapper).insertOrResolveOperation(eq(1L), anyString(), nullable(String.class), anyString(),
                anyString(), any());
        verify(mapper).selectLastInsertId();
        verify(mapper).selectOperationForUpdate(1L, 1L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void readsDeterministicItemComponentReconciliationWithoutOpeningImport() {
        when(mapper.selectComponentReconciliations(1L, RUN)).thenReturn(List.of(
                new LegacyTradeBenefitComponentReconciliationDO()
                        .setReconciliationId("a".repeat(64)).setTenantId(1L).setMigrationRunId(RUN)
                        .setCandidateId("38000000-0000-4000-8000-000000000003")
                        .setLegacyOrderId(31L).setLegacyOrderNo("T31").setComponentType("VIP")
                        .setSourceItemComponentRowCount(1).setSourceItemComponentAmountMinor(106L)
                        .setItemComponentRowCount(1).setItemComponentAmountMinor(106L)
                        .setExcludedItemComponentRowCount(0).setExcludedItemComponentAmountMinor(0L)
                        .setHeaderComponentCount(1).setHeaderComponentAmountMinor(100L)
                        .setAmountGapMinor(6L).setOrderAssessmentStatus("QUARANTINED_HEADER_ITEM")
                        .setReconciliationStatus("AMOUNT_MISMATCH").setReconciliationHash("b".repeat(64))
                        .setCanonicalImportAllowed(false)));

        assertThat(service.listComponentReconciliations(RUN)).singleElement().satisfies(value -> {
            assertThat(value.getLegacyOrderId()).isEqualTo(31L);
            assertThat(value.getComponentType()).isEqualTo("VIP");
            assertThat(value.getSourceItemComponentRowCount()).isEqualTo(1);
            assertThat(value.getItemComponentAmountMinor()).isEqualTo(106L);
            assertThat(value.getExcludedItemComponentRowCount()).isZero();
            assertThat(value.getHeaderComponentAmountMinor()).isEqualTo(100L);
            assertThat(value.getAmountGapMinor()).isEqualTo(6L);
            assertThat(value.getReconciliationStatus()).isEqualTo("AMOUNT_MISMATCH");
            assertThat(value.getCanonicalImportAllowed()).isFalse();
        });
    }

    @Test
    void snapshotHashChangesWithMoneyOrBenefitIdentityEvidence() {
        LegacyTradeOrderAssessmentSourceDO source = source(40L, 100, 0, 0, 0);
        String baseline = LegacyTradeBenefitMigrationServiceImpl.sourceSnapshotHash(source);
        source.setHeaderGenericDiscountAmountMinor(101L);
        String moneyChanged = LegacyTradeBenefitMigrationServiceImpl.sourceSnapshotHash(source);
        source.setHeaderGenericDiscountAmountMinor(100L).setLegacySeckillActivityId(601L);
        String identityChanged = LegacyTradeBenefitMigrationServiceImpl.sourceSnapshotHash(source);

        assertThat(moneyChanged).isNotEqualTo(baseline);
        assertThat(identityChanged).isNotEqualTo(baseline);
    }

    @Test
    void rejectsIncompleteSourceEvidenceBeforePersistingRun() {
        whenSourceRows(List.of(source(50L, 100, 0, 0, 0).setItemPayAmountMinor(null)));

        assertThatThrownBy(() -> service.assess(command())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source money evidence is incomplete");
        verify(mapper, never()).insertRun(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsCrossTenantSourceRowsBeforePersistingAssessmentEvidence() {
        whenSourceRows(List.of(source(60L, 100, 0, 0, 0).setTenantId(2L)));

        assertThatThrownBy(() -> service.assess(command())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source tenant does not match assessment tenant");
        verify(mapper, never()).insertRun(any());
        verify(mapper, never()).insertCandidate(any());
        verify(mapper, never()).insertComponent(any());
        verifyNoInteractions(outboxAppender);
    }

    private static LegacyTradeBenefitAssessmentCommand command() {
        return new LegacyTradeBenefitAssessmentCommand().setIdempotencyKey("legacy-trade-benefit-assessment-v1")
                .setMigrationRunId(RUN).setPolicyVersion("legacy-trade-benefit-v4")
                .setEvidenceRef("evidence:local-yudao-trade-current")
                .setCorrelationId(CORRELATION).setOccurredAt(OCCURRED_AT);
    }

    private static LegacyTradeOrderAssessmentSourceDO source(long orderId, long generic, long coupon,
                                                               long point, long vip) {
        long gross = 1_000L;
        long benefit = generic + coupon + point + vip;
        long pay = gross - benefit;
        return new LegacyTradeOrderAssessmentSourceDO().setTenantId(1L).setLegacyOrderId(orderId)
                .setLegacyOrderNo("T" + orderId).setSourceCreatedAt(UPDATED_AT.minusDays(1))
                .setSourceUpdatedAt(UPDATED_AT.plusSeconds(orderId)).setLegacyBuyerId(900L + orderId)
                .setLegacyOrderStatus(20).setBuyerIdentityStatus("MISSING")
                .setDeleted(false).setHeaderQuantity(1).setItemRowCount(1).setItemQuantity(1)
                .setHeaderGrossAmountMinor(gross).setHeaderGenericDiscountAmountMinor(generic)
                .setHeaderCouponAmountMinor(coupon).setHeaderPointAmountMinor(point)
                .setHeaderVipAmountMinor(vip).setHeaderDeliveryAmountMinor(0L)
                .setHeaderAdjustAmountMinor(0L).setHeaderPayAmountMinor(pay)
                .setItemGrossAmountMinor(gross).setItemGenericDiscountAmountMinor(generic)
                .setItemCouponAmountMinor(coupon).setItemPointAmountMinor(point)
                .setItemVipAmountMinor(vip).setItemDeliveryAmountMinor(0L)
                .setItemAdjustAmountMinor(0L).setItemPayAmountMinor(pay)
                .setInvalidItemMoneyCount(0);
    }

    private void whenSourceRows(List<LegacyTradeOrderAssessmentSourceDO> sources) {
        when(mapper.selectSourceOrders(1L)).thenReturn(sources);
        List<LegacyTradeOrderItemAssessmentSourceDO> items = new ArrayList<>();
        for (LegacyTradeOrderAssessmentSourceDO source : sources) {
            items.add(item(source, source.getLegacyOrderId() * 10));
        }
        when(mapper.selectSourceOrderItems(1L)).thenReturn(items);
    }

    private static LegacyTradeOrderItemAssessmentSourceDO item(LegacyTradeOrderAssessmentSourceDO source,
                                                                 long itemId) {
        return new LegacyTradeOrderItemAssessmentSourceDO().setTenantId(source.getTenantId())
                .setLegacyOrderItemId(itemId).setLegacyOrderId(source.getLegacyOrderId())
                .setLegacyBuyerId(source.getLegacyBuyerId())
                .setSourceUpdatedAt(source.getSourceUpdatedAt()).setDeleted(false)
                .setLegacySpuId(100L + itemId).setLegacySkuId(200L + itemId)
                .setItemQuantity(source.getItemQuantity()).setUnitPriceMinor(source.getItemGrossAmountMinor())
                .setGrossAmountMinor(source.getItemGrossAmountMinor())
                .setGenericDiscountAmountMinor(source.getItemGenericDiscountAmountMinor())
                .setCouponAmountMinor(source.getItemCouponAmountMinor())
                .setPointAmountMinor(source.getItemPointAmountMinor()).setVipAmountMinor(source.getItemVipAmountMinor())
                .setDeliveryAmountMinor(source.getItemDeliveryAmountMinor())
                .setAdjustAmountMinor(source.getItemAdjustAmountMinor()).setPayAmountMinor(source.getItemPayAmountMinor())
                .setUsedPointQuantity(source.getLegacyUsedPointQuantity() == null ? 0 : source.getLegacyUsedPointQuantity());
    }
}
