package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryCommandServiceImplTest {

    private final InventoryOperationMapper operationMapper = mock(InventoryOperationMapper.class);
    private final InventoryBalanceMapper balanceMapper = mock(InventoryBalanceMapper.class);
    private final InventoryReservationMapper reservationMapper = mock(InventoryReservationMapper.class);
    private final InventoryLedgerTransactionMapper ledgerTransactionMapper = mock(InventoryLedgerTransactionMapper.class);
    private final InventoryLedgerEntryMapper ledgerEntryMapper = mock(InventoryLedgerEntryMapper.class);
    private final InventoryMigrationStoreMapper migrationMapper = mock(InventoryMigrationStoreMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CatalogSkuValidationApi catalogSkuValidationApi = mock(CatalogSkuValidationApi.class);
    private final InventoryCommandServiceImpl service = new InventoryCommandServiceImpl(operationMapper, balanceMapper,
            reservationMapper, ledgerTransactionMapper, ledgerEntryMapper, migrationMapper,
            outboxAppender, catalogSkuValidationApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReceiveIntoNewBalanceAndAppendSameVersionEvent() {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq("RECEIVE"), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(5));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new InventoryOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(balanceMapper.insertOrResolve(anyString(), eq(1L), anyString(), anyString(), anyString(),
                eq("SELLABLE"), eq("QUALIFIED"), eq("PIECE"), any())).thenReturn(1);
        when(balanceMapper.selectDimensionForUpdate(eq(1L), anyString(), anyString(), anyString(),
                eq("SELLABLE"), eq("QUALIFIED"))).thenReturn(balance("balance-1", "0.000000", "0.000000", 0L));
        when(balanceMapper.updateBalanceCas(eq(1L), eq("balance-1"), eq(0L),
                eq(new BigDecimal("10.000000")), eq(new BigDecimal("0.000000")), any())).thenReturn(1);
        when(ledgerTransactionMapper.insert(any(InventoryLedgerTransactionDO.class))).thenAnswer(invocation -> {
            invocation.<InventoryLedgerTransactionDO>getArgument(0).setLedgerTransactionId(201L);
            return 1;
        });
        when(ledgerEntryMapper.insert(any(InventoryLedgerEntryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), eq(201L), anyString(), any())).thenReturn(1);

        InventoryCommandResult result = service.execute(command(InventoryOperation.RECEIVE, "10.000000", null));

        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getOnHandQuantity()).isEqualByComparingTo("10.000000");
        assertThat(result.getReservedQuantity()).isEqualByComparingTo("0.000000");
        verify(outboxAppender).append(argThat(event -> event.getAggregateVersion() == 1L
                && event.getSourceSystem().equals("cloudmold-inventory")
                && event.getPayload().get("after_available_quantity").equals("10.000000")));
    }

    @Test
    void shouldReturnImmutableFirstResultForReplay() {
        InventoryCommandResult first = InventoryCommandResult.builder()
                .operationId(101L).ledgerTransactionId(201L).balanceId("balance-1")
                .aggregateVersion(1L).onHandQuantity(new BigDecimal("10.000000"))
                .reservedQuantity(new BigDecimal("0.000000")).availableQuantity(new BigDecimal("10.000000"))
                .duplicate(false).build();
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenReturn(new InventoryOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken("original-attempt")
                .setRequestHash(fingerprintFromFirstExecution()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        InventoryCommandResult replay = service.execute(command(InventoryOperation.RECEIVE, "10.000000", null));

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getAggregateVersion()).isEqualTo(1L);
        verifyNoInteractions(balanceMapper, reservationMapper, ledgerTransactionMapper, ledgerEntryMapper, outboxAppender);
        verifyNoInteractions(catalogSkuValidationApi, migrationMapper);
    }

    @Test
    void shouldRejectNewLegacyWriteAfterCanonicalMigration() {
        prepareNewOperation("RECEIVE");
        when(balanceMapper.insertOrResolve(anyString(), eq(1L), anyString(), anyString(), anyString(),
                eq("SELLABLE"), eq("QUALIFIED"), eq("PIECE"), any())).thenReturn(0);
        when(balanceMapper.selectDimensionForUpdate(eq(1L), anyString(), anyString(), anyString(),
                eq("SELLABLE"), eq("QUALIFIED")))
                .thenReturn(balance("migrated-balance", "10.000000", "0.000000", 1L));
        when(migrationMapper.selectResolvedBridgeIdForLegacyBalanceForUpdate(1L, "migrated-balance"))
                .thenReturn("bridge-1");

        assertThatThrownBy(() -> service.execute(command(InventoryOperation.RECEIVE, "1.000000", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("legacy inventory balance is frozen after canonical migration");

        verify(balanceMapper, never()).updateBalanceCas(anyLong(), anyString(), anyLong(), any(), any(), any());
        verifyNoInteractions(ledgerTransactionMapper, ledgerEntryMapper, outboxAppender);
        InOrder lockOrder = inOrder(balanceMapper, migrationMapper);
        lockOrder.verify(balanceMapper).selectDimensionForUpdate(eq(1L), anyString(), anyString(), anyString(),
                eq("SELLABLE"), eq("QUALIFIED"));
        lockOrder.verify(migrationMapper).selectResolvedBridgeIdForLegacyBalanceForUpdate(1L, "migrated-balance");
    }

    @Test
    void shouldRejectReserveThatWouldOversell() {
        prepareNewOperation("RESERVE");
        when(balanceMapper.selectDimensionForUpdate(eq(1L), anyString(), anyString(), anyString(),
                eq("SELLABLE"), eq("QUALIFIED"))).thenReturn(balance("balance-1", "10.000000", "8.000000", 2L));

        assertThatThrownBy(() -> service.execute(command(InventoryOperation.RESERVE, "3.000000", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("insufficient available inventory");

        verify(balanceMapper, never()).updateBalanceCas(anyLong(), anyString(), anyLong(), any(), any(), any());
        verifyNoInteractions(ledgerTransactionMapper, ledgerEntryMapper, outboxAppender);
    }

    @Test
    void shouldRejectClosingTerminalReservation() {
        prepareNewOperation("SHIP");
        InventoryReservationDO closed = new InventoryReservationDO()
                .setReservationId("reservation-1").setTenantId(1L).setBalanceId("balance-1")
                .setQuantity(new BigDecimal("3.000000")).setStatus(20);
        when(reservationMapper.selectHint(1L, "reservation-1")).thenReturn(closed);
        when(balanceMapper.selectByIdForUpdate(1L, "balance-1"))
                .thenReturn(balance("balance-1", "7.000000", "0.000000", 3L));
        when(reservationMapper.selectForUpdate(1L, "reservation-1")).thenReturn(closed);

        assertThatThrownBy(() -> service.execute(command(InventoryOperation.SHIP, "3.000000", "reservation-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("reservation is not active");

        verify(balanceMapper, never()).updateBalanceCas(anyLong(), anyString(), anyLong(), any(), any(), any());
    }

    @Test
    void shouldRejectMoreThanSixFractionalDigitsBeforeWriting() {
        InventoryCommand command = command(InventoryOperation.RECEIVE, "1.0000001", null);

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity supports at most 6 fractional digits");
        verifyNoInteractions(operationMapper, balanceMapper, reservationMapper, ledgerTransactionMapper,
                ledgerEntryMapper, migrationMapper, outboxAppender);
    }

    @Test
    void shouldRejectInventoryWriteForInactiveCanonicalSku() {
        prepareNewOperation("RECEIVE");
        doThrow(new IllegalArgumentException("canonical SKU is not ACTIVE"))
                .when(catalogSkuValidationApi).requireActiveSku("sku-1");

        assertThatThrownBy(() -> service.execute(command(InventoryOperation.RECEIVE, "1.000000", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("canonical SKU is not ACTIVE");

        verifyNoInteractions(balanceMapper, reservationMapper, migrationMapper,
                ledgerTransactionMapper, ledgerEntryMapper, outboxAppender);
    }

    @Test
    void shouldDisposeReturnedNonSellableInventoryWithImmutableLedgerEvidence() {
        prepareNewOperation("DISPOSE");
        when(balanceMapper.selectDimensionForUpdate(1L, "owner-1", "sku-1", "warehouse-1",
                "NON_SELLABLE", "DAMAGED"))
                .thenReturn(new InventoryBalanceDO().setBalanceId("damaged-balance-1").setTenantId(1L)
                        .setOwnerId("owner-1").setCanonicalSkuId("sku-1").setWarehouseId("warehouse-1")
                        .setStockStatus("NON_SELLABLE").setQualityStatus("DAMAGED").setBaseUomCode("PIECE")
                        .setOnHandQuantity(new BigDecimal("2.000000"))
                        .setReservedQuantity(new BigDecimal("0.000000"))
                        .setInTransitQuantity(new BigDecimal("0.000000")).setVersion(7L));
        when(balanceMapper.updateBalanceCas(eq(1L), eq("damaged-balance-1"), eq(7L),
                eq(new BigDecimal("0.000000")), eq(new BigDecimal("0.000000")), any())).thenReturn(1);
        when(ledgerTransactionMapper.insert(any(InventoryLedgerTransactionDO.class))).thenAnswer(invocation -> {
            invocation.<InventoryLedgerTransactionDO>getArgument(0).setLedgerTransactionId(301L);
            return 1;
        });
        when(ledgerEntryMapper.insert(any(InventoryLedgerEntryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-dispose", "b".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), eq(301L), anyString(), any())).thenReturn(1);

        InventoryCommand command = command(InventoryOperation.DISPOSE, "2.000000", null)
                .setStockStatus("NON_SELLABLE").setQualityStatus("DAMAGED")
                .setBusinessType("AFTER_SALE_DISPOSAL");
        InventoryCommandResult result = service.execute(command);

        assertThat(result.getOnHandQuantity()).isEqualByComparingTo("0.000000");
        assertThat(result.getAvailableQuantity()).isEqualByComparingTo("0.000000");
        ArgumentCaptor<InventoryLedgerTransactionDO> transaction =
                ArgumentCaptor.forClass(InventoryLedgerTransactionDO.class);
        verify(ledgerTransactionMapper).insert(transaction.capture());
        assertThat(transaction.getValue().getCommandType()).isEqualTo("DISPOSE");
        verify(outboxAppender).append(argThat(event -> "RETURN_DISPOSAL".equals(
                event.getPayload().get("movement_type"))
                && "0.000000".equals(event.getPayload().get("after_on_hand_quantity"))));
    }

    private void prepareNewOperation(String type) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq(type), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(5));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new InventoryOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
    }

    private static InventoryBalanceDO balance(String id, String onHand, String reserved, long version) {
        return new InventoryBalanceDO().setBalanceId(id).setTenantId(1L).setOwnerId("owner-1")
                .setCanonicalSkuId("sku-1").setWarehouseId("warehouse-1")
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PIECE")
                .setOnHandQuantity(new BigDecimal(onHand)).setReservedQuantity(new BigDecimal(reserved))
                .setInTransitQuantity(new BigDecimal("0.000000")).setVersion(version);
    }

    private static InventoryCommand command(InventoryOperation operation, String quantity, String reservationId) {
        return InventoryCommand.builder().operation(operation).idempotencyKey("inventory-test-key-0001")
                .ownerId("owner-1").canonicalSkuId("sku-1").warehouseId("warehouse-1")
                .stockStatus("SELLABLE").qualityStatus("QUALIFIED").uomCode("PIECE")
                .quantity(new BigDecimal(quantity)).reservationId(reservationId)
                .businessType("TEST").businessId("business-1").businessItemId("line-1").businessNo("TEST-1")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T10:00:00Z")).build();
    }

    private static String fingerprintFromFirstExecution() {
        String canonical = String.join("\u001f", "1", "RECEIVE", "owner-1", "sku-1", "warehouse-1",
                "SELLABLE", "QUALIFIED", "PIECE", "10.000000", "", "TEST", "business-1", "line-1",
                "TEST-1", "", "2026-07-12T10:00:00Z");
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(canonical);
    }

}
