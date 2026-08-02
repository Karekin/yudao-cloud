package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockTransferDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockTransferOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerEntryDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockTransferMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockTransferOperationMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerEntryMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerTransactionMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryStockTransferServiceImplTest {

    private static final String OWNER = "10000000-0000-4000-8000-000000000001";
    private static final String SKU = "10000000-0000-4000-8000-000000000002";
    private static final String SOURCE_WAREHOUSE = "10000000-0000-4000-8000-000000000003";
    private static final String SOURCE_LOCATION = "10000000-0000-4000-8000-000000000004";
    private static final String TARGET_WAREHOUSE = "10000000-0000-4000-8000-000000000005";
    private static final String TARGET_LOCATION = "10000000-0000-4000-8000-000000000006";
    private static final String MOVEMENT_GROUP = "10000000-0000-4000-8000-000000000007";
    private static final String CORRELATION = "10000000-0000-4000-8000-000000000008";
    private static final String SOURCE_BALANCE = "10000000-0000-4000-8000-000000000009";
    private static final String TARGET_BALANCE = "20000000-0000-4000-8000-000000000001";

    private final InventoryStockTransferOperationMapper operationMapper =
            mock(InventoryStockTransferOperationMapper.class);
    private final InventoryStockTransferMapper transferMapper = mock(InventoryStockTransferMapper.class);
    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryLotMapper lotMapper = mock(InventoryLotMapper.class);
    private final InventoryV3LedgerTransactionMapper transactionMapper =
            mock(InventoryV3LedgerTransactionMapper.class);
    private final InventoryV3LedgerEntryMapper entryMapper = mock(InventoryV3LedgerEntryMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CatalogSkuValidationApi catalogApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouseApi = mock(WarehouseReferenceValidationApi.class);
    private final MerchantOwnerValidationApi merchantApi = mock(MerchantOwnerValidationApi.class);
    private final InventoryStockTransferServiceImpl service = new InventoryStockTransferServiceImpl(
            operationMapper, transferMapper, balanceMapper, lotMapper, transactionMapper, entryMapper,
            outboxAppender, catalogApi, warehouseApi, merchantApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(balanceMapper.updateBalanceCas(anyLong(), anyString(), anyLong(), any(), any(), any(), any()))
                .thenReturn(1);
        when(transferMapper.addDispatched(anyLong(), anyString(), anyLong(), any(), anyLong(), any()))
                .thenReturn(1);
        when(transferMapper.addReceived(anyLong(), anyString(), anyLong(), any(), anyLong(), any()))
                .thenReturn(1);
        when(operationMapper.markSucceeded(anyLong(), anyLong(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            ((InventoryV3LedgerTransactionDO) invocation.getArgument(0)).setLedgerTransactionId(501L);
            return 1;
        }).when(transactionMapper).insert(any(InventoryV3LedgerTransactionDO.class));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void dispatchMovesUnreservedOnHandToInTransitAndPreservesCompanyQuantity() {
        prepareNewOperation();
        prepareTransfer("0.000000", "0.000000", 0L);
        prepareBalances(balance(SOURCE_BALANCE, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                        "10.000000", "2.000000", "0.000000", 3L),
                balance(TARGET_BALANCE, TARGET_WAREHOUSE, TARGET_LOCATION,
                        "1.000000", "0.000000", "0.000000", 1L));

        InventoryStockTransferResult result = service.execute(command(InventoryStockTransferOperation.DISPATCH,
                "3.000000", "dispatch-1"));

        assertThat(result.getSourceOnHandQuantity()).isEqualByComparingTo("7.000000");
        assertThat(result.getSourceInTransitQuantity()).isEqualByComparingTo("3.000000");
        assertThat(result.getTargetOnHandQuantity()).isEqualByComparingTo("1.000000");
        assertThat(result.getOutstandingQuantity()).isEqualByComparingTo("3.000000");
        verify(transferMapper).addDispatched(eq(1L), eq(MOVEMENT_GROUP), eq(0L),
                eq(new BigDecimal("3.000000")), eq(101L), any());
        ArgumentCaptor<InventoryV3LedgerEntryDO> entry = ArgumentCaptor.forClass(InventoryV3LedgerEntryDO.class);
        verify(entryMapper).insert(entry.capture());
        assertThat(entry.getValue().getDeltaOnHandQuantity()).isEqualByComparingTo("-3.000000");
        assertThat(entry.getValue().getDeltaInTransitQuantity()).isEqualByComparingTo("3.000000");
        assertThat(entry.getValue().getDeltaOnHandQuantity().add(entry.getValue().getDeltaInTransitQuantity()))
                .isEqualByComparingTo("0.000000");
        verify(outboxAppender).append(argThat(event -> event.getSchemaVersion() == 6
                && event.getPayload().get("movement_type").equals("STOCK_TRANSFER_DISPATCH")
                && event.getPayload().get("movement_group_id").equals(MOVEMENT_GROUP)));
    }

    @Test
    void receiveSettlesOnlyDispatchedQuantityAndCreditsExactTarget() {
        prepareNewOperation();
        prepareTransfer("5.000000", "2.000000", 4L);
        prepareBalances(balance(SOURCE_BALANCE, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                        "5.000000", "0.000000", "3.000000", 5L),
                balance(TARGET_BALANCE, TARGET_WAREHOUSE, TARGET_LOCATION,
                        "2.000000", "0.000000", "0.000000", 2L));

        InventoryStockTransferResult result = service.execute(command(InventoryStockTransferOperation.RECEIVE,
                "2.000000", "receive-1"));

        assertThat(result.getSourceOnHandQuantity()).isEqualByComparingTo("5.000000");
        assertThat(result.getSourceInTransitQuantity()).isEqualByComparingTo("1.000000");
        assertThat(result.getTargetOnHandQuantity()).isEqualByComparingTo("4.000000");
        assertThat(result.getCumulativeReceivedQuantity()).isEqualByComparingTo("4.000000");
        assertThat(result.getOutstandingQuantity()).isEqualByComparingTo("1.000000");
        ArgumentCaptor<InventoryV3LedgerEntryDO> entries = ArgumentCaptor.forClass(InventoryV3LedgerEntryDO.class);
        verify(entryMapper, times(2)).insert(entries.capture());
        List<InventoryV3LedgerEntryDO> values = entries.getAllValues();
        BigDecimal companyDelta = values.stream()
                .map(item -> item.getDeltaOnHandQuantity().add(item.getDeltaInTransitQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(companyDelta).isEqualByComparingTo("0.000000");
        verify(outboxAppender, times(2)).append(any());
    }

    @Test
    void receiptCannotExceedMovementGroupOutstandingQuantity() {
        prepareNewOperation();
        prepareTransfer("5.000000", "4.000000", 4L);
        prepareBalances(balance(SOURCE_BALANCE, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                        "5.000000", "0.000000", "3.000000", 5L),
                balance(TARGET_BALANCE, TARGET_WAREHOUSE, TARGET_LOCATION,
                        "2.000000", "0.000000", "0.000000", 2L));

        assertThatThrownBy(() -> service.execute(command(InventoryStockTransferOperation.RECEIVE,
                "2.000000", "receive-too-much")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stock transfer receipt exceeds dispatched quantity");
        verifyNoInteractions(transactionMapper, entryMapper, outboxAppender);
    }

    @Test
    void replayUsesTypedImmutableColumnsBeforeCurrentMasterValidation() {
        AtomicReference<String> attempt = new AtomicReference<>();
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), eq("dispatch-replay"), isNull(), eq(MOVEMENT_GROUP),
                eq("DISPATCH"), anyString(), anyString(), any())).thenAnswer(invocation -> {
            requestHash.set(invocation.getArgument(5));
            attempt.set(invocation.getArgument(6));
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(1L, 101L)).thenAnswer(ignored -> completedOperation(requestHash.get()));

        InventoryStockTransferResult replay = service.execute(command(InventoryStockTransferOperation.DISPATCH,
                "1.000000", "dispatch-replay"));

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getLedgerTransactionId()).isEqualTo(501L);
        verifyNoInteractions(merchantApi, catalogApi, warehouseApi, transferMapper, balanceMapper,
                transactionMapper, entryMapper, outboxAppender);
    }

    @Test
    void movementGroupRejectsDifferentTargetIdentity() {
        prepareNewOperation();
        InventoryStockTransferDO existing = transfer("0.000000", "0.000000", 0L)
                .setTargetLocationId("30000000-0000-4000-8000-000000000001");
        when(transferMapper.selectForUpdate(1L, MOVEMENT_GROUP)).thenReturn(existing);

        assertThatThrownBy(() -> service.execute(command(InventoryStockTransferOperation.DISPATCH,
                "1.000000", "identity-conflict")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("movement group conflicts with different transfer identity");
        verifyNoInteractions(balanceMapper, transactionMapper, entryMapper, outboxAppender);
    }

    @Test
    void sameIdempotencyKeyWithChangedPayloadFailsBeforeMasterOrBalanceAccess() {
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(1L, 101L)).thenReturn(new InventoryStockTransferOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken("first-attempt")
                .setRequestHash("different-request-hash").setStatus(10));

        assertThatThrownBy(() -> service.execute(command(InventoryStockTransferOperation.DISPATCH,
                "2.000000", "conflicting-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotency key or source event conflicts with different transfer payload");
        verifyNoInteractions(merchantApi, catalogApi, warehouseApi, transferMapper, balanceMapper,
                transactionMapper, entryMapper, outboxAppender);
    }

    @Test
    void balanceCasConflictRollsBackBeforeLedgerAndOutbox() {
        prepareNewOperation();
        prepareTransfer("0.000000", "0.000000", 0L);
        prepareBalances(balance(SOURCE_BALANCE, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                        "10.000000", "0.000000", "0.000000", 3L),
                balance(TARGET_BALANCE, TARGET_WAREHOUSE, TARGET_LOCATION,
                        "0.000000", "0.000000", "0.000000", 0L));
        when(balanceMapper.updateBalanceCas(eq(1L), eq(SOURCE_BALANCE), eq(3L),
                any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.execute(command(InventoryStockTransferOperation.DISPATCH,
                "1.000000", "cas-conflict")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inventory stock transfer source balance version conflict");
        verifyNoInteractions(transactionMapper, entryMapper, outboxAppender);
    }

    @Test
    void locksBothExactBalancesInLexicographicOrderIndependentOfTransferDirection() {
        String laterSourceId = "30000000-0000-4000-8000-000000000001";
        String earlierTargetId = "20000000-0000-4000-8000-000000000001";
        prepareNewOperation();
        prepareTransfer("0.000000", "0.000000", 0L);
        InventoryV3BalanceDO source = balance(laterSourceId, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                "10.000000", "0.000000", "0.000000", 3L);
        InventoryV3BalanceDO target = balance(earlierTargetId, TARGET_WAREHOUSE, TARGET_LOCATION,
                "0.000000", "0.000000", "0.000000", 0L);
        when(balanceMapper.selectDimension(1L, "MERCHANT", OWNER, SKU, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                null, "SELLABLE", "QUALIFIED")).thenReturn(source);
        when(balanceMapper.selectDimension(1L, "MERCHANT", OWNER, SKU, TARGET_WAREHOUSE, TARGET_LOCATION,
                null, "SELLABLE", "QUALIFIED")).thenReturn(target);
        when(balanceMapper.selectByIdForUpdate(1L, earlierTargetId)).thenReturn(target);
        when(balanceMapper.selectByIdForUpdate(1L, laterSourceId)).thenReturn(source);

        service.execute(command(InventoryStockTransferOperation.DISPATCH, "1.000000", "stable-lock-order"));

        var lockOrder = inOrder(balanceMapper);
        lockOrder.verify(balanceMapper).selectByIdForUpdate(1L, earlierTargetId);
        lockOrder.verify(balanceMapper).selectByIdForUpdate(1L, laterSourceId);
    }

    private void prepareNewOperation() {
        AtomicReference<String> attempt = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq(MOVEMENT_GROUP), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            attempt.set(invocation.getArgument(6));
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(1L, 101L)).thenAnswer(ignored -> new InventoryStockTransferOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attempt.get()).setStatus(0));
    }

    private void prepareTransfer(String dispatched, String received, long version) {
        when(transferMapper.selectForUpdate(1L, MOVEMENT_GROUP)).thenReturn(transfer(dispatched, received, version));
    }

    private void prepareBalances(InventoryV3BalanceDO source, InventoryV3BalanceDO target) {
        when(balanceMapper.selectDimension(1L, "MERCHANT", OWNER, SKU, SOURCE_WAREHOUSE, SOURCE_LOCATION,
                null, "SELLABLE", "QUALIFIED")).thenReturn(source);
        when(balanceMapper.selectDimension(1L, "MERCHANT", OWNER, SKU, TARGET_WAREHOUSE, TARGET_LOCATION,
                null, "SELLABLE", "QUALIFIED")).thenReturn(target);
        when(balanceMapper.selectByIdForUpdate(1L, SOURCE_BALANCE)).thenReturn(source);
        when(balanceMapper.selectByIdForUpdate(1L, TARGET_BALANCE)).thenReturn(target);
    }

    private InventoryStockTransferDO transfer(String dispatched, String received, long version) {
        return new InventoryStockTransferDO().setMovementGroupId(MOVEMENT_GROUP).setTenantId(1L)
                .setOwnerType("MERCHANT").setOwnerId(OWNER).setCanonicalSkuId(SKU)
                .setSourceWarehouseId(SOURCE_WAREHOUSE).setSourceLocationId(SOURCE_LOCATION)
                .setTargetWarehouseId(TARGET_WAREHOUSE).setTargetLocationId(TARGET_LOCATION)
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PIECE")
                .setDispatchedQuantity(new BigDecimal(dispatched)).setReceivedQuantity(new BigDecimal(received))
                .setVersion(version);
    }

    private InventoryV3BalanceDO balance(String id, String warehouseId, String locationId, String onHand,
                                          String reserved, String inTransit, long version) {
        return new InventoryV3BalanceDO().setBalanceId(id).setTenantId(1L).setOwnerType("MERCHANT")
                .setOwnerId(OWNER).setCanonicalSkuId(SKU).setWarehouseId(warehouseId).setLocationId(locationId)
                .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PIECE")
                .setOnHandQuantity(new BigDecimal(onHand)).setReservedQuantity(new BigDecimal(reserved))
                .setInTransitQuantity(new BigDecimal(inTransit)).setVersion(version);
    }

    private InventoryStockTransferCommand command(InventoryStockTransferOperation operation, String quantity,
                                                   String idempotencyKey) {
        return InventoryStockTransferCommand.builder().operation(operation).idempotencyKey(idempotencyKey)
                .movementGroupId(MOVEMENT_GROUP).ownerType("MERCHANT").ownerId(OWNER).canonicalSkuId(SKU)
                .sourceWarehouseId(SOURCE_WAREHOUSE).sourceLocationId(SOURCE_LOCATION)
                .targetWarehouseId(TARGET_WAREHOUSE).targetLocationId(TARGET_LOCATION)
                .stockStatus("SELLABLE").qualityStatus("QUALIFIED").baseUomCode("PIECE")
                .quantity(new BigDecimal(quantity)).businessType("WAREHOUSE_TRANSFER")
                .businessId("transfer-1").businessItemId("line-1").businessNo("TR-001")
                .correlationId(CORRELATION).occurredAt(Instant.parse("2026-08-02T01:00:00Z")).build();
    }

    private InventoryStockTransferOperationDO completedOperation(String requestHash) {
        return new InventoryStockTransferOperationDO().setOperationId(101L).setTenantId(1L)
                .setAttemptToken("first-attempt").setRequestHash(requestHash).setStatus(10)
                .setMovementGroupId(MOVEMENT_GROUP).setLedgerTransactionId(501L)
                .setSourceBalanceId(SOURCE_BALANCE).setSourceAggregateVersion(4L)
                .setSourceOnHandQuantity(new BigDecimal("9.000000"))
                .setSourceInTransitQuantity(new BigDecimal("1.000000"))
                .setTargetBalanceId(TARGET_BALANCE).setTargetAggregateVersion(1L)
                .setTargetOnHandQuantity(new BigDecimal("1.000000"))
                .setTargetInTransitQuantity(new BigDecimal("0.000000"))
                .setCumulativeDispatchedQuantity(new BigDecimal("1.000000"))
                .setCumulativeReceivedQuantity(new BigDecimal("0.000000"))
                .setOutstandingQuantity(new BigDecimal("1.000000"));
    }
}
