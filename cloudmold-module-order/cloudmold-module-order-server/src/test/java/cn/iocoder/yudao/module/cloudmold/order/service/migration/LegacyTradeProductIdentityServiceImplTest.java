package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeProductIdentityCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeProductIdentityResult;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeProductIdentityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyTradeProductIdentityServiceImplTest {

    private static final String SOURCE_RUN = "45000000-0000-4000-8000-000000000001";
    private static final String IDENTITY_RUN = "4b000000-0000-4000-8000-000000000001";
    private static final String CORRELATION = "4b000000-0000-4000-8000-000000000002";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 4, 0);

    private final LegacyTradeProductIdentityMapper mapper = mock(LegacyTradeProductIdentityMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final LegacyTradeProductIdentityServiceImpl service =
            new LegacyTradeProductIdentityServiceImpl(mapper, outboxAppender);
    private final AtomicReference<LegacyTradeProductIdentityRunDO> savedRun = new AtomicReference<>();
    private final List<LegacyTradeProductIdentityItemDO> savedItems = new ArrayList<>();
    private final List<AppendDomainEventCommand> events = new ArrayList<>();
    private LegacyTradeProductIdentityOperationDO operation;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> {
            if (operation == null) {
                operation = new LegacyTradeProductIdentityOperationDO().setOperationId(1L).setTenantId(1L)
                        .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4))
                        .setStatus(0);
            }
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), nullable(String.class),
                anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(1L);
        when(mapper.selectOperationForUpdate(1L, 1L)).thenAnswer(ignored -> operation);
        doAnswer(invocation -> {
            operation.setStatus(10).setIdentityRunId(invocation.getArgument(2))
                    .setResultJson(invocation.getArgument(3));
            return 1;
        }).when(mapper).markOperationSucceeded(eq(1L), eq(1L), anyString(), anyString(), any());
        when(mapper.selectSourceRun(1L, SOURCE_RUN)).thenReturn(new LegacyTradeBenefitMigrationRunDO()
                .setMigrationRunId(SOURCE_RUN).setTenantId(1L).setPolicyVersion("legacy-trade-benefit-v5")
                .setSourceItemCount(2).setItemEvidenceComplete(true)
                .setProductSnapshotEvidenceComplete(true));
        doAnswer(invocation -> { savedRun.set(invocation.getArgument(0)); return 1; })
                .when(mapper).insertRun(any());
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
    void currentRowsAndMajorityPairsNeverQualifyHistoricalIdentity() {
        when(mapper.selectSourceItems(1L, SOURCE_RUN)).thenReturn(List.of(
                source(100L, 633L, 1L, 2, true),
                source(101L, 634L, 14L, 1, true)));

        LegacyTradeProductIdentityResult result = service.assess(command());

        assertThat(result.getSourceItemCount()).isEqualTo(2);
        assertThat(result.getSourcePairUnambiguousCount()).isEqualTo(1);
        assertThat(result.getSourceParentConflictItemCount()).isEqualTo(1);
        assertThat(result.getCurrentRelationObservedCount()).isEqualTo(2);
        assertThat(result.getHistoricalIdentityQualifiedCount()).isZero();
        assertThat(result.getIdentityAdmittedItemCount()).isZero();
        assertThat(result.getTargetMappingEnabled()).isFalse();
        assertThat(savedItems).allSatisfy(value -> {
            assertThat(value.getHistoricalIdentityStatus()).isEqualTo("MISSING");
            assertThat(value.getIdentityAdmissionAllowed()).isFalse();
            assertThat(value.getTargetMappingAllowed()).isFalse();
            assertThat(value.getBlockerCodes()).contains("HISTORICAL_PRODUCT_IDENTITY_QUALIFICATION_MISSING");
        });
        assertThat(savedItems.get(0).getBlockerCodes())
                .contains("SKU_PARENT_CONFLICT_IN_IMMUTABLE_ORDER_HISTORY");
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(LegacyTradeProductIdentityServiceImpl.IDENTITY_EVENT);
            assertThat(event.getSchemaVersion()).isEqualTo(1);
            assertThat(event.getAggregateId()).isNotEqualTo(event.getPayload().get("item_evidence_id"));
            assertThat(event.getPayload()).containsEntry("target_mapping_enabled", false);
        });
    }

    @Test
    void exactPerItemHistoricalQualificationCanResolveAParentConflict() {
        LegacyTradeProductIdentityItemSourceDO source = source(100L, 633L, 1L, 2, false)
                .setQualificationCount(1).setQualificationId("4b000000-0000-4000-8000-000000000010")
                .setQualifiedLegacyOrderItemId(100L).setHistoricalSpuId(633L).setHistoricalSkuId(1L)
                .setQualificationSourceItemEvidenceHash("a".repeat(64))
                .setHistoricalProductSnapshotHash("b".repeat(64));

        LegacyTradeProductIdentityItemDO item = LegacyTradeProductIdentityServiceImpl.assessItem(
                1L, command(), source, NOW);

        assertThat(item.getSourcePairStatus()).isEqualTo("SOURCE_SKU_PARENT_CONFLICT");
        assertThat(item.getCurrentReferenceStatus()).isEqualTo("CURRENT_RELATION_MISSING_OR_MISMATCH");
        assertThat(item.getHistoricalIdentityStatus()).isEqualTo("QUALIFIED");
        assertThat(item.getBlockerCodes()).isEqualTo("[]");
        assertThat(item.getIdentityAdmissionAllowed()).isTrue();
        assertThat(item.getTargetMappingAllowed()).isTrue();
    }

    @Test
    void qualificationMustBindTheExactImmutableItemEvidence() {
        LegacyTradeProductIdentityItemSourceDO source = source(100L, 633L, 1L, 1, true)
                .setQualificationCount(1).setQualificationId("4b000000-0000-4000-8000-000000000010")
                .setQualifiedLegacyOrderItemId(100L).setHistoricalSpuId(633L).setHistoricalSkuId(1L)
                .setQualificationSourceItemEvidenceHash("f".repeat(64))
                .setHistoricalProductSnapshotHash("b".repeat(64));

        LegacyTradeProductIdentityItemDO item = LegacyTradeProductIdentityServiceImpl.assessItem(
                1L, command(), source, NOW);

        assertThat(item.getHistoricalIdentityStatus()).isEqualTo("AMBIGUOUS");
        assertThat(item.getQualificationId()).isNull();
        assertThat(item.getIdentityAdmissionAllowed()).isFalse();
        assertThat(item.getBlockerCodes())
                .contains("HISTORICAL_PRODUCT_IDENTITY_QUALIFICATION_AMBIGUOUS_OR_INVALID");
    }

    private static LegacyTradeProductIdentityCommand command() {
        return new LegacyTradeProductIdentityCommand().setIdempotencyKey("product-identity-v1")
                .setIdentityRunId(IDENTITY_RUN).setSourceMigrationRunId(SOURCE_RUN)
                .setPolicyVersion(LegacyTradeProductIdentityServiceImpl.POLICY_VERSION)
                .setEvidenceRef("evidence:local-trade-product-identity")
                .setCorrelationId(CORRELATION).setOccurredAt(Instant.parse("2026-07-17T04:00:00Z"));
    }

    private static LegacyTradeProductIdentityItemSourceDO source(long itemId, long spuId, long skuId,
                                                                  int parentCount, boolean currentMatch) {
        Long currentSpu = currentMatch ? spuId : null;
        Long currentSkuSpu = currentMatch ? spuId : spuId + 1;
        return new LegacyTradeProductIdentityItemSourceDO().setTenantId(1L).setSourceMigrationRunId(SOURCE_RUN)
                .setCandidateId("4b000000-0000-4000-8001-" + String.format("%012d", itemId))
                .setItemEvidenceId("4b000000-0000-4000-8002-" + String.format("%012d", itemId))
                .setLegacyOrderId(10L).setLegacyOrderItemId(itemId).setLegacySpuId(spuId).setLegacySkuId(skuId)
                .setSourceItemEvidenceHash("a".repeat(64)).setDeleted(false).setOrderDeleted(false)
                .setSourceParentCardinality(parentCount).setCurrentSpuId(currentSpu).setCurrentSpuStatus(0)
                .setCurrentSpuDeleted(false).setCurrentSpuCreatedAt(NOW.minusDays(2))
                .setCurrentSpuUpdatedAt(NOW.minusDays(1)).setCurrentSkuId(skuId)
                .setCurrentSkuSpuId(currentSkuSpu).setCurrentSkuDeleted(false)
                .setCurrentSkuCreatedAt(NOW.minusDays(2)).setCurrentSkuUpdatedAt(NOW.minusDays(1))
                .setQualificationCount(0);
    }
}
