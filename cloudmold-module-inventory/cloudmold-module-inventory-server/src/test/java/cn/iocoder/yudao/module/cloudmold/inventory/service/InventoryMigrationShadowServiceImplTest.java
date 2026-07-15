package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationShadowApi.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationPilotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationShadowMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryMigrationShadowServiceImplTest {

    private static final String WINDOW = "60000000-0000-4000-8000-000000000001";
    private static final String BATCH = "60000000-0000-4000-8000-000000000002";
    private static final String RUN = "60000000-0000-4000-8000-000000000003";
    private static final String ITEM = "60000000-0000-4000-8000-000000000004";
    private static final String BALANCE = "60000000-0000-4000-8000-000000000005";
    private static final String HASH = "a".repeat(64);
    private static final String BASE_GTID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa:1";
    private static final String SOURCE_GTID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa:1-2";

    private final InventoryMigrationStoreMapper migrationMapper = mock(InventoryMigrationStoreMapper.class);
    private final InventoryMigrationPilotMapper pilotMapper = mock(InventoryMigrationPilotMapper.class);
    private final InventoryMigrationShadowMapper shadowMapper = mock(InventoryMigrationShadowMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final InventoryMigrationPilotProperties properties = enabledProperties();
    private final InventoryMigrationShadowServiceImpl service = new InventoryMigrationShadowServiceImpl(
            migrationMapper, pilotMapper, shadowMapper, outbox, properties);
    private final AtomicReference<InventoryMigrationOperationDO> operation = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(migrationMapper.insertOrResolveCommand(anyLong(), anyString(), nullable(String.class), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            InventoryMigrationOperationDO value = new InventoryMigrationOperationDO();
            value.setOperationId(901L);
            value.setAttemptToken(invocation.getArgument(5));
            value.setRequestHash(invocation.getArgument(4));
            value.setStatus(0);
            operation.set(value);
            return 1;
        });
        when(migrationMapper.selectLastInsertId()).thenReturn(901L);
        when(migrationMapper.selectOperationForUpdate(1L, 901L)).thenAnswer(invocation -> operation.get());
        when(migrationMapper.markOperationSucceeded(eq(1L), eq(901L), eq(RUN), anyString(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void defaultsKeepShadowDisabledBeforeAnyPersistence() {
        InventoryMigrationShadowServiceImpl disabled = new InventoryMigrationShadowServiceImpl(
                migrationMapper, pilotMapper, shadowMapper, outbox, new InventoryMigrationPilotProperties());

        assertThatThrownBy(() -> disabled.start(BATCH, startCommand(), 50L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");

        verifyNoInteractions(migrationMapper, pilotMapper, shadowMapper, outbox);
    }

    @Test
    void roundRejectsAnIncompleteObservationDenominatorBeforeSourceRead() {
        InventoryMigrationPilotItemDO item = admittedItem();
        stubOpenWindow(item);
        RecordShadowRoundCommand command = roundCommand(List.of());

        assertThatThrownBy(() -> service.recordRound(WINDOW, command, 50L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("complete pilot denominator");

        verify(migrationMapper, never()).selectLegacyBalanceForUpdate(anyLong(), anyString());
        verify(shadowMapper, never()).insertRound(any());
        verify(shadowMapper, never()).insertComparison(any());
    }

    @Test
    void uncoveredTargetPersistsTheWholeRoundAsUncomparableWithTruthfulEvents() {
        InventoryMigrationPilotItemDO item = admittedItem();
        stubOpenWindow(item);
        InventoryBalanceDO balance = currentBalance();
        when(migrationMapper.selectLegacyBalanceForUpdate(1L, BALANCE)).thenReturn(balance);
        when(migrationMapper.selectInitialSourceFact(1L, BALANCE)).thenReturn(null);
        when(migrationMapper.countActiveReservations(1L, BALANCE)).thenReturn(0);
        when(migrationMapper.sumActiveReservationQuantity(1L, BALANCE)).thenReturn(new BigDecimal("0.000000"));
        when(shadowMapper.selectCurrentGtidSet()).thenReturn(SOURCE_GTID);
        when(shadowMapper.gtidSubset(anyString(), anyString())).thenAnswer(invocation -> {
            String subset = invocation.getArgument(0);
            String superset = invocation.getArgument(1);
            if (Objects.equals(subset, superset)) return 1;
            if (BASE_GTID.equals(subset) && SOURCE_GTID.equals(superset)) return 1;
            return 0;
        });
        when(shadowMapper.insertRound(any())).thenReturn(1);
        when(shadowMapper.insertComparison(any())).thenReturn(1);
        when(shadowMapper.advanceWindow(anyLong(), anyString(), anyLong(), anyInt(), anyInt(), anyInt(),
                anyString(), anyString(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(1);

        ShadowTargetObservation observation = availableObservation(item);
        ShadowWindowResult result = service.recordRound(WINDOW, roundCommand(List.of(observation)), 50L);

        assertThat(result.getObservedRoundCount()).isEqualTo(1);
        assertThat(result.getTotalMatchCount()).isZero();
        assertThat(result.getTotalUncomparableCount()).isEqualTo(1);
        verify(shadowMapper).insertComparison(argThat(value ->
                "UNCOMPARABLE".equals(value.getComparisonResult())
                        && value.getReasonCodes().contains("TARGET_WATERMARK_NOT_COVERED")
                        && Boolean.FALSE.equals(value.getComparable())));
        ArgumentCaptor<AppendDomainEventCommand> events = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outbox, times(3)).append(events.capture());
        AppendDomainEventCommand roundEvent = events.getAllValues().stream()
                .filter(value -> InventoryMigrationShadowServiceImpl.ROUND_EVENT.equals(value.getEventType()))
                .findFirst().orElseThrow();
        assertThat(roundEvent.getPayload().get("target_contains_source")).isEqualTo(false);
        assertThat(roundEvent.getPayload().get("round_result")).isEqualTo("UNCOMPARABLE");
    }

    @Test
    void finalizeUsesUncomparablePriorityAndKeepsExecutionClosed() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        InventoryMigrationShadowWindowDO window = openWindow(admittedItem())
                .setStatus("OBSERVING").setVerificationResult("PENDING").setVersion(4L).setAggregateVersion(2L)
                .setObservedRoundCount(3).setTotalMatchCount(1).setTotalDifferentCount(1)
                .setTotalUncomparableCount(1).setStartedAt(now.minusHours(2)).setLastObservedAt(now.minusSeconds(10))
                .setMinimumDurationSeconds(3600).setMaxRoundIntervalSeconds(60);
        when(shadowMapper.selectWindowForUpdate(1L, WINDOW)).thenReturn(window);
        when(pilotMapper.selectBatchForUpdate(1L, BATCH)).thenReturn(admittedBatch());
        when(pilotMapper.selectApprovalsForUpdate(1L, BATCH)).thenReturn(approvals());
        List<InventoryMigrationShadowRoundDO> rounds = List.of(round(1), round(2), round(3));
        when(shadowMapper.selectRounds(1L, WINDOW)).thenReturn(rounds);
        when(shadowMapper.finalizeWindow(eq(1L), eq(WINDOW), eq(4L), eq("UNCOMPARABLE"), eq(60L), any()))
                .thenReturn(1);

        ShadowWindowResult result = service.finalizeWindow(WINDOW, finalizeCommand(), 60L);

        assertThat(result.getStatus()).isEqualTo("VERIFIED");
        assertThat(result.getVerificationResult()).isEqualTo("UNCOMPARABLE");
        assertThat(result.isShadowMatchVerified()).isFalse();
        assertThat(result.isExecutionAvailable()).isFalse();
        assertThat(result.isCutoverReady()).isFalse();
        verify(outbox).append(argThat((AppendDomainEventCommand event) ->
                InventoryMigrationShadowServiceImpl.WINDOW_EVENT.equals(event.getEventType())
                        && "UNCOMPARABLE".equals(event.getPayload().get("verification_result"))
                        && Boolean.FALSE.equals(event.getPayload().get("execution_available"))));
    }

    private void stubOpenWindow(InventoryMigrationPilotItemDO item) {
        when(shadowMapper.selectWindowForUpdate(1L, WINDOW)).thenReturn(openWindow(item));
        when(pilotMapper.selectBatchForUpdate(1L, BATCH)).thenReturn(admittedBatch());
        when(pilotMapper.selectApprovalsForUpdate(1L, BATCH)).thenReturn(approvals());
        when(pilotMapper.selectItemsForUpdate(1L, BATCH)).thenReturn(List.of(item));
        when(shadowMapper.selectRounds(1L, WINDOW)).thenReturn(List.of());
    }

    private static InventoryMigrationPilotProperties enabledProperties() {
        InventoryMigrationPilotProperties value = new InventoryMigrationPilotProperties();
        value.setEnvironment("PRODUCTION");
        value.setEnvironmentFingerprint("prod-cn:fixture");
        value.setShadowEnabled(true);
        value.setShadowRequiredRoundCount(3);
        value.setShadowMinimumDurationSeconds(3600);
        value.setShadowMaxRoundIntervalSeconds(60);
        value.setShadowMaxWatermarkLagSeconds(300);
        value.setTrustedShadowCollectorIds(new ArrayList<>(List.of(50L)));
        value.setTrustedShadowVerifierIds(new ArrayList<>(List.of(60L)));
        return value;
    }

    private static InventoryMigrationShadowWindowDO openWindow(InventoryMigrationPilotItemDO item) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new InventoryMigrationShadowWindowDO().setWindowId(WINDOW).setTenantId(1L).setBatchId(BATCH)
                .setMigrationRunId(RUN).setEnvironment("PRODUCTION").setEnvironmentFingerprint("prod-cn:fixture")
                .setManifestHash(HASH).setExpectedItemSetHash(
                        InventoryMigrationShadowServiceImpl.expectedItemSetHash(List.of(item)))
                .setAdmissionCheckpointId(UUID.randomUUID().toString()).setAdmissionCheckpointHash(HASH)
                .setAdmissionEventId(UUID.randomUUID().toString()).setAdmissionBatchVersion(4L)
                .setPolicyVersion("shadow-v1").setPolicyHash(HASH)
                .setTargetProjectionKind(InventoryMigrationShadowServiceImpl.TARGET_PROJECTION)
                .setTargetProjectionVersion(1).setTargetMaterialized(false).setExpectedItemCount(1)
                .setRequiredRoundCount(3).setMinimumDurationSeconds(3600).setMaxRoundIntervalSeconds(60)
                .setMaxWatermarkLagSeconds(300).setCollectorId(50L).setStatus("OPEN")
                .setVerificationResult("PENDING").setVersion(1L).setAggregateVersion(1L)
                .setObservedRoundCount(0).setTotalMatchCount(0).setTotalDifferentCount(0)
                .setTotalUncomparableCount(0).setStartedAt(now.minusSeconds(20)).setCreatedAt(now).setUpdatedAt(now);
    }

    private static InventoryMigrationPilotBatchDO admittedBatch() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new InventoryMigrationPilotBatchDO().setBatchId(BATCH).setTenantId(1L).setMigrationRunId(RUN)
                .setEnvironment("PRODUCTION").setSourceClassification("PRODUCTION_HISTORY")
                .setManifestHash(HASH).setPolicyHash(HASH).setPolicyVersion("pilot-v1")
                .setExpectedItemCount(1).setRequesterId(10L).setExecutorId(40L).setApprovalCount(2)
                .setStatus("ADMISSION_PASSED").setVersion(4L).setSourceWatermarkKind("MYSQL_GTID_SET")
                .setSourceWatermarkValue(BASE_GTID).setSourceWatermarkCapturedAt(now.minusSeconds(60))
                .setTargetWatermarkKind("MYSQL_GTID_SET").setTargetWatermarkValue(BASE_GTID)
                .setTargetWatermarkAppliedAt(now.minusSeconds(50));
    }

    private static InventoryMigrationPilotItemDO admittedItem() {
        return new InventoryMigrationPilotItemDO().setItemId(ITEM).setTenantId(1L).setBatchId(BATCH).setOrdinal(1)
                .setLegacyBalanceId(BALANCE).setItemScopeHash(HASH).setOwnerType("MERCHANT").setOwnerId("merchant-1")
                .setCanonicalSkuId("sku-1").setWarehouseId("warehouse-1").setLocationId("location-1")
                .setLotTrackingPolicy("NOT_TRACKED").setStockStatus("SELLABLE").setQualityStatus("QUALIFIED")
                .setBaseUomCode("EA").setQuantityEvidenceRef("evidence:source-quantity")
                .setStatus("ADMISSION_PASSED").setVersion(3L);
    }

    private static InventoryBalanceDO currentBalance() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new InventoryBalanceDO().setBalanceId(BALANCE).setTenantId(1L).setOwnerId("merchant-1")
                .setCanonicalSkuId("sku-1").setWarehouseId("warehouse-1").setStockStatus("SELLABLE")
                .setQualityStatus("QUALIFIED").setBaseUomCode("EA")
                .setOnHandQuantity(new BigDecimal("10.000000")).setReservedQuantity(new BigDecimal("0.000000"))
                .setInTransitQuantity(new BigDecimal("0.000000")).setVersion(2L).setUpdatedAt(now.minusSeconds(5));
    }

    private static List<InventoryMigrationPilotApprovalDO> approvals() {
        return List.of(new InventoryMigrationPilotApprovalDO().setApprovalRole("DATA_OWNER").setApproverId(20L),
                new InventoryMigrationPilotApprovalDO().setApprovalRole("CHANGE_MANAGER").setApproverId(30L));
    }

    private static StartShadowWindowCommand startCommand() {
        return new StartShadowWindowCommand().setIdempotencyKey("shadow:start").setExpectedBatchVersion(4L)
                .setEvidenceRef("ticket:shadow-start").setCorrelationId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now());
    }

    private static RecordShadowRoundCommand roundCommand(List<ShadowTargetObservation> observations) {
        Instant now = Instant.now();
        return new RecordShadowRoundCommand().setIdempotencyKey("shadow:round:1").setExpectedWindowVersion(1L)
                .setObservedAt(now).setSourceWatermarkKind("MYSQL_GTID_SET").setSourceWatermarkValue(SOURCE_GTID)
                .setSourceWatermarkCapturedAt(now.minusSeconds(10)).setTargetWatermarkKind("MYSQL_GTID_SET")
                .setTargetWatermarkValue(BASE_GTID).setTargetWatermarkAppliedAt(now.minusSeconds(5))
                .setWatermarkValidationEvidenceRef("evidence:gtid-round-1").setEvidenceRef("evidence:round-1")
                .setObservations(observations).setCorrelationId(UUID.randomUUID().toString()).setOccurredAt(now);
    }

    private static ShadowTargetObservation availableObservation(InventoryMigrationPilotItemDO item) {
        ShadowTargetObservation value = new ShadowTargetObservation().setPilotItemId(ITEM)
                .setExpectedItemScopeHash(HASH).setAvailable(true).setTargetRecordVersion(2L)
                .setTargetCanonicalGrainHash(InventoryMigrationShadowServiceImpl.canonicalGrainHash(item))
                .setTargetOnHandQuantity(new BigDecimal("10.000000"))
                .setTargetReservedQuantity(new BigDecimal("0.000000"))
                .setTargetInTransitQuantity(new BigDecimal("0.000000")).setTargetEvidenceRef("evidence:target-1");
        value.setTargetProjectionHash(InventoryMigrationShadowServiceImpl.targetSnapshotHash(value));
        return value;
    }

    private static FinalizeShadowWindowCommand finalizeCommand() {
        return new FinalizeShadowWindowCommand().setIdempotencyKey("shadow:finalize")
                .setExpectedWindowVersion(4L).setEvidenceRef("ticket:shadow-finalize")
                .setCorrelationId(UUID.randomUUID().toString()).setOccurredAt(Instant.now());
    }

    private static InventoryMigrationShadowRoundDO round(int number) {
        return new InventoryMigrationShadowRoundDO().setRoundId(UUID.randomUUID().toString()).setRoundNumber(number)
                .setDenominatorHash(HASH).setExpectedItemCount(1).setMatchCount(number == 1 ? 1 : 0)
                .setDifferentCount(number == 2 ? 1 : 0).setUncomparableCount(number == 3 ? 1 : 0)
                .setPreviousSourceWatermarkHash(HASH).setSourceWatermarkValue(SOURCE_GTID)
                .setSourceWatermarkHash(HASH).setSourceMonotonic(true).setPreviousTargetWatermarkHash(HASH)
                .setTargetWatermarkValue(BASE_GTID).setTargetWatermarkHash(HASH).setTargetMonotonic(true)
                .setTargetContainsSource(number != 3).setWatermarkValidator("MYSQL_GTID_SET_CONTAINS_V1")
                .setWatermarkLagSeconds(5).setRoundGapSeconds(10).setObservedAt(LocalDateTime.now(ZoneOffset.UTC));
    }
}
