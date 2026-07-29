package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Operation;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class InventoryV3CommandServiceImplTest {

    private static final String OWNER = "10000000-0000-4000-8000-000000000001";
    private static final String SKU = "10000000-0000-4000-8000-000000000002";
    private static final String WAREHOUSE = "10000000-0000-4000-8000-000000000003";
    private static final String LOCATION = "10000000-0000-4000-8000-000000000004";
    private static final String TARGET_LOCATION = "10000000-0000-4000-8000-000000000009";
    private static final String LOT = "10000000-0000-4000-8000-000000000005";
    private static final String RESERVATION = "10000000-0000-4000-8000-000000000006";
    private static final String CORRELATION = "10000000-0000-4000-8000-000000000007";
    private static final String BALANCE = "10000000-0000-4000-8000-000000000008";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T01:00:00Z");

    private final InventoryV3OperationMapper operationMapper = mock(InventoryV3OperationMapper.class);
    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryLotMapper lotMapper = mock(InventoryLotMapper.class);
    private final InventoryV3ReservationMapper reservationMapper = mock(InventoryV3ReservationMapper.class);
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper =
            mock(InventoryV3LedgerTransactionMapper.class);
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper = mock(InventoryV3LedgerEntryMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CatalogSkuValidationApi catalogSkuValidationApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouseValidationApi =
            mock(WarehouseReferenceValidationApi.class);
    private final MerchantOwnerValidationApi merchantOwnerValidationApi = mock(MerchantOwnerValidationApi.class);
    private final InventoryV3CommandServiceImpl service = new InventoryV3CommandServiceImpl(operationMapper,
            balanceMapper, lotMapper, reservationMapper, ledgerTransactionMapper, ledgerEntryMapper,
            outboxAppender, catalogSkuValidationApi, warehouseValidationApi, merchantOwnerValidationApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReceiveIntoCanonicalLocationAndAppendSchema3Event() {
        prepareNewOperation("RECEIVE");
        prepareBalanceWrite(balance(null, "SELLABLE", "QUALIFIED", "0.000000", "0.000000", 0L));

        InventoryV3CommandResult result = service.execute(command(InventoryV3Operation.RECEIVE,
                "10.000000", null, null, "SELLABLE", "QUALIFIED"));

        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getOnHandQuantity()).isEqualByComparingTo("10.000000");
        assertThat(result.getAvailableQuantity()).isEqualByComparingTo("10.000000");
        verify(merchantOwnerValidationApi).requireActiveMerchant(OWNER);
        verify(catalogSkuValidationApi).requireActiveSku(SKU);
        verify(warehouseValidationApi).requireActiveLocation(WAREHOUSE, LOCATION);
        verify(outboxAppender).append(argThat(event -> event.getSchemaVersion() == 3
                && event.getEventType().equals("inventory.stock.changed")
                && event.getAggregateType().equals("inventory_balance_v3")
                && event.getPayload().get("owner_id").equals(OWNER)
                && event.getPayload().get("warehouse_id").equals(WAREHOUSE)
                && event.getPayload().get("location_id").equals(LOCATION)
                && event.getPayload().get("delta_on_hand_quantity").equals("10.000000")
                && event.getPayload().get("after_available_quantity").equals("10.000000")
                && event.getPayload().get("movement_type").equals("PURCHASE_RECEIPT")
                && event.getPayload().get("ledger_transaction_id").equals(201L)));
    }

    @Test
    void shouldEmitSchema5WhenProcurementCostEvidenceIsComplete() {
        prepareNewOperation("RECEIVE");
        prepareBalanceWrite(balance(null, "SELLABLE", "QUALIFIED", "0.000000", "0.000000", 0L));
        InventoryV3Command command = command(InventoryV3Operation.RECEIVE,
                "10.000000", null, null, "SELLABLE", "QUALIFIED")
                .setUnitCostAmountMinor(12000L).setMovementCostAmountMinor(120000L)
                .setCurrencyCode("cny").setCostSourceSystem("cloudmold-procurement")
                .setCostSourceRef("receipt-line:PRL-001").setCostPolicyVersion("FIFO-V1");

        service.execute(command);

        verify(outboxAppender).append(argThat(event -> event.getSchemaVersion() == 5
                && event.getPayload().get("unit_cost_amount_minor").equals(12000L)
                && event.getPayload().get("movement_cost_amount_minor").equals(120000L)
                && event.getPayload().get("currency_code").equals("CNY")
                && event.getPayload().get("cost_source_ref").equals("receipt-line:PRL-001")
                && event.getIdempotencyKey().endsWith(":event:v5:SINGLE")));
    }

    @Test
    void shouldRejectPartialOrInconsistentCostEvidenceBeforeBalanceMutation() {
        InventoryV3Command partial = command(InventoryV3Operation.RECEIVE,
                "2.000000", null, null, "SELLABLE", "QUALIFIED")
                .setUnitCostAmountMinor(100L);
        assertThatThrownBy(() -> service.execute(partial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("movementCostAmountMinor is required and cannot be negative");

        InventoryV3Command inconsistent = command(InventoryV3Operation.RECEIVE,
                "2.000000", null, null, "SELLABLE", "QUALIFIED")
                .setUnitCostAmountMinor(100L).setMovementCostAmountMinor(199L).setCurrencyCode("CNY")
                .setCostSourceSystem("cloudmold-procurement").setCostSourceRef("receipt-line:bad")
                .setCostPolicyVersion("FIFO-V1");
        assertThatThrownBy(() -> service.execute(inconsistent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("movementCostAmountMinor must equal quantity multiplied by unitCostAmountMinor");
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper, ledgerEntryMapper,
                outboxAppender);
    }

    @Test
    void shouldFailClosedBeforeWritingForInactiveMerchantOrLocation() {
        prepareNewOperation("RECEIVE");
        doThrow(new IllegalArgumentException("merchant is not ACTIVE"))
                .when(merchantOwnerValidationApi).requireActiveMerchant(OWNER);

        assertThatThrownBy(() -> service.execute(command(InventoryV3Operation.RECEIVE,
                "1.000000", null, null, "SELLABLE", "QUALIFIED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("merchant is not ACTIVE");
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper,
                ledgerEntryMapper, outboxAppender);

        reset(merchantOwnerValidationApi);
        doThrow(new IllegalArgumentException("warehouse location is not ACTIVE"))
                .when(warehouseValidationApi).requireActiveLocation(WAREHOUSE, LOCATION);
        assertThatThrownBy(() -> service.execute(command(InventoryV3Operation.RECEIVE,
                "1.000000", null, null, "SELLABLE", "QUALIFIED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("warehouse location is not ACTIVE");
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper,
                ledgerEntryMapper, outboxAppender);
    }

    @Test
    void shouldReplayImmutableResultEvenAfterMasterDataBecomesInactive() {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq("RECEIVE"), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            attemptToken.set(invocation.getArgument(5));
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new InventoryV3OperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        prepareBalanceWrite(balance(null, "SELLABLE", "QUALIFIED", "0.000000", "0.000000", 0L));
        InventoryV3Command sameCommand = command(InventoryV3Operation.RECEIVE,
                "1.000000", null, null, "SELLABLE", "QUALIFIED");
        InventoryV3CommandResult first = service.execute(sameCommand);

        clearInvocations(merchantOwnerValidationApi, catalogSkuValidationApi, warehouseValidationApi, lotMapper,
                balanceMapper, reservationMapper, ledgerTransactionMapper, ledgerEntryMapper, outboxAppender);
        doThrow(new IllegalArgumentException("merchant is not ACTIVE"))
                .when(merchantOwnerValidationApi).requireActiveMerchant(OWNER);
        when(operationMapper.selectForUpdate(101L, 1L)).thenReturn(new InventoryV3OperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken("original-attempt")
                .setRequestHash(requestHash.get()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        InventoryV3CommandResult replay = service.execute(sameCommand);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getLedgerTransactionId()).isEqualTo(first.getLedgerTransactionId());
        verifyNoInteractions(merchantOwnerValidationApi, catalogSkuValidationApi, warehouseValidationApi, lotMapper,
                balanceMapper, reservationMapper, ledgerTransactionMapper, ledgerEntryMapper, outboxAppender);
    }

    @Test
    void shouldKeepLotAndQualityAsExactBalanceDimensions() {
        prepareNewOperation("RECEIVE");
        when(lotMapper.selectCurrent(1L, LOT)).thenReturn(lot(LocalDate.of(2027, 7, 15), "ACTIVE"));
        prepareBalanceWrite(balance(LOT, "NON_SELLABLE", "DAMAGED", "0.000000", "0.000000", 0L));

        InventoryV3CommandResult result = service.execute(command(InventoryV3Operation.RECEIVE,
                "2.000000", null, LOT, "NON_SELLABLE", "DAMAGED"));

        assertThat(result.getAvailableQuantity()).isEqualByComparingTo("0.000000");
        verify(balanceMapper).insertOrResolve(anyString(), eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), eq(LOT), eq("NON_SELLABLE"), eq("DAMAGED"), eq("PIECE"), any());
        verify(balanceMapper).selectDimensionForUpdate(1L, "MERCHANT", OWNER, SKU, WAREHOUSE, LOCATION,
                LOT, "NON_SELLABLE", "DAMAGED");
        verify(outboxAppender).append(argThat(event -> event.getPayload().get("lot_id").equals(LOT)
                && event.getPayload().get("lot_code").equals("LOT-001")
                && event.getPayload().get("quality_status").equals("DAMAGED")
                && event.getPayload().get("after_available_quantity").equals("0.000000")));
    }

    @Test
    void shouldRejectIdempotencyConflictAcrossFullV3Fingerprint() {
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenReturn(new InventoryV3OperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken("original-attempt")
                .setRequestHash("different-fingerprint").setStatus(10).setResultJson("{}"));

        assertThatThrownBy(() -> service.execute(command(InventoryV3Operation.RECEIVE,
                "1.000000", null, null, "SELLABLE", "QUALIFIED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotency key or source event conflicts with different payload");
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper, ledgerEntryMapper,
                outboxAppender);
    }

    @Test
    void shouldRejectExpiredOrRecalledLotBeforeReservationWrite() {
        prepareNewOperation("RESERVE");
        when(lotMapper.selectCurrent(1L, LOT)).thenReturn(lot(LocalDate.of(2026, 7, 14), "ACTIVE"));
        assertThatThrownBy(() -> service.execute(command(InventoryV3Operation.RESERVE,
                "1.000000", null, LOT, "SELLABLE", "QUALIFIED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expired inventory lot cannot be reserved or shipped");
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper,
                ledgerEntryMapper, outboxAppender);

        when(lotMapper.selectCurrent(1L, LOT)).thenReturn(lot(LocalDate.of(2027, 7, 15), "RECALLED"));
        assertThatThrownBy(() -> service.execute(command(InventoryV3Operation.RESERVE,
                "1.000000", null, LOT, "SELLABLE", "QUALIFIED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("inventory lot is not ACTIVE");
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper,
                ledgerEntryMapper, outboxAppender);
    }

    @Test
    void shouldCreateSingleAllocationReservationWithoutCollapsingSchema() {
        prepareNewOperation("RESERVE");
        when(balanceMapper.selectDimensionForUpdate(1L, "MERCHANT", OWNER, SKU, WAREHOUSE, LOCATION,
                null, "SELLABLE", "QUALIFIED"))
                .thenReturn(balance(null, "SELLABLE", "QUALIFIED", "10.000000", "0.000000", 1L));
        preparePersistenceSuccess();

        InventoryV3CommandResult result = service.execute(command(InventoryV3Operation.RESERVE,
                "3.000000", null, null, "SELLABLE", "QUALIFIED"));

        assertThat(result.getReservationId()).isNotBlank();
        assertThat(result.getAllocationId()).isNotBlank();
        assertThat(result.getReservedQuantity()).isEqualByComparingTo("3.000000");
        verify(reservationMapper).insert(argThat((InventoryV3ReservationDO row) ->
                row.getQuantity().compareTo(new BigDecimal("3.000000")) == 0 && row.getStatus() == 10));
        verify(reservationMapper).insertAllocation(argThat(row -> row.getReservationId().equals(result.getReservationId())
                && row.getAllocationId().equals(result.getAllocationId())
                && row.getBalanceId().equals(BALANCE)));
    }

    @Test
    void shouldShipAndCloseTheSingleAllocationReservation() {
        prepareNewOperation("SHIP");
        InventoryV3BalanceDO balance = balance(null, "SELLABLE", "QUALIFIED", "10.000000", "3.000000", 2L);
        InventoryV3ReservationDO reservation = new InventoryV3ReservationDO().setReservationId(RESERVATION)
                .setTenantId(1L).setQuantity(new BigDecimal("3.000000")).setStatus(10).setVersion(1L);
        InventoryV3ReservationAllocationDO allocation = new InventoryV3ReservationAllocationDO()
                .setAllocationId("10000000-0000-4000-8000-000000000009").setTenantId(1L)
                .setReservationId(RESERVATION).setBalanceId(BALANCE)
                .setQuantity(new BigDecimal("3.000000")).setStatus(10).setVersion(1L);
        when(reservationMapper.selectAllocationHints(1L, RESERVATION)).thenReturn(List.of(allocation));
        when(balanceMapper.selectByIdForUpdate(1L, BALANCE)).thenReturn(balance);
        when(reservationMapper.selectForUpdate(1L, RESERVATION)).thenReturn(reservation);
        when(reservationMapper.selectAllocationForUpdate(1L, RESERVATION, allocation.getAllocationId()))
                .thenReturn(allocation);
        when(reservationMapper.closeAllocation(eq(1L), eq(allocation.getAllocationId()), eq(20), eq(101L), any()))
                .thenReturn(1);
        when(reservationMapper.closeReservation(eq(1L), eq(RESERVATION), eq(20), eq(101L), any()))
                .thenReturn(1);
        preparePersistenceSuccess();

        InventoryV3CommandResult result = service.execute(command(InventoryV3Operation.SHIP,
                "3.000000", RESERVATION, null, "SELLABLE", "QUALIFIED"));

        assertThat(result.getOnHandQuantity()).isEqualByComparingTo("7.000000");
        assertThat(result.getReservedQuantity()).isEqualByComparingTo("0.000000");
        verify(balanceMapper).updateBalanceCas(eq(1L), eq(BALANCE), eq(2L),
                eq(new BigDecimal("7.000000")), eq(new BigDecimal("0.000000")),
                eq(new BigDecimal("0.000000")), any());
        verify(outboxAppender).append(argThat(event -> event.getPayload().get("movement_type").equals("SALE_SHIPMENT")
                && event.getPayload().get("reservation_id").equals(RESERVATION)
                && event.getPayload().get("delta_on_hand_quantity").equals("-3.000000")
                && event.getPayload().get("delta_reserved_quantity").equals("-3.000000")));
    }

    @Test
    void shouldFlipQualityReleaseAcrossDualLedgerWithCounterpartyEntries() {
        prepareNewOperation("QUALITY_RELEASE");
        // 源维度：NON_SELLABLE/PENDING_QC 收货暂存余额（onHand=10），目标维度：SELLABLE/QUALIFIED 可售余额（onHand=0），二者 balanceId 不同
        InventoryV3BalanceDO source = balanceWith("10000000-0000-4000-8000-000000000010", null,
                "NON_SELLABLE", "PENDING_QC", "10.000000", "0.000000", 1L);
        InventoryV3BalanceDO target = balanceWith("10000000-0000-4000-8000-000000000020", null,
                "SELLABLE", "QUALIFIED", "0.000000", "0.000000", 1L);
        // 按维度参数返回对应余额（源/目标维度不同，两把 FOR UPDATE 各自命中）
        when(balanceMapper.selectDimensionForUpdate(eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), nullable(String.class), eq("NON_SELLABLE"), eq("PENDING_QC")))
                .thenReturn(source);
        when(balanceMapper.selectDimensionForUpdate(eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), nullable(String.class), eq("SELLABLE"), eq("QUALIFIED")))
                .thenReturn(target);
        when(balanceMapper.insertOrResolve(anyString(), eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), nullable(String.class), anyString(), anyString(), eq("PIECE"), any()))
                .thenReturn(1);
        // CAS 接受任意 balanceId（双账两个不同 balance 都要走 CAS）
        when(balanceMapper.updateBalanceCas(eq(1L), anyString(), anyLong(), any(), any(), any(), any())).thenReturn(1);
        when(ledgerTransactionMapper.insert(any(InventoryV3LedgerTransactionDO.class))).thenAnswer(invocation -> {
            invocation.<InventoryV3LedgerTransactionDO>getArgument(0).setLedgerTransactionId(201L);
            return 1;
        });
        when(ledgerEntryMapper.insert(any(InventoryV3LedgerEntryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-v3", "a".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), eq(201L), anyString(), any())).thenReturn(1);

        InventoryV3Command command = InventoryV3Command.builder().operation(InventoryV3Operation.QUALITY_RELEASE)
                .idempotencyKey("inventory-v3-qc-release-key")
                .ownerType("MERCHANT").ownerId(OWNER).canonicalSkuId(SKU).warehouseId(WAREHOUSE)
                .locationId(LOCATION).lotId(null)
                .stockStatus("NON_SELLABLE").qualityStatus("PENDING_QC")
                .targetStockStatus("SELLABLE").targetQualityStatus("QUALIFIED")
                .baseUomCode("PIECE").quantity(new BigDecimal("7.000000"))
                .businessType("QC").businessId("qc-task-1").businessItemId("receipt-line-1").businessNo("QC-1")
                .correlationId(CORRELATION).occurredAt(OCCURRED_AT).build();

        InventoryV3CommandResult result = service.execute(command);

        // 返回源余额快照：翻转出 7 后 onHand=3
        assertThat(result.getBalanceId()).isEqualTo("10000000-0000-4000-8000-000000000010");
        assertThat(result.getOnHandQuantity()).isEqualByComparingTo("3.000000");

        // 两条 entry：OUT（源 -7，counterparty=目标）+ IN（目标 +7，counterparty=源），delta 互为相反数即量守恒
        ArgumentCaptor<InventoryV3LedgerEntryDO> entryCaptor = ArgumentCaptor.forClass(InventoryV3LedgerEntryDO.class);
        verify(ledgerEntryMapper, times(2)).insert(entryCaptor.capture());
        InventoryV3LedgerEntryDO outEntry = entryCaptor.getAllValues().stream()
                .filter(e -> "OUT".equals(e.getEntryRole())).findFirst().orElseThrow();
        InventoryV3LedgerEntryDO inEntry = entryCaptor.getAllValues().stream()
                .filter(e -> "IN".equals(e.getEntryRole())).findFirst().orElseThrow();
        assertThat(outEntry.getBalanceId()).isEqualTo("10000000-0000-4000-8000-000000000010");
        assertThat(outEntry.getCounterpartyBalanceId()).isEqualTo("10000000-0000-4000-8000-000000000020");
        assertThat(outEntry.getDeltaOnHandQuantity()).isEqualByComparingTo("-7.000000");
        assertThat(outEntry.getAfterOnHandQuantity()).isEqualByComparingTo("3.000000");
        assertThat(inEntry.getBalanceId()).isEqualTo("10000000-0000-4000-8000-000000000020");
        assertThat(inEntry.getCounterpartyBalanceId()).isEqualTo("10000000-0000-4000-8000-000000000010");
        assertThat(inEntry.getDeltaOnHandQuantity()).isEqualByComparingTo("7.000000");
        assertThat(inEntry.getAfterOnHandQuantity()).isEqualByComparingTo("7.000000");
        assertThat(inEntry.getDeltaOnHandQuantity().add(outEntry.getDeltaOnHandQuantity()))
                .isEqualByComparingTo("0.000000");

        // 两个事件（OUT/IN），counterparty 互指、各自带正确的维度与 movement_type
        ArgumentCaptor<AppendDomainEventCommand> eventCaptor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(eventCaptor.capture());
        Map<String, Object> outPayload = eventCaptor.getAllValues().get(0).getPayload();
        Map<String, Object> inPayload = eventCaptor.getAllValues().get(1).getPayload();
        assertThat(outPayload.get("entry_role")).isEqualTo("OUT");
        assertThat(outPayload.get("counterparty_balance_id")).isEqualTo("10000000-0000-4000-8000-000000000020");
        assertThat(outPayload.get("stock_status")).isEqualTo("NON_SELLABLE");
        assertThat(outPayload.get("movement_type")).isEqualTo("QUALITY_RELEASE");
        assertThat(inPayload.get("entry_role")).isEqualTo("IN");
        assertThat(inPayload.get("counterparty_balance_id")).isEqualTo("10000000-0000-4000-8000-000000000010");
        assertThat(inPayload.get("stock_status")).isEqualTo("SELLABLE");
    }

    @Test
    void shouldFailClosedWhenQualityReleaseMissingTargetDimensions() {
        prepareNewOperation("QUALITY_RELEASE");
        InventoryV3Command missing = InventoryV3Command.builder().operation(InventoryV3Operation.QUALITY_RELEASE)
                .idempotencyKey("inventory-v3-qc-missing-target")
                .ownerType("MERCHANT").ownerId(OWNER).canonicalSkuId(SKU).warehouseId(WAREHOUSE)
                .locationId(LOCATION).lotId(null)
                .stockStatus("NON_SELLABLE").qualityStatus("PENDING_QC")
                .baseUomCode("PIECE").quantity(new BigDecimal("7.000000"))
                .businessType("QC").businessId("qc-task-1").businessItemId("receipt-line-1").businessNo("QC-1")
                .correlationId(CORRELATION).occurredAt(OCCURRED_AT).build();
        assertThatThrownBy(() -> service.execute(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetStockStatus");
    }

    @Test
    void shouldRelocateQualifiedInventoryAcrossLocationsWithConservedDualLedger() {
        prepareNewOperation("RELOCATE");
        InventoryV3BalanceDO source = balanceWith("10000000-0000-4000-8000-000000000030", null,
                "SELLABLE", "QUALIFIED", "8.000000", "0.000000", 2L);
        InventoryV3BalanceDO target = balanceWith("10000000-0000-4000-8000-000000000040", null,
                "SELLABLE", "QUALIFIED", "1.000000", "0.000000", 3L).setLocationId(TARGET_LOCATION);
        when(balanceMapper.selectDimensionForUpdate(eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), nullable(String.class), eq("SELLABLE"), eq("QUALIFIED")))
                .thenReturn(source);
        when(balanceMapper.selectDimensionForUpdate(eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(TARGET_LOCATION), nullable(String.class), eq("SELLABLE"), eq("QUALIFIED")))
                .thenReturn(target);
        when(balanceMapper.insertOrResolve(anyString(), eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(TARGET_LOCATION), nullable(String.class), eq("SELLABLE"), eq("QUALIFIED"),
                eq("PIECE"), any())).thenReturn(1);
        when(balanceMapper.updateBalanceCas(eq(1L), anyString(), anyLong(), any(), any(), any(), any())).thenReturn(1);
        when(ledgerTransactionMapper.insert(any(InventoryV3LedgerTransactionDO.class))).thenAnswer(invocation -> {
            invocation.<InventoryV3LedgerTransactionDO>getArgument(0).setLedgerTransactionId(202L);
            return 1;
        });
        when(ledgerEntryMapper.insert(any(InventoryV3LedgerEntryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-v3", "a".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), eq(202L), anyString(), any())).thenReturn(1);

        InventoryV3Command command = InventoryV3Command.builder().operation(InventoryV3Operation.RELOCATE)
                .idempotencyKey("inventory-v3-relocate-key")
                .ownerType("MERCHANT").ownerId(OWNER).canonicalSkuId(SKU).warehouseId(WAREHOUSE)
                .locationId(LOCATION).targetLocationId(TARGET_LOCATION).lotId(null)
                .stockStatus("SELLABLE").qualityStatus("QUALIFIED")
                .baseUomCode("PIECE").quantity(new BigDecimal("5.000000"))
                .businessType("INBOUND_PUTAWAY").businessId("putaway-1").businessItemId("receipt-line-1")
                .businessNo("PUTAWAY-1").correlationId(CORRELATION).occurredAt(OCCURRED_AT).build();

        service.execute(command);

        ArgumentCaptor<InventoryV3LedgerEntryDO> entryCaptor = ArgumentCaptor.forClass(InventoryV3LedgerEntryDO.class);
        verify(ledgerEntryMapper, times(2)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues()).extracting(InventoryV3LedgerEntryDO::getDeltaOnHandQuantity)
                .containsExactlyInAnyOrder(new BigDecimal("-5.000000"), new BigDecimal("5.000000"));
        ArgumentCaptor<AppendDomainEventCommand> eventCaptor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(event -> event.getPayload().get("location_id"))
                .containsExactly(LOCATION, TARGET_LOCATION);
        assertThat(eventCaptor.getAllValues()).allSatisfy(event ->
                assertThat(event.getPayload().get("movement_type")).isEqualTo("RELOCATE"));
        verify(warehouseValidationApi).requireActiveLocation(WAREHOUSE, TARGET_LOCATION);
    }

    private void prepareNewOperation(String type) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq(type), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(5));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new InventoryV3OperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
    }

    private void prepareBalanceWrite(InventoryV3BalanceDO balance) {
        when(balanceMapper.insertOrResolve(anyString(), eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), nullable(String.class), anyString(), anyString(), eq("PIECE"), any()))
                .thenReturn(1);
        when(balanceMapper.selectDimensionForUpdate(eq(1L), eq("MERCHANT"), eq(OWNER), eq(SKU),
                eq(WAREHOUSE), eq(LOCATION), nullable(String.class), anyString(), anyString())).thenReturn(balance);
        preparePersistenceSuccess();
    }

    private void preparePersistenceSuccess() {
        when(balanceMapper.updateBalanceCas(eq(1L), eq(BALANCE), anyLong(), any(), any(), any(), any())).thenReturn(1);
        when(ledgerTransactionMapper.insert(any(InventoryV3LedgerTransactionDO.class))).thenAnswer(invocation -> {
            invocation.<InventoryV3LedgerTransactionDO>getArgument(0).setLedgerTransactionId(201L);
            return 1;
        });
        when(ledgerEntryMapper.insert(any(InventoryV3LedgerEntryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-v3", "a".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), eq(201L), anyString(), any())).thenReturn(1);
    }

    private static InventoryLotDO lot(LocalDate expiresOn, String status) {
        return new InventoryLotDO().setLotId(LOT).setTenantId(1L).setOwnerType("MERCHANT").setOwnerId(OWNER)
                .setCanonicalSkuId(SKU).setLotCode("LOT-001").setExpiresOn(expiresOn).setStatus(status).setVersion(1L);
    }

    private static InventoryV3BalanceDO balance(String lotId, String stockStatus, String qualityStatus,
                                                 String onHand, String reserved, long version) {
        return new InventoryV3BalanceDO().setBalanceId(BALANCE).setTenantId(1L).setOwnerType("MERCHANT")
                .setOwnerId(OWNER).setCanonicalSkuId(SKU).setWarehouseId(WAREHOUSE).setLocationId(LOCATION)
                .setLotId(lotId).setStockStatus(stockStatus).setQualityStatus(qualityStatus).setBaseUomCode("PIECE")
                .setOnHandQuantity(new BigDecimal(onHand)).setReservedQuantity(new BigDecimal(reserved))
                .setInTransitQuantity(new BigDecimal("0.000000")).setVersion(version);
    }

    private static InventoryV3BalanceDO balanceWith(String balanceId, String lotId, String stockStatus,
                                                    String qualityStatus, String onHand, String reserved,
                                                    long version) {
        return new InventoryV3BalanceDO().setBalanceId(balanceId).setTenantId(1L).setOwnerType("MERCHANT")
                .setOwnerId(OWNER).setCanonicalSkuId(SKU).setWarehouseId(WAREHOUSE).setLocationId(LOCATION)
                .setLotId(lotId).setStockStatus(stockStatus).setQualityStatus(qualityStatus)
                .setBaseUomCode("PIECE").setOnHandQuantity(new BigDecimal(onHand))
                .setReservedQuantity(new BigDecimal(reserved)).setInTransitQuantity(new BigDecimal("0.000000"))
                .setVersion(version);
    }

    private static InventoryV3Command command(InventoryV3Operation operation, String quantity,
                                               String reservationId, String lotId,
                                               String stockStatus, String qualityStatus) {
        return InventoryV3Command.builder().operation(operation).idempotencyKey("inventory-v3-test-key")
                .ownerType("MERCHANT").ownerId(OWNER).canonicalSkuId(SKU).warehouseId(WAREHOUSE)
                .locationId(LOCATION).lotId(lotId).stockStatus(stockStatus).qualityStatus(qualityStatus)
                .baseUomCode("PIECE").quantity(new BigDecimal(quantity)).reservationId(reservationId)
                .businessType("ORDER").businessId("order-1").businessItemId("line-1").businessNo("ORDER-1")
                .correlationId(CORRELATION).occurredAt(OCCURRED_AT).build();
    }
}
