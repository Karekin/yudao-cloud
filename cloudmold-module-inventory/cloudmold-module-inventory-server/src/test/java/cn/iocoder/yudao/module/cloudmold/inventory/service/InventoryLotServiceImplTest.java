package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotStoreMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryLotServiceImplTest {

    private static final String OWNER = "20000000-0000-4000-8000-000000000001";
    private static final String SKU = "20000000-0000-4000-8000-000000000002";
    private static final String BALANCE = "20000000-0000-4000-8000-000000000003";
    private static final Instant T0 = Instant.parse("2026-07-15T05:00:00Z");
    private Harness harness;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(9L);
        harness = new Harness();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void registersLotAndReplaysImmutableFirstResult() {
        InventoryLotCommand command = register("lot-register");
        InventoryLotResult first = harness.execute(command);

        assertThat(first.getLotStatus()).isEqualTo("ACTIVE");
        assertThat(first.getLotVersion()).isEqualTo(1L);
        assertThat(harness.events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("inventory.lot.lifecycle.changed");
            assertThat(event.getAggregateType()).isEqualTo("inventory_lot");
            assertThat(event.getPayload()).containsEntry("change_type", "CREATED")
                    .containsEntry("lot_code", "LOT-20260715-A")
                    .containsEntry("manufactured_on", "2026-07-01")
                    .containsEntry("expires_on", "2027-07-01")
                    .containsEntry("received_at", "2026-07-15T05:00:00Z");
        });

        InventoryLotResult replay = harness.execute(command);
        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getLotId()).isEqualTo(first.getLotId());
        assertThat(harness.events).hasSize(1);
        verify(harness.merchantOwnerValidationApi, times(1)).requireActiveMerchant(OWNER);
        verify(harness.catalogSkuValidationApi, times(1)).requireActiveSku(SKU);
    }

    @Test
    void linksEndsAndHistoricallyResolvesQualifiedSourceMapping() {
        InventoryLotResult registered = harness.execute(register("source-register"));
        InventoryLotCommand link = base(InventoryLotOperation.LINK_SOURCE, "source-link")
                .setLotId(registered.getLotId()).setExpectedLotVersion(1L)
                .setMappedSourceSystem("cloudmold_erp_operator").setMappedSourceType("controlled_lot")
                .setMappedSourceId("receipt-20:batch-A").setValidFrom(T0)
                .setVerificationRef("evidence:receipt-20-batch-A");
        InventoryLotResult linked = harness.execute(link);
        assertThat(harness.events.get(1).getPayload())
                .containsEntry("valid_from", "2026-07-15T05:00:00Z")
                .containsEntry("valid_to", null);

        InventoryLotView current = harness.service.requireBySource("CLOUDMOLD_ERP_OPERATOR", "CONTROLLED_LOT",
                "receipt-20:batch-A", T0.plusSeconds(10), T0.plusSeconds(10));
        assertThat(current.getLotId()).isEqualTo(registered.getLotId());
        assertThat(current.getMappingStatus()).isEqualTo("ACTIVE");

        InventoryLotCommand end = base(InventoryLotOperation.END_SOURCE, "source-end")
                .setMappingId(linked.getMappingId()).setExpectedMappingVersion(1L).setValidTo(T0.plusSeconds(60));
        InventoryLotResult ended = harness.execute(end);
        assertThat(ended.getMappingStatus()).isEqualTo("ENDED");
        assertThat(ended.getMappingVersion()).isEqualTo(2L);
        assertThat(harness.service.requireBySource("CLOUDMOLD_ERP_OPERATOR", "CONTROLLED_LOT",
                "receipt-20:batch-A", T0.plusSeconds(30), T0.plusSeconds(90)).getMappingStatus()).isEqualTo("ENDED");
        assertThatThrownBy(() -> harness.service.requireBySource("CLOUDMOLD_ERP_OPERATOR", "CONTROLLED_LOT",
                "receipt-20:batch-A", T0.plusSeconds(61), T0.plusSeconds(90)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one");
    }

    @Test
    void recallFencesAllocationButPreservesPhysicalAndUnreservedQuantities() {
        InventoryLotResult registered = harness.execute(register("recall-register"));
        harness.availability = List.of(new InventoryV3AvailabilityDO().setBalanceId(BALANCE)
                .setOwnerType("MERCHANT").setOwnerId(OWNER).setCanonicalSkuId(SKU)
                .setWarehouseId("warehouse-1").setLocationId("location-1").setLotId(registered.getLotId())
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("EA")
                .setOnHandQuantity(new BigDecimal("10.000000")).setReservedQuantity(new BigDecimal("2.000000"))
                .setInTransitQuantity(BigDecimal.ZERO).setAggregateVersion(2L));
        InventoryLotCommand recall = base(InventoryLotOperation.RECALL, "recall-lot")
                .setLotId(registered.getLotId()).setExpectedLotVersion(1L)
                .setRecallReference("ticket:product-recall-20260715");
        InventoryLotResult recalled = harness.execute(recall);

        assertThat(recalled.getLotStatus()).isEqualTo("RECALLED");
        InventoryV3AvailabilityView row = harness.service.listByLot(registered.getLotId(), T0).get(0);
        assertThat(row.getUnreservedQuantity()).isEqualByComparingTo("8.000000");
        assertThat(row.getAllocatableQuantity()).isEqualByComparingTo("0.000000");
        assertThat(row.getAllocationEligibility()).isEqualTo("LOT_RECALLED");
        assertThat(harness.events.get(1).getPayload())
                .containsEntry("recall_reference", "ticket:product-recall-20260715");
    }

    @Test
    void closeRejectsAnyRemainingQuantityBeforeChangingStatus() {
        InventoryLotResult registered = harness.execute(register("close-register"));
        when(harness.mapper.countNonZeroBalances(9L, registered.getLotId())).thenReturn(1L);
        InventoryLotCommand close = base(InventoryLotOperation.CLOSE, "close-lot")
                .setLotId(registered.getLotId()).setExpectedLotVersion(1L);

        assertThatThrownBy(() -> harness.execute(close)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity remains");
        assertThat(harness.lot.getStatus()).isEqualTo("ACTIVE");
        verify(harness.mapper).lockBalanceIds(9L, registered.getLotId());
        verify(harness.mapper).lockActiveAllocationIds(9L, registered.getLotId());
    }

    @Test
    void rejectsInvalidDatesAndFabricatedEvidence() {
        InventoryLotCommand invalidDates = register("bad-dates")
                .setManufacturedOn(LocalDate.of(2026, 7, 16)).setExpiresOn(LocalDate.of(2026, 7, 15));
        assertThatThrownBy(() -> harness.execute(invalidDates)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresOn");

        InventoryLotCommand invalidEvidence = register("bad-evidence").setEvidenceRef("raw-secret-or-url");
        assertThatThrownBy(() -> harness.execute(invalidEvidence)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opaque evidence");
        verifyNoInteractions(harness.outboxAppender);
    }

    private static InventoryLotCommand register(String key) {
        return base(InventoryLotOperation.REGISTER, key).setOwnerType("merchant").setOwnerId(OWNER)
                .setCanonicalSkuId(SKU).setLotCode("LOT-20260715-A")
                .setManufacturedOn(LocalDate.of(2026, 7, 1)).setExpiresOn(LocalDate.of(2027, 7, 1))
                .setReceivedAt(T0);
    }

    private static InventoryLotCommand base(InventoryLotOperation operation, String key) {
        return new InventoryLotCommand().setOperation(operation).setIdempotencyKey(key).setRunId("lot-run")
                .setReasonCode("CONTROLLED_TEST").setEvidenceRef("evidence:" + key)
                .setTraceId("trace-" + key).setOccurredAt(T0);
    }

    private static final class Harness {
        final InventoryLotStoreMapper mapper = mock(InventoryLotStoreMapper.class);
        final MerchantOwnerValidationApi merchantOwnerValidationApi = mock(MerchantOwnerValidationApi.class);
        final CatalogSkuValidationApi catalogSkuValidationApi = mock(CatalogSkuValidationApi.class);
        final OutboxAppender outboxAppender = mock(OutboxAppender.class);
        final InventoryLotServiceImpl service = new InventoryLotServiceImpl(mapper, merchantOwnerValidationApi,
                catalogSkuValidationApi, outboxAppender);
        final AtomicLong sequence = new AtomicLong();
        final ThreadLocal<Long> lastOperation = new ThreadLocal<>();
        final Map<String, Long> operationByKey = new HashMap<>();
        final Map<Long, InventoryLotOperationDO> operations = new HashMap<>();
        final List<AppendDomainEventCommand> events = new ArrayList<>();
        InventoryLotDO lot;
        InventoryLotSourceMappingDO mapping;
        List<InventoryV3AvailabilityDO> availability = List.of();

        Harness() {
            stubOperations();
            doAnswer(i -> { lot = i.getArgument(0); return 1; }).when(mapper).insertLot(any());
            when(mapper.selectLot(eq(9L), anyString())).thenAnswer(i -> lot != null
                    && lot.getLotId().equals(i.getArgument(1)) ? lot : null);
            when(mapper.selectLotForUpdate(eq(9L), anyString())).thenAnswer(i -> lot != null
                    && lot.getLotId().equals(i.getArgument(1)) ? lot : null);
            when(mapper.updateLotStatus(anyLong(), anyString(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(1);
            when(mapper.lockBalanceIds(anyLong(), anyString())).thenReturn(List.of());
            when(mapper.lockActiveAllocationIds(anyLong(), anyString())).thenReturn(List.of());
            when(mapper.countNonZeroBalances(anyLong(), anyString())).thenReturn(0L);
            when(mapper.countActiveAllocations(anyLong(), anyString())).thenReturn(0L);
            doAnswer(i -> { mapping = i.getArgument(0); return 1; }).when(mapper).insertMapping(any());
            when(mapper.selectMappingForUpdate(eq(9L), anyString())).thenAnswer(i -> mapping != null
                    && mapping.getMappingId().equals(i.getArgument(1)) ? mapping : null);
            when(mapper.selectOverlappingMappingsForUpdate(anyLong(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(List.of());
            when(mapper.endMapping(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(1);
            when(mapper.selectEffectiveMappings(anyLong(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(i -> {
                        if (mapping == null || !mapping.getSourceSystem().equals(i.getArgument(1))
                                || !mapping.getSourceType().equals(i.getArgument(2))
                                || !mapping.getSourceId().equals(i.getArgument(3))) return List.of();
                        LocalDateTime at = i.getArgument(4);
                        return !at.isBefore(mapping.getValidFrom())
                                && (mapping.getValidTo() == null || at.isBefore(mapping.getValidTo()))
                                ? List.of(mapping) : List.of();
                    });
            when(mapper.selectAvailabilityByLot(anyLong(), anyString())).thenAnswer(i -> availability);
            doAnswer(i -> { events.add(i.getArgument(0)); return null; }).when(outboxAppender).append(any());
        }

        InventoryLotResult execute(InventoryLotCommand command) {
            return service.execute(command);
        }

        private void stubOperations() {
            doAnswer(i -> {
                String key = i.getArgument(1);
                String sourceEventId = i.getArgument(2);
                String commandType = i.getArgument(3);
                String hash = i.getArgument(4);
                String attempt = i.getArgument(5);
                Long id = operationByKey.get(key);
                if (id == null) {
                    id = sequence.incrementAndGet();
                    operationByKey.put(key, id);
                    operations.put(id, new InventoryLotOperationDO().setOperationId(id).setTenantId(9L)
                            .setIdempotencyKey(key).setSourceEventId(sourceEventId).setCommandType(commandType)
                            .setRequestHash(hash).setAttemptToken(attempt).setStatus(0));
                }
                lastOperation.set(id);
                return 1;
            }).when(mapper).insertOrResolveOperation(anyLong(), anyString(), nullable(String.class), anyString(),
                    anyString(), anyString(), any());
            when(mapper.selectLastInsertId()).thenAnswer(i -> lastOperation.get());
            when(mapper.selectOperationForUpdate(anyLong(), eq(9L)))
                    .thenAnswer(i -> operations.get(i.getArgument(0)));
            when(mapper.markOperationSucceeded(anyLong(), eq(9L), anyString(), nullable(String.class),
                    anyString(), any())).thenAnswer(i -> {
                InventoryLotOperationDO operation = operations.get(i.getArgument(0));
                operation.setStatus(10).setLotId(i.getArgument(2)).setMappingId(i.getArgument(3))
                        .setResultJson(i.getArgument(4));
                return 1;
            });
        }
    }
}
