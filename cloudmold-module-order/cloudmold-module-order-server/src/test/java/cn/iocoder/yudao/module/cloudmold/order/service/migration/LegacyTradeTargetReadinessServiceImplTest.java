package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeTargetReadinessCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeTargetReadinessResult;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeTargetReadinessMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyTradeTargetReadinessServiceImplTest {

    private static final String SOURCE_RUN = "43000000-0000-4000-8000-000000000001";
    private static final String READY_RUN = "44000000-0000-4000-8000-000000000001";
    private static final String CORRELATION = "44000000-0000-4000-8000-000000000002";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 2, 0);

    private final LegacyTradeTargetReadinessMapper mapper = mock(LegacyTradeTargetReadinessMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final LegacyTradeTargetReadinessServiceImpl service =
            new LegacyTradeTargetReadinessServiceImpl(mapper, outboxAppender);
    private final AtomicReference<LegacyTradeTargetReadinessRunDO> savedRun = new AtomicReference<>();
    private final List<LegacyTradeTargetReadinessOrderDO> savedOrders = new ArrayList<>();
    private final List<LegacyTradeTargetReadinessItemDO> savedItems = new ArrayList<>();
    private final List<AppendDomainEventCommand> events = new ArrayList<>();
    private LegacyTradeTargetReadinessOperationDO operation;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> {
            if (operation == null) {
                operation = new LegacyTradeTargetReadinessOperationDO().setOperationId(1L).setTenantId(1L)
                        .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4))
                        .setStatus(0);
            }
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), nullable(String.class),
                anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(1L);
        when(mapper.selectOperationForUpdate(1L, 1L)).thenAnswer(ignored -> operation);
        doAnswer(invocation -> {
            operation.setStatus(10).setTargetReadinessRunId(invocation.getArgument(2))
                    .setResultJson(invocation.getArgument(3));
            return 1;
        }).when(mapper).markOperationSucceeded(eq(1L), eq(1L), anyString(), anyString(), any());
        when(mapper.selectSourceRun(1L, SOURCE_RUN)).thenReturn(new LegacyTradeBenefitMigrationRunDO()
                .setMigrationRunId(SOURCE_RUN).setTenantId(1L).setPolicyVersion("legacy-trade-benefit-v4")
                .setSourceOrderCount(2).setSourceItemCount(2).setItemEvidenceComplete(true));
        doAnswer(invocation -> { savedRun.set(invocation.getArgument(0)); return 1; })
                .when(mapper).insertRun(any());
        doAnswer(invocation -> { savedOrders.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insertOrder(any());
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
    void missingExplicitMappingsProduceZeroAdmissionWithoutInventingTargets() {
        when(mapper.selectSourceOrders(1L, SOURCE_RUN)).thenReturn(List.of(
                orderSource(10L, false), orderSource(11L, true)));
        when(mapper.selectSourceItems(1L, SOURCE_RUN)).thenReturn(List.of(
                itemSource(10L, 100L, false, false), itemSource(11L, 110L, false, true)));

        LegacyTradeTargetReadinessResult result = service.assess(command());

        assertThat(result.getSourceOrderCount()).isEqualTo(2);
        assertThat(result.getActiveOrderCount()).isEqualTo(1);
        assertThat(result.getExcludedOrderCount()).isEqualTo(1);
        assertThat(result.getSourceItemCount()).isEqualTo(2);
        assertThat(result.getActiveItemCount()).isEqualTo(1);
        assertThat(result.getExcludedItemCount()).isEqualTo(1);
        assertThat(result.getBuyerResolvedOrderCount()).isZero();
        assertThat(result.getOrderMappingQualifiedCount()).isZero();
        assertThat(result.getLifecycleMappingQualifiedCount()).isZero();
        assertThat(result.getFullyMappedItemCount()).isZero();
        assertThat(result.getMappingAdmittedOrderCount()).isZero();
        assertThat(result.getMappingBlockedOrderCount()).isEqualTo(1);
        assertThat(result.getCanonicalImportAllowedOrderCount()).isZero();
        assertThat(result.getProductionMigrationEnabled()).isFalse();
        assertThat(result.getTargetMappingEvidenceHash()).matches("[0-9a-f]{64}");
        assertThat(savedOrders).filteredOn(value -> value.getLegacyOrderId().equals(10L))
                .singleElement().satisfies(value -> {
                    assertThat(value.getMappingReadinessStatus()).isEqualTo("BLOCKED");
                    assertThat(value.getMappingAdmissionAllowed()).isFalse();
                    assertThat(value.getCanonicalImportAllowed()).isFalse();
                    assertThat(value.getBlockerCodes()).contains("BUYER_IDENTITY_MISSING")
                            .contains("ORDER_MAPPING_MISSING").contains("LIFECYCLE_MAPPING_MISSING")
                            .contains("ORDER_ITEM_TARGET_MAPPING_INCOMPLETE");
                });
        assertThat(savedItems).filteredOn(value -> value.getLegacyOrderItemId().equals(100L))
                .singleElement().satisfies(value -> {
                    assertThat(value.getSpuMappingStatus()).isEqualTo("MISSING");
                    assertThat(value.getSkuMappingStatus()).isEqualTo("MISSING");
                    assertThat(value.getHistoricalProductIdentityStatus()).isEqualTo("MISSING");
                    assertThat(value.getOrderItemMappingStatus()).isEqualTo("MISSING");
                    assertThat(value.getMappingReadinessStatus()).isEqualTo("BLOCKED");
                    assertThat(value.getCanonicalImportAllowed()).isFalse();
                });
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(LegacyTradeTargetReadinessServiceImpl.READINESS_EVENT);
            assertThat(event.getSchemaVersion()).isEqualTo(2);
            assertThat(event.getPayload()).containsEntry("target_readiness_run_id", READY_RUN)
                    .containsEntry("source_migration_run_id", SOURCE_RUN)
                    .containsEntry("canonical_import_allowed", false);
        });
    }

    @Test
    void fullyQualifiedPlansCanPassMappingAdmissionButNeverOpenCanonicalImport() {
        LegacyTradeTargetReadinessCommand command = command();
        LegacyTradeTargetReadinessItemSourceDO itemSource = itemSource(20L, 200L, false, false)
                .setProductIdentityQualificationCount(1)
                .setProductIdentityQualificationId("44000000-0000-4000-8000-000000000009")
                .setQualifiedLegacyOrderItemId(200L).setHistoricalSpuId(1200L).setHistoricalSkuId(2200L)
                .setProductIdentitySourceItemEvidenceHash("b".repeat(64))
                .setHistoricalProductSnapshotHash("c".repeat(64))
                .setSpuMappingCount(1).setSpuMappingId("44000000-0000-4000-8000-000000000010")
                .setCanonicalSpuId("44000000-0000-4000-8000-000000000011").setSpuMappingVersion(1L)
                .setSkuMappingCount(1).setSkuMappingId("44000000-0000-4000-8000-000000000012")
                .setCanonicalSkuId("44000000-0000-4000-8000-000000000013")
                .setCanonicalSkuSpuId("44000000-0000-4000-8000-000000000011").setSkuMappingVersion(1L)
                .setOrderItemMappingCount(1)
                .setOrderItemMappingPlanId("44000000-0000-4000-8000-000000000014")
                .setPlannedOrderItemId("44000000-0000-4000-8000-000000000015")
                .setPlannedOrderId("44000000-0000-4000-8000-000000000016")
                .setOrderItemMappingVersion(1L).setOrderItemMappingSourceSnapshotHash("b".repeat(64));
        LegacyTradeTargetReadinessItemDO item = LegacyTradeTargetReadinessServiceImpl.assessItem(
                1L, command, itemSource, "44000000-0000-4000-8000-000000000017", NOW);
        LegacyTradeTargetReadinessOrderSourceDO orderSource = orderSource(20L, false)
                .setBuyerIdentityStatus("RESOLVED")
                .setBuyerSourceIdentityId("44000000-0000-4000-8000-000000000018")
                .setBuyerPrincipalId("44000000-0000-4000-8000-000000000019").setBuyerIdentityVersion(1L)
                .setOrderMappingCount(1).setOrderMappingPlanId("44000000-0000-4000-8000-000000000020")
                .setPlannedOrderId("44000000-0000-4000-8000-000000000016").setOrderMappingVersion(1L)
                .setOrderMappingSourceSnapshotHash("a".repeat(64)).setLifecycleMappingCount(1)
                .setStatusMappingId("44000000-0000-4000-8000-000000000021")
                .setCanonicalOrderStatus("PAYMENT_CONFIRMED").setLifecycleMappingVersion(1L);

        LegacyTradeTargetReadinessOrderDO order = LegacyTradeTargetReadinessServiceImpl.assessOrder(
                1L, command, orderSource, List.of(item), NOW);

        assertThat(item.getMappingReadinessStatus()).isEqualTo("READY");
        assertThat(item.getMappingAdmissionAllowed()).isTrue();
        assertThat(item.getCanonicalImportAllowed()).isFalse();
        assertThat(order.getMappingReadinessStatus()).isEqualTo("READY");
        assertThat(order.getMappingAdmissionAllowed()).isTrue();
        assertThat(order.getCanonicalImportAllowed()).isFalse();
        assertThat(order.getBlockerCodes()).isEqualTo("[]");
    }

    private static LegacyTradeTargetReadinessCommand command() {
        return new LegacyTradeTargetReadinessCommand().setIdempotencyKey("target-readiness-v1")
                .setTargetReadinessRunId(READY_RUN).setSourceMigrationRunId(SOURCE_RUN)
                .setPolicyVersion(LegacyTradeTargetReadinessServiceImpl.POLICY_VERSION)
                .setEvidenceRef("evidence:explicit-target-mapping-review").setCorrelationId(CORRELATION)
                .setOccurredAt(Instant.parse("2026-07-17T02:00:00Z"));
    }

    private static LegacyTradeTargetReadinessOrderSourceDO orderSource(long orderId, boolean deleted) {
        return new LegacyTradeTargetReadinessOrderSourceDO().setTenantId(1L)
                .setCandidateId("43000000-0000-4000-8000-" + String.format("%012d", orderId))
                .setLegacyOrderId(orderId).setLegacyOrderStatus(20).setLegacySnapshotHash("a".repeat(64))
                .setDeleted(deleted).setBuyerIdentityStatus("MISSING").setNegativeMoney(false)
                .setHeaderMoneyMismatch(false).setHeaderItemMismatch(false).setInvalidItemMoneyCount(0)
                .setOrderMappingCount(0).setLifecycleMappingCount(0);
    }

    private static LegacyTradeTargetReadinessItemSourceDO itemSource(long orderId, long itemId,
                                                                      boolean deleted, boolean orderDeleted) {
        return new LegacyTradeTargetReadinessItemSourceDO().setTenantId(1L)
                .setCandidateId("43000000-0000-4000-8000-" + String.format("%012d", orderId))
                .setLegacyOrderId(orderId).setLegacyOrderItemId(itemId)
                .setItemEvidenceId("43000000-0000-4000-8001-" + String.format("%012d", itemId))
                .setLegacyItemSnapshotHash("b".repeat(64)).setDeleted(deleted).setOrderDeleted(orderDeleted)
                .setLegacySpuId(1000L + itemId).setLegacySkuId(2000L + itemId)
                .setSourceProductIdentityStatus("SOURCE_IDS_PRESENT")
                .setItemQuantity(1).setUnitPriceMinor(1000L).setGrossAmountMinor(1000L)
                .setGenericDiscountAmountMinor(100L).setCouponAmountMinor(0L).setPointAmountMinor(0L)
                .setVipAmountMinor(0L).setDeliveryAmountMinor(0L).setAdjustAmountMinor(0L)
                .setPayAmountMinor(900L).setSpuMappingCount(0).setSkuMappingCount(0)
                .setOrderItemMappingCount(0);
    }
}
