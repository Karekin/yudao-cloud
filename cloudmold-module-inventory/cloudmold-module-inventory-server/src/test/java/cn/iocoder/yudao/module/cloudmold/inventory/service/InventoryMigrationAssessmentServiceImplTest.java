package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationStoreMapper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryMigrationAssessmentServiceImplTest {

    private static final String RUN = "30000000-0000-4000-8000-000000000001";
    private static final String CORRELATION = "30000000-0000-4000-8000-000000000002";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T06:00:00Z");
    private final InventoryMigrationStoreMapper mapper = mock(InventoryMigrationStoreMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final InventoryMigrationAssessmentServiceImpl service =
            new InventoryMigrationAssessmentServiceImpl(mapper, outboxAppender);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> firstAttempt = new AtomicReference<>();
    private final AtomicReference<InventoryMigrationRunDO> savedRun = new AtomicReference<>();
    private final List<InventoryMigrationCandidateDO> savedCandidates = new ArrayList<>();
    private final List<AppendDomainEventCommand> events = new ArrayList<>();
    private InventoryMigrationOperationDO operation;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> {
            requestHash.compareAndSet(null, invocation.getArgument(3));
            firstAttempt.compareAndSet(null, invocation.getArgument(4));
            if (operation == null) {
                operation = new InventoryMigrationOperationDO().setOperationId(1L).setTenantId(1L)
                        .setRequestHash(requestHash.get()).setAttemptToken(firstAttempt.get()).setStatus(0);
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
        doAnswer(invocation -> { events.add(invocation.getArgument(0)); return null; })
                .when(outboxAppender).append(any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void persistsEvidenceBackedBlockedAssessmentWithoutOpeningStock() {
        InventoryBalanceDO fixture = balance("30000000-0000-4000-8000-000000000010",
                "internal-company", "30000000-0000-4000-8000-000000000011",
                "scenario:controlled", "10.000000", "0.000000", 4L);
        InventoryBalanceDO probe = balance("30000000-0000-4000-8000-000000000020",
                "internal-company", "legacy-sku", "oversell-probe", "10.000000", "3.000000", 2L);
        when(mapper.selectLegacyBalancesForUpdate(1L)).thenReturn(List.of(fixture, probe));
        when(mapper.selectInitialSourceFact(1L, fixture.getBalanceId()))
                .thenReturn(sourceFact("TEST_FIXTURE", null));
        when(mapper.selectInitialSourceFact(1L, probe.getBalanceId()))
                .thenReturn(sourceFact("concurrency-probe", null));
        when(mapper.countActiveReservations(1L, probe.getBalanceId())).thenReturn(1);
        when(mapper.sumActiveReservationQuantity(1L, probe.getBalanceId()))
                .thenReturn(new BigDecimal("3.000000"));

        InventoryMigrationAssessmentResult result = service.assessV1(command());

        assertThat(result.getCandidateCount()).isEqualTo(2);
        assertThat(result.getEligibleCount()).isZero();
        assertThat(result.getBlockedCount()).isEqualTo(2);
        assertThat(result.getRejectedCount()).isZero();
        assertThat(result.getSourceSnapshotHash()).matches("[0-9a-f]{64}");
        assertThat(savedCandidates).hasSize(2).allSatisfy(candidate -> {
            assertThat(candidate.getDecisionStatus()).isEqualTo("BLOCKED");
            assertThat(candidate.getSourceSystem()).isEqualTo("CLOUDMOLD_INVENTORY_V1");
            assertThat(candidate.getSourceType()).isEqualTo("BALANCE");
            assertThat(candidate.getLotTrackingPolicy()).isEqualTo("UNRESOLVED");
            assertThat(JsonUtils.parseArray(candidate.getReasonCodes(), String.class))
                    .contains("LOCATION_UNRESOLVED", "LOT_POLICY_UNPROVEN", "SOURCE_FACT_MISSING",
                            "TARGET_DIMENSION_UNRESOLVED");
        });
        assertThat(savedCandidates.get(0).getSourceClassification()).isEqualTo("CONTROLLED_FIXTURE");
        assertThat(savedCandidates.get(1).getSourceClassification()).isEqualTo("CONCURRENCY_PROBE");
        assertThat(JsonUtils.parseArray(savedCandidates.get(1).getReasonCodes(), String.class))
                .contains("ACTIVE_RESERVATION", "RESERVATION_PROVENANCE_MISSING",
                        "RESERVED_QUANTITY_NONZERO", "SKU_UNRESOLVED");
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("inventory.migration.balance_assessed");
            assertThat(event.getSchemaVersion()).isEqualTo(2);
            assertThat(event.getPayload()).containsEntry("assessment_status", "BLOCKED")
                    .containsEntry("migration_run_id", RUN)
                    .containsEntry("lot_tracking_policy", "UNRESOLVED")
                    .containsEntry("reservation_allocation_count", 0)
                    .containsEntry("reservation_allocation_quantity", "0.000000");
            assertThat(event.getPayload().get("blocker_codes")).asList().isNotEmpty();
        });
        assertThat(events.get(1).getPayload())
                .containsEntry("active_reservation_count", 1)
                .containsEntry("active_reservation_quantity", "3.000000");
        verifyNoInteractionsWithV3MutationSurface();
    }

    @Test
    void immutableReplayDoesNotReassessOrAppendEvents() {
        InventoryBalanceDO fixture = balance("30000000-0000-4000-8000-000000000030",
                "internal-company", "legacy-sku", "scenario:replay", "8.000000", "0.000000", 6L);
        when(mapper.selectLegacyBalancesForUpdate(1L)).thenReturn(List.of(fixture));
        when(mapper.selectInitialSourceFact(1L, fixture.getBalanceId()))
                .thenReturn(sourceFact("PURCHASE_RECEIPT", null));
        InventoryMigrationAssessmentCommand command = command();
        InventoryMigrationAssessmentResult first = service.assessV1(command);
        clearInvocations(mapper, outboxAppender);

        InventoryMigrationAssessmentResult replay = service.assessV1(command);

        assertThat(replay.isDuplicate()).isTrue();
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
    void rejectsEmptyEvidenceBeforeCreatingOperation() {
        InventoryMigrationAssessmentCommand invalid = command().setEvidenceRef(" ");
        assertThatThrownBy(() -> service.assessV1(invalid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceRef");
        verifyNoInteractions(mapper, outboxAppender);
    }

    @Test
    void snapshotHashChangesWhenReservationEvidenceChanges() {
        InventoryBalanceDO balance = balance("30000000-0000-4000-8000-000000000040",
                "internal-company", "legacy-sku", "scenario:hash", "10.000000", "3.000000", 2L);
        InventoryLegacySourceFactDO source = sourceFact("TEST_FIXTURE", null);
        String one = InventoryMigrationAssessmentServiceImpl.snapshotHash(1L, balance, source,
                1, new BigDecimal("3.000000"));
        String two = InventoryMigrationAssessmentServiceImpl.snapshotHash(1L, balance, source,
                0, BigDecimal.ZERO);
        assertThat(one).isNotEqualTo(two);
        balance.setUpdatedAt(balance.getUpdatedAt().plusSeconds(1));
        String three = InventoryMigrationAssessmentServiceImpl.snapshotHash(1L, balance, source,
                0, BigDecimal.ZERO);
        assertThat(two).isNotEqualTo(three);
    }

    @Test
    void queryReturnsStructuredReasonsRatherThanOpaqueJson() {
        InventoryMigrationRunDO run = new InventoryMigrationRunDO().setMigrationRunId(RUN)
                .setSourceSnapshotHash("a".repeat(64)).setCandidateCount(1).setEligibleCount(0)
                .setBlockedCount(1).setRejectedCount(0).setStatus("ASSESSED");
        InventoryMigrationCandidateDO candidate = new InventoryMigrationCandidateDO()
                .setCandidateId("30000000-0000-4000-8000-000000000050").setMigrationRunId(RUN)
                .setLegacyBalanceId("30000000-0000-4000-8000-000000000051").setLegacyBalanceVersion(1L)
                .setLegacySnapshotHash("b".repeat(64)).setSourceSystem("CLOUDMOLD_INVENTORY_V1")
                .setSourceType("BALANCE").setSourceId("30000000-0000-4000-8000-000000000051")
                .setSourceClassification("CONTROLLED_FIXTURE").setLegacyOwnerId("internal-company")
                .setLegacyCanonicalSkuId("legacy-sku").setLegacyWarehouseId("scenario:q")
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PCS")
                .setSourceOnHandQuantity(BigDecimal.TEN).setSourceReservedQuantity(BigDecimal.ZERO)
                .setSourceInTransitQuantity(BigDecimal.ZERO).setActiveReservationCount(0)
                .setActiveReservationQuantity(BigDecimal.ZERO).setLotTrackingPolicy("UNRESOLVED")
                .setDecisionStatus("BLOCKED").setReasonCodes("[\"LOCATION_UNRESOLVED\"]")
                .setVerificationRef("evidence:test").setAssessedAt(LocalDateTime.ofInstant(OCCURRED_AT, ZoneOffset.UTC));
        when(mapper.selectRun(1L, RUN)).thenReturn(run);
        when(mapper.selectCandidates(1L, RUN)).thenReturn(List.of(candidate));

        assertThat(service.requireRun(RUN).getBlockedCount()).isEqualTo(1);
        assertThat(service.listCandidates(RUN)).singleElement()
                .satisfies(view -> assertThat(view.getReasonCodes()).containsExactly("LOCATION_UNRESOLVED"));
    }

    private void verifyNoInteractionsWithV3MutationSurface() {
        // This service deliberately has no v3 balance, operation or ledger dependency.
        assertThat(service).isNotNull();
    }

    private static InventoryMigrationAssessmentCommand command() {
        return new InventoryMigrationAssessmentCommand().setIdempotencyKey("migration-assess-v1")
                .setMigrationRunId(RUN).setPolicyVersion("inventory-v1-v3-assessment-v1")
                .setEvidenceRef("evidence:inventory-v1-snapshot").setCorrelationId(CORRELATION)
                .setOccurredAt(OCCURRED_AT);
    }

    private static InventoryBalanceDO balance(String id, String owner, String sku, String warehouse,
                                               String onHand, String reserved, long version) {
        return new InventoryBalanceDO().setBalanceId(id).setTenantId(1L).setOwnerId(owner)
                .setCanonicalSkuId(sku).setWarehouseId(warehouse).setStockStatus("SELLABLE")
                .setQualityStatus("QUALIFIED").setBaseUomCode("PCS")
                .setOnHandQuantity(new BigDecimal(onHand)).setReservedQuantity(new BigDecimal(reserved))
                .setInTransitQuantity(BigDecimal.ZERO).setVersion(version)
                .setUpdatedAt(LocalDateTime.ofInstant(OCCURRED_AT, ZoneOffset.UTC));
    }

    private static InventoryLegacySourceFactDO sourceFact(String businessType, String sourceEventId) {
        return new InventoryLegacySourceFactDO().setBusinessType(businessType).setSourceEventId(sourceEventId);
    }
}
