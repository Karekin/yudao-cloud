package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryMigrationQualificationServiceImplTest {

    private static final String RUN = "40000000-0000-4000-8000-000000000001";
    private static final String CANDIDATE = "40000000-0000-4000-8000-000000000002";
    private static final String QUALIFICATION = "40000000-0000-4000-8000-000000000003";
    private static final String BALANCE = "40000000-0000-4000-8000-000000000004";
    private static final String OWNER = "40000000-0000-4000-8000-000000000005";
    private static final String SKU = "40000000-0000-4000-8000-000000000006";
    private static final String WAREHOUSE = "40000000-0000-4000-8000-000000000007";
    private static final String LOCATION = "40000000-0000-4000-8000-000000000008";
    private static final String MAPPING = "40000000-0000-4000-8000-000000000009";
    private static final String CORRELATION = "40000000-0000-4000-8000-000000000010";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T07:00:00Z");
    private static final BigDecimal TEN = new BigDecimal("10.000000");
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InventoryMigrationStoreMapper migrationMapper = mock(InventoryMigrationStoreMapper.class);
    private final InventoryV3OperationMapper operationMapper = mock(InventoryV3OperationMapper.class);
    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryV3LedgerTransactionMapper transactionMapper = mock(InventoryV3LedgerTransactionMapper.class);
    private final InventoryV3LedgerEntryMapper entryMapper = mock(InventoryV3LedgerEntryMapper.class);
    private final InventoryLotMapper lotMapper = mock(InventoryLotMapper.class);
    private final MerchantOwnerValidationApi merchantApi = mock(MerchantOwnerValidationApi.class);
    private final CatalogSkuValidationApi catalogApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouseApi = mock(WarehouseReferenceValidationApi.class);
    private final WarehouseSourceMappingQueryApi mappingApi = mock(WarehouseSourceMappingQueryApi.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final InventoryMigrationQualificationServiceImpl service =
            new InventoryMigrationQualificationServiceImpl(migrationMapper, operationMapper, balanceMapper,
                    transactionMapper, entryMapper, lotMapper, merchantApi, catalogApi, warehouseApi, mappingApi, outbox);
    private final AtomicReference<InventoryMigrationOperationDO> migrationOperation = new AtomicReference<>();
    private final AtomicReference<InventoryV3OperationDO> openingOperation = new AtomicReference<>();
    private final AtomicReference<InventoryMigrationQualificationDO> savedQualification = new AtomicReference<>();
    private final List<AppendDomainEventCommand> events = new ArrayList<>();
    private InventoryBalanceDO sourceBalance;
    private InventoryMigrationCandidateDO candidate;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        sourceBalance = sourceBalance();
        candidate = candidate(sourceBalance);
        when(migrationMapper.insertOrResolveCommand(anyLong(), anyString(), nullable(String.class), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            InventoryMigrationOperationDO operation = new InventoryMigrationOperationDO();
            operation.setOperationId(101L);
            operation.setTenantId(1L);
            operation.setRequestHash(invocation.getArgument(4));
            operation.setAttemptToken(invocation.getArgument(5));
            operation.setStatus(0);
            migrationOperation.set(operation);
            return 1;
        });
        when(migrationMapper.selectLastInsertId()).thenReturn(101L);
        when(migrationMapper.selectOperationForUpdate(1L, 101L)).thenAnswer(i -> migrationOperation.get());
        when(migrationMapper.markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(migrationMapper.selectCandidateForUpdate(1L, CANDIDATE)).thenAnswer(i -> candidate);
        when(migrationMapper.selectLegacyBalanceForUpdate(1L, BALANCE)).thenAnswer(i -> sourceBalance);
        when(migrationMapper.selectInitialSourceFact(1L, BALANCE))
                .thenReturn(new InventoryLegacySourceFactDO().setBusinessType("MIGRATION_CANARY")
                        .setSourceEventId("source-canary-event"));
        when(migrationMapper.countActiveReservations(1L, BALANCE)).thenReturn(0);
        when(migrationMapper.sumActiveReservationQuantity(1L, BALANCE)).thenReturn(ZERO);
        when(mappingApi.resolveActive(any(), eq(OCCURRED_AT))).thenReturn(WarehouseSourceMappingView.builder()
                .mappingId(MAPPING).sourceSystem("CLOUDMOLD_INVENTORY_V1").sourceType("WAREHOUSE")
                .sourceId("migration-canary:cmig22").canonicalType("WAREHOUSE").canonicalId(WAREHOUSE)
                .warehouseId(WAREHOUSE).version(1L).build());
        when(migrationMapper.insertQualification(any())).thenAnswer(invocation -> {
            savedQualification.set(invocation.getArgument(0));
            return 1;
        });
        when(outbox.append(any())).thenAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void qualifiesOnlyExplicitCanaryWithExactSourceAndWarehouseMapping() {
        InventoryMigrationQualificationResult result = service.qualify(qualificationCommand());

        assertThat(result.getStatus()).isEqualTo("QUALIFIED");
        assertThat(result.getSourceOnHandQuantity()).isEqualByComparingTo(TEN);
        assertThat(result.getWarehouseSourceMappingId()).isEqualTo(MAPPING);
        assertThat(result.getResolvedBlockerCodes()).containsExactlyInAnyOrderElementsOf(
                JsonUtils.parseArray(candidate.getReasonCodes(), String.class));
        assertThat(savedQualification.get().getSourceReservedQuantity()).isEqualByComparingTo(ZERO);
        assertThat(savedQualification.get().getLotTrackingPolicy()).isEqualTo("NOT_TRACKED");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("inventory.migration.balance_qualified");
            assertThat(event.getSchemaVersion()).isEqualTo(1);
            assertThat(event.getPayload()).containsEntry("source_classification", "CONTROLLED_CANARY")
                    .containsEntry("qualification_status", "QUALIFIED")
                    .containsEntry("warehouse_source_mapping_id", MAPPING);
        });
    }

    @Test
    void refusesToQualifyExistingFixtureCorpus() {
        candidate.setSourceClassification("CONTROLLED_FIXTURE");
        assertThatThrownBy(() -> service.qualify(qualificationCommand()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CONTROLLED_CANARY");
        verify(migrationMapper, never()).insertQualification(any());
    }

    @Test
    void refusesQualificationWhenLegacySnapshotChanged() {
        sourceBalance.setVersion(2L);
        assertThatThrownBy(() -> service.qualify(qualificationCommand()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("changed after assessment");
        verify(migrationMapper, never()).insertQualification(any());
    }

    @Test
    void opensExactlyOneZeroV3BalanceAndResolvesBridge() {
        InventoryMigrationQualificationDO qualification = qualification();
        when(migrationMapper.selectQualificationForUpdate(1L, QUALIFICATION)).thenReturn(qualification);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), eq("MIGRATION_OPENING"),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            InventoryV3OperationDO operation = new InventoryV3OperationDO();
            operation.setOperationId(202L);
            operation.setTenantId(1L);
            operation.setRequestHash(invocation.getArgument(4));
            operation.setAttemptToken(invocation.getArgument(5));
            operation.setStatus(0);
            openingOperation.set(operation);
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(202L);
        when(operationMapper.selectForUpdate(202L, 1L)).thenAnswer(i -> openingOperation.get());
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyLong(), anyString(), any())).thenReturn(1);
        when(balanceMapper.insertOrResolve(anyString(), anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), nullable(String.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        InventoryV3BalanceDO target = targetBalance();
        when(balanceMapper.selectDimensionForUpdate(1L, "MERCHANT", OWNER, SKU, WAREHOUSE, LOCATION,
                null, "SELLABLE", "QUALIFIED")).thenReturn(target);
        when(balanceMapper.updateBalanceCas(eq(1L), eq(target.getBalanceId()), eq(0L), eq(TEN),
                eq(ZERO), eq(ZERO), any())).thenReturn(1);
        when(transactionMapper.insert(any(InventoryV3LedgerTransactionDO.class))).thenAnswer(invocation -> {
            InventoryV3LedgerTransactionDO transaction = invocation.getArgument(0);
            transaction.setLedgerTransactionId(303L);
            return 1;
        });
        when(entryMapper.insert(any(InventoryV3LedgerEntryDO.class))).thenReturn(1);
        when(migrationMapper.insertResolvedBridge(anyString(), eq(1L), eq(BALANCE), eq(target.getBalanceId()),
                anyString(), eq(TEN), eq(ZERO), eq(ZERO), eq(TEN), eq(ZERO), eq(ZERO), any())).thenReturn(1);
        when(migrationMapper.markQualificationMigrated(eq(1L), eq(QUALIFICATION), eq(202L),
                eq(target.getBalanceId()), eq(303L), anyString(), any())).thenReturn(1);

        InventoryMigrationOpeningResult result = service.migrate(openingCommand());

        assertThat(result.getOpeningOperationId()).isEqualTo(202L);
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getOnHandQuantity()).isEqualByComparingTo(TEN);
        assertThat(result.getReservedQuantity()).isEqualByComparingTo(ZERO);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("inventory.stock.changed");
            assertThat(event.getSchemaVersion()).isEqualTo(4);
            assertThat(event.getPayload()).containsEntry("movement_type", "MIGRATION_OPENING")
                    .containsEntry("opening_driver", "INVENTORY_MIGRATION_QUALIFICATION")
                    .containsEntry("migration_qualification_id", QUALIFICATION)
                    .containsEntry("delta_reserved_quantity", "0.000000");
        });
        verify(entryMapper).insert(argThat((InventoryV3LedgerEntryDO entry) -> entry.getDeltaOnHandQuantity().compareTo(TEN) == 0
                && entry.getDeltaReservedQuantity().signum() == 0
                && entry.getDeltaInTransitQuantity().signum() == 0));
    }

    @Test
    void refusesOpeningIntoAnExistingV3Balance() {
        InventoryMigrationQualificationDO qualification = qualification();
        when(migrationMapper.selectQualificationForUpdate(1L, QUALIFICATION)).thenReturn(qualification);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    InventoryV3OperationDO operation = new InventoryV3OperationDO();
                    operation.setOperationId(202L);
                    operation.setAttemptToken(invocation.getArgument(5));
                    operation.setStatus(0);
                    openingOperation.set(operation);
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(202L);
        when(operationMapper.selectForUpdate(202L, 1L)).thenAnswer(i -> openingOperation.get());
        when(balanceMapper.insertOrResolve(anyString(), anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), nullable(String.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        InventoryV3BalanceDO target = targetBalance();
        target.setVersion(1L);
        target.setOnHandQuantity(BigDecimal.ONE.setScale(6));
        when(balanceMapper.selectDimensionForUpdate(anyLong(), anyString(), anyString(), anyString(), anyString(),
                anyString(), nullable(String.class), anyString(), anyString())).thenReturn(target);

        assertThatThrownBy(() -> service.migrate(openingCommand()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("new zero v3 balance");
        verify(migrationMapper, never()).insertResolvedBridge(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replaysCompletedQualificationWithoutReadingSource() {
        InventoryMigrationQualificationResult stored = InventoryMigrationQualificationResult.builder()
                .operationId(101L).qualificationId(QUALIFICATION).migrationRunId(RUN).candidateId(CANDIDATE)
                .status("QUALIFIED").version(1L).duplicate(false).build();
        InventoryMigrationOperationDO existing = new InventoryMigrationOperationDO();
        existing.setOperationId(101L);
        existing.setTenantId(1L);
        existing.setStatus(10);
        existing.setResultJson(JsonUtils.toJsonString(stored));
        AtomicReference<String> firstHash = new AtomicReference<>();
        when(migrationMapper.insertOrResolveCommand(anyLong(), anyString(), nullable(String.class), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            firstHash.set(invocation.getArgument(4));
            existing.setRequestHash(firstHash.get());
            existing.setAttemptToken("different-attempt");
            migrationOperation.set(existing);
            return 1;
        });

        InventoryMigrationQualificationResult replay = service.qualify(qualificationCommand());
        assertThat(replay.isDuplicate()).isTrue();
        verify(migrationMapper, never()).selectCandidateForUpdate(anyLong(), anyString());
    }

    private InventoryMigrationQualificationCommand qualificationCommand() {
        return new InventoryMigrationQualificationCommand().setIdempotencyKey("cmig22:qualify")
                .setSourceEventId("qualify-source-event").setMigrationRunId(RUN).setCandidateId(CANDIDATE)
                .setExpectedSourceSnapshotHash(candidate.getLegacySnapshotHash()).setWarehouseId(WAREHOUSE)
                .setLocationId(LOCATION).setLotTrackingPolicy("NOT_TRACKED")
                .setPolicyVersion("controlled-canary-v1").setEvidenceRef("run:cmig22/qualification")
                .setCorrelationId(CORRELATION).setOccurredAt(OCCURRED_AT);
    }

    private InventoryMigrationOpeningCommand openingCommand() {
        return new InventoryMigrationOpeningCommand().setIdempotencyKey("cmig22:migrate")
                .setSourceEventId("migrate-source-event").setQualificationId(QUALIFICATION).setExpectedVersion(1L)
                .setExpectedSourceSnapshotHash(candidate.getLegacySnapshotHash())
                .setEvidenceRef("run:cmig22/opening").setCorrelationId(CORRELATION).setOccurredAt(OCCURRED_AT);
    }

    private InventoryBalanceDO sourceBalance() {
        return new InventoryBalanceDO().setBalanceId(BALANCE).setTenantId(1L).setOwnerId(OWNER)
                .setCanonicalSkuId(SKU).setWarehouseId("migration-canary:cmig22")
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PCS")
                .setOnHandQuantity(TEN).setReservedQuantity(ZERO).setInTransitQuantity(ZERO).setVersion(1L)
                .setUpdatedAt(LocalDateTime.ofInstant(OCCURRED_AT, ZoneOffset.UTC));
    }

    private InventoryMigrationCandidateDO candidate(InventoryBalanceDO balance) {
        InventoryLegacySourceFactDO fact = new InventoryLegacySourceFactDO().setBusinessType("MIGRATION_CANARY")
                .setSourceEventId("source-canary-event");
        String snapshot = InventoryMigrationAssessmentServiceImpl.snapshotHash(1L, balance, fact, 0, ZERO);
        List<String> blockers = List.of("CONTROLLED_NON_PRODUCTION_SOURCE", "LOCATION_UNRESOLVED",
                "LOT_POLICY_UNPROVEN", "OWNER_TYPE_MISSING", "TARGET_DIMENSION_UNRESOLVED",
                "WAREHOUSE_UNRESOLVED");
        return new InventoryMigrationCandidateDO().setCandidateId(CANDIDATE).setTenantId(1L)
                .setMigrationRunId(RUN).setLegacyBalanceId(BALANCE).setLegacyBalanceVersion(1L)
                .setLegacySnapshotHash(snapshot).setSourceSystem("CLOUDMOLD_INVENTORY_V1").setSourceType("BALANCE")
                .setSourceId(BALANCE).setSourceClassification("CONTROLLED_CANARY")
                .setSourceUpdatedAt(balance.getUpdatedAt()).setLegacyOwnerId(OWNER).setLegacyCanonicalSkuId(SKU)
                .setLegacyWarehouseId(balance.getWarehouseId()).setStockStatus("SELLABLE")
                .setQualityStatus("QUALIFIED").setBaseUomCode("PCS").setSourceOnHandQuantity(TEN)
                .setSourceReservedQuantity(ZERO).setSourceInTransitQuantity(ZERO)
                .setInitialBusinessType("MIGRATION_CANARY").setInitialSourceEventId("source-canary-event")
                .setActiveReservationCount(0).setActiveReservationQuantity(ZERO).setLotTrackingPolicy("UNRESOLVED")
                .setDecisionStatus("BLOCKED").setReasonCodes(JsonUtils.toJsonString(blockers))
                .setVerificationRef("run:cmig22/assessment").setVersion(1L)
                .setAssessedAt(balance.getUpdatedAt()).setCreatedAt(balance.getUpdatedAt()).setUpdatedAt(balance.getUpdatedAt());
    }

    private InventoryMigrationQualificationDO qualification() {
        return new InventoryMigrationQualificationDO().setQualificationId(QUALIFICATION).setTenantId(1L)
                .setMigrationRunId(RUN).setCandidateId(CANDIDATE).setQualificationOperationId(101L)
                .setSourceSystem("CLOUDMOLD_INVENTORY_V1").setSourceType("BALANCE").setSourceId(BALANCE)
                .setSourceSnapshotHash(candidate.getLegacySnapshotHash()).setSourceVersion(1L)
                .setSourceUpdatedAt(sourceBalance.getUpdatedAt()).setSourceOnHandQuantity(TEN)
                .setSourceReservedQuantity(ZERO).setSourceInTransitQuantity(ZERO).setOwnerType("MERCHANT")
                .setOwnerId(OWNER).setCanonicalSkuId(SKU).setWarehouseSourceMappingId(MAPPING)
                .setWarehouseId(WAREHOUSE).setLocationId(LOCATION).setLotTrackingPolicy("NOT_TRACKED")
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PCS")
                .setResolvedBlockerCodes(candidate.getReasonCodes()).setPolicyVersion("controlled-canary-v1")
                .setVerificationRef("run:cmig22/qualification").setStatus("QUALIFIED").setVersion(1L)
                .setQualifiedAt(sourceBalance.getUpdatedAt()).setCreatedAt(sourceBalance.getUpdatedAt())
                .setUpdatedAt(sourceBalance.getUpdatedAt());
    }

    private InventoryV3BalanceDO targetBalance() {
        InventoryV3BalanceDO target = new InventoryV3BalanceDO();
        target.setBalanceId("40000000-0000-4000-8000-000000000011");
        target.setTenantId(1L);
        target.setOwnerType("MERCHANT");
        target.setOwnerId(OWNER);
        target.setCanonicalSkuId(SKU);
        target.setWarehouseId(WAREHOUSE);
        target.setLocationId(LOCATION);
        target.setStockStatus("SELLABLE");
        target.setQualityStatus("QUALIFIED");
        target.setBaseUomCode("PCS");
        target.setOnHandQuantity(ZERO);
        target.setReservedQuantity(ZERO);
        target.setInTransitQuantity(ZERO);
        target.setVersion(0L);
        return target;
    }
}
